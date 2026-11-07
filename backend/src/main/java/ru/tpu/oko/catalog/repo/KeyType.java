package ru.tpu.oko.catalog.repo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;
import ru.tpu.oko.common.persistence.CodedEnum;
import ru.tpu.oko.common.persistence.CodedEnumJpaConverter;

/** Тип ключа: public — виджет, secret — серверы продуктов. */
public enum KeyType implements CodedEnum {
    PUBLIC("public"),
    SECRET("secret");

    private final String code;

    KeyType(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static KeyType fromCode(String code) {
        return CodedEnum.fromCode(KeyType.class, code);
    }

    @Converter
    public static class JpaConverter extends CodedEnumJpaConverter<KeyType> {
        public JpaConverter() {
            super(KeyType.class);
        }
    }
}
