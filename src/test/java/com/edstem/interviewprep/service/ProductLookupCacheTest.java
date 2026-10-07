package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.edstem.interviewprep.common.CacheConfig;
import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.UpdateProductRequest;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;

@SpringBootTest
class ProductLookupCacheTest {

    private static final int REPEATED_LOOKUPS = 5;

    @Autowired
    private ProductService productService;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    private Statistics statistics;
    private Long productId;

    @BeforeEach
    void createProductWithEmptyCache() {
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
        statistics = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        productId = productRepository.save(new Product(
                "Cached Lamp", "Home", new BigDecimal("19.99"), 5, new BigDecimal("4.0"),
                Instant.parse("2026-01-15T10:00:00Z"))).getId();
    }

    @AfterEach
    void deleteProductAndClearCache() {
        productRepository.deleteById(productId);
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
    }

    @Test
    void repeatedLookupsOfTheSameProductQueryTheDatabaseOnce() {
        statistics.clear();

        for (int lookup = 0; lookup < REPEATED_LOOKUPS; lookup++) {
            assertThat(productService.get(productId).name()).isEqualTo("Cached Lamp");
        }

        assertThat(statistics.getPrepareStatementCount()).isEqualTo(1);
    }

    @Test
    void lookupAfterUpdateReturnsTheUpdatedProduct() {
        productService.get(productId);

        productService.update(productId, new UpdateProductRequest(
                "Renamed Lamp", "Home", new BigDecimal("25.00"), 0, new BigDecimal("3.5")));

        ProductResponse product = productService.get(productId);
        assertThat(product.name()).isEqualTo("Renamed Lamp");
        assertThat(product.price()).isEqualByComparingTo("25.00");
        assertThat(product.stock()).isZero();
    }

    @Test
    void lookupAfterDeleteReportsNotFound() {
        productService.get(productId);

        productService.delete(productId);

        assertThatThrownBy(() -> productService.get(productId)).isInstanceOf(ResourceNotFoundException.class);
    }
}
