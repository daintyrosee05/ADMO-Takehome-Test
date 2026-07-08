package com.example.orderservice.controller;

import com.example.orderservice.dto.CancelRequest;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.dto.OrderResponse;
import com.example.orderservice.model.Order;
import com.example.orderservice.service.OrderService;
import jakarta.validation.Valid;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(@Valid @RequestBody OrderRequest request) {
        Order order = orderService.createOrder(request);
        OrderResponse body = new OrderResponse(order);
        return ResponseEntity.created(URI.create("/api/orders/" + order.getOrderId())).body(body);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable UUID id) {
        return new OrderResponse(orderService.getOrder(id));
    }

    @GetMapping
    public Page<OrderResponse> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "newest") String sort) {
        Pageable pageable = PageRequest.of(page, size);
        return orderService.listOrders(pageable, sort).map(OrderResponse::new);
    }

    @PutMapping("/{id}")
    public OrderResponse updateOrder(@PathVariable UUID id, @Valid @RequestBody OrderRequest request) {
        return new OrderResponse(orderService.updateOrder(id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable UUID id) {
        orderService.deleteOrder(id);
        return ResponseEntity.noContent().build();
    }

    // --- Status transitions -------------------------------------------------
    // Each transition is its own endpoint rather than a generic
    // PATCH { "status": ... }. That keeps per-transition requirements (e.g.
    // cancel needing a reason) local to one method, and adding a brand new
    // transition later (e.g. a "return" flow) means adding one endpoint here
    // plus one map entry in OrderService, not reworking a shared payload.

    @PostMapping("/{id}/pay")
    public OrderResponse markPaid(@PathVariable UUID id) {
        return new OrderResponse(orderService.markPaid(id));
    }

    @PostMapping("/{id}/ship")
    public OrderResponse markShipped(@PathVariable UUID id) {
        return new OrderResponse(orderService.markShipped(id));
    }

    @PostMapping("/{id}/deliver")
    public OrderResponse markDelivered(@PathVariable UUID id) {
        return new OrderResponse(orderService.markDelivered(id));
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancel(@PathVariable UUID id, @Valid @RequestBody CancelRequest request) {
        return new OrderResponse(orderService.cancel(id, request.getReason()));
    }
}
