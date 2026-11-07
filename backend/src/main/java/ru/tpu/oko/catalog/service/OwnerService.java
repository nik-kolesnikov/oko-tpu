package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.auth.repo.AppUser;
import ru.tpu.oko.auth.repo.AppUserRepository;
import ru.tpu.oko.catalog.repo.ProductOwner;
import ru.tpu.oko.catalog.repo.ProductOwnerRepository;
import ru.tpu.oko.catalog.repo.ProductRepository;
import ru.tpu.oko.common.audit.AuditLog;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.security.CurrentUser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class OwnerService {

    private final ProductOwnerRepository owners;
    private final ProductRepository products;
    private final AppUserRepository users;
    private final ProductAccess access;
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public OwnerService(ProductOwnerRepository owners, ProductRepository products, AppUserRepository users,
                        ProductAccess access, CurrentUser currentUser, AuditLog audit, Clock clock) {
        this.owners = owners;
        this.products = products;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<UserDto> list(long productId) {
        access.requireVisible(productId);
        return owners.findOwners(List.of(productId)).stream().map(row -> UserDto.of(row.user())).toList();
    }

    /** Добавить можно только того, кто уже входил в ОКО (MVP). */
    @Transactional
    public UserDto add(long productId, String login) {
        access.requireVisible(productId);
        AppUser user = users.findByLogin(login.trim()).orElseThrow(() -> ApiException.notFound(
                "Пользователь не найден: добавить можно только того, кто уже входил в ОКО"));
        if (owners.existsByProductIdAndUserId(productId, user.getId())) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Пользователь уже владелец продукта");
        }
        owners.save(new ProductOwner(productId, user.getId(), Instant.now(clock)));
        audit.record(currentUser.id(), "owner.add", "product", productId, Map.of("userId", user.getId()));
        return UserDto.of(user);
    }

    /**
     * Строка продукта блокируется (SELECT ... FOR UPDATE): без этого два параллельных удаления
     * могли бы оставить продукт без владельцев.
     */
    @Transactional
    public void remove(long productId, long userId) {
        access.requireVisible(productId);
        products.findByIdForUpdate(productId).orElseThrow(ApiException::notFound);
        if (!owners.existsByProductIdAndUserId(productId, userId)) {
            throw ApiException.notFound("Пользователь не владелец этого продукта");
        }
        if (owners.countByProductId(productId) <= 1) {
            throw ApiException.conflict(ErrorCode.LAST_OWNER, "Нельзя убрать последнего владельца продукта");
        }
        owners.deleteById(new ProductOwner.Key(productId, userId));
        audit.record(currentUser.id(), "owner.remove", "product", productId, Map.of("userId", userId));
    }
}
