package tech.ydb.slo;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.DoubleHistogram;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import tech.ydb.core.Status;
import tech.ydb.jdbc.exception.YdbStatusable;

import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@Component
public class SloRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(SloRunner.class);

    private final JdbcTemplate jdbcTemplate;
    private final SloService sloService;
    private final SloConfig config;
    private final LongCounter operationsCounter;
    private final DoubleHistogram durationHistogram;

    private final AtomicInteger maxId = new AtomicInteger(0);

    private static final AttributeKey<String> REF_KEY = AttributeKey.stringKey("ref");
    private static final AttributeKey<String> OP_TYPE_KEY = AttributeKey.stringKey("operation_type");
    private static final AttributeKey<String> STATUS_KEY = AttributeKey.stringKey("status");
    private static final AttributeKey<String> ERROR_TYPE_KEY = AttributeKey.stringKey("error_type");

    public SloRunner(JdbcTemplate jdbcTemplate, SloService sloService, SloConfig config,
                     OpenTelemetry openTelemetry) {
        this.jdbcTemplate = jdbcTemplate;
        this.sloService = sloService;
        this.config = config;

        Meter meter = openTelemetry.getMeter("slo");
        this.operationsCounter = meter.counterBuilder("slo.operations")
                .setDescription("Total number of SLO operations")
                .build();
        this.durationHistogram = meter.histogramBuilder("slo.operation.duration.seconds")
                .setDescription("SLO operation latency")
                .setUnit("s")
                .setExplicitBucketBoundariesAdvice(
                        List.of(
                                0.001, 0.005, 0.01, 0.025, 0.05, 0.1, 0.25, 0.5,
                                1.0, 2.5, 5.0, 10.0, 30.0
                        )
                )
                .build();
    }

    @Override
    public void run(String... args) {
        log.info("SLO runner starting with ref={}", config.getRef());
        createTable();
        seedData();
        runWorkload();
        log.info("SLO workload completed, app stays alive for metrics scraping");
    }

    private void createTable() {
        for (int attempt = 0; attempt < 10; attempt++) {
            try {
                jdbcTemplate.execute(
                        "CREATE TABLE slo_test_table (" +
                                "guid Text, " +
                                "id Int32, " +
                                "payload_str Text, " +
                                "payload_double Double, " +
                                "payload_timestamp Timestamp, " +
                                "PRIMARY KEY (guid, id)" +
                                ")"
                );
                log.info("Created table slo_test_table");
                return;
            } catch (Exception e) {
                String msg = e.getMessage();
                if (msg != null && (msg.contains("already exists") || msg.contains("ALREADY_EXISTS") || msg.contains("path exist"))) {
                    log.info("Table slo_test_table already exists");
                    return;
                }
                log.warn("Failed to create table (attempt {}/10): {}", attempt + 1, msg);
                if (attempt == 9) {
                    log.warn("Max attempts reached, proceeding anyway");
                    return;
                }
                try {
                    Thread.sleep((attempt + 1) * 1000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    throw new RuntimeException(ie);
                }
            }
        }
    }

    private void seedData() {
        log.info("Seeding {} initial rows...", config.getInitialDataCount());
        int success = 0;
        for (int i = 1; i <= config.getInitialDataCount(); i++) {
            try {
                String guid = guidFromInt(i);
                String payload = randomString();
                sloService.upsert(guid, i, payload, Math.random(), LocalDateTime.now());
                success++;
            } catch (Exception e) {
                log.warn("Failed to seed row {}: {}", i, e.getMessage());
            }
        }
        maxId.set(config.getInitialDataCount());
        log.info("Seeded {}/{} rows", success, config.getInitialDataCount());
    }

    private void runWorkload() {
        String ref = config.getRef();
        log.info("Starting workload: ref={}, readRps={}, writeRps={}, time={}s",
                ref, config.getReadRps(), config.getWriteRps(), config.getRunTimeSeconds());

        ScheduledExecutorService scheduler = Executors.newScheduledThreadPool(2);
        ExecutorService workers = Executors.newFixedThreadPool(20);

        int intervalMs = 100;
        int readsPerInterval = Math.max(1, config.getReadRps() / 10);
        int writesPerInterval = Math.max(1, config.getWriteRps() / 10);

        ScheduledFuture<?> readFuture = scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < readsPerInterval; i++) {
                workers.submit(() -> doRead(ref));
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        ScheduledFuture<?> writeFuture = scheduler.scheduleAtFixedRate(() -> {
            for (int i = 0; i < writesPerInterval; i++) {
                workers.submit(() -> doWrite(ref));
            }
        }, 0, intervalMs, TimeUnit.MILLISECONDS);

        scheduler.schedule(() -> {
            readFuture.cancel(false);
            writeFuture.cancel(false);
            scheduler.shutdown();
            workers.shutdown();
            log.info("Workload finished for ref={}", ref);
        }, config.getRunTimeSeconds(), TimeUnit.SECONDS);
    }

    private void doWrite(String ref) {
        int id = maxId.incrementAndGet();
        String guid = guidFromInt(id);
        String payload = randomString();
        double payloadDouble = Math.random();
        LocalDateTime ts = LocalDateTime.now();

        long start = System.nanoTime();
        try {
            sloService.upsert2(guid, id, payload, payloadDouble, ts);
            recordLatency(ref, "write", "success", "none", System.nanoTime() - start);
            incrementCounter(ref, "write", "success", "none");
        } catch (Exception e) {
            String errorType = extractErrorType(e);
            recordLatency(ref, "write", "failure", errorType, System.nanoTime() - start);
            incrementCounter(ref, "write", "failure", errorType);
            log.debug("Write failed: [{}] {}", errorType, e.getMessage());
        }
    }

    private void doRead(String ref) {
        int currentMax = maxId.get();
        if (currentMax < 1) {
            return;
        }
        int id = ThreadLocalRandom.current().nextInt(1, currentMax + 1);
        String guid = guidFromInt(id);

        long start = System.nanoTime();
        try {
            sloService.select(guid, id);
            recordLatency(ref, "read", "success", "none", System.nanoTime() - start);
            incrementCounter(ref, "read", "success", "none");
        } catch (Exception e) {
            String errorType = extractErrorType(e);
            recordLatency(ref, "read", "failure", errorType, System.nanoTime() - start);
            incrementCounter(ref, "read", "failure", errorType);
            log.debug("Read failed: [{}] {}", errorType, e.getMessage());
        }
    }

    private void incrementCounter(String ref, String operationType, String status, String errorType) {
        Attributes attrs = Attributes.builder()
                .put(REF_KEY, ref)
                .put(OP_TYPE_KEY, operationType)
                .put(STATUS_KEY, status)
                .put(ERROR_TYPE_KEY, errorType)
                .build();
        operationsCounter.add(1, attrs);
    }

    private void recordLatency(String ref, String operationType, String status, String errorType,
                               long durationNanos) {
        Attributes attrs = Attributes.builder()
                .put(REF_KEY, ref)
                .put(OP_TYPE_KEY, operationType)
                .put(STATUS_KEY, status)
                .put(ERROR_TYPE_KEY, errorType)
                .build();
        durationHistogram.record(durationNanos / 1_000_000_000.0, attrs);
    }

    static String extractErrorType(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof YdbStatusable statusable) {
                Status status = statusable.getStatus();
                if (status != null && status.getCode() != null) {
                    return status.getCode().name();
                }
            }
            current = current.getCause();
        }
        return throwable.getClass().getSimpleName();
    }

    static String guidFromInt(int value) {
        try {
            byte[] intBytes = new byte[4];
            intBytes[0] = (byte) (value >> 24);
            intBytes[1] = (byte) (value >> 16);
            intBytes[2] = (byte) (value >> 8);
            intBytes[3] = (byte) value;
            byte[] hash = MessageDigest.getInstance("SHA-1").digest(intBytes);
            StringBuilder sb = new StringBuilder(36);
            for (int i = 0; i < 16; i++) {
                sb.append(String.format("%02x", hash[i]));
                if (i == 3 || i == 5 || i == 7 || i == 9) {
                    sb.append('-');
                }
            }
            return sb.toString();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    static String randomString() {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        int len = 20 + rng.nextInt(21);
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) (32 + rng.nextInt(95)));
        }
        return sb.toString();
    }
}
