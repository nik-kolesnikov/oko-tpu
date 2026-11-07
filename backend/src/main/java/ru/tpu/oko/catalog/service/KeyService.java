package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.auth.repo.AppUserRepository;
import ru.tpu.oko.catalog.api.dto.ApiKeyDto;
import ru.tpu.oko.catalog.api.dto.ApiKeyIssuedDto;
import ru.tpu.oko.catalog.repo.ApiKey;
import ru.tpu.oko.catalog.repo.ApiKeyRepository;
import ru.tpu.oko.catalog.repo.KeyType;
import ru.tpu.oko.common.audit.AuditLog;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.security.CurrentUser;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Выпуск, перевыпуск и отзыв ключей. Полный ключ возвращается только в ответе на выпуск. */
@Service
public class KeyService {

    private final ApiKeyRepository keys;
    private final AppUserRepository users;
    private final ProductAccess access;
    private final CurrentUser currentUser;
    private final AuditLog audit;
    private final Clock clock;

    public KeyService(ApiKeyRepository keys, AppUserRepository users, ProductAccess access, CurrentUser currentUser,
                      AuditLog audit, Clock clock) {
        this.keys = keys;
        this.users = users;
        this.access = access;
        this.currentUser = currentUser;
        this.audit = audit;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ApiKeyDto> list(long productId, boolean includeRevoked) {
        access.requireVisible(productId);
        List<ApiKey> list = includeRevoked
                ? keys.findByProductIdOrderByCreatedAtDescIdDesc(productId)
                : keys.findByProductIdAndRevokedAtIsNullOrderByCreatedAtDescIdDesc(productId);
        Map<Long, UserDto> creators = creators(list);
        return list.stream().map(k -> toDto(k, creators.get(k.getCreatedBy()))).toList();
    }

    @Transactional
    public ApiKeyIssuedDto create(long productId, KeyType type) {
        access.requireVisibleNotArchived(productId);
        return issue(productId, type, "key.create");
    }

    /** Новый ключ того же типа, старый отзывается сразу. */
    @Transactional
    public ApiKeyIssuedDto reissue(long productId, long keyId) {
        access.requireVisibleNotArchived(productId);
        ApiKey old = keys.findByIdAndProductId(keyId, productId).orElseThrow(ApiException::notFound);
        if (old.isRevoked()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Ключ уже отозван, выпустите новый");
        }
        old.revoke(Instant.now(clock));
        keys.saveAndFlush(old);
        audit.record(currentUser.id(), "key.revoke", "api_key", keyId, Map.of("reason", "reissue"));
        return issue(productId, old.getKeyType(), "key.reissue");
    }

    /** Идемпотентно. Приём оценок с этим ключом сразу отвечает 401. */
    @Transactional
    public ApiKeyDto revoke(long productId, long keyId) {
        access.requireVisible(productId);
        ApiKey key = keys.findByIdAndProductId(keyId, productId).orElseThrow(ApiException::notFound);
        if (!key.isRevoked()) {
            key.revoke(Instant.now(clock));
            audit.record(currentUser.id(), "key.revoke", "api_key", keyId, Map.of());
        }
        return toDto(key, creators(List.of(key)).get(key.getCreatedBy()));
    }

    private ApiKeyIssuedDto issue(long productId, KeyType type, String action) {
        ApiKeyGenerator.Generated generated = ApiKeyGenerator.generate(type);
        ApiKey key = keys.save(new ApiKey(productId, type, generated.prefix(), generated.hash(), currentUser.id(),
                Instant.now(clock)));
        audit.record(currentUser.id(), action, "api_key", key.getId(), Map.of("type", type.code()));
        UserDto creator = creators(List.of(key)).get(key.getCreatedBy());
        return new ApiKeyIssuedDto(key.getId(), key.getProductId(), key.getKeyType(), key.getKeyPrefix(),
                key.getCreatedAt(), creator, key.getRevokedAt(), generated.raw());
    }

    private Map<Long, UserDto> creators(List<ApiKey> list) {
        return users.findAllById(list.stream().map(ApiKey::getCreatedBy).distinct().toList()).stream()
                .map(UserDto::of)
                .collect(Collectors.toMap(UserDto::id, Function.identity()));
    }

    private static ApiKeyDto toDto(ApiKey k, UserDto creator) {
        return new ApiKeyDto(k.getId(), k.getProductId(), k.getKeyType(), k.getKeyPrefix(), k.getCreatedAt(), creator,
                k.getRevokedAt());
    }
}
