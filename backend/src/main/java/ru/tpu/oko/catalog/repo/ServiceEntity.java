package ru.tpu.oko.catalog.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Сервис внутри продукта (таблица service). Названо ServiceEntity, чтобы не путать со Spring @Service. */
@Entity
@Table(name = "service")
public class ServiceEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String name;

    private String url;

    @Column(name = "monitoring_ref")
    private String monitoringRef;

    @Convert(converter = ServiceState.JpaConverter.class)
    @Column(nullable = false)
    private ServiceState status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ServiceEntity() {
    }

    public ServiceEntity(long productId, String name, String url, String monitoringRef, Instant now) {
        this.productId = productId;
        this.name = name;
        this.url = url;
        this.monitoringRef = monitoringRef;
        this.status = ServiceState.ACTIVE;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getMonitoringRef() {
        return monitoringRef;
    }

    public void setMonitoringRef(String monitoringRef) {
        this.monitoringRef = monitoringRef;
    }

    public ServiceState getStatus() {
        return status;
    }

    public void setStatus(ServiceState status) {
        this.status = status;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
