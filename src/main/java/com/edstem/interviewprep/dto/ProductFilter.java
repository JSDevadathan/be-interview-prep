package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.common.error.FieldValidationException;
import java.math.BigDecimal;

public record ProductFilter(
        String category,
        BigDecimal minPrice,
        BigDecimal maxPrice,
        boolean isInStockOnly,
        String nameContains) {

    public ProductFilter {
        requireNotNegative("minPrice", minPrice);
        requireNotNegative("maxPrice", maxPrice);
        if (minPrice != null && maxPrice != null && maxPrice.compareTo(minPrice) < 0) {
            throw new FieldValidationException("maxPrice", "maxPrice must not be less than minPrice");
        }
    }

    private static void requireNotNegative(String field, BigDecimal price) {
        if (price != null && price.signum() < 0) {
            throw new FieldValidationException(field, field + " must not be negative");
        }
    }
}
