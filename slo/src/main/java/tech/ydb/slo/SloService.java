package tech.ydb.slo;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Random;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import tech.ydb.retry.YdbTransactional;

@Service
public class SloService {

    private static final Logger log = LoggerFactory.getLogger(SloService.class);

    private static final int INITIAL_DATA_COUNT = 1000;
    private static final int MIN_PARTITIONS = 6;
    private static final int MAX_PARTITIONS = 1000;
    private static final int PARTITION_SIZE_MB = 1;

    private static final ThreadLocal<MessageDigest> SHA1 = ThreadLocal.withInitial(() -> {
        try {
            return MessageDigest.getInstance("SHA-1");
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    });

    private final JdbcTemplate jdbc;

    public SloService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional
    public void createTable(String tableName) {
        try {
            jdbc.execute("DROP TABLE " + tableName);
            log.info("Dropped existing table '{}'", tableName);
        } catch (Exception e) {
            log.debug("Table '{}' did not exist, creating new", tableName);
        }
        jdbc.execute(
                "CREATE TABLE " + tableName + " (" +
                        "id                Int64, " +
                        "hash              Int64, " +
                        "payload_str       Text, " +
                        "payload_double    Double, " +
                        "payload_timestamp Timestamp, " +
                        "payload_hash      Int64, " +
                        "PRIMARY KEY (hash, id)" +
                        ")"
        );
        try {
            jdbc.execute("ALTER TABLE " + tableName + " SET (AUTO_PARTITIONING_BY_SIZE = ENABLED)");
            jdbc.execute("ALTER TABLE " + tableName + " SET (AUTO_PARTITIONING_BY_LOAD = ENABLED)");
            jdbc.execute("ALTER TABLE " + tableName +
                    " SET (AUTO_PARTITIONING_MIN_PARTITIONS_COUNT = " + MIN_PARTITIONS + ")");
            jdbc.execute("ALTER TABLE " + tableName +
                    " SET (AUTO_PARTITIONING_MAX_PARTITIONS_COUNT = " + MAX_PARTITIONS + ")");
            jdbc.execute("ALTER TABLE " + tableName +
                    " SET (AUTO_PARTITIONING_PARTITION_SIZE_MB = " + PARTITION_SIZE_MB + ")");
        } catch (Exception e) {
            log.warn("Auto-partitioning settings skipped: {}", e.getMessage());
        }
    }

    @Transactional
    public void dropTable(String tableName) {
        jdbc.execute("DROP TABLE " + tableName);
    }

    @YdbTransactional
    public void upsert(String tableName, long id, long hash, String payloadStr,
                       double payloadDouble, long payloadHash) {
        jdbc.update(
                "UPSERT INTO " + tableName +
                        " (id, hash, payload_str, payload_double, payload_timestamp, payload_hash) " +
                        "VALUES (?, ?, ?, ?, CurrentUtcTimestamp(), ?)",
                id, hash, payloadStr, payloadDouble, payloadHash
        );
    }

    @YdbTransactional
    public boolean select(String tableName, long hash, long id) {
        var results = jdbc.queryForList(
                "SELECT id FROM " + tableName + " WHERE hash = ? AND id = ?",
                Long.class, hash, id
        );
        return !results.isEmpty();
    }

    @Transactional(readOnly = true)
    public long selectMaxId(String tableName) {
        Long max = jdbc.queryForObject(
                "SELECT MAX(id) FROM " + tableName, Long.class);
        return max != null ? max : 0;
    }

    public void seedData(String tableName) {
        log.info("Seeding {} initial rows into '{}'...", INITIAL_DATA_COUNT, tableName);
        Random rnd = ThreadLocalRandom.current();
        for (int i = 1; i <= INITIAL_DATA_COUNT; i++) {
            long hash = numericHash(i);
            String payloadStr = randomString(rnd, 20, 40);
            double payloadDouble = rnd.nextDouble();
            long payloadHash = numericHash(rnd.nextLong());
            upsert(tableName, i, hash, payloadStr, payloadDouble, payloadHash);
        }
        log.info("Seeded {} rows.", INITIAL_DATA_COUNT);
    }

    static long numericHash(long value) {
        MessageDigest sha1 = SHA1.get();
        sha1.reset();
        byte[] hash = sha1.digest(longToBytes(value));
        return Math.abs(bytesToLong(hash));
    }

    static String randomString(Random rnd, int minLen, int maxLen) {
        int len = rnd.nextInt(maxLen - minLen + 1) + minLen;
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) {
            sb.append((char) (rnd.nextInt(95) + 32));
        }
        return sb.toString();
    }

    private static byte[] longToBytes(long value) {
        byte[] result = new byte[8];
        for (int i = 7; i >= 0; i--) {
            result[i] = (byte) (value & 0xFF);
            value >>= 8;
        }
        return result;
    }

    private static long bytesToLong(byte[] b) {
        long result = 0;
        for (int i = 0; i < 8; i++) {
            result <<= 8;
            result |= (b[i] & 0xFF);
        }
        return result;
    }
}
