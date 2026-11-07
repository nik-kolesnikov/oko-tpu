package ru.tpu.oko.catalog.repo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;
import ru.tpu.oko.common.persistence.CodedEnum;
import ru.tpu.oko.common.persistence.CodedEnumJpaConverter;

/** Статус продукта: active, paused (приостановлен владельцем), archived (мягкое удаление, только админ). */
public enum ProductStatus implements CodedEnum {
    ACTIVE("active"),
    PAUSED("paused"),
    ARCHIVED("archived");

    private final String code;

    ProductStatus(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ProductStatus fromCode(String code) {
        return CodedEnum.fromCode(ProductStatus.class, code);
    }

    @Converter
    public static class JpaConverter extends CodedEnumJpaConverter<ProductStatus> {
        public JpaConverter() {
            super(ProductStatus.class);
        }
    }
}
