package tech.ydb.retry;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureBefore;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.transaction.TransactionAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.interceptor.TransactionInterceptor;

@Configuration
@AutoConfigureBefore(TransactionAutoConfiguration.class)
@ConditionalOnClass(TransactionInterceptor.class)
@ConditionalOnProperty(name = "ydb.transaction.retry.enabled", matchIfMissing = true)
public class YdbTransactionAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(YdbTransactionAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public static YdbTransactionInterceptorPostProcessor ydbTransactionInterceptorPostProcessor() {
        log.debug("creating YdbTransactionInterceptorPostProcessor bean");
        return new YdbTransactionInterceptorPostProcessor();
    }
}