package com.edstem.interviewprep.entity;

import com.edstem.interviewprep.enums.OrderStatus;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Named {@code CustomerOrder} because {@code ORDER} is a reserved word in both SQL and JPQL.
 */
@Entity
@Table(name = "orders", uniqueConstraints = @UniqueConstraint(
        name = "uk_orders_customer_idempotency_key", columnNames = {"customer_username", "idempotency_key"}))
public class CustomerOrder {

    public static final int IDEMPOTENCY_KEY_MAX_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, updatable = false, length = UserAccount.USERNAME_MAX_LENGTH)
    private String customerUsername;

    @Column(nullable = false, updatable = false, length = IDEMPOTENCY_KEY_MAX_LENGTH)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private OrderStatus status;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true)
    private List<OrderItem> items = new ArrayList<>();

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CustomerOrder() {
    }

    public CustomerOrder(String customerUsername, String idempotencyKey, Instant createdAt) {
        this.customerUsername = customerUsername;
        this.idempotencyKey = idempotencyKey;
        this.status = OrderStatus.PLACED;
        this.createdAt = createdAt;
    }

    public void addItem(Product product, int quantity) {
        items.add(new OrderItem(this, product, quantity));
    }

    public boolean isCancelled() {
        return status == OrderStatus.CANCELLED;
    }

    public void cancel() {
        status = OrderStatus.CANCELLED;
    }

    public Long getId() {
        return id;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public List<OrderItem> getItems() {
        return Collections.unmodifiableList(items);
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
