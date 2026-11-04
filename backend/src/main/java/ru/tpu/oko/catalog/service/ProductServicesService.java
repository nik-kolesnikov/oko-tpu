package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.tpu.oko.catalog.api.dto.ServiceCreate;
import ru.tpu.oko.catalog.api.dto.ServiceDto;
import ru.tpu.oko.catalog.api.dto.ServiceUpdate;
import ru.tpu.oko.catalog.repo.ServiceEntity;
import ru.tpu.oko.catalog.repo.ServiceRepository;
import ru.tpu.oko.catalog.repo.ServiceState;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.monitoring.HealthReader;
import ru.tpu.oko.monitoring.HealthView;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Map;

/** Сервисы внутри продукта. */
@Service
public class ProductServicesService {

    private final ServiceRepository services;
    private final ProductAccess access;
    private final HealthReader healthReader;
    private final Clock clock;

    public ProductServicesService(ServiceRepository services, ProductAccess access, HealthReader healthReader,
                                  Clock clock) {
        this.services = services;
        this.access = access;
        this.healthReader = healthReader;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<ServiceDto> list(long productId, boolean includeArchived) {
        access.requireVisible(productId);
        List<ServiceEntity> list = includeArchived
                ? services.findByProductIdOrderByNameAscIdAsc(productId)
                : services.findByProductIdAndStatusOrderByNameAscIdAsc(productId, ServiceState.ACTIVE);
        return toDtos(list);
    }

    @Transactional
    public ServiceDto create(long productId, ServiceCreate request) {
        access.requireVisibleNotArchived(productId);
        String monitoringRef = normalizeRef(request.monitoringRef());
        requireFreeMonitoringRef(monitoringRef, -1);
        ServiceEntity service = services.saveAndFlush(new ServiceEntity(productId, request.name(),
                UrlFormat.optionalHttpUrl("url", request.url()), monitoringRef, Instant.now(clock)));
        return toDtos(List.of(service)).getFirst();
    }

    @Transactional
    public ServiceDto update(long productId, long serviceId, ServiceUpdate request) {
        access.requireVisible(productId);
        ServiceEntity service = services.findByIdAndProductId(serviceId, productId).orElseThrow(ApiException::notFound);
        if (request.getName() != null) {
            service.setName(request.getName());
        }
        if (request.isUrlSet()) {
            service.setUrl(UrlFormat.optionalHttpUrl("url", request.getUrl()));
        }
        if (request.isMonitoringRefSet()) {
            String monitoringRef = normalizeRef(request.getMonitoringRef());
            requireFreeMonitoringRef(monitoringRef, serviceId);
            service.setMonitoringRef(monitoringRef);
        }
        services.saveAndFlush(service);
        return toDtos(List.of(service)).getFirst();
    }

    /** Мягкое удаление: оценки и статистика сервиса сохраняются. */
    @Transactional
    public void archive(long productId, long serviceId) {
        access.requireVisible(productId);
        ServiceEntity service = services.findByIdAndProductId(serviceId, productId).orElseThrow(ApiException::notFound);
        service.setStatus(ServiceState.ARCHIVED);
    }

    private void requireFreeMonitoringRef(String monitoringRef, long serviceId) {
        if (monitoringRef != null && services.existsByMonitoringRefAndIdNot(monitoringRef, serviceId)) {
            throw ApiException.conflict(ErrorCode.DUPLICATE_MONITORING_REF,
                    "Этот идентификатор мониторинга уже привязан к другому сервису");
        }
    }

    private static String normalizeRef(String monitoringRef) {
        return monitoringRef == null || monitoringRef.isBlank() ? null : monitoringRef.trim();
    }

    private List<ServiceDto> toDtos(List<ServiceEntity> list) {
        Map<Long, HealthView> health = healthReader.read(list.stream().map(ServiceEntity::getId).toList());
        return list.stream()
                .map(s -> new ServiceDto(s.getId(), s.getProductId(), s.getName(), s.getUrl(), s.getMonitoringRef(),
                        s.getStatus(), health.getOrDefault(s.getId(), HealthView.NO_DATA), s.getCreatedAt()))
                .toList();
    }
}
