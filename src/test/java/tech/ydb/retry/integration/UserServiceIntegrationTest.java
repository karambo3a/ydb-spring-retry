package tech.ydb.retry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.ydb.retry.integration.app.SimpleUserRepository;
import tech.ydb.retry.integration.app.User;
import tech.ydb.retry.integration.app.UserApplication;
import tech.ydb.retry.integration.app.UserService;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles({"enabled", "ydb"})
class UserServiceIntegrationTest extends YdbDockerTest {

    @Autowired
    private UserService userService;

    @Autowired
    private SimpleUserRepository userRepository;

    @BeforeEach
    void cleanUp() {
        userService.deleteAll();
    }

    @Test
    void shouldInsertWithTransactionalAndFindById() {
        User user = createUser(1L, "username", "firstname", "lastname");
        userService.insertWithTransactional(user);

        User found = userService.findById(1L);
        assertNotNull(found);
        assertEquals("username", found.getUsername());
        assertEquals("firstname", found.getFirstname());
        assertEquals("lastname", found.getLastname());
    }

    @Test
    void shouldInsertWithYdbTransactional() {
        User user = createUser(2L, "username", "firstname", "lastname");
        userService.insertWithYdbTransactional(user);

        User found = userService.findById(2L);
        assertNotNull(found);
        assertEquals("username", found.getUsername());
    }

    @Test
    void shouldInsertWithCustomRetry() {
        User user = createUser(3L, "username", "firstname", "lastname");
        userService.insertWithCustomRetry(user);

        User found = userService.findById(3L);
        assertNotNull(found);
        assertEquals("username", found.getUsername());
    }

    @Test
    void shouldInsertIdempotent() {
        User user = createUser(4L, "username", "firstname", "lastname");
        userService.insertIdempotent(user);

        User found = userService.findById(4L);
        assertNotNull(found);
        assertEquals("username", found.getUsername());
    }

    @Test
    void shouldFindByLastname() {
        userService.insertWithTransactional(createUser(10L, "username1", "firstname1", "lastname1"));
        userService.insertWithTransactional(createUser(11L, "username2", "firstname2", "lastname1"));
        userService.insertWithTransactional(createUser(12L, "username3", "firstname3", "lastname3"));

        List<User> smiths = userService.findByLastname("lastname1");
        assertEquals(2, smiths.size());

        List<User> jones = userService.findByLastname("lastname3");
        assertEquals(1, jones.size());
    }

    @Test
    void shouldFindByIdWithYdbTransactional() {
        userService.insertWithTransactional(createUser(13L, "username", "firstname", "lastname"));

        User found = userService.findByIdWithYdbTransactional(13L);
        assertNotNull(found);
        assertEquals("username", found.getUsername());
    }

    @Test
    void shouldDeleteAll() {
        userService.insertWithTransactional(createUser(14L, "username1", "firstname1", "lastname1"));
        userService.insertWithTransactional(createUser(15L, "username2", "firstname2", "lastname2"));

        userService.deleteAll();

        assertNull(userService.findById(14L));
        assertNull(userService.findById(15L));
    }

    @Test
    void shouldReturnNullForNonExistentUser() {
        assertNull(userService.findById(999L));
    }

    @Test
    void shouldInsertMultipleUsersAndFindAll() {
        userService.insertWithTransactional(createUser(60L, "username1", "firstname1", "lastname"));
        userService.insertWithTransactional(createUser(61L, "username2", "firstname2", "lastname"));
        userService.insertWithTransactional(createUser(62L, "username3", "firstname3", "lastname"));

        List<User> users = userService.findByLastname("lastname");
        assertEquals(3, users.size());
    }

    private User createUser(Long id, String username, String firstname, String lastname) {
        return new User(id, username, firstname, lastname);
    }
}
