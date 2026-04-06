package tech.ydb.retry;

import org.springframework.lang.Nullable;
import tech.ydb.core.StatusCode;

import java.util.Set;

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

    public static boolean shouldRetry(@Nullable StatusCode statusCode, boolean isIdempotent) {
        if (statusCode == null) {
            return false;
        }
        return RETRYABLE_CODES.contains(statusCode) || (isIdempotent && IDEMPOTENT_RETRYABLE_CODES.contains(statusCode));
    }
}
