package tech.ydb.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.BeanFactoryAware;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.PriorityOrdered;
import org.springframework.lang.Nullable;
import org.springframework.transaction.interceptor.TransactionAspectSupport;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.util.ReflectionUtils;

import java.lang.reflect.Field;

public class YdbTransactionInterceptorPostProcessor implements BeanPostProcessor, PriorityOrdered, BeanFactoryAware {

    private static final Logger log = LoggerFactory.getLogger(YdbTransactionInterceptorPostProcessor.class);
    private BeanFactory beanFactory;

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) throws BeansException {
        if (bean instanceof TransactionInterceptor standardInterceptor &&
                !(bean instanceof YdbTransactionInterceptor)) {

            log.debug("REPLACING TransactionInterceptor with YdbTransactionInterceptor for bean: {}", beanName);

            YdbTransactionInterceptor ydbInterceptor = new YdbTransactionInterceptor();

            ydbInterceptor.setTransactionAttributeSource(
                    standardInterceptor.getTransactionAttributeSource());
            ydbInterceptor.setTransactionManager(
                    standardInterceptor.getTransactionManager());
            ydbInterceptor.setTransactionManagerBeanName(resolveTransactionManagerBeanName(standardInterceptor));
            ydbInterceptor.setBeanFactory(beanFactory);
            ydbInterceptor.afterPropertiesSet();

            log.debug("REPLACEMENT COMPLETE");
            return ydbInterceptor;
        }

        return bean;
    }

    @Override
    public int getOrder() {
        return HIGHEST_PRECEDENCE;
    }

    @Override
    public void setBeanFactory(BeanFactory beanFactory) throws BeansException {
        this.beanFactory = beanFactory;
    }

    @Nullable
    private String resolveTransactionManagerBeanName(TransactionInterceptor standardInterceptor) {
        Field field = ReflectionUtils.findField(TransactionAspectSupport.class, "transactionManagerBeanName");
        if (field == null) {
            return null;
        }
        ReflectionUtils.makeAccessible(field);
        return (String) ReflectionUtils.getField(field, standardInterceptor);
    }
}