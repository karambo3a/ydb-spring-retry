package tech.ydb.retry;

import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.ydb.core.StatusCode.ABORTED;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.CLIENT_CANCELLED;
import static tech.ydb.core.StatusCode.OVERLOADED;
import static tech.ydb.core.StatusCode.SESSION_BUSY;
import static tech.ydb.core.StatusCode.SESSION_EXPIRED;
import static tech.ydb.core.StatusCode.TIMEOUT;
import static tech.ydb.core.StatusCode.TRANSPORT_UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNDETERMINED;

class YdbTransactionConfigOverrideTest extends InterceptorTestSupport {

    @Test
    void shouldOverrideMaxAttemptsFromAnnotation() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(1, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(ABORTED), "ok");

        Object result = interceptor.invoke(invocationFor("ydbCustomRetry"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldUseConfigMaxAttemptsWhenAnnotationNotSet() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(2, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(CLIENT_CANCELLED), "ok");

        Object result = interceptor.invoke(invocationFor("defaultRetry"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldExhaustAnnotatedMaxAttemptsAndPropagate() {
        TestableInterceptor interceptor = interceptorWithConfig(1, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(SESSION_BUSY), new ConfigurableStatusException(OVERLOADED));

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("ydbCustomRetry"))
        );

        assertEquals(OVERLOADED, exception.getStatus().getCode());
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldUseAnnotatedMaxAttemptsWhenLowerThanConfig() {
        TestableInterceptor interceptor = interceptorWithConfig(1, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(OVERLOADED), new ConfigurableStatusException(TRANSPORT_UNAVAILABLE));

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("ydbCustomRetry"))
        );

        assertEquals(TRANSPORT_UNAVAILABLE, exception.getStatus().getCode());
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldUseAnnotatedMaxAttemptsWhenHigherThanConfig() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(1, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(BAD_SESSION), new ConfigurableStatusException(SESSION_BUSY),
                new ConfigurableStatusException(ABORTED), new ConfigurableStatusException(CLIENT_CANCELLED),
                "ok");

        Object result = interceptor.invoke(invocationFor("ydbRequiredRetry"));

        assertEquals("ok", result);
        assertEquals(5, interceptor.attemptsCount());
    }

    @Test
    void shouldRetryDifferentStatusCodesAcrossAttempts() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(1, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(ABORTED),
                new ConfigurableStatusException(BAD_SESSION),
                "ok");

        Object result = interceptor.invoke(invocationFor("ydbRequiredRetry"));

        assertEquals("ok", result);
        assertEquals(3, interceptor.attemptsCount());
    }

    @Test
    void shouldRetryTimeoutWhenIdempotent() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, true);
        interceptor.enqueueOutcome(new ConfigurableStatusException(TIMEOUT), "ok");

        Object result = interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldNotRetrySessionExpiredWhenNotIdempotent() {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, true);
        interceptor.enqueueOutcome(new ConfigurableStatusException(SESSION_EXPIRED));

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("ydbNonIdempotentRetry"))
        );

        assertEquals(SESSION_EXPIRED, exception.getStatus().getCode());
        assertEquals(1, interceptor.attemptsCount());
    }

    @Test
    void shouldRetryAlwaysRetryableCodesWhenIdempotent() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, true);
        interceptor.enqueueOutcome(new ConfigurableStatusException(ABORTED), "ok");

        Object result = interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldRetryMixedStatusCodesWhenIdempotent() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, true);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(TIMEOUT),
                new ConfigurableStatusException(ABORTED),
                new ConfigurableStatusException(UNDETERMINED),
                "ok"
        );

        Object result = interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals("ok", result);
        assertEquals(4, interceptor.attemptsCount());
    }

    @Test
    void shouldStopAtIdempotentOnlyCodeWhenNotIdempotent() {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(BAD_SESSION),
                new ConfigurableStatusException(TIMEOUT)
        );

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("ydbNonIdempotentRetry"))
        );

        assertEquals(TIMEOUT, exception.getStatus().getCode());
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldUseZeroDelayForTimeoutWhenIdempotent() throws Throwable {
        List<Long> delays = new ArrayList<>();
        TestableInterceptor interceptor = interceptorWithSleeper(5, 100, 50, 1000, 500, true, delays::add);
        interceptor.enqueueOutcome(new ConfigurableStatusException(TIMEOUT), "ok");

        interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals(1, delays.size());
        assertEquals(0, delays.get(0));
    }

    @Test
    void shouldUseZeroDelayForSessionExpiredWhenIdempotent() throws Throwable {
        List<Long> delays = new ArrayList<>();
        TestableInterceptor interceptor = interceptorWithSleeper(5, 100, 50, 1000, 500, true, delays::add);
        interceptor.enqueueOutcome(new ConfigurableStatusException(SESSION_EXPIRED), "ok");

        interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals(1, delays.size());
        assertEquals(0, delays.getFirst());
    }

    @Test
    void shouldUseFastBackoffForUndeterminedWhenIdempotent() throws Throwable {
        List<Long> delays = new ArrayList<>();
        TestableInterceptor interceptor = interceptorWithSleeper(5, 100, 50, 1000, 500, true, delays::add);
        interceptor.enqueueOutcome(new ConfigurableStatusException(UNDETERMINED), "ok");

        interceptor.invoke(invocationFor("ydbIdempotentRetry"));

        assertEquals(1, delays.size());
        assertTrue(delays.getFirst() >= 0);
    }
}
