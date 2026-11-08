package com.ecommerce.order.export;

import java.util.function.Function;

public class Pipeline<I, O> {

    private final Function<I, O> currentStage;

    private Pipeline(Function<I, O> currentStage) {
        this.currentStage = currentStage;
    }

    public static <I> Pipeline<I, I> of() {
        return new Pipeline<>(Function.identity());
    }

    public <NEXT> Pipeline<I, NEXT> addFilter(Filter<O, NEXT> filter) {
        return new Pipeline<>(currentStage.andThen(filter::process));
    }

    public O execute(I input) {
        return currentStage.apply(input);
    }
}
