package com.vijaysinghpuwar.trustkart.common.cache;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * A single cached value with a time-to-live, for small, public, read-mostly data that is identical for every caller
 * (the home page, the category tree). One caller refreshes an expired value while the others wait for it, so a burst
 * of requests after expiry runs the loader once. Expiry uses a monotonic timer, independent of the business clock.
 * PostgreSQL stays the source of truth: {@link #clear()} drops the value when the underlying data changes.
 */
public final class Memo<T> {

    private record Entry<T>(T value, long expiresAtNanos) {}

    private final long ttlNanos;
    private final Supplier<T> loader;
    private volatile Entry<T> entry;

    public Memo(Duration ttl, Supplier<T> loader) {
        this.ttlNanos = ttl.toNanos();
        this.loader = loader;
    }

    public T get() {
        Entry<T> e = entry;
        if (e != null && System.nanoTime() - e.expiresAtNanos() < 0) {
            return e.value();
        }
        synchronized (this) {
            e = entry;
            if (e != null && System.nanoTime() - e.expiresAtNanos() < 0) {
                return e.value();
            }
            T value = loader.get();
            entry = new Entry<>(value, System.nanoTime() + ttlNanos);
            return value;
        }
    }

    public void clear() {
        entry = null;
    }
}
