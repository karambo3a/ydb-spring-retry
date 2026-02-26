package tech.ydb.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.transaction.interceptor.TransactionInterceptor;

public class YdbTransactionInterceptorPostProcessor implements BeanPostProcessor, PriorityOrdered {

    private static final Logger log = LoggerFactory.getLogger(YdbTransactionInterceptorPostProcessor.class);

    static {
        log.info("YdbTransactionInterceptorPostProcessor CLASS LOADED");
    }

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TransactionInterceptor standardInterceptor &&
                !(bean instanceof YdbTransactionInterceptor)) {

            log.info("REPLACING TransactionInterceptor with YdbTransactionInterceptor for bean: {}", beanName);

            YdbTransactionInterceptor ydbInterceptor = new YdbTransactionInterceptor();

            ydbInterceptor.setTransactionAttributeSource(
                    standardInterceptor.getTransactionAttributeSource());
            ydbInterceptor.setTransactionManager(
                    standardInterceptor.getTransactionManager());

            log.info("REPLACEMENT COMPLETE");
            return ydbInterceptor;
        }

        return bean;
    }

    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}