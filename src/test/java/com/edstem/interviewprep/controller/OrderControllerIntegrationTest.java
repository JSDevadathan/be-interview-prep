package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.common.CacheConfig;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.CustomerOrderRepository;
import com.edstem.interviewprep.repository.ProductRepository;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

@SpringBootTest
@AutoConfigureMockMvc
@WithMockUser(username = OrderControllerIntegrationTest.CUSTOMER)
class OrderControllerIntegrationTest {

    static final String CUSTOMER = "alice";

    private static final String OTHER_CUSTOMER = "bob";
    private static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";
    private static final long UNKNOWN_ID = 999_999L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CustomerOrderRepository orderRepository;

    @Autowired
    private CacheManager cacheManager;

    private final List<Long> createdProductIds = new ArrayList<>();

    @AfterEach
    void deleteCreatedOrdersAndProducts() {
        orderRepository.deleteAll();
        productRepository.deleteAllById(createdProductIds);
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
    }

    @Test
    void placeOrderReservesStockForEveryItem() throws Exception {
        long lamp = createProduct(5);
        long mug = createProduct(3);

        ResultActions result = placeOrder("key-1", items(lamp, 2, mug, 3))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items[0].productId").value(lamp))
                .andExpect(jsonPath("$.items[0].quantity").value(2))
                .andExpect(jsonPath("$.items[1].productId").value(mug))
                .andExpect(jsonPath("$.items[1].quantity").value(3));

        assertThat(result.andReturn().getResponse().getHeader("Location")).endsWith("/api/orders/" + idOf(result));
        assertThat(stockOf(lamp)).isEqualTo(3);
        assertThat(stockOf(mug)).isZero();
    }

