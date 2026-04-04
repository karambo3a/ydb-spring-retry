package tech.ydb.retry;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "ydb.transaction.retry")
public class YdbRetryProperties {

    private int maxAttempts = YdbRetryPolicyConfig.DEFAULT_MAX_ATTEMPTS;
    private int slowBackoffBaseMs = YdbRetryPolicyConfig.DEFAULT_SLOW_BACKOFF_BASE_MS;
    private int fastBackoffBaseMs = YdbRetryPolicyConfig.DEFAULT_FAST_BACKOFF_BASE_MS;
    private int slowCapBackoffMs = YdbRetryPolicyConfig.DEFAULT_SLOW_CAP_BACKOFF_MS;
    private int fastCapBackoffMs = YdbRetryPolicyConfig.DEFAULT_FAST_CAP_BACKOFF_MS;
    private boolean idempotent = false;

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public void setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
    }

    public int getSlowBackoffBaseMs() {
        return slowBackoffBaseMs;
    }

    public void setSlowBackoffBaseMs(int slowBackoffBaseMs) {
        this.slowBackoffBaseMs = slowBackoffBaseMs;
    }

    public int getFastBackoffBaseMs() {
        return fastBackoffBaseMs;
    }

    public void setFastBackoffBaseMs(int fastBackoffBaseMs) {
        this.fastBackoffBaseMs = fastBackoffBaseMs;
    }

    public int getSlowCapBackoffMs() {
        return slowCapBackoffMs;
    }

    public void setSlowCapBackoffMs(int slowCapBackoffMs) {
        this.slowCapBackoffMs = slowCapBackoffMs;
    }

    public int getFastCapBackoffMs() {
        return fastCapBackoffMs;
    }

    public void setFastCapBackoffMs(int fastCapBackoffMs) {
        this.fastCapBackoffMs = fastCapBackoffMs;
    }

    public boolean isIdempotent() {
        return idempotent;
    }

    public void setIdempotent(boolean idempotent) {
        this.idempotent = idempotent;
    }

    public YdbRetryPolicyConfig toConfig() {
        return new YdbRetryPolicyConfig(
                maxAttempts,
                slowBackoffBaseMs,
                fastBackoffBaseMs,
                slowCapBackoffMs,
                fastCapBackoffMs,
                idempotent
        );
    }
}
