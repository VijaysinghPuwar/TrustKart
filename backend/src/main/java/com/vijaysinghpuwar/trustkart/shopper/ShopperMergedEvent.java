package com.vijaysinghpuwar.trustkart.shopper;

/**
 * Published inside the sign-in transaction when a guest with data signs into an account that already has
 * data. Each module moves or merges its own rows from {@code fromShopperId} to {@code toShopperId}.
 */
public record ShopperMergedEvent(long fromShopperId, long toShopperId) {}
