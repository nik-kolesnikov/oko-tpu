package ru.tpu.oko.common.persistence;

/**
 * Перечисление с кодом в нижнем регистре: так оно хранится в БД (text + CHECK) и выглядит в JSON.
 */
public interface CodedEnum {

    String code();

    static <E extends Enum<E> & CodedEnum> E fromCode(Class<E> type, String code) {
        for (E value : type.getEnumConstants()) {
            if (value.code().equals(code)) {
                return value;
            }
        }
        throw new IllegalArgumentException("Недопустимое значение: " + code);
    }
}
