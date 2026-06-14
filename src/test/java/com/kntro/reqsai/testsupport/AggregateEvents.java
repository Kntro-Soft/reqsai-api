package com.kntro.reqsai.testsupport;

import com.kntro.reqsai.shared.domain.model.AggregateRoot;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;

/**
 * Test helper to read the domain events an aggregate has registered. {@code AggregateRoot.domainEvents()}
 * is package-private (an internal Spring Data hook), so tests reach it via reflection rather than
 * widening production visibility. Shared across bounded contexts.
 */
public final class AggregateEvents {

    private AggregateEvents() {
    }

    @SuppressWarnings("unchecked")
    public static List<Object> of(AggregateRoot aggregate) {
        try {
            Method m = AggregateRoot.class.getDeclaredMethod("domainEvents");
            m.setAccessible(true);
            return List.copyOf((Collection<Object>) m.invoke(aggregate));
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Could not read domain events", e);
        }
    }
}
