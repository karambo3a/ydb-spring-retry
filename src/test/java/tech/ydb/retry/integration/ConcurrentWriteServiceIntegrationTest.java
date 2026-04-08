package tech.ydb.retry.integration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import tech.ydb.retry.integration.app.User;
import tech.ydb.retry.integration.app.UserApplication;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import tech.ydb.retry.integration.app.UserService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@SpringBootTest(classes = UserApplication.class)
@ActiveProfiles({"enabled", "ydb"})
class ConcurrentWriteServiceIntegrationTest extends YdbDockerTest {

    @Autowired
    private UserService userService;

    @BeforeEach
    void cleanUp() {
        userService.deleteAll();
        userService.getAttemptCountAndReset();
        userService.setReadGate(null);
    }

    @Test
    void shouldVerifyInterceptorRetriesOnSimulatedAbort() {
        int result = userService.retryWithSimulatedAbort(createUser(1L, "user", "firstname", "lastname"));

        int totalAttempts = userService.getAttemptCountAndReset();
        assertEquals(3, totalAttempts);
        assertEquals(3, result);
    }

    @Test
    void shouldRetryViaYdbTransactionalOnConcurrentUpdates() throws Exception {
        userService.insertUserRaw(createUser(1L, "user", "firstname", "lastname"));

        int threadCount = 3;
        CountDownLatch readGate = new CountDownLatch(threadCount);
        userService.setReadGate(readGate);

        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    userService.updateWithConflict(1L, "newFirstname" + idx);
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        assertTrue(doneLatch.await(60, TimeUnit.SECONDS));

        int totalAttempts = userService.getAttemptCountAndReset();

        assertTrue(totalAttempts > threadCount);
        assertEquals(threadCount, successCount.get());

        String firstname = userService.getFirstname(1L);
        assertTrue(firstname.startsWith("newFirstname"));

        executor.shutdown();
    }

    @Test
    void shouldInsertConcurrentlyWithoutConflicts() throws Exception {
        int threadCount = 10;
        CountDownLatch readyLatch = new CountDownLatch(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch doneLatch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger();

        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int idx = i;
            executor.submit(() -> {
                try {
                    readyLatch.countDown();
                    startLatch.await();
                    userService.insertUserRaw(
                            createUser(1000L + idx, "username" + idx, "firstname" + idx, "lastname" + idx)
                    );
                    successCount.incrementAndGet();
                } catch (Exception ignored) {
                } finally {
                    doneLatch.countDown();
                }
            });
        }

        readyLatch.await();
        startLatch.countDown();
        assertTrue(doneLatch.await(30, TimeUnit.SECONDS));

        assertEquals(threadCount, successCount.get());

        executor.shutdown();
    }

    private User createUser(Long id, String username, String firstname, String lastname) {
        return new User(id, username, firstname, lastname);
    }
}
