package com.vijaysinghpuwar.trustkart.common.web;

import java.time.Duration;
import org.springframework.http.CacheControl;

/**
 * Cache policy for public, non-personalised catalog and search responses.
 *
 * <ul>
 *   <li>Browsers reuse a response for a minute ({@code max-age}).</li>
 *   <li>The CDN in front of the API (Vercel) keeps it for five minutes ({@code s-maxage}), then keeps serving the
 *       last copy for up to a week while it fetches a fresh one in the background
 *       ({@code stale-while-revalidate}) or while the API is unavailable ({@code stale-if-error}).</li>
 * </ul>
 *
 * The API runs on an instance that sleeps when idle and takes minutes to wake; with this policy a visitor still gets
 * the catalog instantly from the edge, and their visit is what wakes the API. Prices and stock shown from a stale copy
 * are never trusted: cart, checkout and the order itself always read live data. These responses must never carry a
 * cookie (see the CSRF token repository in {@code SecurityConfig}).
 */
public final class PublicCache {

    public static final CacheControl CATALOG = CacheControl.maxAge(Duration.ofSeconds(60)).cachePublic()
            .sMaxAge(Duration.ofMinutes(5))
            .staleWhileRevalidate(Duration.ofDays(7))
            .staleIfError(Duration.ofDays(7));

    private PublicCache() {}
}
