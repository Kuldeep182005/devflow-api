package com.devflow.api.security.ratelimit;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Production-hardened rate limiter supporting distributed Redis storage with
 * a bounded in-memory fallback for resilience when Redis is temporarily unreachable.
 */
@Component
public class RedisRateLimiter implements RateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final int MAX_IN_MEMORY_ENTRIES = 5000;

    private final ObjectProvider<StringRedisTemplate> redisProvider;
    private final StringRedisTemplate directRedis;
    private final Map<String, InMemoryCounter> inMemoryFallback = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public RedisRateLimiter(ObjectProvider<StringRedisTemplate> redisProvider) {
        this.redisProvider = redisProvider;
        this.directRedis = null;
    }

    public RedisRateLimiter(StringRedisTemplate directRedis) {
        this.redisProvider = null;
        this.directRedis = directRedis;
    }

    @Override
    public RateLimitResult tryAcquire(String tier, String key, int limit, int windowSeconds) {
        String safeTier = sanitize(tier, "default");
        String safeKey = sanitize(key, "anonymous");
        String redisKey = "devflow:ratelimit:" + safeTier + ":" + safeKey;

        StringRedisTemplate redis = directRedis != null ? directRedis : (redisProvider != null ? redisProvider.getIfAvailable() : null);
        if (redis != null) {
            try {
                Long count = redis.opsForValue().increment(redisKey);
                if (count != null && count == 1) {
                    redis.expire(redisKey, Duration.ofSeconds(Math.max(1, windowSeconds)));
                }
                Long ttl = redis.getExpire(redisKey);
                long retryAfter = (ttl != null && ttl > 0) ? ttl : windowSeconds;

                if (count != null && count > limit) {
                    return RateLimitResult.reject(count, limit, retryAfter);
                }
                return RateLimitResult.allow(count != null ? count : 1, limit);
            } catch (Exception exception) {
                log.warn("Redis rate limiting unavailable, falling back to in-memory: {}", exception.getMessage());
            }
        }

        // Bounded in-memory fallback
        return acquireInMemory(safeTier + ":" + safeKey, limit, windowSeconds);
    }

    private RateLimitResult acquireInMemory(String key, int limit, int windowSeconds) {
        if (inMemoryFallback.size() > MAX_IN_MEMORY_ENTRIES) {
            // Purge expired entries to prevent memory exhaustion
            Instant now = Instant.now();
            inMemoryFallback.entrySet().removeIf(entry -> entry.getValue().expiresAt().isBefore(now));
            if (inMemoryFallback.size() > MAX_IN_MEMORY_ENTRIES) {
                // Fail safe: allow request if memory limit exceeded
                return RateLimitResult.allow(1, limit);
            }
        }

        Instant now = Instant.now();
        InMemoryCounter counter = inMemoryFallback.compute(key, (k, existing) -> {
            if (existing == null || existing.expiresAt().isBefore(now)) {
                return new InMemoryCounter(new AtomicLong(1), now.plusSeconds(windowSeconds));
            }
            existing.count().incrementAndGet();
            return existing;
        });

        long currentCount = counter.count().get();
        long retryAfter = Math.max(1, Duration.between(now, counter.expiresAt()).toSeconds());

        if (currentCount > limit) {
            return RateLimitResult.reject(currentCount, limit, retryAfter);
        }
        return RateLimitResult.allow(currentCount, limit);
    }

    private String sanitize(String value, String fallback) {
        if (value == null || value.isBlank()) {
            return fallback;
        }
        String cleaned = value.trim().replaceAll("[^a-zA-Z0-9_.-]", "_");
        if (cleaned.length() > 64) {
            cleaned = cleaned.substring(0, 64);
        }
        return cleaned.isEmpty() ? fallback : cleaned;
    }

    private record InMemoryCounter(AtomicLong count, Instant expiresAt) {
    }
}
