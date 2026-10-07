package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;

import com.edstem.interviewprep.common.CacheConfig;
import com.edstem.interviewprep.dto.ProductResponse;
import com.edstem.interviewprep.dto.UpdateProductRequest;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.mockito.stubbing.Answer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class ProductLookupCacheConcurrencyTest {

    private static final long TIMEOUT_SECONDS = 10;
    private static final UpdateProductRequest RENAME =
            new UpdateProductRequest("Renamed Lamp", "Home", new BigDecimal("25.00"), 1, new BigDecimal("3.5"));

    @Autowired
    private ProductService productService;

    @MockitoSpyBean
    private ProductRepository productRepository;

    @Autowired
    private CacheManager cacheManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final AtomicBoolean shouldPauseNextLoad = new AtomicBoolean();
    private final CountDownLatch loadedOldRow = new CountDownLatch(1);
    private final CountDownLatch resumeLoad = new CountDownLatch(1);
    private Long productId;

    @BeforeEach
    void createProductWithEmptyCacheAndPausableLoads() {
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
        productId = productRepository.save(new Product(
                "Original Lamp", "Home", new BigDecimal("19.99"), 5, new BigDecimal("4.0"),
                Instant.parse("2026-01-15T10:00:00Z"))).getId();
        Answer<?> delegateToRepository =
                Mockito.mockingDetails(productRepository).getMockCreationSettings().getDefaultAnswer();
        doAnswer(invocation -> {
            Object row = delegateToRepository.answer(invocation);
            if (shouldPauseNextLoad.compareAndSet(true, false)) {
                loadedOldRow.countDown();
                requireResumed();
            }
            return row;
        }).when(productRepository).findById(any());
    }

    @AfterEach
    void deleteProductAndClearCache() {
        productRepository.deleteById(productId);
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
    }

    @Test
    void lookupThatReadTheOldRowBeforeAnUpdateCommittedIsNotServedAfterwards() throws Exception {
        CountDownLatch updateCommitted = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            shouldPauseNextLoad.set(true);
            Future<ProductResponse> slowLookup = executor.submit(() -> productService.get(productId));
            assertThat(loadedOldRow.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            Future<?> update = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                    @Override
                    public void afterCommit() {
                        updateCommitted.countDown();
                    }
                });
                productService.update(productId, RENAME);
            }));
            assertThat(updateCommitted.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)).isTrue();

            resumeLoad.countDown();
            assertThat(slowLookup.get(TIMEOUT_SECONDS, TimeUnit.SECONDS).name()).isEqualTo("Original Lamp");
            update.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            resumeLoad.countDown();
            executor.shutdownNow();
        }

        assertThat(productService.get(productId).name()).isEqualTo("Renamed Lamp");
    }

    private void requireResumed() throws InterruptedException {
        if (!resumeLoad.await(TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Paused product load was not resumed within " + TIMEOUT_SECONDS + "s");
        }
    }
}
