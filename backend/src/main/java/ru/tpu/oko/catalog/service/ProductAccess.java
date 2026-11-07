package ru.tpu.oko.catalog.service;

import org.springframework.stereotype.Component;
import ru.tpu.oko.catalog.repo.Product;
import ru.tpu.oko.catalog.repo.ProductOwnerRepository;
import ru.tpu.oko.catalog.repo.ProductRepository;
import ru.tpu.oko.common.error.ApiException;
import ru.tpu.oko.common.error.ErrorCode;
import ru.tpu.oko.common.security.CurrentUser;

/**
 * Правило доступа к продукту: админ ЦЭУ видит всё, владелец — только свои.
 * Чужой продукт для владельца выглядит как 404, чтобы не раскрывать, что он существует.
 */
@Component
public class ProductAccess {

    private final ProductRepository products;
    private final ProductOwnerRepository owners;
    private final CurrentUser currentUser;

    public ProductAccess(ProductRepository products, ProductOwnerRepository owners, CurrentUser currentUser) {
        this.products = products;
        this.owners = owners;
        this.currentUser = currentUser;
    }

    public Product requireVisible(long productId) {
        Product product = products.findById(productId).orElseThrow(ApiException::notFound);
        if (!currentUser.isAdmin() && !owners.existsByProductIdAndUserId(productId, currentUser.id())) {
            throw ApiException.notFound();
        }
        return product;
    }

    /** Продукт виден и не в архиве — иначе 409 CONFLICT. */
    public Product requireVisibleNotArchived(long productId) {
        Product product = requireVisible(productId);
        if (product.isArchived()) {
            throw ApiException.conflict(ErrorCode.CONFLICT, "Продукт в архиве, изменения недоступны");
        }
        return product;
    }
}
