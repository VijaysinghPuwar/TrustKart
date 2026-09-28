package com.vijaysinghpuwar.trustkart.wallet;

/** BUDGET: purchases draw down the virtual balance. UNLIMITED: checkout always succeeds and the balance is untouched. */
public enum WalletMode {
    BUDGET,
    UNLIMITED
}
