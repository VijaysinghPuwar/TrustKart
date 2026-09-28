package com.vijaysinghpuwar.trustkart.purchase;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class PurchaseViews {

    private PurchaseViews() {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record QuoteLine(long productId, String slug, String name, String optionsLabel, String imageUrl, String unitPrice,
            int quantity, String lineTotal, String issue) {}

    /**
     * Server-priced checkout preview. {@code canPlace} is false when any line has an issue or, in Budget mode,
     * the balance is short; {@code shortfall} says by how much.
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Quote(List<QuoteLine> lines, int itemCount, String subtotal, String shipping, String total,
            String walletMode, String balance, String balanceAfter, String shortfall, boolean canPlace, boolean simulation) {}

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ItemView(long productId, String slug, String name, String optionsLabel, String categoryName, String imageUrl,
            String unitPrice, int quantity, String lineTotal) {}

    /** Every purchase view carries simulation=true so no client can present it as a real order. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PurchaseView(UUID id, String orderNumber, String status, Instant createdAt, Instant refundedAt, int itemCount,
            String subtotal, String shipping, String total, String walletMode, String balanceBefore, String balanceAfter,
            String deliveryPreset, SimulationAddress simulationAddress, List<ItemView> items, OrderTracking.Tracking tracking,
            boolean simulation) {}

    /** {@code stage} and {@code estimatedDelivery} let order lists show delivery progress without the full timeline. */
    public record PurchaseSummary(UUID id, String orderNumber, String status, Instant createdAt, int itemCount, String total,
            String walletMode, List<String> thumbnails, String stage, String stageLabel, Instant estimatedDelivery) {}

    public record PurchasePage(List<PurchaseSummary> items, int page, int size, long totalItems, int totalPages) {}
}
