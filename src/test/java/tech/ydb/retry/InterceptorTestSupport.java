package tech.ydb.retry;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.mockito.Mockito;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tech.ydb.core.Status;
import tech.ydb.core.StatusCode;
import tech.ydb.jdbc.exception.YdbStatusable;

import java.lang.reflect.Method;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;

abstract class InterceptorTestSupport {

    @AfterEach
    void cleanupTransactionContext() {
        TransactionSynchronizationManager.clear();
    }

    static TestableInterceptor interceptorWithConfig(boolean enabled, int maxAttempts, int slowBase, int fastBase,
                                                     int slowCap, int fastCap, boolean isIdempotent) {
        return interceptorWithSleeper(enabled, maxAttempts, slowBase, fastBase, slowCap, fastCap, isIdempotent, delay -> {
        });
    }

    static TestableInterceptor interceptorWithSleeper(boolean enabled, int maxAttempts, int slowBase, int fastBase,
                                                      int slowCap, int fastCap, boolean isIdempotent,
                                                      BackoffSleeper sleeper) {
        TestableInterceptor interceptor = new TestableInterceptor(
                new YdbRetryPolicyConfig(enabled, maxAttempts, slowBase, fastBase, slowCap, fastCap, isIdempotent),
                sleeper
        );
        interceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        return interceptor;
    }

    static MethodInvocation invocationFor(String methodName) {
        MethodInvocation invocation = Mockito.mock(MethodInvocation.class);
        Method method = methodOf(methodName);
        Object target = targetFor(methodName);
        Mockito.when(invocation.getMethod()).thenReturn(method);
        Mockito.when(invocation.getThis()).thenReturn(target);
        Mockito.when(invocation.getArguments()).thenReturn(new Object[0]);
        return invocation;
    }

    private static Object targetFor(String methodName) {
        if (methodName.startsWith("ydb") || methodName.startsWith("default")) {
            return new YdbTransactionalTestService();
        }
        return new TransactionalTestService();
    }

    static Method methodOf(String methodName) {
        try {
            if (methodName.startsWith("ydb") || methodName.startsWith("default")) {
                return YdbTransactionalTestService.class.getMethod(methodName);
            }
            return TransactionalTestService.class.getMethod(methodName);
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
    }

    static final class TestableInterceptor extends YdbTransactionInterceptor {
        private final Deque<Object> outcomes = new ArrayDeque<>();
        private final AtomicInteger attempts = new AtomicInteger();

        TestableInterceptor(YdbRetryPolicyConfig retryConfig,
                            BackoffSleeper backoffSleeper) {
            super(retryConfig, backoffSleeper);
        }

        void enqueueOutcome(Object... results) {
            for (Object result : results) {
                outcomes.addLast(result);
            }
        }

        int attemptsCount() {
            return attempts.get();
        }

        @Override
        protected Object invokeWithinTransaction(Method method, Class<?> targetClass, InvocationCallback invocation)
                throws Throwable {
            attempts.incrementAndGet();
            Object result = outcomes.removeFirst();
            if (result instanceof Throwable throwable) {
                throw throwable;
            }
            return result;
        }
    }

    static class TransactionalTestService {
        @Transactional
        public String regularTx() {
            return "ok";
        }
    }

    static class YdbTransactionalTestService {
        @YdbTransactional(maxAttempts = 2)
        public String ydbCustomRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 5)
        public String ydbRequiredRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 2, propagation = Propagation.REQUIRES_NEW)
        public String ydbRequiresNewRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 3, propagation = Propagation.NESTED)
        public String ydbNestedRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 3, propagation = Propagation.NOT_SUPPORTED)
        public String ydbNotSupportedRetry() {
            return "ok";
        }

        @YdbTransactional
        public String defaultRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 100, slowBackoffBaseMs = 200, fastBackoffBaseMs = 10, slowCapBackoffMs = 10000, fastCapBackoffMs = 12)
        public String ydbNewTransactionSettings() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = -2)
        public String ydbNegativeMaxAttempts() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 5, idempotent = 1)
        public String ydbIdempotentRetry() {
            return "ok";
        }

        @YdbTransactional(maxAttempts = 3, idempotent = 0)
        public String ydbNonIdempotentRetry() {
            return "ok";
        }
    }

    static final class ConfigurableStatusException extends RuntimeException implements YdbStatusable {
        private final StatusCode statusCode;

        ConfigurableStatusException(StatusCode statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public Status getStatus() {
            return Status.of(statusCode);
        }
    }
}
