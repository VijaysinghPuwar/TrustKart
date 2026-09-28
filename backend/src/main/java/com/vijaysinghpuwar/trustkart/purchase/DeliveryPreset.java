package com.vijaysinghpuwar.trustkart.purchase;

/** Where the virtual order is "delivered" in the simulation. Nothing ships; no real address is required. */
public enum DeliveryPreset {
    /** A full (possibly fictional) street address, entered at checkout or picked from the address book. */
    ADDRESS,
    HOME,
    OFFICE,
    DREAM_SETUP,
    HOMELAB,
    COLLECTION,
    CUSTOM
}
