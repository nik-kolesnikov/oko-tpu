package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import ru.tpu.oko.catalog.repo.ProductStatus;

/**
 * Схема {@code ProductUpdate} (PATCH). Отсутствующее поле — «не менять». Для description различаем
 * «не передано» и явный null («очистить»), поэтому класс с флагом, а не record.
 */
public class ProductUpdate {

    @Size(min = 2, max = 200, message = "от 2 до 200 символов")
    @Pattern(regexp = "(?s).*\\S.*", message = "не должно состоять из пробелов")
    private String name;

    private String description;
    private boolean descriptionSet;

    private String url;

    private ProductStatus status;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
        this.descriptionSet = true;
    }

    public boolean isDescriptionSet() {
        return descriptionSet;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public ProductStatus getStatus() {
        return status;
    }

    public void setStatus(ProductStatus status) {
        this.status = status;
    }
}
