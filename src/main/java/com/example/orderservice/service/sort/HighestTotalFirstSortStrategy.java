package com.example.orderservice.service.sort;

import com.example.orderservice.model.Order;
import org.springframework.stereotype.Component;

import java.util.Comparator;

@Component
public class HighestTotalFirstSortStrategy implements OrderSortStrategy {

    @Override
    public String key() {
        return "highestTotal";
    }

    @Override
    public Comparator<Order> comparator() {
        return Comparator.comparing(Order::getTotalAmount).reversed();
    }
}
