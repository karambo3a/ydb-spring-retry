package tech.ydb.retry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.ydb.core.StatusCode;
import tech.ydb.retry.integration.app.User;
import tech.ydb.retry.integration.app.UserApplication;
import tech.ydb.retry.integration.app.UserService;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles({"enabled", "ydb"})
class IdempotentRetryIntegrationTest extends YdbDockerTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void cleanUp() {
        DeterministicErrorChannel.configure();
        DeterministicErrorChannel.resetCounters();
        userService.deleteAll();
    }

    @ParameterizedTest(name = "Idempotent")
    @EnumSource(value = StatusCode.class, names = {"TIMEOUT", "SESSION_EXPIRED", "UNDETERMINED"})
    void shouldRecoverFromIdempotentOnlyError_whenIdempotent(StatusCode code) {
        DeterministicErrorChannel.configure().onError("executeQuery", 1, code);

        userService.saveIdempotent(createUser(1L, "user1", "first1", "last1"));

        assertEquals(2, DeterministicErrorChannel.getCallCount("executeQuery"));
        assertNotNull(userService.findById(1L));
    }

    @ParameterizedTest(name = "Non-idempotent")
    @EnumSource(value = StatusCode.class, names = {"TIMEOUT", "SESSION_EXPIRED", "UNDETERMINED"})
    void shouldNotRetry_fromIdempotentOnlyError_whenNotIdempotent(StatusCode code) {
        DeterministicErrorChannel.configure().onError("executeQuery", 1, code);

        assertThrows(Exception.class, () -> userService.save(createUser(2L, "user2", "first2", "last2")));
        assertEquals(1, DeterministicErrorChannel.getCallCount("executeQuery"));
        assertNull(userService.findById(2L));
    }

    @ParameterizedTest(name = "Idempotent")
    @EnumSource(value = StatusCode.class, names = {"TIMEOUT", "SESSION_EXPIRED", "UNDETERMINED"})
    void shouldRecoverFromIdempotentOnlyCommitError_whenIdempotent(StatusCode code) {
        DeterministicErrorChannel.configure().onError("commitTransaction", 1, code);

        userService.saveIdempotent(createUser(3L, "user3", "first3", "last3"));

        assertEquals(2, DeterministicErrorChannel.getCallCount("commitTransaction"));
        assertNotNull(userService.findById(3L));
    }

    private User createUser(Long id, String username, String firstname, String lastname) {
        return new User(id, username, firstname, lastname);
    }
}
