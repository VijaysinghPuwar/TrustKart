package com.vijaysinghpuwar.trustkart.purchase;

/**
 * The commercial state of an order. Delivery progress is separate and computed ({@link TrackingStage}).
 * CANCELLED: stopped before it shipped. REFUNDED: returned after delivery. Both return stock and funds.
 */
public enum PurchaseStatus {
    COMPLETED,
    CANCELLED,
    REFUNDED
}
