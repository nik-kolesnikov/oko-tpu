package ru.tpu.oko.catalog.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Сайт, на котором разрешено встраивать виджет продукта (frame-ancestors). */
@Entity
@Table(name = "allowed_origin")
public class AllowedOrigin {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Column(nullable = false)
    private String origin;

    protected AllowedOrigin() {
    }

    public AllowedOrigin(long productId, String origin) {
        this.productId = productId;
        this.origin = origin;
    }

    public String getOrigin() {
        return origin;
    }
}
