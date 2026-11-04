package ru.tpu.oko.catalog.repo;

import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.Optional;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Список для панели. {@code pattern} — LIKE-шаблон в нижнем регистре с экранированными % и _.
     * {@code all = true} — админ, иначе только продукты, где {@code userId} владелец.
     */
    @Query(value = """
            select p from Product p
            where p.status in :statuses
              and lower(p.name) like :pattern escape '\\'
              and (:all = true or exists (
                    select 1 from ProductOwner o where o.productId = p.id and o.userId = :userId))
            order by lower(p.name), p.id
            """,
            countQuery = """
            select count(p) from Product p
            where p.status in :statuses
              and lower(p.name) like :pattern escape '\\'
              and (:all = true or exists (
                    select 1 from ProductOwner o where o.productId = p.id and o.userId = :userId))
            """)
    Page<Product> search(Collection<ProductStatus> statuses, String pattern, boolean all, long userId,
                         Pageable pageable);

    /** Занят ли URL другим неархивным продуктом (без учёта регистра). {@code excludeId = -1} — не исключать. */
    @Query("""
            select count(p) > 0 from Product p
            where lower(p.url) = lower(:url) and p.status <> :archived and p.id <> :excludeId
            """)
    boolean existsActiveUrl(String url, long excludeId, ProductStatus archived);

    default boolean urlTaken(String url, long excludeId) {
        return existsActiveUrl(url, excludeId, ProductStatus.ARCHIVED);
    }

    /** SELECT ... FOR UPDATE: сериализует изменения владельцев одного продукта. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Product p where p.id = :id")
    Optional<Product> findByIdForUpdate(long id);
}
