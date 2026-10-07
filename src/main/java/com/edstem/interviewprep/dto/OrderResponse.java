package com.edstem.interviewprep.dto;

import com.edstem.interviewprep.entity.CustomerOrder;
import com.edstem.interviewprep.enums.OrderStatus;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public record OrderResponse(Long id, OrderStatus status, List<OrderItemResponse> items, Instant createdAt) {

    public static OrderResponse from(CustomerOrder order) {
        List<OrderItemResponse> items = order.getItems().stream()
                .map(OrderItemResponse::from)
                .sorted(Comparator.comparing(OrderItemResponse::productId))
                .toList();
        return new OrderResponse(order.getId(), order.getStatus(), items, order.getCreatedAt());
    }
}
