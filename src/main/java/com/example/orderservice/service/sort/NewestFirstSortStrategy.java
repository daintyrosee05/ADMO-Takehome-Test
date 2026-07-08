package com.example.orderservice.service.sort;

import com.example.orderservice.model.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class NewestFirstSortStrategy implements OrderSortStrategy {

    @Override
    public String key() {
        return "newest";
    }

    @Override
    public Comparator<Order> comparator() {
        return Comparator.comparing(Order::getCreatedAt).reversed();
    }
}
