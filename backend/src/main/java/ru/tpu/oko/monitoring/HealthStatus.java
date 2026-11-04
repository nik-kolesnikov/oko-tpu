package ru.tpu.oko.monitoring;

import com.fasterxml.jackson.annotation.JsonValue;
import ru.tpu.oko.common.persistence.CodedEnum;

/** Схема {@code HealthStatus}. unknown — нет привязки, данных ещё нет или они старше порога. */
public enum HealthStatus implements CodedEnum {
    UP("up"),
    DOWN("down"),
    UNKNOWN("unknown");

    private final String code;

    HealthStatus(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    public static HealthStatus fromCode(String code) {
        return CodedEnum.fromCode(HealthStatus.class, code);
    }
}
