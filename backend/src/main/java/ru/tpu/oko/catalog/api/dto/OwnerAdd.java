package ru.tpu.oko.catalog.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record OwnerAdd(@NotBlank(message = "обязательное поле") @Size(max = 200) String login) {
}
