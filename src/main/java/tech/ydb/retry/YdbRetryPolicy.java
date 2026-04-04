package tech.ydb.retry;

import org.springframework.lang.Nullable;
import tech.ydb.core.StatusCode;

import java.util.Set;

import static tech.ydb.core.StatusCode.*;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.OVERLOADED;

public final class YdbRetryPolicy {
    private static final Set<StatusCode> RETRYABLE_CODES = Set.of(
            ABORTED,
            UNAVAILABLE,
            OVERLOADED,
            BAD_SESSION,
            SESSION_BUSY,
            CLIENT_CANCELLED,
            CLIENT_INTERNAL_ERROR,
            TRANSPORT_UNAVAILABLE,
            CLIENT_RESOURCE_EXHAUSTED
    );

    private static final Set<StatusCode> IDEMPOTENT_RETRYABLE_CODES = Set.of(
            TIMEOUT,
            SESSION_EXPIRED,
            UNDETERMINED
    );

    public boolean shouldRetry(@Nullable StatusCode statusCode, boolean isIdempotent) {
        if (statusCode == null) {
            return false;
        }
        return RETRYABLE_CODES.contains(statusCode) || (isIdempotent && IDEMPOTENT_RETRYABLE_CODES.contains(statusCode));
    }
}
