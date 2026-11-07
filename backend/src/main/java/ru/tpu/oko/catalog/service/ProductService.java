package ru.tpu.oko.catalog.service;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.auth.repo.AppUserRepository;
import ru.tpu.oko.catalog.api.dto.ProductCreate;
import ru.tpu.oko.catalog.api.dto.ProductDto;
import ru.tpu.oko.catalog.api.dto.ProductUpdate;
import ru.tpu.oko.catalog.repo.ApiKeyRepository;
import ru.tpu.oko.catalog.repo.OwnerRow;
import ru.tpu.oko.catalog.repo.Product;
import ru.tpu.oko.catalog.repo.ProductOwner;
import ru.tpu.oko.catalog.repo.ProductOwnerRepository;
import ru.tpu.oko.catalog.repo.ProductRepository;
import ru.tpu.oko.catalog.repo.ProductStatus;
import ru.tpu.oko.catalog.repo.WidgetSettings;
import ru.tpu.oko.catalog.repo.WidgetSettingsRepository;
import ru.tpu.oko.common.PageDto;
import ru.tpu.oko.common.audit.AuditLog;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.error.ErrorResponse.FieldErrorDto;
import ru.tpu.oko.common.security.CurrentUser;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ProductService {

    private static final Set<ProductStatus> DEFAULT_STATUSES = Set.of(ProductStatus.ACTIVE, ProductStatus.PAUSED);

    private final ProductRepository products;
    private final ProductOwnerRepository owners;
    private final AppUserRepository users;
    private final WidgetSettingsRepository widgetSettings;
    private final ApiKeyRepository keys;
    private final ProductAccess access;
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public ProductService(ProductRepository products, ProductOwnerRepository owners, AppUserRepository users,
                          WidgetSettingsRepository widgetSettings, ApiKeyRepository keys, ProductAccess access,
                          CurrentUser currentUser, AuditLog audit, Clock clock) {
        this.products = products;
        this.owners = owners;
        this.users = users;
        this.widgetSettings = widgetSettings;
        this.keys = keys;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public PageDto<ProductDto> list(Collection<ProductStatus> statuses, String q, int page, int size) {
        Collection<ProductStatus> effective = statuses == null || statuses.isEmpty() ? DEFAULT_STATUSES : statuses;
        Page<Product> result = products.search(effective, likePattern(q), currentUser.isAdmin(), currentUser.id(),
                PageRequest.of(page - 1, size));
        return PageDto.of(result, this::toDtos);
    }

    @Transactional
    public ProductDto create(ProductCreate request) {
        long me = currentUser.id();
        List<Long> ownerIds = resolveOwners(request.ownerLogins(), me);
        String url = UrlFormat.requireHttpUrl("url", request.url());
        if (products.urlTaken(url, -1)) {
            throw duplicateUrl();
        }
        Instant now = Instant.now(clock);
        Product product = products.saveAndFlush(new Product(request.name(), request.description(), url, me, now));
        for (Long ownerId : ownerIds) {
            owners.save(new ProductOwner(product.getId(), ownerId, now));
        }
        widgetSettings.save(WidgetSettings.defaults(product.getId(), now));
        audit.record(me, "product.create", "product", product.getId(), Map.of("owners", ownerIds));
        return toDtos(List.of(product)).getFirst();
    }

    @Transactional(readOnly = true)
    public ProductDto get(long productId) {
        return toDtos(List.of(access.requireVisible(productId))).getFirst();
    }

    @Transactional
    public ProductDto update(long productId, ProductUpdate request) {
        Product product = access.requireVisibleNotArchived(productId);
        if (request.getStatus() == ProductStatus.ARCHIVED) {
            throw ApiException.validation("status", "в архив продукт переводится только через DELETE");
        }
        if (request.getName() != null) {
            product.setName(request.getName());
        }
        if (request.isDescriptionSet()) {
            product.setDescription(request.getDescription());
        }
        if (request.getUrl() != null) {
            String url = UrlFormat.requireHttpUrl("url", request.getUrl());
            if (products.urlTaken(url, productId)) {
                throw duplicateUrl();
            }
            product.setUrl(url);
        }
        if (request.getStatus() != null && request.getStatus() != product.getStatus()) {
            audit.record(currentUser.id(), "product.status", "product", productId,
                    Map.of("from", product.getStatus().code(), "to", request.getStatus().code()));
            product.setStatus(request.getStatus());
        }
        product.touch(Instant.now(clock));
        products.saveAndFlush(product);
        return toDtos(List.of(product)).getFirst();
    }

    /** Только админ ЦЭУ. Мягкое удаление: ключи отзываются, виджет выключается, данные остаются. */
    @Transactional
    public void archive(long productId) {
        Product product = access.requireVisible(productId);
        if (!currentUser.isAdmin()) {
            throw ApiException.forbidden("Архивировать продукт может только админ ЦЭУ");
        }
        if (product.isArchived()) {
            return;
        }
        Instant now = Instant.now(clock);
        product.setStatus(ProductStatus.ARCHIVED);
        product.touch(now);
        int revoked = keys.revokeAll(productId, now);
        widgetSettings.findById(productId).ifPresent(s -> s.disable(now));
        audit.record(currentUser.id(), "product.archive", "product", productId, Map.of("revokedKeys", revoked));
    }

    private List<Long> resolveOwners(List<String> ownerLogins, long me) {
        if (ownerLogins == null) {
            return List.of(me);
        }
        if (!currentUser.isAdmin()) {
            throw ApiException.forbidden("Назначать владельцев при создании продукта может только админ ЦЭУ");
        }
        if (ownerLogins.isEmpty()) {
            throw ApiException.validation("ownerLogins", "нужен хотя бы один владелец");
        }
        Set<Long> ids = new LinkedHashSet<>();
        List<FieldErrorDto> errors = new ArrayList<>();
        for (int i = 0; i < ownerLogins.size(); i++) {
            String login = ownerLogins.get(i) == null ? "" : ownerLogins.get(i).trim();
            int index = i;
            users.findByLogin(login).ifPresentOrElse(u -> ids.add(u.getId()), () -> errors.add(new FieldErrorDto(
                    "ownerLogins[" + index + "]", "пользователь не найден: добавить можно только того, кто уже входил в ОКО")));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        return List.copyOf(ids);
    }

    private List<ProductDto> toDtos(List<Product> list) {
        if (list.isEmpty()) {
            return List.of();
        }
        Map<Long, List<UserDto>> ownersByProduct = owners.findOwners(list.stream().map(Product::getId).toList())
                .stream()
                .collect(Collectors.groupingBy(OwnerRow::productId,
                        Collectors.mapping(row -> UserDto.of(row.user()), Collectors.toList())));
        return list.stream()
                .map(p -> new ProductDto(p.getId(), p.getName(), p.getDescription(), p.getUrl(), p.getStatus(),
                        ownersByProduct.getOrDefault(p.getId(), List.of()), p.getCreatedAt(), p.getUpdatedAt()))
                .toList();
    }

    /** Подстрока без учёта регистра; % и _ из запроса ищутся буквально. */
    static String likePattern(String q) {
        if (q == null || q.isBlank()) {
            return "%";
        }
        String escaped = q.trim().toLowerCase(Locale.ROOT)
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
        return "%" + escaped + "%";
    }

    private static ApiException duplicateUrl() {
        return ApiException.conflict(ErrorCode.DUPLICATE_URL, "Продукт с таким URL уже зарегистрирован");
    }
}
