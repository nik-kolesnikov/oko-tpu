package ru.tpu.oko.catalog.repo;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ServiceRepository extends JpaRepository<ServiceEntity, Long> {

    List<ServiceEntity> findByProductIdOrderByNameAscIdAsc(long productId);

    List<ServiceEntity> findByProductIdAndStatusOrderByNameAscIdAsc(long productId, ServiceState status);

    Optional<ServiceEntity> findByIdAndProductId(long id, long productId);

    boolean existsByIdAndProductIdAndStatus(long id, long productId, ServiceState status);

    boolean existsByMonitoringRefAndIdNot(String monitoringRef, long id);
}
