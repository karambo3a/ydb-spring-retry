package tech.ydb.retry;

import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.aop.support.AopUtils;
import org.springframework.dao.RecoverableDataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.TransactionAttribute;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import tech.ydb.core.StatusCode;
import tech.ydb.jdbc.exception.YdbConditionallyRetryableException;
import tech.ydb.jdbc.exception.YdbRetryableException;
import tech.ydb.jdbc.exception.YdbStatusable;
import tech.ydb.jdbc.exception.YdbUnavailbaleException;

import java.sql.SQLException;

import static tech.ydb.core.StatusCode.ABORTED;
import static tech.ydb.core.StatusCode.BAD_SESSION;
import static tech.ydb.core.StatusCode.CLIENT_CANCELLED;
import static tech.ydb.core.StatusCode.CLIENT_INTERNAL_ERROR;
import static tech.ydb.core.StatusCode.CLIENT_RESOURCE_EXHAUSTED;
import static tech.ydb.core.StatusCode.OVERLOADED;
import static tech.ydb.core.StatusCode.SESSION_BUSY;
import static tech.ydb.core.StatusCode.TRANSPORT_UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNAVAILABLE;
import static tech.ydb.core.StatusCode.UNDETERMINED;

public class YdbTransactionInterceptor extends TransactionInterceptor {

    private static final Logger log = LoggerFactory.getLogger(YdbTransactionInterceptor.class);
    private final YdbRetryPolicyConfig retryConfig = new YdbRetryPolicyConfig();
    private int invokeCnt = 0;

    @Override
    @Nullable
    public Object invoke(final MethodInvocation invocation) throws Throwable {
        invokeCnt++;
        Class<?> targetClass = invocation.getThis() != null ? AopUtils.getTargetClass(invocation.getThis()) : null;

        // If the transaction attribute is null, the method is non-transactional.
        TransactionAttributeSource tas = getTransactionAttributeSource();
        final TransactionAttribute txAttr = (tas != null ? tas.getTransactionAttribute(invocation.getMethod(), targetClass) : null);
        if (txAttr == null) {
            return this.invokeWithinTransaction(invocation.getMethod(), targetClass, createCallback(invocation));
        }

        return invokeWithinTransactionWithRetryContext(invocation, targetClass);
    }


    @Nullable
    private Object invokeWithinTransactionWithRetryContext(final MethodInvocation invocation, @Nullable Class<?> targetClass) throws Throwable {
        for (int i = 0; i <= retryConfig.maxAttempts; i++) {
            log.info("invokeCnt = {} attempt = {}", invokeCnt, i);
            try {
                return this.invokeWithinTransaction(invocation.getMethod(), targetClass, createCallback(invocation));
            } catch (RecoverableDataAccessException | SQLException ex) {
                log.info(String.valueOf(ex));
                if (ex.getCause() instanceof YdbRetryableException || ex.getCause() instanceof YdbConditionallyRetryableException || ex.getCause() instanceof YdbUnavailbaleException) {
                    YdbStatusable e = (YdbStatusable) ex;
                    log.info("YDB STATUSABLE" + String.valueOf(e));

                    if (i == retryConfig.maxAttempts) {
                        throw ex;
                    }
                    long delay = calculateDelay(e.getStatus().getCode(), i);
                    if (delay < 0) {
                        throw ex;
                    }

                    Thread.sleep(delay);
                }
            }
        }
        return null;
    }

    private long calculateDelay(StatusCode statusCode, int attempt) {
        // instant
        if (statusCode == BAD_SESSION || statusCode == SESSION_BUSY) {
            return 0;
            // fast + full jitter
        } else if (statusCode == ABORTED || statusCode == UNDETERMINED || statusCode == CLIENT_CANCELLED || statusCode == CLIENT_INTERNAL_ERROR) {
            return delayWithFullJitter(retryConfig.fastBackoffBaseMs, retryConfig.fastCapBackoffMs, retryConfig.fastPow, attempt);
            // fast + equal jitter
        } else if (statusCode == UNAVAILABLE || statusCode == TRANSPORT_UNAVAILABLE) {
            return delayWithEqualJitter(retryConfig.fastBackoffBaseMs, retryConfig.fastCapBackoffMs, retryConfig.fastPow, attempt);
            // slow + equal jitter
        } else if (statusCode == OVERLOADED || statusCode == CLIENT_RESOURCE_EXHAUSTED) {
            return delayWithEqualJitter(retryConfig.slowBackoffBaseMs, retryConfig.slowCapBackoffMs, retryConfig.slowPow, attempt);
        }
        return -1;
    }

    private long delayWithFullJitter(int baseMs, int capMs, int pow, int attempt) {
        int currentDelay = Math.min(baseMs * ((1 << Math.min(pow, attempt)) - 1), capMs);
        return retryConfig.getJitter(currentDelay);
    }

    private long delayWithEqualJitter(int baseMs, int capMs, int pow, int attempt) {
        int tmp = baseMs * ((1 << Math.min(pow, attempt)) - 1) / 2;
        return Math.min(tmp + retryConfig.getJitter(tmp), capMs);
    }

    private InvocationCallback createCallback(MethodInvocation invocation) {
        return new InvocationCallback() {
            @Nullable
            public Object proceedWithInvocation() throws Throwable {
                return invocation.proceed();
            }

            public Object getTarget() {
                return invocation.getThis();
            }

            public Object[] getArguments() {
                return invocation.getArguments();
            }
        };
    }
}
