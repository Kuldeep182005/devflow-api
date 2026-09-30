package com.devflow.api.security.ratelimit;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class RateLimiterTest {

    private StringRedisTemplate redisTemplate;
    private ValueOperations<String, String> valueOperations;
    private RedisRateLimiter rateLimiter;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        redisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock(ValueOperations.class);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiter = new RedisRateLimiter(redisTemplate);
    }

    @Test
    @DisplayName("Allows request when counter is within limit")
    void allowsRequestWithinLimit() {
        when(valueOperations.increment(anyString())).thenReturn(1L);
        when(valueOperations.getOperations()).thenReturn(redisTemplate);
        when(redisTemplate.getExpire(anyString(), any(TimeUnit.class))).thenReturn(59L);

        RateLimitResult result = rateLimiter.tryAcquire("test-tier", "client-1", 10, 60);

        assertThat(result.allowed()).isTrue();
        assertThat(result.retryAfterSeconds()).isEqualTo(0);
    }

    @Test
    @DisplayName("Rejects request when counter exceeds limit and provides retryAfterSeconds")
    void rejectsRequestExceedingLimit() {
        when(valueOperations.increment(anyString())).thenReturn(11L);
        when(valueOperations.getOperations()).thenReturn(redisTemplate);
        when(redisTemplate.getExpire(anyString())).thenReturn(45L);

        RateLimitResult result = rateLimiter.tryAcquire("test-tier", "client-2", 10, 60);

        assertThat(result.allowed()).isFalse();
        assertThat(result.retryAfterSeconds()).isEqualTo(45L);
    }

    @Test
    @DisplayName("Gracefully falls back to bounded in-memory rate limiter when Redis throws connection error")
    void fallsBackToMemoryWhenRedisFails() {
        when(valueOperations.increment(anyString()))
                .thenThrow(new RedisConnectionFailureException("Redis connection refused"));

        // First 3 requests should be allowed under limit 3
        assertThat(rateLimiter.tryAcquire("fail-tier", "client-failover", 3, 60).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire("fail-tier", "client-failover", 3, 60).allowed()).isTrue();
        assertThat(rateLimiter.tryAcquire("fail-tier", "client-failover", 3, 60).allowed()).isTrue();

        // 4th request exceeds in-memory limit and is rejected with retryAfter > 0
        RateLimitResult rejected = rateLimiter.tryAcquire("fail-tier", "client-failover", 3, 60);
        assertThat(rejected.allowed()).isFalse();
        assertThat(rejected.retryAfterSeconds()).isGreaterThan(0);
    }

    @Test
    @DisplayName("Gracefully handles missing or null RedisTemplate by operating completely in-memory")
    void operatesInMemoryWhenRedisTemplateNull() {
        RedisRateLimiter inMemoryOnly = new RedisRateLimiter((StringRedisTemplate) null);

        RateLimitResult res1 = inMemoryOnly.tryAcquire("tier", "c1", 2, 60);
        RateLimitResult res2 = inMemoryOnly.tryAcquire("tier", "c1", 2, 60);
        RateLimitResult res3 = inMemoryOnly.tryAcquire("tier", "c1", 2, 60);

        assertThat(res1.allowed()).isTrue();
        assertThat(res2.allowed()).isTrue();
        assertThat(res3.allowed()).isFalse();
        assertThat(res3.retryAfterSeconds()).isGreaterThan(0);
    }
}
