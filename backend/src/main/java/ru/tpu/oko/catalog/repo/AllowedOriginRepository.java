package ru.tpu.oko.catalog.repo;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface AllowedOriginRepository extends JpaRepository<AllowedOrigin, Long> {

    @Query("select a.origin from AllowedOrigin a where a.productId = :productId order by a.origin")
    List<String> findOrigins(long productId);

    @Modifying
    @Query("delete from AllowedOrigin a where a.productId = :productId")
    void deleteByProductId(long productId);
}
