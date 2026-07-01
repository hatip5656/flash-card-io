package io.flashcard.repository;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;

@Repository
public class CandidateRepository {

    private final JdbcTemplate jdbc;

    public CandidateRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> listCandidates(String status, String level, int limit) {
        StringBuilder query = new StringBuilder("SELECT * FROM candidate_words WHERE status = ?");
        List<Object> params = new ArrayList<>();
        params.add(status);

        if (level != null) {
            query.append(" AND cefr_level = ?");
            params.add(level);
        }
        query.append(" ORDER BY discovered_at DESC LIMIT ?");
        params.add(limit);

        List<Map<String, Object>> words = jdbc.queryForList(query.toString(), params.toArray());
        List<Integer> wordIds = words.stream().map(w -> ((Number) w.get("id")).intValue()).toList();

        Map<Integer, List<Map<String, Object>>> sentMap = new HashMap<>();
        if (!wordIds.isEmpty()) {
            jdbc.queryForList(
                "SELECT candidate_id, estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = ANY(?) ORDER BY candidate_id, sort_order",
                (Object) wordIds.toArray(new Integer[0]))
                .forEach(s -> sentMap.computeIfAbsent(((Number) s.get("candidate_id")).intValue(), k -> new ArrayList<>()).add(s));
        }

        List<Map<String, Object>> result = words.stream().map(w -> {
            Map<String, Object> entry = new HashMap<>(w);
            entry.put("sentences", sentMap.getOrDefault(((Number) w.get("id")).intValue(), List.of()));
            return entry;
        }).toList();

        List<Map<String, Object>> stats = jdbc.queryForList("""
            SELECT status, cefr_level, COUNT(*) as count
            FROM candidate_words GROUP BY status, cefr_level ORDER BY status, cefr_level
            """);

        return Map.of("count", result.size(), "words", result, "stats", stats);
    }

    public Map<String, Object> translateCandidate(int id, String turkish, List<Map<String, String>> sentences) {
        var rows = jdbc.queryForList(
            "UPDATE candidate_words SET turkish = ?, translated_at = NOW(), status = 'translated' WHERE id = ? RETURNING *",
            turkish, id);
        if (rows.isEmpty()) return null;

        int sentencesUpdated = 0;
        if (sentences != null) {
            for (Map<String, String> s : sentences) {
                if (s.get("estonian") == null || s.get("turkish") == null) continue;
                sentencesUpdated += jdbc.update(
                    "UPDATE candidate_sentences SET turkish = ? WHERE candidate_id = ? AND estonian = ?",
                    s.get("turkish"), id, s.get("estonian"));
            }
        }

        Map<String, Object> result = new HashMap<>(rows.get(0));
        result.put("sentencesUpdated", sentencesUpdated);
        return result;
    }

    @Transactional
    public String approveCandidate(int id) {
        var candidates = jdbc.queryForList("SELECT * FROM candidate_words WHERE id = ?", id);
        if (candidates.isEmpty()) return null;

        Map<String, Object> w = candidates.get(0);
        if (w.get("turkish") == null) throw new IllegalStateException("Add Turkish translation before approving");

        String estonian = (String) w.get("estonian");
        String cefrLevel = (String) w.get("cefr_level");
        String wordId = cefrLevel.toLowerCase() + "-" + estonian.replaceAll("\\s+", "-");

        // Check duplicate
        Integer dupCount = jdbc.queryForObject(
            "SELECT COUNT(*) FROM words WHERE estonian = ?", Integer.class, estonian);
        if (dupCount != null && dupCount > 0) {
            jdbc.update("UPDATE candidate_words SET status = 'rejected' WHERE id = ?", id);
            throw new IllegalStateException("Word already exists");
        }

        jdbc.update(
            "INSERT INTO words (id, estonian, english, turkish, cefr_level) VALUES (?, ?, ?, ?, ?)",
            wordId, estonian, w.get("english"), w.get("turkish"), cefrLevel);

        var sents = jdbc.queryForList(
            "SELECT estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = ? ORDER BY sort_order", id);
        for (Map<String, Object> s : sents) {
            jdbc.update(
                "INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order) VALUES (?, ?, ?, ?, ?)",
                wordId, s.get("estonian"), s.get("english"), s.get("turkish"), s.get("sort_order"));
        }

        jdbc.update("UPDATE candidate_words SET status = 'approved' WHERE id = ?", id);
        return wordId;
    }

