package com.example.orderservice.service;

import com.example.orderservice.dto.OrderItemRequest;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.exception.IllegalStatusTransitionException;
import com.example.orderservice.exception.InvalidOrderException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.sort.OrderSortStrategy;
import com.example.orderservice.service.sort.SortStrategyRegistry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@Transactional
public class OrderService {

    /**
     * Legal status transitions. A transition is legal only if it appears
     * here; anything else - including staying in a terminal state - is
     * rejected. Adding a future status just means adding one more map entry;
     * none of the transition methods below need to change shape.
     */
    private static final Map<OrderStatus, Set<OrderStatus>> ALLOWED_TRANSITIONS = new EnumMap<>(OrderStatus.class);

    static {
        ALLOWED_TRANSITIONS.put(OrderStatus.CREATED, EnumSet.of(OrderStatus.PAID, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.PAID, EnumSet.of(OrderStatus.SHIPPED, OrderStatus.CANCELLED));
        ALLOWED_TRANSITIONS.put(OrderStatus.SHIPPED, EnumSet.of(OrderStatus.DELIVERED));
        ALLOWED_TRANSITIONS.put(OrderStatus.DELIVERED, EnumSet.noneOf(OrderStatus.class));
        ALLOWED_TRANSITIONS.put(OrderStatus.CANCELLED, EnumSet.noneOf(OrderStatus.class));
    }

    private final OrderRepository orderRepository;
    private final SortStrategyRegistry sortStrategyRegistry;

    public OrderService(OrderRepository orderRepository, SortStrategyRegistry sortStrategyRegistry) {
        this.orderRepository = orderRepository;
        this.sortStrategyRegistry = sortStrategyRegistry;
    }

    public Order createOrder(OrderRequest request) {
        Order order = new Order(request.getCustomerName());
        applyItems(order, request.getItems());
        return orderRepository.save(order);
    }

    public Order getOrder(UUID orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    /**
     * List is paginated and sorted in-memory via the selected Comparator
     * strategy. For a domain of this size that's a deliberate simplicity
     * trade-off over pushing every rule down into a JPA Sort / Specification
     * (see README "Design Decisions" - some rules, like oldestUnpaidFirst,
     * aren't expressible as a single-column DB sort anyway).
     */
    public Page<Order> listOrders(Pageable pageable, String sortKey) {
        OrderSortStrategy strategy = sortStrategyRegistry.get(sortKey);
        List<Order> sorted = orderRepository.findAll().stream()
                .sorted(strategy.comparator())
                .collect(Collectors.toList());

        int start = Math.min((int) pageable.getOffset(), sorted.size());
        int end = Math.min(start + pageable.getPageSize(), sorted.size());
        return new PageImpl<>(sorted.subList(start, end), pageable, sorted.size());
    }

    public Order updateOrder(UUID orderId, OrderRequest request) {
        Order order = getOrder(orderId);

        boolean itemsChanged = itemsDiffer(order, request.getItems());
        if (itemsChanged && order.getStatus() != OrderStatus.CREATED) {
            // "Items are immutable after payment." CREATED is the only status
            // where items may still change; everything from PAID onward
            // (PAID, SHIPPED, DELIVERED, CANCELLED) locks them.
            throw new InvalidOrderException(
                    "Line items cannot be modified once an order is " + order.getStatus());
        }

        order.setCustomerName(request.getCustomerName());
        if (itemsChanged) {
            applyItems(order, request.getItems());
        }
        return orderRepository.save(order);
    }

    public void deleteOrder(UUID orderId) {
        if (!orderRepository.existsById(orderId)) {
            throw new OrderNotFoundException(orderId);
        }
        orderRepository.deleteById(orderId);
    }

    public Order markPaid(UUID orderId) {
        return transition(orderId, OrderStatus.PAID);
    }

    public Order markShipped(UUID orderId) {
        return transition(orderId, OrderStatus.SHIPPED);
    }

    public Order markDelivered(UUID orderId) {
        return transition(orderId, OrderStatus.DELIVERED);
    }

    public Order cancel(UUID orderId, String reason) {
        Order order = getOrder(orderId);
        assertTransitionAllowed(order.getStatus(), OrderStatus.CANCELLED);
        order.setStatus(OrderStatus.CANCELLED);
        order.setCancellationReason(reason);
        return orderRepository.save(order);
    }

    private Order transition(UUID orderId, OrderStatus target) {
        Order order = getOrder(orderId);
        assertTransitionAllowed(order.getStatus(), target);
        order.setStatus(target);
        return orderRepository.save(order);
    }

    private void assertTransitionAllowed(OrderStatus from, OrderStatus to) {
        Set<OrderStatus> allowed = ALLOWED_TRANSITIONS.getOrDefault(from, Set.of());
        if (!allowed.contains(to)) {
            throw new IllegalStatusTransitionException(from, to);
        }
    }

    private void applyItems(Order order, List<OrderItemRequest> itemRequests) {
        order.clearItems();
        for (OrderItemRequest itemRequest : itemRequests) {
            order.addItem(new OrderItem(
                    itemRequest.getProductName(),
                    itemRequest.getQuantity(),
                    itemRequest.getUnitPrice()));
        }
        order.recalculateTotal();
    }

    private boolean itemsDiffer(Order order, List<OrderItemRequest> newItems) {
        List<OrderItem> current = order.getItems();
        if (current.size() != newItems.size()) {
            return true;
        }
        for (int i = 0; i < current.size(); i++) {
            OrderItem existing = current.get(i);
            OrderItemRequest incoming = newItems.get(i);
            if (!existing.getProductName().equals(incoming.getProductName())
                    || !existing.getQuantity().equals(incoming.getQuantity())
                    || existing.getUnitPrice().compareTo(incoming.getUnitPrice()) != 0) {
                return true;
            }
        }
        return false;
    }
}
