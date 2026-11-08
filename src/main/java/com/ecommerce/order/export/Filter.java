package com.ecommerce.order.export;

@FunctionalInterface
public interface Filter<I, O> {

    O process(I input);
}
