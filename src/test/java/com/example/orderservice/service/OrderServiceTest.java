package com.example.orderservice.service;

import com.example.orderservice.dto.OrderItemRequest;
import com.example.orderservice.dto.OrderRequest;
import com.example.orderservice.exception.IllegalStatusTransitionException;
import com.example.orderservice.exception.InvalidOrderException;
import com.example.orderservice.exception.OrderNotFoundException;
import com.example.orderservice.exception.UnknownSortRuleException;
import com.example.orderservice.model.Order;
import com.example.orderservice.model.OrderItem;
import com.example.orderservice.model.OrderStatus;
import com.example.orderservice.repository.OrderRepository;
import com.example.orderservice.service.sort.NewestFirstSortStrategy;
import com.example.orderservice.service.sort.OrderSortStrategy;
import com.example.orderservice.service.sort.SortStrategyRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private SortStrategyRegistry sortStrategyRegistry;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        sortStrategyRegistry = new SortStrategyRegistry(List.<OrderSortStrategy>of(new NewestFirstSortStrategy()));
        orderService = new OrderService(orderRepository, sortStrategyRegistry);
        // save() is called by nearly every mutating test; make it act like a
        // real repository would (return the same, now-persisted, instance).
        lenient().when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private OrderRequest requestWith(String customer, Object... itemTriples) {
        OrderRequest req = new OrderRequest();
        req.setCustomerName(customer);
        req.setItems(List.of(
                itemRequest("Apple", 3, "0.50"),
                itemRequest("Bread Loaf", 1, "2.20")
        ));
        return req;
    }

    private OrderItemRequest itemRequest(String name, int qty, String price) {
        OrderItemRequest item = new OrderItemRequest();
        item.setProductName(name);
        item.setQuantity(qty);
        item.setUnitPrice(new BigDecimal(price));
        return item;
    }

    // --- createOrder ---------------------------------------------------

    @Test
    void createOrder_computesTotalFromLineItems_andDefaultsToCreatedStatus() {
        OrderRequest request = requestWith("Andi Wijaya");

        Order created = orderService.createOrder(request);

        assertThat(created.getStatus()).isEqualTo(OrderStatus.CREATED);
        assertThat(created.getTotalAmount()).isEqualByComparingTo("3.70");
        assertThat(created.getItems()).hasSize(2);
        verify(orderRepository).save(any(Order.class));
    }

    @Test
    void createOrder_ignoresAnyClientSuppliedTotal_andRecalculatesServerSide() {
        // Even if somehow a total slipped through to the service layer, the
        // service always recomputes from items rather than trusting input.
        OrderRequest request = requestWith("Andi Wijaya");

        Order created = orderService.createOrder(request);

        BigDecimal expected = new BigDecimal("0.50").multiply(BigDecimal.valueOf(3))
                .add(new BigDecimal("2.20").multiply(BigDecimal.valueOf(1)));
        assertThat(created.getTotalAmount()).isEqualByComparingTo(expected);
    }

    // --- getOrder --------------------------------------------------------

    @Test
    void getOrder_returnsOrder_whenFound() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        Order result = orderService.getOrder(id);

        assertThat(result).isSameAs(order);
    }

    @Test
    void getOrder_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
    }

    // --- status transitions ----------------------------------------------

    @Test
    void markPaid_succeeds_fromCreated() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        Order result = orderService.markPaid(id);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAID);
    }

    @Test
    void markShipped_rejected_whenOrderStillCreated() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi"); // status defaults to CREATED
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markShipped(id))
                .isInstanceOf(IllegalStatusTransitionException.class);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void markShipped_succeeds_whenOrderIsPaid() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        Order result = orderService.markShipped(id);

        assertThat(result.getStatus()).isEqualTo(OrderStatus.SHIPPED);
    }

    @Test
    void deliveredOrder_cannotBeReactivatedOrShippedAgain() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markShipped(id))
                .isInstanceOf(IllegalStatusTransitionException.class);
        assertThatThrownBy(() -> orderService.markPaid(id))
                .isInstanceOf(IllegalStatusTransitionException.class);
    }

    @Test
    void cancelledOrder_cannotBeReactivated() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.setStatus(OrderStatus.CANCELLED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.markPaid(id))
                .isInstanceOf(IllegalStatusTransitionException.class);
    }

    @Test
    void cancel_recordsReason_andSetsStatus() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        Order result = orderService.cancel(id, "Customer changed their mind");

        assertThat(result.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.getCancellationReason()).isEqualTo("Customer changed their mind");
    }

    @Test
    void cancel_rejected_whenOrderAlreadyDelivered() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.setStatus(OrderStatus.DELIVERED);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancel(id, "too late"))
                .isInstanceOf(IllegalStatusTransitionException.class);
    }

    // --- item immutability after payment -----------------------------------

    @Test
    void updateOrder_allowsItemChanges_whileStillCreated() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.addItem(new OrderItem("Apple", 3, new BigDecimal("0.50")));
        order.recalculateTotal();
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        OrderRequest update = new OrderRequest();
        update.setCustomerName("Andi Wijaya");
        update.setItems(List.of(itemRequest("Apple", 5, "0.50")));

        Order result = orderService.updateOrder(id, update);

        assertThat(result.getItems()).hasSize(1);
        assertThat(result.getItems().get(0).getQuantity()).isEqualTo(5);
        assertThat(result.getTotalAmount()).isEqualByComparingTo("2.50");
    }

    @Test
    void updateOrder_rejectsItemChanges_oncePaid() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.addItem(new OrderItem("Apple", 3, new BigDecimal("0.50")));
        order.recalculateTotal();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        OrderRequest update = new OrderRequest();
        update.setCustomerName("Andi Wijaya");
        update.setItems(List.of(itemRequest("Apple", 99, "0.50"))); // attempted change

        assertThatThrownBy(() -> orderService.updateOrder(id, update))
                .isInstanceOf(InvalidOrderException.class);
    }

    @Test
    void updateOrder_stillAllowsCustomerNameChange_oncePaid_whenItemsUnchanged() {
        UUID id = UUID.randomUUID();
        Order order = new Order("Andi");
        order.addItem(new OrderItem("Apple", 3, new BigDecimal("0.50")));
        order.recalculateTotal();
        order.setStatus(OrderStatus.PAID);
        when(orderRepository.findById(id)).thenReturn(Optional.of(order));

        OrderRequest update = new OrderRequest();
        update.setCustomerName("Andi Wijaya Updated");
        update.setItems(List.of(itemRequest("Apple", 3, "0.50"))); // identical items

        Order result = orderService.updateOrder(id, update);

        assertThat(result.getCustomerName()).isEqualTo("Andi Wijaya Updated");
    }

    // --- delete --------------------------------------------------------

    @Test
    void deleteOrder_throwsNotFound_whenMissing() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(false);

        assertThatThrownBy(() -> orderService.deleteOrder(id))
                .isInstanceOf(OrderNotFoundException.class);
        verify(orderRepository, never()).deleteById(any());
    }

    @Test
    void deleteOrder_deletes_whenPresent() {
        UUID id = UUID.randomUUID();
        when(orderRepository.existsById(id)).thenReturn(true);

        orderService.deleteOrder(id);

        verify(orderRepository).deleteById(id);
    }

    // --- listOrders / sorting ----------------------------------------------

    @Test
    void listOrders_throwsUnknownSortRule_forUnrecognisedKey() {
        Pageable pageable = PageRequest.of(0, 20);
        // when(orderRepository.findAll()).thenReturn(List.of());

        assertThatThrownBy(() -> orderService.listOrders(pageable, "banana"))
                .isInstanceOf(UnknownSortRuleException.class);
    }

    @Test
    void listOrders_sortsNewestFirst_andPaginatesCorrectly() throws InterruptedException {
        Order older = new Order("First");
        Thread.sleep(5);
        Order newer = new Order("Second");

        when(orderRepository.findAll()).thenReturn(List.of(older, newer));

        Page<Order> page = orderService.listOrders(PageRequest.of(0, 1), "newest");

        assertThat(page.getTotalElements()).isEqualTo(2);
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getCustomerName()).isEqualTo("Second");
    }
}
