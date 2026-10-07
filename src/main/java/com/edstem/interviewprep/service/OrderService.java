package com.edstem.interviewprep.service;

import com.edstem.interviewprep.common.error.FieldValidationException;
import com.edstem.interviewprep.common.error.IdempotencyKeyReusedException;
import com.edstem.interviewprep.common.error.ResourceConflictException;
import com.edstem.interviewprep.common.error.ResourceNotFoundException;
import com.edstem.interviewprep.dto.CreateOrderRequest;
import com.edstem.interviewprep.dto.OrderItemRequest;
import com.edstem.interviewprep.dto.OrderItemResponse;
import com.edstem.interviewprep.dto.OrderResponse;
import com.edstem.interviewprep.dto.PlacedOrder;
import com.edstem.interviewprep.entity.CustomerOrder;
import com.edstem.interviewprep.entity.OrderItem;
import com.edstem.interviewprep.repository.CustomerOrderRepository;
import com.edstem.interviewprep.repository.ProductRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class OrderService {

    public static final String IDEMPOTENCY_KEY_HEADER = "Idempotency-Key";

    private static final String RESOURCE_NAME = "Order";
    private static final String ITEMS_FIELD = "items";
    private static final ChronoUnit DATABASE_TIMESTAMP_PRECISION = ChronoUnit.MICROS;

    private final CustomerOrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final ProductService productService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    public OrderService(
            CustomerOrderRepository orderRepository,
            ProductRepository productRepository,
            ProductService productService,
            TransactionTemplate transactionTemplate,
            Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.productService = productService;
        this.transactionTemplate = transactionTemplate;
        this.clock = clock;
    }

    /**
     * A repeated Idempotency-Key from the same customer returns the order it first created instead of placing a new
     * one. Two copies of a request can also arrive at the same moment: both miss the lookup, the unique key lets
     * only one insert commit, and the loser rolls back and returns the winner's order.
     */
    public PlacedOrder place(String customerUsername, String idempotencyKey, CreateOrderRequest request) {
        requireValidIdempotencyKey(idempotencyKey);
        List<OrderItemResponse> requestedItems = inProductOrder(request.items());
        Optional<PlacedOrder> replay = findReplay(customerUsername, idempotencyKey, requestedItems);
        if (replay.isPresent()) {
            return replay.get();
        }
        try {
            return transactionTemplate.execute(
                    status -> createOrder(customerUsername, idempotencyKey, requestedItems));
        } catch (DataIntegrityViolationException | ResourceConflictException failure) {
            return findReplay(customerUsername, idempotencyKey, requestedItems).orElseThrow(() -> failure);
        }
    }

    @Transactional(readOnly = true)
    public OrderResponse get(String customerUsername, Long id) {
        return orderRepository.findByIdAndCustomerUsername(id, customerUsername)
                .map(OrderResponse::from)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
    }

    /**
     * The row lock makes a second cancel of the same order wait and then see it already cancelled, so its stock
     * is returned exactly once.
     */
    @Transactional
    public OrderResponse cancel(String customerUsername, Long id) {
        CustomerOrder order = orderRepository.findForUpdate(id, customerUsername)
                .orElseThrow(() -> new ResourceNotFoundException(RESOURCE_NAME, id));
        if (order.isCancelled()) {
            return OrderResponse.from(order);
        }
        order.cancel();
        order.getItems().stream()
                .sorted(Comparator.comparing(OrderItem::getProductId))
                .forEach(item -> productService.releaseStock(item.getProductId(), item.getQuantity()));
        return OrderResponse.from(order);
    }

    /**
     * Items are reserved in product id order so that two orders sharing products always lock their rows in the
     * same sequence and cannot deadlock each other.
     */
    private PlacedOrder createOrder(String customerUsername, String idempotencyKey, List<OrderItemResponse> items) {
        items.forEach(item -> productService.reserveStock(item.productId(), item.quantity()));
        CustomerOrder order = new CustomerOrder(
                customerUsername, idempotencyKey, Instant.now(clock).truncatedTo(DATABASE_TIMESTAMP_PRECISION));
        items.forEach(item -> order.addItem(productRepository.getReferenceById(item.productId()), item.quantity()));
        return new PlacedOrder(OrderResponse.from(orderRepository.saveAndFlush(order)), false);
    }

    private Optional<PlacedOrder> findReplay(
            String customerUsername, String idempotencyKey, List<OrderItemResponse> requestedItems) {
        return orderRepository.findByCustomerUsernameAndIdempotencyKey(customerUsername, idempotencyKey)
                .map(OrderResponse::from)
                .map(existing -> {
                    if (!existing.items().equals(requestedItems)) {
                        throw new IdempotencyKeyReusedException(idempotencyKey);
                    }
                    return new PlacedOrder(existing, true);
                });
    }

    private static List<OrderItemResponse> inProductOrder(List<OrderItemRequest> items) {
        List<OrderItemResponse> sorted = items.stream()
                .map(item -> new OrderItemResponse(item.productId(), item.quantity()))
                .sorted(Comparator.comparing(OrderItemResponse::productId))
                .toList();
        long distinctProducts = sorted.stream().map(OrderItemResponse::productId).distinct().count();
        if (distinctProducts < sorted.size()) {
            throw new FieldValidationException(ITEMS_FIELD, "items must not list the same productId more than once");
        }
        return sorted;
    }

    private static void requireValidIdempotencyKey(String idempotencyKey) {
        if (idempotencyKey.isBlank() || idempotencyKey.length() > CustomerOrder.IDEMPOTENCY_KEY_MAX_LENGTH) {
            throw new FieldValidationException(IDEMPOTENCY_KEY_HEADER, "%s must be 1 to %d characters"
                    .formatted(IDEMPOTENCY_KEY_HEADER, CustomerOrder.IDEMPOTENCY_KEY_MAX_LENGTH));
        }
    }
}
