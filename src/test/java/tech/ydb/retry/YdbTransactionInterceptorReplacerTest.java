package tech.ydb.retry;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

class YdbTransactionInterceptorReplacerTest {

    @Test
    void shouldHaveLowestPrecedenceOrder() {
        YdbTransactionInterceptorReplacer pp = new YdbTransactionInterceptorReplacer();
        assertEquals(LOWEST_PRECEDENCE, pp.getOrder());
    }

    @Test
    void shouldSkipWhenTransactionInterceptorNotFound() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        YdbTransactionInterceptorReplacer pp = new YdbTransactionInterceptorReplacer();
        pp.postProcessBeanDefinitionRegistry(beanFactory);

        assertFalse(beanFactory.containsBeanDefinition("transactionInterceptor"));
    }

    @Test
    void shouldSkipWhenAlreadyYdbTransactionInterceptorFactory() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(YdbTransactionInterceptorFactory.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("transactionInterceptor", beanDefinition);

        YdbTransactionInterceptorReplacer pp = new YdbTransactionInterceptorReplacer();
        pp.postProcessBeanDefinitionRegistry(beanFactory);

        String beanClassName = beanFactory.getBeanDefinition("transactionInterceptor").getBeanClassName();
        assertEquals(YdbTransactionInterceptorFactory.class.getName(), beanClassName);
    }

    @Test
    void shouldReplaceStandardTransactionInterceptorBeanDefinition() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(TransactionInterceptor.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("transactionInterceptor", beanDefinition);

        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        YdbRetryProperties properties = new YdbRetryProperties();
        TransactionAttributeSource tas = new AnnotationTransactionAttributeSource();

        beanFactory.registerSingleton("transactionManager", txManager);
        beanFactory.registerSingleton(YdbRetryProperties.class.getName(), properties);
        beanFactory.registerSingleton(TransactionAttributeSource.class.getName(), tas);

        YdbTransactionInterceptorReplacer pp = new YdbTransactionInterceptorReplacer();
        pp.postProcessBeanDefinitionRegistry(beanFactory);

        beanDefinition = beanFactory.getBeanDefinition("transactionInterceptor");
        assertEquals(YdbTransactionInterceptorFactory.class.getName(), beanDefinition.getBeanClassName());

        Object bean = beanFactory.getBean("transactionInterceptor");
        assertInstanceOf(YdbTransactionInterceptor.class, bean);
    }

    @Test
    void shouldRegisterInterceptorWithCorrectProperties() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        BeanDefinition beanDefinition = BeanDefinitionBuilder.genericBeanDefinition(TransactionInterceptor.class).getBeanDefinition();
        beanFactory.registerBeanDefinition("transactionInterceptor", beanDefinition);

        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        YdbRetryProperties properties = new YdbRetryProperties();
        properties.setEnabled(false);
        properties.setMaxAttempts(3);
        TransactionAttributeSource tas = new AnnotationTransactionAttributeSource();

        beanFactory.registerSingleton("transactionManager", txManager);
        beanFactory.registerSingleton(YdbRetryProperties.class.getName(), properties);
        beanFactory.registerSingleton(TransactionAttributeSource.class.getName(), tas);

        YdbTransactionInterceptorReplacer pp = new YdbTransactionInterceptorReplacer();
        pp.postProcessBeanDefinitionRegistry(beanFactory);

        Object bean = beanFactory.getBean("transactionInterceptor");
        assertInstanceOf(YdbTransactionInterceptor.class, bean);
    }
}
