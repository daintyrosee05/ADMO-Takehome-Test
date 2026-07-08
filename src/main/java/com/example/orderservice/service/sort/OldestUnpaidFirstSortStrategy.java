package com.example.orderservice.service.sort;

import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderStatus;
import org.springframework.stereotype.Component;

import java.util.Comparator;

/**
 * Orders still in CREATED (i.e. not yet paid) come first, oldest first
 * within that group; everything else (PAID and beyond, including CANCELLED)
 * follows, also oldest first. This is a case where the ordering key isn't a
 * single column, which is exactly why sorting is done in the service layer
 * via Comparator rather than pushed down as a JPA Sort - see README.
 */
@Component
public class OldestUnpaidFirstSortStrategy implements OrderSortStrategy {

    @Override
    public String key() {
        return "oldestUnpaidFirst";
    }

    @Override
    public Comparator<Order> comparator() {
        return Comparator
                .<Order, Integer>comparing(o -> o.getStatus() == OrderStatus.CREATED ? 0 : 1)
                .thenComparing(Order::getCreatedAt);
    }
}
