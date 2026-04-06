package tech.ydb.retry;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static tech.ydb.core.StatusCode.ABORTED;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.CLIENT_INTERNAL_ERROR;
import static tech.ydb.core.StatusCode.TIMEOUT;
import static tech.ydb.core.StatusCode.UNAUTHORIZED;

class TransactionalDefaultRetryTest extends InterceptorTestSupport {

    @Test
    void shouldRetryWithDefaultConfigUntilSuccess() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(3, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(BAD_SESSION), "ok");

        Object result = interceptor.invoke(invocationFor("regularTx"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldExhaustDefaultMaxAttemptsAndPropagateLastException() {
        TestableInterceptor interceptor = interceptorWithConfig(2, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(BAD_SESSION), new ConfigurableStatusException(ABORTED));

        assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("regularTx"))
        );
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldPropagateNonRetryableExceptionImmediately() {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(UNAUTHORIZED));

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("regularTx"))
        );

        assertEquals(UNAUTHORIZED, exception.getStatus().getCode());
        assertEquals(1, interceptor.attemptsCount());
    }

    @Test
    void shouldNotRetryNonYdbRuntimeException() {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new IllegalStateException("not ydb"));

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> interceptor.invoke(invocationFor("regularTx"))
        );

        assertEquals("not ydb", exception.getMessage());
        assertEquals(1, interceptor.attemptsCount());
    }

    @Test
    void shouldImmediatelyPropagateJavaError() {
        TestableInterceptor interceptor = interceptorWithConfig(5, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new OutOfMemoryError("test oom"));

        assertThrows(
                OutOfMemoryError.class,
                () -> interceptor.invoke(invocationFor("regularTx"))
        );
        assertEquals(1, interceptor.attemptsCount());
    }

    @Test
    void shouldRetryWhenYdbStatusExtractedFromExceptionChain() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(3, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(
                new RuntimeException("wrapped", new ConfigurableStatusException(BAD_SESSION)), "ok");

        Object result = interceptor.invoke(invocationFor("regularTx"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldCallSleeperWithBackoffDelay() throws Throwable {
        List<Long> delays = new ArrayList<>();
        TestableInterceptor interceptor = interceptorWithSleeper(5, 0, 0, 0, 0, false, delays::add);
        interceptor.enqueueOutcome(
                new ConfigurableStatusException(ABORTED),
                new ConfigurableStatusException(ABORTED),
                "ok"
        );

        Object result = interceptor.invoke(invocationFor("regularTx"));

        assertEquals("ok", result);
        assertEquals(3, interceptor.attemptsCount());
        assertEquals(2, delays.size());
        for (Long delay : delays) {
            assertTrue(delay >= 0);
        }
    }

    @Test
    void shouldUseZeroDelayForBadSession() throws Throwable {
        List<Long> delays = new ArrayList<>();
        TestableInterceptor interceptor = interceptorWithSleeper(5, 100, 50, 1000, 500, false, delays::add);
        interceptor.enqueueOutcome(new ConfigurableStatusException(BAD_SESSION), "ok");

        Object result = interceptor.invoke(invocationFor("regularTx"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
        assertEquals(1, delays.size());
        assertEquals(0, delays.get(0));
    }

    @Test
    void shouldHandleInterruptedSleep() {
        ConfigurableStatusException originalException = new ConfigurableStatusException(CLIENT_INTERNAL_ERROR);
        TestableInterceptor interceptor = interceptorWithSleeper(
                3, 0, 0, 0, 0, false, delay -> {
                    throw new InterruptedException("sleep interrupted");
                });
        interceptor.enqueueOutcome(originalException, "ok");

        try {
            InterruptedException thrown = assertThrows(
                    InterruptedException.class,
                    () -> interceptor.invoke(invocationFor("regularTx"))
            );
            assertEquals("sleep interrupted", thrown.getMessage());
            assertEquals(1, thrown.getSuppressed().length);
            assertSame(originalException, thrown.getSuppressed()[0]);
            assertTrue(Thread.currentThread().isInterrupted());
        } finally {
            Thread.interrupted();
        }
    }

    @Test
    void shouldRetryTimeoutForTransactionalMethodWhenDefaultConfigIdempotent() throws Throwable {
        TestableInterceptor interceptor = interceptorWithConfig(3, 0, 0, 0, 0, true);
        interceptor.enqueueOutcome(new ConfigurableStatusException(TIMEOUT), "ok");

        Object result = interceptor.invoke(invocationFor("regularTx"));

        assertEquals("ok", result);
        assertEquals(2, interceptor.attemptsCount());
    }

    @Test
    void shouldNotRetryTimeoutForTransactionalMethodWhenDefaultConfigNotIdempotent() {
        TestableInterceptor interceptor = interceptorWithConfig(3, 0, 0, 0, 0, false);
        interceptor.enqueueOutcome(new ConfigurableStatusException(TIMEOUT));

        ConfigurableStatusException exception = assertThrows(
                ConfigurableStatusException.class,
                () -> interceptor.invoke(invocationFor("regularTx"))
        );

        assertEquals(TIMEOUT, exception.getStatus().getCode());
        assertEquals(1, interceptor.attemptsCount());
    }
}
