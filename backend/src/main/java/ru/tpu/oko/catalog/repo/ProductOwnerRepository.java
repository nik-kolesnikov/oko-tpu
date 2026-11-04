package ru.tpu.oko.catalog.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.Collection;
import java.util.List;

public interface ProductOwnerRepository extends JpaRepository<ProductOwner, ProductOwner.Key> {

    boolean existsByProductIdAndUserId(long productId, long userId);

    long countByProductId(long productId);

    @Query("""
            select new ru.tpu.oko.catalog.repo.OwnerRow(o.productId, u)
            from ProductOwner o join AppUser u on u.id = o.userId
            where o.productId in :productIds
            order by u.fullName, u.id
            """)
    List<OwnerRow> findOwners(Collection<Long> productIds);
}
