package ru.tpu.oko.catalog.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

/** Владелец — связь «пользователь ↔ продукт», а не отдельная роль. */
@Entity
@Table(name = "product_owner")
@IdClass(ProductOwner.Key.class)
public class ProductOwner {

    @Id
    @Column(name = "product_id")
    private Long productId;

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    protected ProductOwner() {
    }

    public ProductOwner(long productId, long userId, Instant addedAt) {
        this.productId = productId;
        this.userId = userId;
        this.addedAt = addedAt;
    }

    public Long getProductId() {
        return productId;
    }

    public Long getUserId() {
        return userId;
    }

    public static class Key implements Serializable {

        private Long productId;
        private Long userId;

        public Key() {
        }

        public Key(Long productId, Long userId) {
            this.productId = productId;
            this.userId = userId;
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Key other && Objects.equals(productId, other.productId)
                    && Objects.equals(userId, other.userId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(productId, userId);
        }
    }
}
