package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ru.tpu.oko.catalog.api.dto.OriginList;
import ru.tpu.oko.catalog.service.OriginService;
import ru.tpu.oko.common.ApiPaths;

@RestController
@RequestMapping(ApiPaths.V1 + "/products/{productId}/origins")
public class OriginController {

    private final OriginService origins;

    public OriginController(OriginService origins) {
        this.origins = origins;
    }

    @GetMapping
    public OriginList list(@PathVariable long productId) {
        return new OriginList(origins.list(productId));
    }

    @PutMapping
    public OriginList replace(@PathVariable long productId, @Valid @RequestBody OriginList request) {
        return new OriginList(origins.replace(productId, request.origins()));
    }
}
