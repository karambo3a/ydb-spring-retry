package tech.ydb.retry;

import org.springframework.beans.factory.FactoryBean;
import org.springframework.lang.Nullable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.interceptor.TransactionAttributeSource;

public class YdbTransactionInterceptorFactory implements FactoryBean<YdbTransactionInterceptor> {

    private YdbRetryProperties retryProperties;
    private TransactionAttributeSource transactionAttributeSource;

    @Nullable
    private PlatformTransactionManager transactionManager;

    public void setRetryProperties(YdbRetryProperties retryProperties) {
        this.retryProperties = retryProperties;
    }

    public void setTransactionAttributeSource(TransactionAttributeSource transactionAttributeSource) {
        this.transactionAttributeSource = transactionAttributeSource;
    }

    public void setTransactionManager(@Nullable PlatformTransactionManager transactionManager) {
        this.transactionManager = transactionManager;
    }

    @Override
    public YdbTransactionInterceptor getObject() {
        YdbTransactionInterceptor interceptor = new YdbTransactionInterceptor(
                retryProperties.toConfig(),
                Thread::sleep
        );
        interceptor.setTransactionAttributeSource(transactionAttributeSource);
        if (transactionManager != null) {
            interceptor.setTransactionManager(transactionManager);
        }
        return interceptor;
    }

    @Override
    public Class<?> getObjectType() {
        return YdbTransactionInterceptor.class;
    }
}
