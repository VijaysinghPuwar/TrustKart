package com.vijaysinghpuwar.trustkart.cart;

import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import com.vijaysinghpuwar.trustkart.wishlist.WishlistService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.util.UUID;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Requests carry product ids and quantities only. Prices, totals and stock limits are the server's. */
@RestController
@RequestMapping("/api/v1/cart")
@Tag(name = "Cart")
class CartController {

    record AddItem(@NotNull @Positive Long productId, @NotNull @Min(1) @Max(10) Integer quantity) {}

    record UpdateItem(@Min(1) @Max(10) Integer quantity, Boolean savedForLater) {}

    private final CartService cart;
    private final WishlistService wishlists;
    private final ShopperService shoppers;

    CartController(CartService cart, WishlistService wishlists, ShopperService shoppers) {
        this.cart = cart;
        this.wishlists = wishlists;
        this.shoppers = shoppers;
    }

    @GetMapping
    CartView view(HttpServletRequest request) {
        return shoppers.current(request).map(s -> cart.view(s.getId())).orElse(CartView.empty());
    }

    @PostMapping("/items")
    CartView add(@Valid @RequestBody AddItem body, HttpServletRequest request, HttpServletResponse response) {
        return cart.add(shoppers.currentOrCreate(request, response).getId(), body.productId(), body.quantity());
    }

    @PatchMapping("/items/{id}")
    CartView update(@PathVariable UUID id, @Valid @RequestBody UpdateItem body, HttpServletRequest request,
            HttpServletResponse response) {
        return cart.update(shoppers.currentOrCreate(request, response).getId(), id, body.quantity(), body.savedForLater());
    }

    @DeleteMapping("/items/{id}")
    CartView remove(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        return cart.remove(shoppers.currentOrCreate(request, response).getId(), id);
    }

    @PostMapping("/items/{id}/move-to-wishlist")
    @Operation(summary = "Moves a cart line into the default wishlist")
    CartView moveToWishlist(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        long shopperId = shoppers.currentOrCreate(request, response).getId();
        wishlists.add(shopperId, cart.productIdOf(shopperId, id), null);
        return cart.remove(shopperId, id);
    }
}
