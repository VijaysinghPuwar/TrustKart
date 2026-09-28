package com.vijaysinghpuwar.trustkart.purchase;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Computes where an order is from its placement time and "now". Pure and deterministic: the same inputs
 * always give the same stage, events and tracking number, which is what makes notifications idempotent.
 */
public final class OrderTracking {

    public static final String CARRIER = "TrustKart Logistics";
    /** Returns are accepted for this long after delivery. */
    public static final Duration RETURN_WINDOW = Duration.ofDays(30);

    private static final String ORIGIN = "TrustKart Fulfillment Center, Reno, NV";
    private static final String HUB = "TrustKart Sort Hub, Salt Lake City, UT";

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Event(String stage, String label, String description, String location, Instant at, boolean done) {}

    /**
     * {@code canCancel}: the order hasn't shipped yet. {@code canReturn}: delivered and still inside the return
     * window, which ends at {@code returnBy}.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Tracking(String stage, String stageLabel, String trackingNumber, String carrier, Instant estimatedDelivery,
            Instant deliveredAt, boolean canCancel, boolean canReturn, Instant returnBy, int progress, List<Event> events) {}

    private OrderTracking() {}

    /** The furthest timeline stage reached at {@code now}. */
    public static TrackingStage stageAt(Instant placedAt, Instant now) {
        TrackingStage reached = TrackingStage.PLACED;
        for (TrackingStage s : TrackingStage.values()) {
            if (s.onTimeline() && !now.isBefore(placedAt.plus(s.offset()))) {
                reached = s;
            }
        }
        return reached;
    }

    public static Instant reachedAt(Instant placedAt, TrackingStage stage) {
        return placedAt.plus(stage.offset());
    }

    public static String trackingNumber(UUID purchaseId) {
        return "TK1Z" + Tokens.sha256(purchaseId.toString()).substring(0, 14).toUpperCase(Locale.ROOT);
    }

    /**
     * Full tracking view. A cancelled order's timeline stops where it was cancelled; a returned one shows the
     * complete delivery followed by the return.
     */
    public static Tracking of(UUID purchaseId, Instant placedAt, PurchaseStatus status, Instant closedAt,
            SimulationAddress address, Instant now) {
        // The timeline freezes at cancellation: nothing moves after an order is cancelled.
        Instant effectiveNow = status == PurchaseStatus.CANCELLED && closedAt != null ? closedAt : now;
        TrackingStage reached = stageAt(placedAt, effectiveNow);
        Instant deliveredAt = reached == TrackingStage.DELIVERED ? reachedAt(placedAt, TrackingStage.DELIVERED) : null;

        List<Event> events = new ArrayList<>();
        for (TrackingStage s : TrackingStage.values()) {
            if (!s.onTimeline()) {
                continue;
            }
            boolean done = s.ordinal() <= reached.ordinal();
            if (status == PurchaseStatus.CANCELLED && !done) {
                continue;
            }
            events.add(new Event(s.name(), s.label(), s.description(), location(s, address), reachedAt(placedAt, s), done));
        }
        TrackingStage current = reached;
        if (status == PurchaseStatus.CANCELLED || status == PurchaseStatus.REFUNDED) {
            current = status == PurchaseStatus.CANCELLED ? TrackingStage.CANCELLED : TrackingStage.REFUNDED;
            events.add(new Event(current.name(), current.label(), current.description(), null, closedAt, true));
        }

        Instant returnBy = deliveredAt == null ? null : deliveredAt.plus(RETURN_WINDOW);
        boolean open = status == PurchaseStatus.COMPLETED;
        boolean canCancel = open && reached.ordinal() < TrackingStage.SHIPPED.ordinal();
        boolean canReturn = open && deliveredAt != null && now.isBefore(returnBy);
        int progress = Math.round(100f * reached.ordinal() / TrackingStage.DELIVERED.ordinal());
        return new Tracking(current.name(), current.label(), trackingNumber(purchaseId), CARRIER,
                reachedAt(placedAt, TrackingStage.DELIVERED), deliveredAt, canCancel, canReturn,
                open ? returnBy : null, progress, events);
    }

    private static String location(TrackingStage stage, SimulationAddress address) {
        String destination = address == null || address.city() == null || address.city().isBlank() ? null
                : address.region() == null || address.region().isBlank() ? address.city()
                : address.city() + ", " + address.region();
        return switch (stage) {
            case PLACED -> null;
            case PROCESSING, PACKED, SHIPPED -> ORIGIN;
            case IN_TRANSIT -> HUB;
            case LOCAL_FACILITY -> destination == null ? "Local delivery station" : "Delivery station, " + destination;
            case OUT_FOR_DELIVERY, DELIVERED -> destination;
            default -> null;
        };
    }
}
