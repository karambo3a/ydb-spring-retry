package tech.ydb.retry;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.springframework.core.Ordered.LOWEST_PRECEDENCE;

class YdbTransactionalInterceptorPostProcessorTest {

    private final YdbRetryProperties defaultProperties = new YdbRetryProperties();

    @Test
    void shouldReturnNonTransactionInterceptorBeanUnmodified() {
        YdbTransactionInterceptorPostProcessor pp = new YdbTransactionInterceptorPostProcessor(defaultProperties);
        Object bean = new Object();
        assertSame(bean, pp.postProcessAfterInitialization(bean, "someBean"));
    }

    @Test
    void shouldNotReWrapYdbTransactionInterceptor() {
        YdbTransactionInterceptorPostProcessor pp = new YdbTransactionInterceptorPostProcessor(defaultProperties);
        YdbTransactionInterceptor ydbInterceptor = new YdbTransactionInterceptor();
        Object result = pp.postProcessAfterInitialization(ydbInterceptor, "ydbInterceptor");
        assertSame(ydbInterceptor, result);
    }

    @Test
    void shouldReplaceStandardTransactionInterceptor() {
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("txManager", txManager);

        YdbTransactionInterceptorPostProcessor pp = new YdbTransactionInterceptorPostProcessor(defaultProperties);
        pp.setBeanFactory(beanFactory);

        TransactionInterceptor standardInterceptor = new TransactionInterceptor();
        standardInterceptor.setTransactionAttributeSource(new AnnotationTransactionAttributeSource());
        standardInterceptor.setTransactionManager(txManager);
        standardInterceptor.setBeanFactory(beanFactory);
        standardInterceptor.afterPropertiesSet();

        Object result = pp.postProcessAfterInitialization(standardInterceptor, "txInterceptor");

        assertInstanceOf(YdbTransactionInterceptor.class, result);
        assertNotSame(standardInterceptor, result);
    }

    @Test
    void shouldPreserveTransactionAttributeSource() {
        PlatformTransactionManager txManager = Mockito.mock(PlatformTransactionManager.class);
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("txManager", txManager);

        YdbTransactionInterceptorPostProcessor pp = new YdbTransactionInterceptorPostProcessor(defaultProperties);
        pp.setBeanFactory(beanFactory);

        AnnotationTransactionAttributeSource tas = new AnnotationTransactionAttributeSource();
        TransactionInterceptor standardInterceptor = new TransactionInterceptor();
        standardInterceptor.setTransactionAttributeSource(tas);
        standardInterceptor.setTransactionManager(txManager);
        standardInterceptor.setBeanFactory(beanFactory);
        standardInterceptor.afterPropertiesSet();

        YdbTransactionInterceptor replacement = (YdbTransactionInterceptor)
                pp.postProcessAfterInitialization(standardInterceptor, "txInterceptor");

        assertSame(tas, replacement.getTransactionAttributeSource());
    }

    @Test
    void shouldHaveLowestPrecedenceOrder() {
        YdbTransactionInterceptorPostProcessor pp = new YdbTransactionInterceptorPostProcessor(defaultProperties);
        assertEquals(LOWEST_PRECEDENCE, pp.getOrder());
    }
}
