package com.vijaysinghpuwar.trustkart.shopper;

import com.vijaysinghpuwar.trustkart.security.AuthCookies;
import com.vijaysinghpuwar.trustkart.security.AuthenticatedUser;
import com.vijaysinghpuwar.trustkart.security.Tokens;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Resolves "who owns this cart/wallet" for the current request. Signed-in requests use the user's shopper;
 * anonymous ones use the guest cookie. Read paths never create shoppers, so bots browsing the catalog don't
 * create rows; the first write (add to cart, add funds) does.
 */
@Service
public class ShopperService {

    private static final Logger log = LoggerFactory.getLogger(ShopperService.class);
    private static final Duration GUEST_RETENTION = Duration.ofDays(30);
    private static final Duration SEEN_RESOLUTION = Duration.ofMinutes(5);

    private final ShopperRepository shoppers;
    private final AuthCookies cookies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    public ShopperService(ShopperRepository shoppers, AuthCookies cookies, ApplicationEventPublisher events, Clock clock) {
        this.shoppers = shoppers;
        this.cookies = cookies;
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public Optional<Shopper> current(HttpServletRequest request) {
        Optional<Shopper> shopper = AuthenticatedUser.current()
                .map(u -> shoppers.findByUserId(u.userId()).orElseGet(() -> shoppers.save(Shopper.forUser(u.userId(), clock.instant()))))
                .or(() -> AuthCookies.read(request, AuthCookies.GUEST).flatMap(t -> shoppers.findByGuestTokenHash(Tokens.sha256(t))));
        shopper.ifPresent(this::markSeen);
        return shopper;
    }

    @Transactional
    public Shopper currentOrCreate(HttpServletRequest request, HttpServletResponse response) {
        return current(request).orElseGet(() -> {
            String token = Tokens.random();
            Shopper guest = shoppers.save(Shopper.guest(Tokens.sha256(token), clock.instant()));
            cookies.setGuest(response, token);
            return guest;
        });
    }

    /**
     * Called in the sign-in/registration transaction. Adopts or merges the guest's shopper so a cart built
     * before signing in is never lost, then drops the guest cookie.
     */
    @Transactional
    public Shopper onSignIn(long userId, HttpServletRequest request, HttpServletResponse response) {
        Optional<Shopper> guest = AuthCookies.read(request, AuthCookies.GUEST)
                .flatMap(t -> shoppers.findByGuestTokenHash(Tokens.sha256(t)));
        Optional<Shopper> existing = shoppers.findByUserId(userId);
        Shopper result;
        if (existing.isEmpty() && guest.isPresent()) {
            result = guest.get();
            result.attachToUser(userId);
        } else if (existing.isPresent() && guest.isPresent() && !guest.get().getId().equals(existing.get().getId())) {
            result = existing.get();
            events.publishEvent(new ShopperMergedEvent(guest.get().getId(), result.getId()));
            shoppers.delete(guest.get());
        } else {
            result = existing.orElseGet(() -> shoppers.save(Shopper.forUser(userId, clock.instant())));
        }
        cookies.clearGuest(response);
        return result;
    }

    private void markSeen(Shopper shopper) {
        Instant now = clock.instant();
        if (shopper.getLastSeenAt().plus(SEEN_RESOLUTION).isBefore(now)) {
            shopper.seen(now);
        }
    }

    @Scheduled(cron = "0 17 3 * * *")
    @Transactional
    public void removeIdleGuests() {
        int removed = shoppers.deleteIdleGuests(clock.instant().minus(GUEST_RETENTION));
        if (removed > 0) {
            log.info("Removed {} idle guest shoppers", removed);
        }
    }
}
