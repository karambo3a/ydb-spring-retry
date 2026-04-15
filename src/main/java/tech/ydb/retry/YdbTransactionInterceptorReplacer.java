package tech.ydb.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.BeanDefinitionRegistryPostProcessor;
import org.springframework.core.Ordered;

public class YdbTransactionInterceptorReplacer implements BeanDefinitionRegistryPostProcessor, Ordered {

    private static final Logger log = LoggerFactory.getLogger(YdbTransactionInterceptorReplacer.class);

    private static final String TRANSACTION_INTERCEPTOR_BEAN_NAME = "transactionInterceptor";

    @Override
    public void postProcessBeanDefinitionRegistry(BeanDefinitionRegistry registry) throws BeansException {
        if (!registry.containsBeanDefinition(TRANSACTION_INTERCEPTOR_BEAN_NAME)) {
            log.debug("BeanDefinition '{}' not found", TRANSACTION_INTERCEPTOR_BEAN_NAME);
            return;
        }

        BeanDefinition existingBd = registry.getBeanDefinition(TRANSACTION_INTERCEPTOR_BEAN_NAME);

        if (YdbTransactionInterceptorFactory.class.getName().equals(existingBd.getBeanClassName())) {
            log.debug("BeanDefinition '{}' is already YdbTransactionInterceptorFactory", TRANSACTION_INTERCEPTOR_BEAN_NAME);
            return;
        }

        AbstractBeanDefinition newBd = BeanDefinitionBuilder
                .genericBeanDefinition(YdbTransactionInterceptorFactory.class)
                .setRole(existingBd.getRole())
                .setAutowireMode(AbstractBeanDefinition.AUTOWIRE_BY_TYPE)
                .getBeanDefinition();

        registry.removeBeanDefinition(TRANSACTION_INTERCEPTOR_BEAN_NAME);
        registry.registerBeanDefinition(TRANSACTION_INTERCEPTOR_BEAN_NAME, newBd);

        log.info("registered YdbTransactionInterceptorFactory as bean '{}'", TRANSACTION_INTERCEPTOR_BEAN_NAME);
    }

    @Override
    public int getOrder() {
        return LOWEST_PRECEDENCE;
    }
}
