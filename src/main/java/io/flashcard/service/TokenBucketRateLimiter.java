package io.flashcard.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class TokenBucketRateLimiter {

    private final int maxTokens;
    private final long refillIntervalMs;
    private final AtomicInteger tokens;
    private final AtomicLong lastRefill;

    public TokenBucketRateLimiter(int maxTokens, long windowMs) {
        this.maxTokens = maxTokens;
        this.refillIntervalMs = windowMs / maxTokens;
        this.tokens = new AtomicInteger(maxTokens);
        this.lastRefill = new AtomicLong(System.currentTimeMillis());
    }

    public boolean tryConsume() {
        long now = System.currentTimeMillis();
        long elapsed = now - lastRefill.get();
        int refill = (int) (elapsed / refillIntervalMs);
        if (refill > 0) {
            tokens.updateAndGet(t -> Math.min(maxTokens, t + refill));
            lastRefill.set(now);
        }
        return tokens.getAndUpdate(t -> t > 0 ? t - 1 : t) > 0;
    }

    /** Block until a token is available, up to maxWaitMs. Returns false if timed out. */
    public boolean consumeBlocking(long maxWaitMs) {
        long deadline = System.currentTimeMillis() + maxWaitMs;
        while (System.currentTimeMillis() < deadline) {
            if (tryConsume()) return true;
            try { Thread.sleep(refillIntervalMs); } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        return false;
    }
}
