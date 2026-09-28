package com.vijaysinghpuwar.trustkart.address;

import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/addresses")
@Tag(name = "Addresses", description = "Saved delivery addresses for the simulation. They may be fictional.")
class AddressController {

    private final AddressService addresses;
    private final ShopperService shoppers;

    AddressController(AddressService addresses, ShopperService shoppers) {
        this.addresses = addresses;
        this.shoppers = shoppers;
    }

    @GetMapping
    List<AddressService.AddressView> list(HttpServletRequest request) {
        return shoppers.current(request).map(s -> addresses.list(s.getId())).orElse(List.of());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    AddressService.AddressView create(@Valid @RequestBody AddressInput body, @RequestParam(defaultValue = "false") boolean makeDefault,
            HttpServletRequest request, HttpServletResponse response) {
        return addresses.create(shoppers.currentOrCreate(request, response).getId(), body, makeDefault);
    }

    @PutMapping("/{id}")
    AddressService.AddressView update(@PathVariable UUID id, @Valid @RequestBody AddressInput body, HttpServletRequest request,
            HttpServletResponse response) {
        return addresses.update(shoppers.currentOrCreate(request, response).getId(), id, body);
    }

    @PostMapping("/{id}/default")
    AddressService.AddressView makeDefault(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        return addresses.makeDefault(shoppers.currentOrCreate(request, response).getId(), id);
    }

    @DeleteMapping("/{id}")
    ResponseEntity<Void> delete(@PathVariable UUID id, HttpServletRequest request, HttpServletResponse response) {
        addresses.delete(shoppers.currentOrCreate(request, response).getId(), id);
        return ResponseEntity.noContent().build();
    }
}
