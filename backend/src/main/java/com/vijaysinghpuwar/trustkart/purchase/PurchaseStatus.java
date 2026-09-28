package com.vijaysinghpuwar.trustkart.purchase;

/** Virtual purchases are complete the moment they're placed; the only other state is refunded. */
public enum PurchaseStatus {
    COMPLETED,
    REFUNDED
}
