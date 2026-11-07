package ru.tpu.oko.catalog.repo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;
import ru.tpu.oko.common.persistence.CodedEnum;
import ru.tpu.oko.common.persistence.CodedEnumJpaConverter;

/** Статус сервиса: active или archived (мягкое удаление). */
public enum ServiceState implements CodedEnum {
    ACTIVE("active"),
    ARCHIVED("archived");

    private final String code;

    ServiceState(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static ServiceState fromCode(String code) {
        return CodedEnum.fromCode(ServiceState.class, code);
    }

    @Converter
    public static class JpaConverter extends CodedEnumJpaConverter<ServiceState> {
        public JpaConverter() {
            super(ServiceState.class);
        }
    }
}
