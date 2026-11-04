package ru.tpu.oko.catalog.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
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
import ru.tpu.oko.catalog.api.dto.ProductCreate;
import ru.tpu.oko.catalog.api.dto.ProductDto;
import ru.tpu.oko.catalog.api.dto.ProductUpdate;
import ru.tpu.oko.catalog.repo.ProductStatus;
import ru.tpu.oko.catalog.service.ProductService;
import ru.tpu.oko.common.ApiPaths;
import ru.tpu.oko.common.PageDto;

import java.util.List;

@RestController
@RequestMapping(ApiPaths.V1 + "/products")
public class ProductController {

    private final ProductService products;

    public ProductController(ProductService products) {
        this.products = products;
    }

    @GetMapping
    public PageDto<ProductDto> list(
            @RequestParam(required = false) List<ProductStatus> status,
            @RequestParam(required = false) @Size(max = 200, message = "не длиннее 200 символов") String q,
            @RequestParam(defaultValue = "1") @Min(value = 1, message = "не меньше 1") int page,
            @RequestParam(defaultValue = "20") @Min(value = 1, message = "от 1 до 100")
            @Max(value = 100, message = "от 1 до 100") int size) {
        return products.list(status, q, page, size);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProductDto create(@Valid @RequestBody ProductCreate request) {
        return products.create(request);
    }

    @GetMapping("/{productId}")
    public ProductDto get(@PathVariable long productId) {
        return products.get(productId);
    }

    @PatchMapping("/{productId}")
    public ProductDto update(@PathVariable long productId, @Valid @RequestBody ProductUpdate request) {
        return products.update(productId, request);
    }

    @DeleteMapping("/{productId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void archive(@PathVariable long productId) {
        products.archive(productId);
    }
}
