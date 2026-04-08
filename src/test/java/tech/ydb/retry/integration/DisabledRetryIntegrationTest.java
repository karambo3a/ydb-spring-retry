package tech.ydb.retry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.ydb.retry.integration.app.User;
import tech.ydb.retry.integration.app.UserApplication;
import tech.ydb.retry.integration.app.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles(value = {"disabled", "ydb"})
class DisabledRetryIntegrationTest extends YdbDockerTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void cleanUp() {
        userService.deleteAll();
        userService.getAttemptCountAndReset();
    }

    @Test
    void shouldNotRetryWhenRetryDisabled() {
        assertThrows(UserService.SimulatedYdbException.class,
                () -> userService.withoutRetryWithSimulatedAbort(createUser(1L, "username", "firstname", "lastname")));

        int totalAttempts = userService.getAttemptCountAndReset();
        assertEquals(1, totalAttempts);
    }

    private User createUser(Long id, String username, String firstname, String lastname) {
        return new User(id, username, firstname, lastname);
    }
}
