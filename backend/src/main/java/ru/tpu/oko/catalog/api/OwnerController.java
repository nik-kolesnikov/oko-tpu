package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.auth.api.UserDto;
import ru.tpu.oko.catalog.api.dto.OwnerAdd;
import ru.tpu.oko.catalog.service.OwnerService;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.ItemsDto;

@RestController
@RequestMapping(ApiPaths.V1 + "/products/{productId}/owners")
public class OwnerController {

    private final OwnerService owners;

    public OwnerController(OwnerService owners) {
        this.owners = owners;
    }

    @GetMapping
    public ItemsDto<UserDto> list(@PathVariable long productId) {
        return new ItemsDto<>(owners.list(productId));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public UserDto add(@PathVariable long productId, @Valid @RequestBody OwnerAdd request) {
        return owners.add(productId, request.login());
    }

    @DeleteMapping("/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remove(@PathVariable long productId, @PathVariable long userId) {
        owners.remove(productId, userId);
    }
}
