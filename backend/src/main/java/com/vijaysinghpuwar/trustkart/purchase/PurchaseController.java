package com.vijaysinghpuwar.trustkart.purchase;

import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Virtual checkout. Place requests contain no prices or totals the server would use: only a delivery preset,
 * an optional fictional address, the total the shopper saw (to detect changes) and, for Instant Virtual Buy,
 * a product id and quantity.
 */
@RestController
@Tag(name = "Virtual checkout", description = "Simulated purchases paid with virtual funds. Nothing is charged or shipped.")
class PurchaseController {

    record Instant(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(10) Integer quantity) {}

    record PlaceBody(@NotNull DeliveryPreset deliveryPreset, @Valid SimulationAddress simulationAddress, UUID addressId,
            @Pattern(regexp = "\\d{1,13}(\\.\\d{1,2})?") String expectedTotal, @Valid Instant instant) {}

    private final PurchaseService purchases;
    private final ShopperService shoppers;

    PurchaseController(PurchaseService purchases, ShopperService shoppers) {
        this.purchases = purchases;
        this.shoppers = shoppers;
    }

    @GetMapping("/api/v1/checkout/quote")
    @Operation(summary = "Server-priced preview of the cart (or one product for Instant Virtual Buy)")
    PurchaseViews.Quote quote(@RequestParam(required = false) @Positive Long productId,
            @RequestParam(required = false) @Min(1) @Max(10) Integer quantity,
            HttpServletRequest request, HttpServletResponse response) {
        PurchaseService.InstantLine instant = productId == null ? null
                : new PurchaseService.InstantLine(productId, quantity == null ? 1 : quantity);
        return purchases.quote(shoppers.currentOrCreate(request, response).getId(), instant);
    }

    @PostMapping("/api/v1/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Place a virtual order. Requires an Idempotency-Key; retries return the original result.")
    PurchaseViews.PurchaseView place(@Valid @RequestBody PlaceBody body,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            HttpServletRequest request, HttpServletResponse response) {
        PurchaseService.InstantLine instant = body.instant() == null ? null
                : new PurchaseService.InstantLine(body.instant().productId(), body.instant().quantity());
        return purchases.place(shoppers.currentOrCreate(request, response).getId(),
                new PurchaseService.PlaceRequest(body.deliveryPreset(), body.simulationAddress(), body.addressId(),
                        body.expectedTotal(), instant),
                idempotencyKey);
    }

    @GetMapping("/api/v1/purchases")
    PurchaseViews.PurchasePage list(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size, HttpServletRequest request) {
        return shoppers.current(request).map(s -> purchases.list(s.getId(), page, size))
                .orElse(new PurchaseViews.PurchasePage(java.util.List.of(), page, size, 0, 0));
    }

    @GetMapping("/api/v1/purchases/{id}")
    PurchaseViews.PurchaseView get(@PathVariable UUID id, HttpServletRequest request) {
        long shopperId = shoppers.current(request).orElseThrow(() -> new NotFoundException("Purchase")).getId();
        return purchases.get(shopperId, id);
    }

    @PostMapping("/api/v1/purchases/{id}/refund")
    @Operation(summary = "Undo a virtual purchase: restores virtual balance (Budget mode) and removes items from the collection")
    PurchaseViews.PurchaseView refund(@PathVariable UUID id, HttpServletRequest request) {
        long shopperId = shoppers.current(request).orElseThrow(() -> new NotFoundException("Purchase")).getId();
        return purchases.refund(shopperId, id);
    }
}