    @Transactional
    public Map<String, Integer> approveAll(String level) {
        StringBuilder query = new StringBuilder(
            "SELECT * FROM candidate_words WHERE status = 'translated' AND turkish IS NOT NULL");
        List<Object> params = new ArrayList<>();
        if (level != null) {
            query.append(" AND cefr_level = ?");
            params.add(level);
        }

        List<Map<String, Object>> candidates = jdbc.queryForList(query.toString(), params.toArray());
        Set<String> existingWords = new HashSet<>(
            jdbc.queryForList("SELECT estonian FROM words", String.class));

        List<Integer> candidateIds = candidates.stream().map(w -> ((Number) w.get("id")).intValue()).toList();
        Map<Integer, List<Map<String, Object>>> sentMap = new HashMap<>();
        if (!candidateIds.isEmpty()) {
            jdbc.queryForList(
                "SELECT candidate_id, estonian, english, turkish, sort_order FROM candidate_sentences WHERE candidate_id = ANY(?) ORDER BY candidate_id, sort_order",
                (Object) candidateIds.toArray(new Integer[0]))
                .forEach(s -> sentMap.computeIfAbsent(((Number) s.get("candidate_id")).intValue(), k -> new ArrayList<>()).add(s));
        }

        int approved = 0, skipped = 0;
        for (Map<String, Object> w : candidates) {
            String estonian = (String) w.get("estonian");
            String cefrLevel = (String) w.get("cefr_level");
            int candidateId = ((Number) w.get("id")).intValue();

            if (existingWords.contains(estonian)) {
                jdbc.update("UPDATE candidate_words SET status = 'rejected' WHERE id = ?", candidateId);
                skipped++;
                continue;
            }

            String wordId = cefrLevel.toLowerCase() + "-" + estonian.replaceAll("\\s+", "-");
            jdbc.update(
                "INSERT INTO words (id, estonian, english, turkish, cefr_level) VALUES (?, ?, ?, ?, ?)",
                wordId, estonian, w.get("english"), w.get("turkish"), cefrLevel);
            existingWords.add(estonian);

            for (Map<String, Object> s : sentMap.getOrDefault(candidateId, List.of())) {
                jdbc.update(
                    "INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order) VALUES (?, ?, ?, ?, ?)",
                    wordId, s.get("estonian"), s.get("english"), s.get("turkish"), s.get("sort_order"));
            }

            jdbc.update("UPDATE candidate_words SET status = 'approved' WHERE id = ?", candidateId);
            approved++;
        }

        return Map.of("approved", approved, "skipped", skipped, "total", candidates.size());
    }

    public void rejectCandidate(int id) {
        jdbc.update("UPDATE candidate_words SET status = 'rejected' WHERE id = ?", id);
    }

    public Map<String, Object> candidateStats() {
        var stats = jdbc.queryForList("""
            SELECT cefr_level, status, COUNT(*) as count
            FROM candidate_words GROUP BY cefr_level, status ORDER BY cefr_level, status
            """);
        Integer pending = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_words WHERE status = 'pending'", Integer.class);
        Integer translated = jdbc.queryForObject(
            "SELECT COUNT(*) FROM candidate_words WHERE status = 'translated'", Integer.class);

        return Map.of(
            "pending", pending != null ? pending : 0,
            "readyToApprove", translated != null ? translated : 0,
            "breakdown", stats);
    }
}
