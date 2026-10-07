package com.edstem.interviewprep.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateOrderRequest(
        @NotEmpty(message = "items must contain at least one item")
        @Size(max = CreateOrderRequest.MAX_ITEMS,
                message = "items must contain at most " + CreateOrderRequest.MAX_ITEMS + " items")
        List<@Valid @NotNull(message = "items must not contain null") OrderItemRequest> items) {

    public static final int MAX_ITEMS = 50;
}
