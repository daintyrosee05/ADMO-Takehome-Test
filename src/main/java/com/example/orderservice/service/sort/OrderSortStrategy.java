package com.example.orderservice.service.sort;

import com.example.orderservice.model.Order;

import java.util.Comparator;

/**
 * Strategy pattern: each ordering rule the list endpoint supports is one
 * implementation of this interface, registered as a Spring bean. Adding a
 * new rule (e.g. "lowest total first") means adding one new class - nothing
 * here, in the factory, or in the controller needs to change (Open/Closed
 * principle), which is exactly what the spec asks for.
 */
public interface OrderSortStrategy {

    /**
     * The value clients pass via ?sort=<key>, e.g. "newest".
     */
    String key();

    Comparator<Order> comparator();
}
