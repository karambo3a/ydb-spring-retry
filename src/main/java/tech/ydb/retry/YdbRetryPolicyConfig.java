package tech.ydb.retry;

import java.util.concurrent.ThreadLocalRandom;
import org.springframework.lang.Nullable;

public final class YdbRetryPolicyConfig {
    public static final int DEFAULT_MAX_ATTEMPTS = 10;
    public static final int DEFAULT_SLOW_BACKOFF_BASE_MS = 50;
    public static final int DEFAULT_FAST_BACKOFF_BASE_MS = 5;
    public static final int DEFAULT_SLOW_CAP_BACKOFF_MS = 5_000;
    public static final int DEFAULT_FAST_CAP_BACKOFF_MS = 500;

    private final int maxAttempts;
    private final int slowBackoffBaseMs;
    private final int fastBackoffBaseMs;
    private final int slowCapBackoffMs;
    private final int fastCapBackoffMs;
    private final int slowPow;
    private final int fastPow;
    private final boolean isIdempotent;

    public YdbRetryPolicyConfig() {
        this(
                DEFAULT_MAX_ATTEMPTS,
                DEFAULT_SLOW_BACKOFF_BASE_MS,
                DEFAULT_FAST_BACKOFF_BASE_MS,
                DEFAULT_SLOW_CAP_BACKOFF_MS,
                DEFAULT_FAST_CAP_BACKOFF_MS,
                false
        );
    }

    public YdbRetryPolicyConfig(int maxAttempts, int slowBackoffBaseMs, int fastBackoffBaseMs,
                                int slowCapBackoffMs, int fastCapBackoffMs) {
        this(maxAttempts, slowBackoffBaseMs, fastBackoffBaseMs, slowCapBackoffMs, fastCapBackoffMs, false);
    }

    public YdbRetryPolicyConfig(int maxAttempts, int slowBackoffBaseMs, int fastBackoffBaseMs,
                                int slowCapBackoffMs, int fastCapBackoffMs, boolean isIdempotent) {
        if (maxAttempts < 1) {
            throw new IllegalArgumentException("maxAttempts must be >= 1");
        }
        if (slowBackoffBaseMs < 0 || fastBackoffBaseMs < 0 || slowCapBackoffMs < 0 || fastCapBackoffMs < 0) {
            throw new IllegalArgumentException("backoff values must be >= 0");
        }
        this.slowBackoffBaseMs = slowBackoffBaseMs;
        this.fastBackoffBaseMs = fastBackoffBaseMs;
        this.slowCapBackoffMs = slowCapBackoffMs;
        this.fastCapBackoffMs = fastCapBackoffMs;
        this.maxAttempts = maxAttempts;
        this.slowPow = powerForCap(this.slowCapBackoffMs);
        this.fastPow = powerForCap(this.fastCapBackoffMs);
        this.isIdempotent = isIdempotent;
    }

    public long getJitter(long bound) {
        if (bound <= 0) {
            return 0;
        }
        return ThreadLocalRandom.current().nextLong(bound);
    }

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public int getSlowBackoffBaseMs() {
        return slowBackoffBaseMs;
    }

    public int getFastBackoffBaseMs() {
        return fastBackoffBaseMs;
    }

    public int getSlowCapBackoffMs() {
        return slowCapBackoffMs;
    }

    public int getFastCapBackoffMs() {
        return fastCapBackoffMs;
    }

    public int getSlowPow() {
        return slowPow;
    }

    public int getFastPow() {
        return fastPow;
    }

    public boolean isIdempotent() {
        return isIdempotent;
    }

    public YdbRetryPolicyConfig merge(@Nullable YdbTransaction transactionPolicy) {
        if (transactionPolicy == null) {
            return this;
        }
        return new YdbRetryPolicyConfig(
                checkCandidate("maxAttempts", transactionPolicy.maxAttempts(), maxAttempts),
                checkCandidate("slowBackoffBaseMs", transactionPolicy.slowBackoffBaseMs(), slowBackoffBaseMs),
                checkCandidate("fastBackoffBaseMs", transactionPolicy.fastBackoffBaseMs(), fastBackoffBaseMs),
                checkCandidate("slowCapBackoffMs", transactionPolicy.slowCapBackoffMs(), slowCapBackoffMs),
                checkCandidate("fastCapBackoffMs", transactionPolicy.fastCapBackoffMs(), fastCapBackoffMs),
                checkIdempotent(transactionPolicy.idempotent(), isIdempotent)
        );
    }

    private static int checkCandidate(String name, int candidate, int fallback) throws IllegalArgumentException {
        if (candidate < -1) {
            throw new IllegalArgumentException(String.format("%s is invalid", name));
        }
        return candidate == -1 ? fallback : candidate;
    }

    private static boolean checkIdempotent(int candidate, boolean fallback) {
        if (candidate == -1) {
            return fallback;
        }
        if (candidate < -1 || candidate > 1) {
            throw new IllegalArgumentException("idempotent must be -1, 0, or 1");
        }
        return candidate == 1;
    }

    private static int powerForCap(int capMs) {
        if (capMs <= 1) {
            return 1;
        }
        return Math.max(1, (int) (Math.log(capMs) / Math.log(2)));
    }
}
