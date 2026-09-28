package com.vijaysinghpuwar.trustkart.catalog.domain;

/** How closely a product photo matches the listed model. Shown to shoppers so imagery never overclaims. */
public enum ImageMatch {
    EXACT,
    PRODUCT_LINE,
    REPRESENTATIVE,
    /** A TrustKart-made studio illustration of the product, used when no clean licensed photograph exists. */
    RENDER
}
