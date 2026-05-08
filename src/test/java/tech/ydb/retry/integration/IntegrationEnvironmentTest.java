package tech.ydb.retry.integration;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

import org.junit.jupiter.api.Test;
import org.testcontainers.DockerClientFactory;

@YdbIntegrationTest
class IntegrationEnvironmentTest {

    @Test
    void dockerShouldBeAvailableForIntegrationTests() {
        try {
            assertTrue(
                    DockerClientFactory.instance().isDockerAvailable(),
                    "Docker/Testcontainers must be available for integration tests");
        } catch (Throwable throwable) {
            fail("Docker/Testcontainers must be available for integration tests", throwable);
        }
    }
}
