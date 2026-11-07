package ru.tpu.oko.ratings.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import ru.tpu.oko.common.persistence.CodedEnum;

/** Роль оценившего. Виджет её не знает (unknown); сервер продукта может передать. */
public enum RaterRole implements CodedEnum {
    STUDENT("student"),
    STAFF("staff"),
    UNKNOWN("unknown");

    private final String code;

    RaterRole(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static RaterRole fromCode(String code) {
        return CodedEnum.fromCode(RaterRole.class, code);
    }
}
