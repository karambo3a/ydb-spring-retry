package tech.ydb.retry;

import org.springframework.lang.Nullable;
import tech.ydb.core.StatusCode;

import static tech.ydb.core.StatusCode.ABORTED;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.CLIENT_CANCELLED;
import static tech.ydb.core.StatusCode.CLIENT_INTERNAL_ERROR;
import static tech.ydb.core.StatusCode.CLIENT_RESOURCE_EXHAUSTED;
import static tech.ydb.core.StatusCode.OVERLOADED;
import static tech.ydb.core.StatusCode.SESSION_BUSY;
import static tech.ydb.core.StatusCode.SESSION_EXPIRED;
import static tech.ydb.core.StatusCode.TIMEOUT;
import static tech.ydb.core.StatusCode.TRANSPORT_UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNDETERMINED;

public class YdbDelayCalculator {
    public static long calculateDelay(@Nullable StatusCode statusCode, YdbRetryPolicyConfig retryConfig, int attempt) {
        if (statusCode == null) {
            return 0;
        }

        if (statusCode == BAD_SESSION || statusCode == SESSION_BUSY || statusCode == TIMEOUT || statusCode == SESSION_EXPIRED) {
            return 0;
        }
        if (statusCode == UNDETERMINED || statusCode == ABORTED || statusCode == CLIENT_CANCELLED || statusCode == CLIENT_INTERNAL_ERROR) {
            return delayWithFullJitter(retryConfig.getFastBackoffBaseMs(), retryConfig.getFastCapBackoffMs(), retryConfig.getFastPow(), attempt, retryConfig);
        }
        if (statusCode == UNAVAILABLE || statusCode == TRANSPORT_UNAVAILABLE) {
            return delayWithEqualJitter(retryConfig.getFastBackoffBaseMs(), retryConfig.getFastCapBackoffMs(), retryConfig.getFastPow(), attempt, retryConfig);
        }
        if (statusCode == OVERLOADED || statusCode == CLIENT_RESOURCE_EXHAUSTED) {
            return delayWithEqualJitter(retryConfig.getSlowBackoffBaseMs(), retryConfig.getSlowCapBackoffMs(), retryConfig.getSlowPow(), attempt, retryConfig);
        }
        return 0;
    }

    private static long delayWithFullJitter(int baseMs, int capMs, int pow, int attempt, YdbRetryPolicyConfig retryConfig) {
        long currentDelay = Math.min(baseMs * ((1L << Math.min(pow, attempt)) - 1), capMs);
        return retryConfig.getJitter(currentDelay);
    }

    private static long delayWithEqualJitter(int baseMs, int capMs, int pow, int attempt, YdbRetryPolicyConfig retryConfig) {
        long tmp = (long) baseMs * ((1L << Math.min(pow, attempt)) - 1) / 2;
        return Math.min(tmp + retryConfig.getJitter(tmp), capMs);
    }
}
