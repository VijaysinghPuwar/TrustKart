package com.vijaysinghpuwar.trustkart.purchase;

import java.time.Duration;

/**
 * Delivery stages in order. Each has a fixed offset from the moment the order was placed, so the whole
 * seven-day journey is a pure function of {@code createdAt} and the clock: no scheduler, no stored stage.
 * CANCELLED and REFUNDED are terminal outcomes rather than points on the timeline.
 */
public enum TrackingStage {
    PLACED(Duration.ZERO, "Order placed", "We've received your order."),
    PROCESSING(Duration.ofMinutes(30), "Processing", "Your order is being prepared at our fulfillment center."),
    PACKED(Duration.ofHours(10), "Packed", "Your items are packed and waiting for pickup."),
    SHIPPED(Duration.ofDays(1), "Shipped", "Your package has left our fulfillment center."),
    IN_TRANSIT(Duration.ofDays(1).plusHours(8), "In transit", "Your package is on its way."),
    LOCAL_FACILITY(Duration.ofDays(5).plusHours(6), "At local facility", "Your package arrived at the local delivery station."),
    OUT_FOR_DELIVERY(Duration.ofDays(6).plusHours(20), "Out for delivery", "Your package is on the delivery vehicle."),
    DELIVERED(Duration.ofDays(7), "Delivered", "Your package was delivered."),
    CANCELLED(null, "Cancelled", "Your order was cancelled and refunded."),
    REFUNDED(null, "Returned", "Your return was processed and refunded.");

    private final Duration offset;
    private final String label;
    private final String description;

    TrackingStage(Duration offset, String label, String description) {
        this.offset = offset;
        this.label = label;
        this.description = description;
    }

    /** Offset from order time, or null for the terminal outcomes. */
    public Duration offset() {
        return offset;
    }

    public String label() {
        return label;
    }

    public String description() {
        return description;
    }

    public boolean onTimeline() {
        return offset != null;
    }
}
