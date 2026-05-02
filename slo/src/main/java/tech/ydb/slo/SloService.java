package tech.ydb.slo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tech.ydb.retry.YdbTransactional;

import java.sql.Timestamp;
import java.time.LocalDateTime;

@Service
public class SloService {

    private static final Logger log = LoggerFactory.getLogger(SloService.class);

    private final JdbcTemplate jdbcTemplate;

    public SloService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @YdbTransactional
    public void upsert(String guid, int id, String payloadStr, double payloadDouble,
                       LocalDateTime payloadTimestamp) {
        jdbcTemplate.update(
                "UPSERT INTO slo_test_table (guid, id, payload_str, payload_double, payload_timestamp) " +
                        "VALUES (?, ?, ?, ?, ?)",
                guid, id, payloadStr, payloadDouble, Timestamp.valueOf(payloadTimestamp)
        );
    }

    @YdbTransactional
    public void upsert2(String guid, int id, String payloadStr, double payloadDouble,
                        LocalDateTime payloadTimestamp) {
        jdbcTemplate.update(
                "UPSERT INTO slo_test_table (guid, id, payload_str, payload_double, payload_timestamp) " +
                        "VALUES (?, ?, ?, ?, ?)",
                guid, id, payloadStr, payloadDouble, Timestamp.valueOf(payloadTimestamp)
        );

        jdbcTemplate.update(
                "UPSERT INTO slo_test_table (guid, id, payload_str, payload_double, payload_timestamp) " +
                        "VALUES (?, ?, ?, ?, ?)",
                guid, id + 1, payloadStr, payloadDouble, Timestamp.valueOf(payloadTimestamp)
        );
    }

    @YdbTransactional(readOnly = true)
    public String select(String guid, int id) {
        return jdbcTemplate.queryForObject(
                "SELECT payload_str FROM slo_test_table WHERE guid = ? AND id = ?",
                String.class, guid, id
        );
    }

    @YdbTransactional(readOnly = true)
    public int selectMaxId() {
        Integer result = jdbcTemplate.queryForObject(
                "SELECT MAX(id) FROM slo_test_table", Integer.class
        );
        return result != null ? result : 0;
    }
}
