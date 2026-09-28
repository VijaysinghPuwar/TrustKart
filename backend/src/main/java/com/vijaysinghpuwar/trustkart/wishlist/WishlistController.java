package com.vijaysinghpuwar.trustkart.wishlist;

import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/wishlist")
@Tag(name = "Wishlist")
class WishlistController {

    record ListName(@NotBlank @Size(max = 60) String name) {}

    record AddItem(@NotNull @Positive Long productId, UUID listId) {}

    record Created(UUID id) {}

    private final WishlistService wishlists;
    private final ShopperService shoppers;

    WishlistController(WishlistService wishlists, ShopperService shoppers) {
        this.wishlists = wishlists;
        this.shoppers = shoppers;
    }

    @GetMapping
    List<WishlistService.ListView> lists(HttpServletRequest request) {
        return shoppers.current(request).map(s -> wishlists.lists(s.getId())).orElse(List.of());
    }

    /** Product ids saved in any list, for rendering filled hearts across the store. */
    @GetMapping("/ids")
    List<Long> ids(HttpServletRequest request) {
        return shoppers.current(request).map(s -> wishlists.savedProductIds(s.getId())).orElse(List.of());
    }

    @PostMapping("/lists")
    Created create(@Valid @RequestBody ListName body, HttpServletRequest request, HttpServletResponse response) {
        return new Created(wishlists.createList(shoppers.currentOrCreate(request, response).getId(), body.name()));
    }

    @PatchMapping("/lists/{id}")
    ResponseEntity<Void> rename(@PathVariable UUID id, @Valid @RequestBody ListName body, HttpServletRequest request,
            HttpServletResponse response) {
        wishlists.renameList(shoppers.currentOrCreate(request, response).getId(), id, body.name());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/lists/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        wishlists.deleteList(shoppers.currentOrCreate(request, response).getId(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/items")
    ResponseEntity<Void> add(@Valid @RequestBody AddItem body, HttpServletRequest request, HttpServletResponse response) {
        wishlists.add(shoppers.currentOrCreate(request, response).getId(), body.productId(), body.listId());
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/items/{productId}")
    ResponseEntity<Void> remove(@PathVariable @Positive long productId, @RequestParam(required = false) UUID listId,
            HttpServletRequest request, HttpServletResponse response) {
        wishlists.remove(shoppers.currentOrCreate(request, response).getId(), productId, listId);
        return ResponseEntity.noContent().build();
    }
}
