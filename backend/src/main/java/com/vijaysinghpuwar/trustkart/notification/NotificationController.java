package com.vijaysinghpuwar.trustkart.notification;

import com.vijaysinghpuwar.trustkart.common.error.NotFoundException;
import com.vijaysinghpuwar.trustkart.notification.NotificationService.NotificationPage;
import com.vijaysinghpuwar.trustkart.notification.NotificationService.Preferences;
import com.vijaysinghpuwar.trustkart.shopper.ShopperService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Notification center. Reading never creates a shopper: a visitor without one simply has no notifications.
 */
@RestController
@RequestMapping("/api/v1/notifications")
@Tag(name = "Notifications", description = "Order and delivery updates for the current shopper")
class NotificationController {

    record PreferencesBody(@NotNull Boolean orderUpdates, @NotNull Boolean deliveryUpdates) {}

    private final NotificationService notifications;
    private final ShopperService shoppers;

    NotificationController(NotificationService notifications, ShopperService shoppers) {
        this.notifications = notifications;
        this.shoppers = shoppers;
    }

    @GetMapping
    NotificationPage list(@RequestParam(defaultValue = "0") @Min(0) @Max(100) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(50) int size, HttpServletRequest request) {
        return shoppers.current(request).map(s -> notifications.list(s.getId(), page, size))
                .orElse(new NotificationPage(List.of(), 0, page, size, 0));
    }

    @GetMapping("/unread-count")
    @Operation(summary = "Unread notification count, for the header bell")
    Map<String, Long> unreadCount(HttpServletRequest request) {
        return Map.of("count", shoppers.current(request).map(s -> notifications.unreadCount(s.getId())).orElse(0L));
    }

    @PostMapping("/{id}/read")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markRead(@PathVariable UUID id, HttpServletRequest request) {
        notifications.markRead(shopperId(request), id);
    }

    @PostMapping("/read-all")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void markAllRead(HttpServletRequest request) {
        shoppers.current(request).ifPresent(s -> notifications.markAllRead(s.getId()));
    }

    @GetMapping("/preferences")
    Preferences preferences(HttpServletRequest request) {
        return shoppers.current(request).map(s -> notifications.preferences(s.getId())).orElse(new Preferences(true, true));
    }

    @PutMapping("/preferences")
    Preferences updatePreferences(@Valid @RequestBody PreferencesBody body, HttpServletRequest request) {
        return notifications.updatePreferences(shopperId(request), new Preferences(body.orderUpdates(), body.deliveryUpdates()));
    }

    private long shopperId(HttpServletRequest request) {
        return shoppers.current(request).orElseThrow(() -> new NotFoundException("Notification")).getId();
    }
}
