package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.Product;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record UpdateProductRequest(
        @NotBlank(message = "name is required")
        @Size(max = Product.NAME_MAX_LENGTH, message = "name must be at most " + Product.NAME_MAX_LENGTH + " characters")
        String name,

        @NotBlank(message = "category is required")
        @Size(max = Product.CATEGORY_MAX_LENGTH,
                message = "category must be at most " + Product.CATEGORY_MAX_LENGTH + " characters")
        String category,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.00", message = "price must not be negative")
        @Digits(integer = Product.PRICE_INTEGER_DIGITS, fraction = Product.PRICE_FRACTION_DIGITS,
                message = "price must have at most " + Product.PRICE_INTEGER_DIGITS + " whole digits and "
                        + Product.PRICE_FRACTION_DIGITS + " decimal places")
        BigDecimal price,

        @NotNull(message = "stock is required")
        @PositiveOrZero(message = "stock must not be negative")
        Integer stock,

        @NotNull(message = "rating is required")
        @DecimalMin(value = "0.0", message = "rating must be between 0.0 and " + Product.MAX_RATING)
        @DecimalMax(value = Product.MAX_RATING, message = "rating must be between 0.0 and " + Product.MAX_RATING)
        @Digits(integer = 1, fraction = 1, message = "rating must have at most one decimal place")
        BigDecimal rating) {
}
