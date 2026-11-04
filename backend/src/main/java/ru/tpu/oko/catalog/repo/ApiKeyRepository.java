package ru.tpu.oko.catalog.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface ApiKeyRepository extends JpaRepository<ApiKey, Long> {

    List<ApiKey> findByProductIdOrderByCreatedAtDescIdDesc(long productId);

    List<ApiKey> findByProductIdAndRevokedAtIsNullOrderByCreatedAtDescIdDesc(long productId);

    Optional<ApiKey> findByIdAndProductId(long id, long productId);

    /** Ключ для приёма оценок: только действующий, вместе со статусом продукта. Без кэша — отзыв срабатывает сразу. */
    @Query("""
            select new ru.tpu.oko.catalog.repo.ResolvedKey(k.id, k.productId, k.keyType, p.status)
            from ApiKey k join Product p on p.id = k.productId
            where k.keyHash = :keyHash and k.revokedAt is null
            """)
    Optional<ResolvedKey> resolveActive(byte[] keyHash);

    @Modifying
    @Query("update ApiKey k set k.revokedAt = :now where k.productId = :productId and k.revokedAt is null")
    int revokeAll(long productId, Instant now);
}
