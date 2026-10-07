package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.PageResponse;
import com.edstem.interviewprep.dto.ProductFilter;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.UpdateProductRequest;
import com.edstem.interviewprep.service.ProductService;
import jakarta.validation.Valid;
import java.math.BigDecimal;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(ProductController.PRODUCTS_PATH)
public class ProductController {

    public static final String PRODUCTS_PATH = "/api/products";
    public static final String PRODUCT_PATH = PRODUCTS_PATH + "/*";

    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public PageResponse<ProductResponse> list(
            @RequestParam(required = false) String category,
            @RequestParam(required = false) BigDecimal minPrice,
            @RequestParam(required = false) BigDecimal maxPrice,
            @RequestParam(defaultValue = "false") boolean inStock,
            @RequestParam(required = false) String name,
            @PageableDefault(size = DEFAULT_PAGE_SIZE, sort = "id") Pageable pageable) {
        return productService.list(new ProductFilter(category, minPrice, maxPrice, inStock, name), pageable);
    }

    @GetMapping("/{id}")
    public ProductResponse get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PutMapping("/{id}")
    public ProductResponse update(@PathVariable Long id, @Valid @RequestBody UpdateProductRequest request) {
        return productService.update(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        productService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
