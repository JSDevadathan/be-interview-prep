package com.edstem.interviewprep.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public record OrderItemRequest(
        @NotNull(message = "productId is required")
        Long productId,

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        @Max(value = OrderItemRequest.MAX_QUANTITY,
                message = "quantity must be at most " + OrderItemRequest.MAX_QUANTITY)
        Integer quantity) {

    public static final int MAX_QUANTITY = 1_000;
}
