package com.edstem.interviewprep.controller;

import com.edstem.interviewprep.dto.CreateOrderRequest;
import com.edstem.interviewprep.dto.OrderResponse;
import com.edstem.interviewprep.dto.PlacedOrder;
import com.edstem.interviewprep.service.OrderService;
import jakarta.validation.Valid;
import java.net.URI;
import java.security.Principal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> place(
            @RequestHeader(OrderService.IDEMPOTENCY_KEY_HEADER) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request,
            Principal principal) {
        PlacedOrder placed = orderService.place(principal.getName(), idempotencyKey, request);
        if (placed.isReplay()) {
            return ResponseEntity.ok(placed.order());
        }
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(placed.order().id())
                .toUri();
        return ResponseEntity.created(location).body(placed.order());
    }

    @GetMapping("/{id}")
    public OrderResponse get(@PathVariable Long id, Principal principal) {
        return orderService.get(principal.getName(), id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable Long id, Principal principal) {
        return orderService.cancel(principal.getName(), id);
    }
}
