package com.edstem.interviewprep.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.edstem.interviewprep.common.error.ResourceConflictException;
import com.edstem.interviewprep.dto.CreateOrderRequest;
import com.edstem.interviewprep.dto.OrderItemRequest;
import com.edstem.interviewprep.dto.OrderResponse;
import com.edstem.interviewprep.dto.UpdateProductRequest;
import com.edstem.interviewprep.enums.OrderStatus;
import com.edstem.interviewprep.dto.PlacedOrder;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.CustomerOrderRepository;
import com.edstem.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest
class OrderConcurrencyTest {

    private static final int SIMULTANEOUS_ORDERS = 50;
    private static final int INITIAL_STOCK = 10;
    private static final long TIMEOUT_SECONDS = 30;
    private static final long BLOCKED_WAIT_MILLIS = 500;

    @Autowired
    private OrderService orderService;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private ProductService productService;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final List<Long> createdProductIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedOrdersAndProducts() {
        orderRepository.deleteAll();
        productRepository.deleteAllById(createdProductIds);
    }

    @Test
    void simultaneousOrdersNeverOversellStock() throws Exception {
        long productId = createProduct(INITIAL_STOCK);
        CreateOrderRequest oneUnit = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1)));

        List<Outcome> outcomes = runSimultaneously(SIMULTANEOUS_ORDERS,
                attempt -> () -> orderService.place("customer" + attempt, "order-" + attempt, oneUnit));

        assertThat(outcomes).filteredOn(Outcome::isSuccess).hasSize(INITIAL_STOCK);
        assertThat(outcomes).filteredOn(outcome -> !outcome.isSuccess())
                .hasSize(SIMULTANEOUS_ORDERS - INITIAL_STOCK)
                .allSatisfy(outcome -> assertThat(outcome.failure()).isInstanceOf(ResourceConflictException.class));
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isZero();
        assertThat(orderRepository.count()).isEqualTo(INITIAL_STOCK);
    }

    @Test
    void simultaneousRetriesOfOneRequestCreateOneOrder() throws Exception {
        long productId = createProduct(INITIAL_STOCK);
        CreateOrderRequest twoUnits = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 2)));
        int simultaneousRetries = 10;

        List<Outcome> outcomes = runSimultaneously(simultaneousRetries,
                attempt -> () -> orderService.place("customer", "same-key", twoUnits));

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.isSuccess()).isTrue());
        assertThat(outcomes).extracting(outcome -> outcome.placed().order().id()).containsOnly(
                outcomes.get(0).placed().order().id());
        assertThat(outcomes).filteredOn(outcome -> !outcome.placed().isReplay()).hasSize(1);
        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(INITIAL_STOCK - 2);
    }

    @Test
    void simultaneousCancelsReturnStockOnce() throws Exception {
        long productId = createProduct(INITIAL_STOCK);
        PlacedOrder placed = orderService.place("customer", "cancel-key",
                new CreateOrderRequest(List.of(new OrderItemRequest(productId, 3))));
        Long orderId = placed.order().id();
        int simultaneousCancels = 10;

        List<Outcome> outcomes = runSimultaneously(simultaneousCancels, attempt -> () -> {
            OrderResponse cancelled = orderService.cancel("customer", orderId);
            assertThat(cancelled.status()).isEqualTo(OrderStatus.CANCELLED);
            return placed;
        });

        assertThat(outcomes).allSatisfy(outcome -> assertThat(outcome.isSuccess()).isTrue());
        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(INITIAL_STOCK);
    }

    /**
     * The admin update reads the product, then holds its transaction open while an order tries to reserve stock.
     * If the order could commit in that window, the admin's flush would write back the stock it read and erase
     * the reservation.
     */
    @Test
    void adminStockUpdateDoesNotEraseConcurrentReservation() throws Exception {
        long productId = createProduct(INITIAL_STOCK);
        Product product = productRepository.findById(productId).orElseThrow();
        UpdateProductRequest rename = new UpdateProductRequest(
                "Renamed Lamp", product.getCategory(), product.getPrice(), INITIAL_STOCK, product.getRating());
        CreateOrderRequest oneUnit = new CreateOrderRequest(List.of(new OrderItemRequest(productId, 1)));
        CountDownLatch adminHasReadProduct = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<?> adminUpdate = executor.submit(() -> transactionTemplate.executeWithoutResult(status -> {
                productService.update(productId, rename);
                adminHasReadProduct.countDown();
                pause(BLOCKED_WAIT_MILLIS);
            }));
            adminHasReadProduct.await(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            Future<PlacedOrder> order = executor.submit(() -> orderService.place("customer", "during-update", oneUnit));

            adminUpdate.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            order.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } finally {
            executor.shutdownNow();
        }

        assertThat(productRepository.findById(productId).orElseThrow().getStock()).isEqualTo(INITIAL_STOCK - 1);
    }

    private static void pause(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while holding the admin transaction open", interrupted);
        }
    }

    private interface AttemptFactory {
        Callable<PlacedOrder> forAttempt(int attempt);
    }

    private record Outcome(PlacedOrder placed, Throwable failure) {

        boolean isSuccess() {
            return failure == null;
        }
    }

    private static List<Outcome> runSimultaneously(int attempts, AttemptFactory factory) throws Exception {
        CountDownLatch startSignal = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(attempts);
        try {
            List<Future<PlacedOrder>> futures = new ArrayList<>();
            for (int attempt = 0; attempt < attempts; attempt++) {
                Callable<PlacedOrder> call = factory.forAttempt(attempt);
                futures.add(executor.submit(() -> {
                    startSignal.await();
                    return call.call();
                }));
            }
            startSignal.countDown();
            List<Outcome> outcomes = new ArrayList<>();
            for (Future<PlacedOrder> future : futures) {
                outcomes.add(outcomeOf(future));
            }
            return outcomes;
        } finally {
            executor.shutdownNow();
        }
    }

    private static Outcome outcomeOf(Future<PlacedOrder> future) throws Exception {
        try {
            return new Outcome(future.get(TIMEOUT_SECONDS, TimeUnit.SECONDS), null);
        } catch (ExecutionException failure) {
            return new Outcome(null, failure.getCause());
        }
    }

    private long createProduct(int stock) {
        Product product = productRepository.save(new Product(
                "Limited Lamp", "Home", new BigDecimal("19.99"), stock, new BigDecimal("4.0"),
                Instant.parse("2026-01-15T10:00:00Z")));
        createdProductIds.add(product.getId());
        return product.getId();
    }
}
