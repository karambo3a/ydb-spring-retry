package tech.ydb.retry.integration.app;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tech.ydb.core.Status;
import tech.ydb.core.StatusCode;
import tech.ydb.jdbc.exception.YdbStatusable;
import tech.ydb.retry.YdbTransactional;

import java.util.List;

@Service
public class UserService {

    private final AtomicInteger attemptCounter = new AtomicInteger();
    private volatile CountDownLatch readGate;
    private final SimpleUserRepository userRepository;

    public UserService(SimpleUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Transactional
    public void insertWithTransactional(User user) {
        userRepository.save(user);
    }

    @YdbTransactional
    public void insertWithYdbTransactional(User user) {
        userRepository.save(user);
    }

    @YdbTransactional(maxAttempts = 3)
    public void insertWithCustomRetry(User user) {
        userRepository.save(user);
    }

    @YdbTransactional(maxAttempts = 5, idempotent = 1)
    public void insertIdempotent(User user) {
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public User findById(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @Transactional(readOnly = true)
    public List<User> findByLastname(String lastname) {
        return userRepository.findByLastname(lastname);
    }

    @YdbTransactional(readOnly = true)
    public User findByIdWithYdbTransactional(Long id) {
        return userRepository.findById(id).orElse(null);
    }

    @YdbTransactional(maxAttempts = 50, idempotent = 1)
    public void updateWithConflict(Long id, String firstname) {
        attemptCounter.incrementAndGet();
        userRepository.findById(id);
        CountDownLatch gate = this.readGate;
        if (gate != null) {
            gate.countDown();
            try {
                gate.await(10, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        userRepository.updateFirstnameById(id, firstname);
    }

    @YdbTransactional(maxAttempts = 5, idempotent = 1)
    public int retryWithSimulatedAbort(User user) {
        userRepository.count();
        userRepository.save(user);
        userRepository.findById(1L);
        int count = attemptCounter.incrementAndGet();
        if (count < 3) {
            throw new SimulatedYdbException(StatusCode.ABORTED);
        }
        return count;
    }

    @Transactional
    public void withoutRetryWithSimulatedAbort(User user) {
        attemptCounter.incrementAndGet();
        userRepository.save(user);
        userRepository.findById(1L);

        throw new SimulatedYdbException(StatusCode.ABORTED);
    }

    @Transactional
    public void insertUserRaw(User user) {
        userRepository.save(user);
    }

    @Transactional(readOnly = true)
    public String getFirstname(Long id) {
        return userRepository.findById(id).orElseThrow().getFirstname();
    }

    @Transactional
    public void deleteAll() {
        userRepository.deleteAll();
    }

    public int getAttemptCountAndReset() {
        return attemptCounter.getAndSet(0);
    }

    public void setReadGate(CountDownLatch readGate) {
        this.readGate = readGate;
    }

    public static class SimulatedYdbException extends RuntimeException implements YdbStatusable {
        private final StatusCode statusCode;

        public SimulatedYdbException(StatusCode statusCode) {
            this.statusCode = statusCode;
        }

        @Override
        public Status getStatus() {
            return Status.of(statusCode);
        }
    }
}
