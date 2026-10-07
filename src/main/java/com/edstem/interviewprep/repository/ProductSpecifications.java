package com.edstem.interviewprep.repository;

import com.edstem.interviewprep.dto.ProductFilter;
import com.edstem.interviewprep.entity.Product;
import java.math.BigDecimal;
import java.util.Locale;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.util.StringUtils;

public final class ProductSpecifications {

    private static final char LIKE_ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> matching(ProductFilter filter) {
        return Specification.allOf(
                hasCategory(filter.category()),
                priceAtLeast(filter.minPrice()),
                priceAtMost(filter.maxPrice()),
                isInStock(filter.isInStockOnly()),
                nameContains(filter.nameContains()));
    }

    private static Specification<Product> hasCategory(String category) {
        if (!StringUtils.hasText(category)) {
            return null;
        }
        String normalizedCategory = category.strip().toLowerCase(Locale.ROOT);
        return (root, query, builder) -> builder.equal(builder.lower(root.get("category")), normalizedCategory);
    }

    private static Specification<Product> priceAtLeast(BigDecimal minPrice) {
        if (minPrice == null) {
            return null;
        }
        return (root, query, builder) -> builder.greaterThanOrEqualTo(root.get("price"), minPrice);
    }

    private static Specification<Product> priceAtMost(BigDecimal maxPrice) {
        if (maxPrice == null) {
            return null;
        }
        return (root, query, builder) -> builder.lessThanOrEqualTo(root.get("price"), maxPrice);
    }

    private static Specification<Product> isInStock(boolean isInStockOnly) {
        if (!isInStockOnly) {
            return null;
        }
        return (root, query, builder) -> builder.greaterThan(root.get("stock"), 0);
    }

    private static Specification<Product> nameContains(String text) {
        if (!StringUtils.hasText(text)) {
            return null;
        }
        String pattern = "%" + escapeLikeWildcards(text.strip().toLowerCase(Locale.ROOT)) + "%";
        return (root, query, builder) -> builder.like(builder.lower(root.get("name")), pattern, LIKE_ESCAPE);
    }

    private static String escapeLikeWildcards(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
