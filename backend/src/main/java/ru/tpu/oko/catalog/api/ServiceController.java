package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.catalog.api.dto.ServiceCreate;
import ru.tpu.oko.catalog.api.dto.ServiceDto;
import ru.tpu.oko.catalog.api.dto.ServiceUpdate;
import ru.tpu.oko.catalog.service.ProductServicesService;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.ItemsDto;

@RestController
@RequestMapping(ApiPaths.V1 + "/products/{productId}/services")
public class ServiceController {

    private final ProductServicesService services;

    public ServiceController(ProductServicesService services) {
        this.services = services;
    }

    @GetMapping
    public ItemsDto<ServiceDto> list(@PathVariable long productId,
                                     @RequestParam(defaultValue = "false") boolean includeArchived) {
        return new ItemsDto<>(services.list(productId, includeArchived));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ServiceDto create(@PathVariable long productId, @Valid @RequestBody ServiceCreate request) {
        return services.create(productId, request);
    }

    @PatchMapping("/{serviceId}")
    public ServiceDto update(@PathVariable long productId, @PathVariable long serviceId,
                             @Valid @RequestBody ServiceUpdate request) {
        return services.update(productId, serviceId, request);
    }

    @DeleteMapping("/{serviceId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable long productId, @PathVariable long serviceId) {
        services.archive(productId, serviceId);
    }
}
