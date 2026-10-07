package com.edstem.interviewprep.service;

import com.edstem.interviewprep.common.CacheConfig;
import com.edstem.interviewprep.common.error.FieldValidationException;
import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductFilter;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.UpdateProductRequest;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.repository.ProductSpecifications;
import java.util.Set;
import java.util.TreeSet;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProductService {

    private static final String RESOURCE_NAME = "Product";
    private static final String ID_FIELD = "id";
    private static final String SORT_FIELD = "sort";
    private static final Set<String> SORTABLE_FIELDS =
            Set.of(ID_FIELD, "name", "category", "price", "stock", "rating", "createdAt");

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    @Transactional(readOnly = true)
    public PageResponse<ProductResponse> list(ProductFilter filter, Pageable pageable) {
        return PageResponse.from(productRepository
                .findAll(ProductSpecifications.matching(filter), withStableOrder(pageable))
                .map(ProductResponse::from));
    }

    /**
     * Not transactional, so a cache hit never borrows a database connection. {@code sync} makes an eviction wait
     * for an in-flight load of the same id, so a row read before an update commits cannot outlive the eviction.
     */
    @Cacheable(cacheNames = CacheConfig.PRODUCTS_CACHE, sync = true)
    public ProductResponse get(Long id) {
        return ProductResponse.from(findProduct(id));
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public ProductResponse update(Long id, UpdateProductRequest request) {
        Product product = findProduct(id);
        product.update(request.name(), request.category(), request.price(), request.stock(), request.rating());
        return ProductResponse.from(product);
    }

    @Transactional
    @CacheEvict(cacheNames = CacheConfig.PRODUCTS_CACHE, key = "#id")
    public void delete(Long id) {
        productRepository.delete(findProduct(id));
    }

    /**
     * Rows that tie on the requested sort would otherwise come back in an arbitrary order, so the same product
     * could appear on two pages or on none. Sorting by id last makes every page deterministic.
     */
    private static Pageable withStableOrder(Pageable pageable) {
        Sort sort = pageable.getSort();
        sort.forEach(order -> requireSortable(order.getProperty()));
        if (sort.getOrderFor(ID_FIELD) == null) {
            sort = sort.and(Sort.by(ID_FIELD));
        }
        return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), sort);
    }

    private static void requireSortable(String property) {
        if (!SORTABLE_FIELDS.contains(property)) {
            throw new FieldValidationException(SORT_FIELD, "sort must be one of " + new TreeSet<>(SORTABLE_FIELDS));
        }
    }

    private Product findProduct(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
    }
}
