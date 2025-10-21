package com.recplatform.service;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

/**
 * Tracks a rolling, real-time popularity signal per business in Redis, fed by
 * the Kafka activity consumer. Deliberately uses StringRedisTemplate (not the
 * JSON-serializing template) because Redis's native INCRBYFLOAT needs the
 * stored value to be a plain numeric string.
 */
@Service
@RequiredArgsConstructor
public class RealtimePopularityService {

    private final StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "popularity:";
    private static final Duration WINDOW = Duration.ofHours(1);

    public void recordActivity(String businessId, double weight) {
        String key = KEY_PREFIX + businessId;
        redisTemplate.opsForValue().increment(key, weight);
        redisTemplate.expire(key, WINDOW);
    }

    /** Returns a roughly 0..1 popularity score based on recent weighted activity. */
    public double getPopularityScore(String businessId) {
        String raw = redisTemplate.opsForValue().get(KEY_PREFIX + businessId);
        if (raw == null) {
            return 0.0;
        }
        double count = Double.parseDouble(raw);
        return Math.min(1.0, count / 100.0);
    }
}
