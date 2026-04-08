package tech.ydb.retry.integration;

import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tech.ydb.test.junit5.YdbHelperExtension;

public abstract class YdbDockerTest {

    @RegisterExtension
    static final YdbHelperExtension ydb = new YdbHelperExtension();

    @DynamicPropertySource
    static void propertySource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () ->
                "jdbc:ydb:" + (ydb.useTls() ? "grpcs://" : "grpc://") +
                        ydb.endpoint() + ydb.database() +
                        (ydb.authToken() != null ? "?token=" + ydb.authToken() : "")
        );
    }
}
