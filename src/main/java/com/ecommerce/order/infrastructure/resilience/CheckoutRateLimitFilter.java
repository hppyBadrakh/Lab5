package com.ecommerce.order.infrastructure.resilience;

import io.github.resilience4j.ratelimiter.RateLimiter;
import io.github.resilience4j.ratelimiter.RateLimiterRegistry;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Load shedding at the checkout ingress (ADR-0008, scenario S3). Only {@code POST /api/v1/orders} is limited.
 * <p>
 * The Resilience4j rate limiter {@value #RATE_LIMITER} hands out a fixed number of permits per refresh
 * period (150 per second by default), like a token bucket that is refilled every second. A request that
 * finds no permit is not queued: it is rejected at once with {@code 429 Too Many Requests} and a
 * {@code Retry-After} header, so a surge never reaches the domain, the repository or the worker pool.
 */
@Component
@ConditionalOnProperty(name = "checkout.rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class CheckoutRateLimitFilter extends OncePerRequestFilter {

    public static final String RATE_LIMITER = "checkout";
    static final String CHECKOUT_PATH = "/api/v1/orders";

    private final RateLimiter rateLimiter;

    public CheckoutRateLimitFilter(RateLimiterRegistry registry) {
        this.rateLimiter = registry.rateLimiter(RATE_LIMITER);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod()) && CHECKOUT_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (rateLimiter.acquirePermission()) {
            chain.doFilter(request, response);
            return;
        }
        long retryAfterSeconds = Math.max(1, rateLimiter.getRateLimiterConfig().getLimitRefreshPeriod().toSeconds());
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("""
                {"type":"about:blank","title":"Too Many Requests","status":429,\
                "detail":"Checkout capacity exceeded. Retry after %d second(s).","instance":"%s"}"""
                .formatted(retryAfterSeconds, CHECKOUT_PATH));
    }
}
