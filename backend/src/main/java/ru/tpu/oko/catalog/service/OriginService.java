package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.catalog.repo.AllowedOrigin;
import ru.tpu.oko.catalog.repo.AllowedOriginRepository;
import ru.tpu.oko.common.audit.AuditLog;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorResponse.FieldErrorDto;
import ru.tpu.oko.common.security.CurrentUser;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Разрешённые сайты продукта: из них строится frame-ancestors страницы виджета. */
@Service
public class OriginService {

    private final AllowedOriginRepository origins;
    private final ProductAccess access;
    private final CurrentUser currentUser;
    private final AuditLog audit;

    public OriginService(AllowedOriginRepository origins, ProductAccess access, CurrentUser currentUser,
                         AuditLog audit) {
        this.origins = origins;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
    }

    @Transactional(readOnly = true)
    public List<String> list(long productId) {
        access.requireVisible(productId);
        return origins.findOrigins(productId);
    }

    /** Список заменяется целиком. Дубликаты (после приведения к виду заголовка Origin) отбрасываются. */
    @Transactional
    public List<String> replace(long productId, List<String> requested) {
        access.requireVisible(productId);
        Set<String> normalized = new LinkedHashSet<>();
        List<FieldErrorDto> errors = new ArrayList<>();
        for (int i = 0; i < requested.size(); i++) {
            int index = i;
            OriginFormat.normalize(requested.get(i)).ifPresentOrElse(normalized::add, () -> errors.add(new FieldErrorDto(
                    "origins[" + index + "]", "нужен вид https://host или https://host:port, без пути, * и / в конце")));
        }
        if (!errors.isEmpty()) {
            throw ApiException.validation(errors);
        }
        origins.deleteByProductId(productId);
        origins.saveAll(normalized.stream().map(o -> new AllowedOrigin(productId, o)).toList());
        audit.record(currentUser.id(), "origins.replace", "product", productId, Map.of("count", normalized.size()));
        return origins.findOrigins(productId);
    }
}
