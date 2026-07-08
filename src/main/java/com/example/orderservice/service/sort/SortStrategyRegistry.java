package com.example.orderservice.service.sort;

import com.example.orderservice.exception.UnknownSortRuleException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Spring injects every OrderSortStrategy bean here automatically. This class
 * never needs to change when a new strategy is added elsewhere in the
 * codebase - it just needs to exist and be annotated @Component.
 */
@Component
public class SortStrategyRegistry {

    private final Map<String, OrderSortStrategy> strategiesByKey;

    public SortStrategyRegistry(List<OrderSortStrategy> strategies) {
        this.strategiesByKey = strategies.stream()
                .collect(Collectors.toMap(OrderSortStrategy::key, Function.identity()));
    }

    public OrderSortStrategy get(String key) {
        OrderSortStrategy strategy = strategiesByKey.get(key);
        if (strategy == null) {
            throw new UnknownSortRuleException(key);
        }
        return strategy;
    }

    public java.util.Set<String> availableKeys() {
        return strategiesByKey.keySet();
    }
}
