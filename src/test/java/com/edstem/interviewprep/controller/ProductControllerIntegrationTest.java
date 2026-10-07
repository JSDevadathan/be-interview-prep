package com.edstem.interviewprep.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.edstem.interviewprep.common.CacheConfig;
import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import com.edstem.interviewprep.service.ProductCatalogSeeder;
import com.jayway.jsonpath.JsonPath;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
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
@WithMockUser
class ProductControllerIntegrationTest {

    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int SEEDED_PAGE_COUNT_AT_DEFAULT_SIZE = 5;
    private static final int MAX_PAGE_SIZE = 100;
    private static final long UNKNOWN_ID = 999_999L;
    private static final String VALID_UPDATE = """
            {"name": "Renamed Lamp", "category": "Home", "price": 42.50, "stock": 3, "rating": 4.5}
            """;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    private final List<Long> createdProductIds = new ArrayList<>();

    @Autowired
    private CacheManager cacheManager;

    @AfterEach
    void deleteCreatedProductsAndClearCache() {
        productRepository.deleteAllById(createdProductIds);
        cacheManager.getCache(CacheConfig.PRODUCTS_CACHE).clear();
    }

    @Test
    void listReturnsFirstPageWithTotalCountAndPageCount() throws Exception {
        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content", hasSize(DEFAULT_PAGE_SIZE)))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.size").value(DEFAULT_PAGE_SIZE))
                .andExpect(jsonPath("$.totalElements").value(ProductCatalogSeeder.SEED_PRODUCT_COUNT))
                .andExpect(jsonPath("$.totalPages").value(SEEDED_PAGE_COUNT_AT_DEFAULT_SIZE));
    }

    @Test
    void listCapsPageSizeAtMaximum() throws Exception {
        mockMvc.perform(get("/api/products").param("size", "500"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size").value(MAX_PAGE_SIZE))
                .andExpect(jsonPath("$.content", hasSize(MAX_PAGE_SIZE)))
                .andExpect(jsonPath("$.totalPages").value(1));
    }

    @Test
    void listSortsByRequestedFieldAndDirection() throws Exception {
        String body = listAll("sort", "price,desc");

        List<BigDecimal> prices = JsonPath.<List<Number>>read(body, "$.content[*].price").stream()
                .map(price -> new BigDecimal(price.toString()))
                .toList();
        assertThat(prices).hasSize(ProductCatalogSeeder.SEED_PRODUCT_COUNT)
                .isSortedAccordingTo(Comparator.reverseOrder());
    }

    @Test
    void listAppliesAllFiltersTogether() throws Exception {
        BigDecimal minPrice = new BigDecimal("100");
        BigDecimal maxPrice = new BigDecimal("450");
        List<Long> expectedIds = productRepository.findAll().stream()
                .filter(product -> product.getCategory().equalsIgnoreCase("electronics"))
                .filter(product -> product.getPrice().compareTo(minPrice) >= 0)
                .filter(product -> product.getPrice().compareTo(maxPrice) <= 0)
                .filter(product -> product.getStock() > 0)
                .filter(product -> product.getName().toLowerCase(Locale.ROOT).contains("smart"))
                .map(Product::getId)
                .sorted()
                .toList();

        String body = listAll(
                "category", "electronics",
                "minPrice", minPrice.toPlainString(),
                "maxPrice", maxPrice.toPlainString(),
                "inStock", "true",
                "name", "SMART");

        assertThat(expectedIds).isNotEmpty();
        assertThat(idsIn(body)).isEqualTo(expectedIds);
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "\\"})
    void listTreatsLikeWildcardsInNameSearchLiterally(String wildcard) throws Exception {
        mockMvc.perform(get("/api/products").param("name", wildcard))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }

    @Test
    void listRejectsUnknownSortField() throws Exception {
        mockMvc.perform(get("/api/products").param("sort", "password"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("sort"))
                .andExpect(jsonPath("$.fieldErrors[0].message")
                        .value("sort must be one of [category, createdAt, id, name, price, rating, stock]"));
    }

    @Test
    void listRejectsMaxPriceBelowMinPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "50").param("maxPrice", "10"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("maxPrice"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("maxPrice must not be less than minPrice"));
    }

    @Test
    void listRejectsNegativePrice() throws Exception {
        mockMvc.perform(get("/api/products").param("minPrice", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("minPrice"))
                .andExpect(jsonPath("$.fieldErrors[0].message").value("minPrice must not be negative"));
    }

    @Test
    void listRejectsNonNumericPrice() throws Exception {
        mockMvc.perform(get("/api/products").param("maxPrice", "cheap"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors[0].field").value("maxPrice"));
    }

    @Test
    void getReturnsProduct() throws Exception {
        long id = createProduct();

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.name").value("Test Lamp"))
                .andExpect(jsonPath("$.category").value("Home"))
                .andExpect(jsonPath("$.price").value(19.99))
                .andExpect(jsonPath("$.stock").value(5))
                .andExpect(jsonPath("$.rating").value(4.0))
                .andExpect(jsonPath("$.createdAt").value("2026-01-15T10:00:00Z"));
    }

    @Test
    void getUnknownProductReturnsNotFound() throws Exception {
        mockMvc.perform(get("/api/products/{id}", UNKNOWN_ID))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Product with id 999999 was not found"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminUpdateIsVisibleOnNextLookup() throws Exception {
        long id = createProduct();
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        putProduct(id, VALID_UPDATE)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Lamp"));

        mockMvc.perform(get("/api/products/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Renamed Lamp"))
                .andExpect(jsonPath("$.price").value(42.5))
                .andExpect(jsonPath("$.stock").value(3));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateRejectsInvalidFieldsWithOneMessagePerField() throws Exception {
        long id = createProduct();

        putProduct(id, """
                {"name": " ", "category": "Home", "price": -1, "stock": 3, "rating": 5.5}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.fieldErrors[0].field").value("name"))
                .andExpect(jsonPath("$.fieldErrors[1].field").value("price"))
                .andExpect(jsonPath("$.fieldErrors[1].message").value("price must not be negative"))
                .andExpect(jsonPath("$.fieldErrors[2].field").value("rating"))
                .andExpect(jsonPath("$.fieldErrors[2].message").value("rating must be between 0.0 and 5.0"));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void updateUnknownProductReturnsNotFound() throws Exception {
        putProduct(UNKNOWN_ID, VALID_UPDATE).andExpect(status().isNotFound());
    }

    @Test
    void updateByNonAdminIsForbidden() throws Exception {
        long id = createProduct();

        putProduct(id, VALID_UPDATE).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    void adminDeleteMakesNextLookupReturnNotFound() throws Exception {
        long id = createProduct();
        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isOk());

        mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isNoContent());

        mockMvc.perform(get("/api/products/{id}", id)).andExpect(status().isNotFound());
    }

    @Test
    void deleteByNonAdminIsForbidden() throws Exception {
        long id = createProduct();

        mockMvc.perform(delete("/api/products/{id}", id)).andExpect(status().isForbidden());
    }

    private long createProduct() {
        Product product = productRepository.save(new Product(
                "Test Lamp", "Home", new BigDecimal("19.99"), 5, new BigDecimal("4.0"),
                Instant.parse("2026-01-15T10:00:00Z")));
        createdProductIds.add(product.getId());
        return product.getId();
    }

    private String listAll(String... parameterPairs) throws Exception {
        var request = get("/api/products").param("size", String.valueOf(MAX_PAGE_SIZE));
        for (int index = 0; index < parameterPairs.length; index += 2) {
            request.param(parameterPairs[index], parameterPairs[index + 1]);
        }
        return mockMvc.perform(request)
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getContentAsString();
    }

    private static List<Long> idsIn(String body) {
        return JsonPath.<List<Number>>read(body, "$.content[*].id").stream()
                .map(Number::longValue)
                .toList();
    }

    private ResultActions putProduct(long id, String json) throws Exception {
        return mockMvc.perform(put("/api/products/{id}", id)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }
}