    @Test
    void retryWithSameIdempotencyKeyReturnsOriginalOrderWithoutTakingStockAgain() throws Exception {
        long lamp = createProduct(5);
        String body = items(lamp, 2);

        long orderId = idOf(placeOrder("retry-key", body).andExpect(status().isCreated()));

        placeOrder("retry-key", body)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId));

        assertThat(orderRepository.count()).isEqualTo(1);
        assertThat(stockOf(lamp)).isEqualTo(3);
    }

    @Test
    void reusingIdempotencyKeyForDifferentItemsIsRejected() throws Exception {
        long lamp = createProduct(5);
        placeOrder("reused-key", items(lamp, 1)).andExpect(status().isCreated());

        placeOrder("reused-key", items(lamp, 2))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail")
                        .value("Idempotency-Key reused-key was already used for a different request"));

        assertThat(stockOf(lamp)).isEqualTo(4);
    }

    @Test
    void sameIdempotencyKeyFromDifferentCustomersCreatesSeparateOrders() throws Exception {
        long lamp = createProduct(5);

        placeOrder("shared-key", items(lamp, 1)).andExpect(status().isCreated());
        placeOrderAs(OTHER_CUSTOMER, "shared-key", items(lamp, 1)).andExpect(status().isCreated());

        assertThat(orderRepository.count()).isEqualTo(2);
        assertThat(stockOf(lamp)).isEqualTo(3);
    }

    @Test
    void insufficientStockReturnsConflictWithClearMessage() throws Exception {
        long lamp = createProduct(2);

        placeOrder("too-many", items(lamp, 3))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail")
                        .value("Insufficient stock for product %d: requested 3, available 2".formatted(lamp)));

        assertThat(stockOf(lamp)).isEqualTo(2);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void orderIsAllOrNothingWhenOneItemIsShort() throws Exception {
        long plentiful = createProduct(10);
        long scarce = createProduct(1);

        placeOrder("partial", items(plentiful, 4, scarce, 2)).andExpect(status().isConflict());

        assertThat(stockOf(plentiful)).isEqualTo(10);
        assertThat(stockOf(scarce)).isEqualTo(1);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void failedRequestCanBeRetriedWithSameKeyOnceStockIsAvailable() throws Exception {
        long lamp = createProduct(1);
        placeOrder("try-again", items(lamp, 2)).andExpect(status().isConflict());
        Product restocked = productRepository.findById(lamp).orElseThrow();
        restocked.update(restocked.getName(), restocked.getCategory(), restocked.getPrice(), 2, restocked.getRating());
        productRepository.save(restocked);

        placeOrder("try-again", items(lamp, 2)).andExpect(status().isCreated());

        assertThat(stockOf(lamp)).isZero();
    }

    @Test
    void unknownProductReturnsNotFoundAndReservesNothing() throws Exception {
        long lamp = createProduct(5);

        placeOrder("unknown", items(lamp, 1, UNKNOWN_ID, 1))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 999999 was not found"));

        assertThat(stockOf(lamp)).isEqualTo(5);
    }

    @Test
    void placeOrderWithoutIdempotencyKeyIsRejected() throws Exception {
        long lamp = createProduct(5);

        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(items(lamp, 1)))
                .andExpect(status().isBadRequest());

        assertThat(stockOf(lamp)).isEqualTo(5);
    }

    @Test
    void placeOrderWithBlankIdempotencyKeyIsRejected() throws Exception {
        long lamp = createProduct(5);

        placeOrder(" ", items(lamp, 1))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value(IDEMPOTENCY_KEY_HEADER))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("Idempotency-Key must be 1 to 100 characters"));
    }

    @Test
    void placeOrderRejectsInvalidItemsWithOneMessagePerField() throws Exception {
        placeOrder("invalid", """
                {"items": [{"productId": null, "quantity": 0}]}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items[0].productId"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("productId is required"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("items[0].quantity"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("quantity must be at least 1"));
    }

    @Test
    void placeOrderRejectsEmptyItems() throws Exception {
        placeOrder("empty", """
                {"items": []}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("items must contain at least one item"));
    }

    @Test
    void placeOrderRejectsRepeatedProduct() throws Exception {
        long lamp = createProduct(5);

        placeOrder("repeated", items(lamp, 1, lamp, 2))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("items"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("items must not list the same productId more than once"));

        assertThat(stockOf(lamp)).isEqualTo(5);
    }

    @Test
    void cachedProductLookupShowsStockTakenByOrder() throws Exception {
        long lamp = createProduct(5);
        mockMvc.perform(get("/api/products/{id}", lamp)).andExpect(jsonPath("$.stock").value(5));

        placeOrder("cached", items(lamp, 2)).andExpect(status().isCreated());

        mockMvc.perform(get("/api/products/{id}", lamp)).andExpect(jsonPath("$.stock").value(3));
    }

    @Test
    void getReturnsOwnOrder() throws Exception {
        long lamp = createProduct(5);
        long orderId = idOf(placeOrder("get-own", items(lamp, 2)));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(orderId))
                .andExpect(jsonPath("$.status").value("PLACED"))
                .andExpect(jsonPath("$.items[0].quantity").value(2));
    }

    @Test
    void cancelReturnsStockOnce() throws Exception {
        long lamp = createProduct(5);
        long mug = createProduct(5);
        long orderId = idOf(placeOrder("cancel", items(lamp, 2, mug, 1)));

        cancel(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
        cancel(orderId)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        assertThat(stockOf(lamp)).isEqualTo(5);
        assertThat(stockOf(mug)).isEqualTo(5);
    }

    @Test
    void otherCustomersOrderIsNotVisibleOrCancellable() throws Exception {
        long lamp = createProduct(5);
        long orderId = idOf(placeOrderAs(OTHER_CUSTOMER, "private", items(lamp, 2)));

        mockMvc.perform(get("/api/orders/{id}", orderId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Order with id %d was not found".formatted(orderId)));
        cancel(orderId).andExpect(status().isNotFound());

        assertThat(stockOf(lamp)).isEqualTo(3);
    }

    @Test
    void cancelUnknownOrderReturnsNotFound() throws Exception {
        cancel(UNKNOWN_ID).andExpect(status().isNotFound());
    }

    private ResultActions placeOrder(String idempotencyKey, String json) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions placeOrderAs(String username, String idempotencyKey, String json) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .with(user(username))
                .header(IDEMPOTENCY_KEY_HEADER, idempotencyKey)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private ResultActions cancel(long orderId) throws Exception {
        return mockMvc.perform(post("/api/orders/{id}/cancel", orderId));
    }

    private static String items(long productId, int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}]}
                """.formatted(productId, quantity);
    }

    private static String items(long firstProductId, int firstQuantity, long secondProductId, int secondQuantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}, {"productId": %d, "quantity": %d}]}
                """.formatted(firstProductId, firstQuantity, secondProductId, secondQuantity);
    }

    private static long idOf(ResultActions result) throws Exception {
        return JsonPath.<Number>read(result.andReturn().getResponse().getContentAsString(), "$.id").longValue();
    }

    private int stockOf(long productId) {
        return productRepository.findById(productId).orElseThrow().getStock();
    }

    private long createProduct(int stock) {
        Product product = productRepository.save(new Product(
                "Test Lamp", "Home", new BigDecimal("19.99"), stock, new BigDecimal("4.0"),
                Instant.parse("2026-01-15T10:00:00Z")));
        createdProductIds.add(product.getId());
        return product.getId();
    }
}
