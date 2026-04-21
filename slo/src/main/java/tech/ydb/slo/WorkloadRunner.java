package tech.ydb.slo;

import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class WorkloadRunner {

    private static final Logger log = LoggerFactory.getLogger(WorkloadRunner.class);
    private static final int NUM_WORKERS = 10;
    private static final int WINDOW_MS = 100;
    private static final int SHUTDOWN_TIMEOUT_SEC = 30;

    private final SloService service;

    public WorkloadRunner(SloService service) {
        this.service = service;
    }

    public void run(SloConfig config, String tableName) {
        Metrics.init(config.otlpEndpoint(), config.reportPeriod());

        AtomicLong maxId = new AtomicLong(service.selectMaxId(tableName) + 1);
        log.info("Init maxId={}, readRPS={}, writeRPS={}, time={}s",
                maxId.get(), config.readRps(), config.writeRps(), config.time());

        int writePermitsPerWindow = Math.max(1, config.writeRps() / NUM_WORKERS);
        int readPermitsPerWindow = Math.max(1, config.readRps() / NUM_WORKERS);

        Semaphore writeSemaphore = new Semaphore(writePermitsPerWindow);
        Semaphore readSemaphore = new Semaphore(readPermitsPerWindow);

        ScheduledExecutorService rateRefillScheduler = Executors.newSingleThreadScheduledExecutor(
                r -> { Thread t = new Thread(r, "rate-refill"); t.setDaemon(true); return t; }
        );
        rateRefillScheduler.scheduleAtFixedRate(() -> {
            writeSemaphore.drainPermits();
            writeSemaphore.release(writePermitsPerWindow);
            readSemaphore.drainPermits();
            readSemaphore.release(readPermitsPerWindow);
        }, WINDOW_MS, WINDOW_MS, TimeUnit.MILLISECONDS);

        int totalWorkers = NUM_WORKERS * 2;
        CountDownLatch finished = new CountDownLatch(totalWorkers);
        AtomicBoolean stopFlag = new AtomicBoolean(false);

        ExecutorService executor = Executors.newFixedThreadPool(totalWorkers, r -> {
            Thread t = new Thread(r);
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < NUM_WORKERS; i++) {
            executor.submit(() -> writeJob(tableName, stopFlag, writeSemaphore, maxId, finished));
            executor.submit(() -> readJob(tableName, stopFlag, readSemaphore, maxId, finished));
        }

        try {
            Thread.sleep(config.time() * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("Test duration elapsed, waiting up to {}s for in-flight requests...",
                SHUTDOWN_TIMEOUT_SEC);
        stopFlag.set(true);

        try {
            finished.await(SHUTDOWN_TIMEOUT_SEC, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        rateRefillScheduler.shutdownNow();
        executor.shutdownNow();

        Metrics.shutdown();
        logStats();

        log.info("Workload complete.");
    }

    private void writeJob(String tableName, AtomicBoolean stop, Semaphore semaphore,
                          AtomicLong maxId, CountDownLatch finished) {
        try {
            while (!stop.get()) {
                if (semaphore.tryAcquire()) {
                    doWrite(tableName, maxId);
                } else {
                    Thread.sleep(WINDOW_MS / 2);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finished.countDown();
        }
    }

    private void readJob(String tableName, AtomicBoolean stop, Semaphore semaphore,
                         AtomicLong maxId, CountDownLatch finished) {
        try {
            while (!stop.get()) {
                if (semaphore.tryAcquire()) {
                    doRead(tableName, maxId);
                } else {
                    Thread.sleep(WINDOW_MS / 2);
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            finished.countDown();
        }
    }

    private void doWrite(String tableName, AtomicLong maxId) {
        long id = maxId.incrementAndGet();
        long hash = SloService.numericHash(id);
        String payloadStr = SloService.randomString(ThreadLocalRandom.current(), 20, 40);
        double payloadDouble = ThreadLocalRandom.current().nextDouble();
        long payloadHash = SloService.numericHash(ThreadLocalRandom.current().nextLong());

        Metrics.inflightInc("write");
        long start = System.nanoTime();
        try {
            service.upsert(tableName, id, hash, payloadStr, payloadDouble, payloadHash);
            long elapsed = System.nanoTime() - start;
            Metrics.recordOk("write", elapsed);
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            Metrics.recordNotOk("write", elapsed);
            log.debug("Write failed id={}: {}", id, e.getMessage());
        } finally {
            Metrics.inflightDec("write");
        }
    }

    private void doRead(String tableName, AtomicLong maxId) {
        long currentMax = maxId.get();
        if (currentMax <= 1) {
            return;
        }
        long id = ThreadLocalRandom.current().nextLong(1, currentMax);
        long hash = SloService.numericHash(id);

        Metrics.inflightInc("read");
        long start = System.nanoTime();
        try {
            service.select(tableName, hash, id);
            long elapsed = System.nanoTime() - start;
            Metrics.recordOk("read", elapsed);
        } catch (Exception e) {
            long elapsed = System.nanoTime() - start;
            Metrics.recordNotOk("read", elapsed);
            log.debug("Read failed id={}: {}", id, e.getMessage());
        } finally {
            Metrics.inflightDec("read");
        }
    }

    private void logStats() {
        long readOk = Metrics.getOks("read");
        long readErr = Metrics.getNotOks("read");
        long writeOk = Metrics.getOks("write");
        long writeErr = Metrics.getNotOks("write");
        log.info("=== SLO Results ===");
        log.info("Read:       ok={}, not_ok={}, error_rate={}%",
                readOk, readErr, errorRate(readOk, readErr));
        log.info("Write:      ok={}, not_ok={}, error_rate={}%",
                writeOk, writeErr, errorRate(writeOk, writeErr));
        long totalOk = readOk + writeOk;
        long totalErr = readErr + writeErr;
        log.info("Total:      ok={}, not_ok={}", totalOk, totalErr);
    }

    private static String errorRate(long ok, long err) {
        long total = ok + err;
        if (total == 0) return "0.00";
        return String.format("%.2f", (double) err / total * 100);
    }
}
