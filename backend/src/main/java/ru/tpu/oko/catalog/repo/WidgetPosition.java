package ru.tpu.oko.catalog.repo;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;
import jakarta.persistence.Converter;
import ru.tpu.oko.common.persistence.CodedEnum;
import ru.tpu.oko.common.persistence.CodedEnumJpaConverter;

/** Положение плашки виджета на странице. */
public enum WidgetPosition implements CodedEnum {
    BOTTOM_RIGHT("bottom-right"),
    BOTTOM_LEFT("bottom-left");

    private final String code;

    WidgetPosition(String code) {
        this.code = code;
    }

    @Override
    @JsonValue
    public String code() {
        return code;
    }

    @JsonCreator
    public static WidgetPosition fromCode(String code) {
        return CodedEnum.fromCode(WidgetPosition.class, code);
    }

    @Converter
    public static class JpaConverter extends CodedEnumJpaConverter<WidgetPosition> {
        public JpaConverter() {
            super(WidgetPosition.class);
        }
    }
}
