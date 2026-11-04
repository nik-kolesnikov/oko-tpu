package ru.tpu.oko.common.persistence;

import jakarta.persistence.AttributeConverter;

public abstract class CodedEnumJpaConverter<E extends Enum<E> & CodedEnum> implements AttributeConverter<E, String> {

    private final Class<E> type;

    protected CodedEnumJpaConverter(Class<E> type) {
        this.type = type;
    }

    @Override
    public String convertToDatabaseColumn(E attribute) {
        return attribute == null ? null : attribute.code();
    }

    @Override
    public E convertToEntityAttribute(String dbData) {
        return dbData == null ? null : CodedEnum.fromCode(type, dbData);
    }
}
