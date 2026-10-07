package com.edstem.interviewprep.dto;

/**
 * {@code isReplay} is true when the request repeated an Idempotency-Key and the earlier order was returned.
 */
public record PlacedOrder(OrderResponse order, boolean isReplay) {
}
