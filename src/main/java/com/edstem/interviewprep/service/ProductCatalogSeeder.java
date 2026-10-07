package com.edstem.interviewprep.service;

import com.edstem.interviewprep.entity.Product;
import com.edstem.interviewprep.repository.ProductRepository;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.IntStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Seeds a deterministic catalog, so listings, filters and sorting return the same results on every run.
 */
@Component
public class ProductCatalogSeeder implements ApplicationRunner {

    public static final int SEED_PRODUCT_COUNT = 100;

    private static final List<String> CATEGORIES = List.of("Electronics", "Books", "Home", "Sports", "Toys");
    private static final List<String> ITEMS = List.of("Headphones", "Notebook", "Lamp", "Water Bottle", "Puzzle");
    private static final List<String> ADJECTIVES =
            List.of("Classic", "Compact", "Deluxe", "Eco", "Pro", "Smart", "Travel", "Ultra", "Vintage", "Wireless");
    private static final Duration CREATION_INTERVAL = Duration.ofHours(1);
    private static final int OUT_OF_STOCK_EVERY = 7;
    private static final int MAX_STOCK = 50;
    private static final int MIN_PRICE_CENTS = 499;
    private static final int PRICE_RANGE_CENTS = 50_000;
    private static final long PRICE_SPREAD_FACTOR = 7_919;
    private static final int MIN_RATING_TENTHS = 10;
    private static final int RATING_RANGE_TENTHS = 41;
    private static final long RATING_SPREAD_FACTOR = 17;

    private static final Logger log = LoggerFactory.getLogger(ProductCatalogSeeder.class);

    private final ProductRepository productRepository;
    private final Clock clock;

    public ProductCatalogSeeder(ProductRepository productRepository, Clock clock) {
        this.productRepository = productRepository;
        this.clock = clock;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (productRepository.count() > 0) {
            return;
        }
        Instant now = Instant.now(clock).truncatedTo(ChronoUnit.SECONDS);
        productRepository.saveAll(IntStream.rangeClosed(1, SEED_PRODUCT_COUNT)
                .mapToObj(number -> seedProduct(number, now))
                .toList());
        log.info("Seeded {} products", SEED_PRODUCT_COUNT);
    }

    private static Product seedProduct(int number, Instant now) {
        int categoryIndex = number % CATEGORIES.size();
        String name = "%s %s %03d".formatted(
                ADJECTIVES.get(number % ADJECTIVES.size()), ITEMS.get(categoryIndex), number);
        BigDecimal price =
                BigDecimal.valueOf(MIN_PRICE_CENTS + (number * PRICE_SPREAD_FACTOR) % PRICE_RANGE_CENTS, 2);
        int stock = number % OUT_OF_STOCK_EVERY == 0 ? 0 : number % MAX_STOCK + 1;
        BigDecimal rating =
                BigDecimal.valueOf(MIN_RATING_TENTHS + (number * RATING_SPREAD_FACTOR) % RATING_RANGE_TENTHS, 1);
        Instant createdAt = now.minus(CREATION_INTERVAL.multipliedBy(SEED_PRODUCT_COUNT - number));
        return new Product(name, CATEGORIES.get(categoryIndex), price, stock, rating, createdAt);
    }
}
