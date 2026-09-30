package com.devflow.api.security.ratelimit;

public record RateLimitResult(
        boolean allowed,
        long currentCount,
        long limit,
        long retryAfterSeconds) {

    public static RateLimitResult allow() {
        return new RateLimitResult(true, 1, 10, 0);
    }

    public static RateLimitResult allow(long currentCount, long limit) {
        return new RateLimitResult(true, currentCount, limit, 0);
    }

    public static RateLimitResult reject(long retryAfterSeconds) {
        return new RateLimitResult(false, 11, 10, Math.max(1, retryAfterSeconds));
    }

    public static RateLimitResult reject(long currentCount, long limit, long retryAfterSeconds) {
        return new RateLimitResult(false, currentCount, limit, Math.max(1, retryAfterSeconds));
    }
}
