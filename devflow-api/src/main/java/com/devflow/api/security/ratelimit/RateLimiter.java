package com.devflow.api.security.ratelimit;

public interface RateLimiter {

    /**
     * Checks whether an action for the given tier and key is permitted within the window.
     *
     * @param tier the rate limit tier (e.g. "auth-login", "webhooks", "global")
     * @param key the client identifier (IP or authenticated user ID)
     * @param limit maximum permitted requests within the window
     * @param windowSeconds length of the window in seconds
     * @return the result including whether allowed and remaining retry-after duration
     */
    RateLimitResult tryAcquire(String tier, String key, int limit, int windowSeconds);
}
