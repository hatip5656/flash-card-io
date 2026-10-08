package io.flashcard.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class SchedulerHistoryService {

    private final JdbcTemplate jdbc;

    public SchedulerHistoryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public void logRun(String jobName, String status, Instant startedAt, long durationMs,
                       int itemsProcessed, int itemsFailed, String message) {
        jdbc.update("""
            INSERT INTO scheduler_runs (job_name, status, started_at, duration_ms, items_processed, items_failed, message)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            """, jobName, status, java.sql.Timestamp.from(startedAt), (int) durationMs,
            itemsProcessed, itemsFailed, message);
    }

    public List<Map<String, Object>> getHistory(String jobName, int limit) {
        if (jobName != null) {
            return jdbc.queryForList(
                "SELECT * FROM scheduler_runs WHERE job_name = ? ORDER BY created_at DESC LIMIT ?",
                jobName, limit);
        }
        return jdbc.queryForList(
            "SELECT * FROM scheduler_runs ORDER BY created_at DESC LIMIT ?", limit);
    }

    public void cleanup(int keepDays) {
        jdbc.update("DELETE FROM scheduler_runs WHERE created_at < NOW() - INTERVAL '" + keepDays + " days'");
    }
}
