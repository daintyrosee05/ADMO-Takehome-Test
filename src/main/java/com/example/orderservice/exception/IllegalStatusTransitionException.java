package com.example.orderservice.exception;

import com.example.orderservice.model.OrderStatus;

public class IllegalStatusTransitionException extends RuntimeException {
    public IllegalStatusTransitionException(OrderStatus from, OrderStatus to) {
        super("Cannot transition order from " + from + " to " + to);
    }

    public IllegalStatusTransitionException(String message) {
        super(message);
    }
}
