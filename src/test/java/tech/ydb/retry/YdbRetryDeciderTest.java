package tech.ydb.retry;

import org.junit.jupiter.api.Test;
import tech.ydb.core.StatusCode;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.ydb.core.StatusCode.ABORTED;
import static tech.ydb.core.StatusCode.BAD_REQUEST;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.CANCELLED;
import static tech.ydb.core.StatusCode.CLIENT_CANCELLED;
import static tech.ydb.core.StatusCode.CLIENT_INTERNAL_ERROR;
import static tech.ydb.core.StatusCode.CLIENT_RESOURCE_EXHAUSTED;
import static tech.ydb.core.StatusCode.EXTERNAL_ERROR;
import static tech.ydb.core.StatusCode.GENERIC_ERROR;
import static tech.ydb.core.StatusCode.INTERNAL_ERROR;
import static tech.ydb.core.StatusCode.NOT_FOUND;
import static tech.ydb.core.StatusCode.OVERLOADED;
import static tech.ydb.core.StatusCode.SCHEME_ERROR;
import static tech.ydb.core.StatusCode.SESSION_BUSY;
import static tech.ydb.core.StatusCode.SESSION_EXPIRED;
import static tech.ydb.core.StatusCode.TIMEOUT;
import static tech.ydb.core.StatusCode.TRANSPORT_UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNAUTHORIZED;
import static tech.ydb.core.StatusCode.UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNDETERMINED;
import static tech.ydb.core.StatusCode.UNSUPPORTED;

class YdbRetryDeciderTest {

    private final YdbRetryPolicy decider = new YdbRetryPolicy();

    @Test
    void shouldRetryAlwaysRetryableStatusesRegardlessOfIdempotence() {
        List<StatusCode> alwaysRetryable = List.of(
                BAD_SESSION,
                SESSION_BUSY,
                ABORTED,
                CLIENT_CANCELLED,
                CLIENT_INTERNAL_ERROR,
                UNAVAILABLE,
                TRANSPORT_UNAVAILABLE,
                OVERLOADED,
                CLIENT_RESOURCE_EXHAUSTED
        );

        for (StatusCode code : alwaysRetryable) {
            assertTrue(decider.shouldRetry(code, false), "Should retry " + code + " when not idempotent");
            assertTrue(decider.shouldRetry(code, true), "Should retry " + code + " when idempotent");
        }
    }

    @Test
    void shouldNotRetryIdempotentOnlyStatusesWhenNotIdempotent() {
        List<StatusCode> idempotentOnly = List.of(TIMEOUT, SESSION_EXPIRED, UNDETERMINED);

        for (StatusCode code : idempotentOnly) {
            assertFalse(decider.shouldRetry(code, false), "Should not retry " + code + " when not idempotent");
        }
    }

    @Test
    void shouldRetryIdempotentOnlyStatusesWhenIdempotent() {
        List<StatusCode> idempotentOnly = List.of(TIMEOUT, SESSION_EXPIRED, UNDETERMINED);

        for (StatusCode code : idempotentOnly) {
            assertTrue(decider.shouldRetry(code, true), "Should retry " + code + " when idempotent");
        }
    }

    @Test
    void shouldNotRetryNonRetryableStatuses() {
        List<StatusCode> nonRetryable = List.of(
                StatusCode.SUCCESS,
                BAD_REQUEST,
                UNAUTHORIZED,
                INTERNAL_ERROR,
                SCHEME_ERROR,
                GENERIC_ERROR,
                NOT_FOUND,
                UNSUPPORTED,
                CANCELLED,
                EXTERNAL_ERROR
        );

        for (StatusCode code : nonRetryable) {
            assertFalse(decider.shouldRetry(code, false), "Should not retry " + code + " when not idempotent");
            assertFalse(decider.shouldRetry(code, true), "Should not retry " + code + " when idempotent");
        }
    }

    @Test
    void shouldNotRetryNullStatusCode() {
        assertFalse(decider.shouldRetry(null, false));
        assertFalse(decider.shouldRetry(null, true));
    }
}
