package tech.ydb.retry;

import java.util.concurrent.ThreadLocalRandom;

public class YdbRetryPolicyConfig {
    public int maxAttempts = 10;
    public boolean enableRetryIdempotence = false;
    public int slowBackoffBaseMs = 50;
    public int fastBackoffBaseMs = 5;
    public int slowCapBackoffMs = 5_000;
    public int fastCapBackoffMs = 500;
    public int slowPow;
    public int fastPow;
    public ThreadLocalRandom random = ThreadLocalRandom.current();

    public YdbRetryPolicyConfig() {
        this.slowPow = (int) (Math.log(slowCapBackoffMs) / Math.log(2));
        this.fastPow = (int) (Math.log(fastCapBackoffMs) / Math.log(2));
    }

    public YdbRetryPolicyConfig(int maxAttempts, boolean enableRetryIdempotence,
                                int slowBackoffBaseMs, int fastBackoffBaseMs,
                                int slowCapBackoffMs, int fastCapBackoffMs) {
        this.maxAttempts = maxAttempts;
        this.enableRetryIdempotence = enableRetryIdempotence;
        this.slowBackoffBaseMs = slowBackoffBaseMs;
        this.fastBackoffBaseMs = fastBackoffBaseMs;
        this.slowCapBackoffMs = slowCapBackoffMs;
        this.fastCapBackoffMs = fastCapBackoffMs;
        this.slowPow = (int) (Math.log(this.slowCapBackoffMs) / Math.log(2));
        this.fastPow = (int) (Math.log(this.fastCapBackoffMs) / Math.log(2));
    }

    public long getJitter(int bound) {
        return random.nextLong(bound);
    }
}
