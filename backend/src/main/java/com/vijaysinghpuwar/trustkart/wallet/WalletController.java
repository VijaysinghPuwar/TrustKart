package com.vijaysinghpuwar.trustkart.wallet;

import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Virtual wallet. The only way to raise a balance is {@code POST /credits}, bounded per request, capped overall
 * and rate limited. There is no endpoint that sets a balance directly.
 */
@RestController
@RequestMapping("/api/v1/wallet")
@Tag(name = "Virtual wallet", description = "Virtual funds only. No real money, cards or banks are involved.")
class WalletController {

    record CreditRequest(
            @NotNull @DecimalMin("1.00") @DecimalMax("10000000.00") @Digits(integer = 8, fraction = 2) BigDecimal amount) {}

    record ModeRequest(@NotNull WalletMode mode) {}

    private final WalletService wallets;
    private final ShopperService shoppers;

    WalletController(WalletService wallets, ShopperService shoppers) {
        this.wallets = wallets;
        this.shoppers = shoppers;
    }

    @GetMapping
    WalletService.WalletView wallet(HttpServletRequest request) {
        return shoppers.current(request).map(s -> wallets.view(s.getId())).orElseGet(WalletService::preview);
    }

    @PostMapping("/credits")
    @Operation(summary = "Add virtual funds ($1 to $10,000,000 per request). Requires an Idempotency-Key header.")
    WalletService.WalletView credit(@Valid @RequestBody CreditRequest body,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9-]{16,64}") String idempotencyKey,
            HttpServletRequest request, HttpServletResponse response) {
        return wallets.credit(shoppers.currentOrCreate(request, response).getId(), body.amount(), idempotencyKey);
    }

    @PostMapping("/reset")
    WalletService.WalletView reset(HttpServletRequest request, HttpServletResponse response) {
        return wallets.reset(shoppers.currentOrCreate(request, response).getId());
    }

    @PutMapping("/mode")
    WalletService.WalletView mode(@Valid @RequestBody ModeRequest body, HttpServletRequest request, HttpServletResponse response) {
        return wallets.setMode(shoppers.currentOrCreate(request, response).getId(), body.mode());
    }

    @GetMapping("/transactions")
    WalletService.TransactionPage transactions(@RequestParam(defaultValue = "0") @Min(0) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size, HttpServletRequest request) {
        return shoppers.current(request).map(s -> wallets.transactions(s.getId(), page, size))
                .orElse(new WalletService.TransactionPage(java.util.List.of(), page, size, 0));
    }
}
