package ru.tpu.oko.catalog.repo;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

/** Ключ продукта. Хранится только SHA-256 и префикс, сам ключ показывается один раз при выпуске. */
@Entity
@Table(name = "api_key")
public class ApiKey {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "product_id", nullable = false)
    private Long productId;

    @Convert(converter = KeyType.JpaConverter.class)
    @Column(name = "key_type", nullable = false)
    private KeyType keyType;

    @Column(name = "key_prefix", nullable = false)
    private String keyPrefix;

    @Column(name = "key_hash", nullable = false, unique = true)
    private byte[] keyHash;

    @Column(name = "created_by", nullable = false)
    private Long createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected ApiKey() {
    }

    public ApiKey(long productId, KeyType keyType, String keyPrefix, byte[] keyHash, long createdBy, Instant now) {
        this.productId = productId;
        this.keyType = keyType;
        this.keyPrefix = keyPrefix;
        this.keyHash = keyHash;
        this.createdBy = createdBy;
        this.createdAt = now;
    }

    public Long getId() {
        return id;
    }

    public Long getProductId() {
        return productId;
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public String getKeyPrefix() {
        return keyPrefix;
    }

    public Long getCreatedBy() {
        return createdBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }

    public boolean isRevoked() {
        return revokedAt != null;
    }

    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }
}
