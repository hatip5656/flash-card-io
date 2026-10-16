package io.flashcard.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Generated tests: a blueprint (test_blueprints) says what to make, Gemini or Claude (via MCP) writes a draft,
 * the draft becomes an exam (kind 'generated', status 'draft') with its audio voiced by our TTS, and an admin
 * reviews and publishes it. Learners only ever see published exams.
 */
@Service
public class TestGeneratorService {

    private static final Logger log = LoggerFactory.getLogger(TestGeneratorService.class);
    public static final String KIND = "generated";

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper;
    private final GeminiService gemini;
    private final TestAudioRenderer renderer;
    private final ExamBankService examBank;
    private final LearningSettings settings;
    // TTS handles one request at a time anyway; one worker keeps renders in order
    private final ExecutorService audioWorker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "test-audio");
        t.setDaemon(true);
        return t;
    });

    public TestGeneratorService(JdbcTemplate jdbc, ObjectMapper objectMapper, GeminiService gemini,
                                TestAudioRenderer renderer, ExamBankService examBank, LearningSettings settings) {
        this.jdbc = jdbc;
        this.objectMapper = objectMapper;
        this.gemini = gemini;
        this.renderer = renderer;
        this.examBank = examBank;
        this.settings = settings;
    }

    // ── Blueprints ──

    public List<Map<String, Object>> blueprints() {
        return withJson(jdbc.queryForList(
            "SELECT id, title, test_type, skill, description, spec::text AS spec, prompt_template, active, updated_at " +
            "FROM test_blueprints ORDER BY test_type, id"), "spec");
    }

    public Map<String, Object> blueprint(String id) {
        var rows = blueprints().stream().filter(b -> id.equals(b.get("id"))).toList();
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Body fields: title, description, spec (object), promptTemplate, active. Only those present change. */
    public boolean updateBlueprint(String id, Map<String, Object> body) throws Exception {
        if (blueprint(id) == null) return false;
        if (body.get("title") instanceof String v) jdbc.update("UPDATE test_blueprints SET title = ? WHERE id = ?", v, id);
        if (body.get("description") instanceof String v) jdbc.update("UPDATE test_blueprints SET description = ? WHERE id = ?", v, id);
        if (body.get("promptTemplate") instanceof String v) jdbc.update("UPDATE test_blueprints SET prompt_template = ? WHERE id = ?", v, id);
        if (body.get("active") instanceof Boolean v) jdbc.update("UPDATE test_blueprints SET active = ? WHERE id = ?", v, id);
        if (body.get("spec") instanceof Map<?, ?> v) {
            jdbc.update("UPDATE test_blueprints SET spec = CAST(? AS JSONB) WHERE id = ?", objectMapper.writeValueAsString(v), id);
        }
        jdbc.update("UPDATE test_blueprints SET updated_at = NOW() WHERE id = ?", id);
        return true;
    }

    /** The full instructions for a blueprint at a level: what Gemini gets, and what Claude should follow. */
    @SuppressWarnings("unchecked")
    public String prompt(Map<String, Object> blueprint, String level, String topic) {
        Map<String, Object> spec = (Map<String, Object>) blueprint.get("spec");
        StringBuilder tasks = new StringBuilder();
        int n = 1;
        for (Object o : (List<?>) spec.getOrDefault("tasks", List.of())) {
            var t = (Map<String, Object>) o;
            tasks.append(n++).append(". type ").append(t.get("type")).append(", ").append(t.get("questions")).append(" questions");
            if (t.get("questionTypes") != null) tasks.append(" (question types: ").append(String.join(", ", (List<String>) t.get("questionTypes"))).append(")");
            if (t.get("speakers") != null) tasks.append(", ").append(t.get("speakers")).append(" speakers");
            if (t.get("style") != null) tasks.append(" — ").append(t.get("style"));
            tasks.append('\n');
        }
        String chosenTopic = topic == null || topic.isBlank() ? "choose a fitting everyday topic yourself" : topic.strip();
        return ((String) blueprint.get("prompt_template"))
            .replace("{{level}}", level)
            .replace("{{topic}}", chosenTopic)
            .replace("{{tasks}}", tasks.toString().strip())
            .replace("{{format}}", TestDraftConverter.FORMAT);
    }

    /** The voices speakers can be given (from learning_settings), by gender. */
    public Map<String, Object> voices() {
        return Map.of("female", settings.list("generator.voices_female"), "male", settings.list("generator.voices_male"));
    }

    public byte[] previewVoice(String voice, String text) throws Exception {
        List<String> all = new ArrayList<>(settings.list("generator.voices_female"));
        all.addAll(settings.list("generator.voices_male"));
        if (!all.contains(voice)) throw new IllegalArgumentException("Unknown voice " + voice);
        String line = text == null || text.isBlank() ? "Tere! Mina olen see hääl, kes sulle täna räägib." : text.strip();
        if (line.length() > 300) throw new IllegalArgumentException("Preview text is limited to 300 characters");
        return renderer.preview(voice, line);
    }

    // ── Creating drafts ──

    /** Asks Gemini for a draft and saves it. Throws when Gemini gives nothing usable. */
    public Map<String, Object> generateWithGemini(String blueprintId, String level, String topic) throws Exception {
        var bp = activeBlueprint(blueprintId, level);
        if (!gemini.isAvailable()) throw new IllegalStateException("Gemini is not configured");
        String raw = gemini.generateJson(prompt(bp, level, topic), 16384, 0.8);
        if (raw == null) throw new IllegalStateException("Gemini did not answer; try again");
        Map<String, Object> draft;
        try {
            draft = objectMapper.readValue(raw, new TypeReference<>() {});
        } catch (Exception e) {
            throw new IllegalStateException("Gemini's answer was not valid JSON");
        }
        return saveDraft(bp, level, topic, "gemini", draft);
    }

    /** Saves a draft written elsewhere (Claude through MCP, or an admin). */
    public Map<String, Object> createDraft(String blueprintId, String level, String topic, String author,
                                           Map<String, Object> draft) throws Exception {
        return saveDraft(activeBlueprint(blueprintId, level), level, topic, author, draft);
    }

    private Map<String, Object> activeBlueprint(String blueprintId, String level) {
        var bp = blueprint(blueprintId);
        if (bp == null || !Boolean.TRUE.equals(bp.get("active"))) throw new IllegalArgumentException("Unknown or inactive blueprint " + blueprintId);
        if (!settings.list("plan.level_order").contains(level)) throw new IllegalArgumentException("Unknown level " + level);
        return bp;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> saveDraft(Map<String, Object> bp, String level, String topic, String author,
                                          Map<String, Object> draft) throws Exception {
        String examId = "gen-" + level.toLowerCase(Locale.ROOT) + "-" + bp.get("test_type") + "-"
            + UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        var result = TestDraftConverter.convert(draft, examId, converterConfig());
        Map<String, Object> spec = (Map<String, Object>) bp.get("spec");

        Map<String, Object> generation = new LinkedHashMap<>();
        generation.put("blueprintId", bp.get("id"));
        generation.put("author", author);
        generation.put("topic", topic);
        generation.put("createdAt", new Date().toInstant().toString());
        generation.put("draft", draft);
        generation.put("warnings", result.warnings());
        generation.put("errors", result.errors());
        generation.put("audio", Map.of("status", result.ok() && !result.audio().isEmpty() ? "pending" : "none"));

        List<String> levels = settings.list("plan.level_order");
        int i = levels.indexOf(level);
        Map<String, Object> scoring = new LinkedHashMap<>();
        scoring.put("type", "level_check");
        scoring.put("level", level);
        scoring.put("passMark", spec.getOrDefault("passMark", 0.6));
        scoring.put("nextLevel", i >= 0 && i + 1 < levels.size() ? levels.get(i + 1) : null);

        String title = str(draft.get("title")).isEmpty() ? bp.get("title") + " (" + level + ")" : str(draft.get("title"));
        jdbc.update("""
            INSERT INTO official_exams (id, cefr_level, title, title_tr, title_en, source, questions, kind, skill,
              scoring, time_limit_minutes, status, plan_role, generation)
            VALUES (?, ?, ?, ?, ?, ?, CAST(? AS JSONB), ?, ?, CAST(? AS JSONB), ?, 'draft', NULL, CAST(? AS JSONB))
            """,
            examId, level, title, emptyToNull(str(draft.get("titleTr"))), emptyToNull(str(draft.get("titleEn"))),
            "Generated (" + author + ")", objectMapper.writeValueAsString(result.questions()), KIND, bp.get("skill"),
            objectMapper.writeValueAsString(scoring),
            spec.get("timeLimitMinutes") instanceof Number n ? n.intValue() : null,
            objectMapper.writeValueAsString(generation));
        log.info("[test-gen] draft {} by {} from {} at {}: {} questions, {} warnings, {} errors",
            examId, author, bp.get("id"), level, result.questions().size(), result.warnings().size(), result.errors().size());
        if (result.ok() && !result.audio().isEmpty()) renderLater(examId, result.audio());
        return get(examId);
    }

    // ── Reviewing ──

    public List<Map<String, Object>> list() {
        return jdbc.queryForList("""
            SELECT id, title, cefr_level, skill, status, plan_role, time_limit_minutes,
                   jsonb_array_length(questions) AS question_count,
                   generation->>'author' AS author, generation->>'blueprintId' AS blueprint_id,
                   generation->>'topic' AS topic, generation->>'createdAt' AS created_at,
                   generation->'audio'->>'status' AS audio_status,
                   jsonb_array_length(COALESCE(generation->'warnings', '[]'::jsonb)) AS warnings,
                   jsonb_array_length(COALESCE(generation->'errors', '[]'::jsonb)) AS errors
            FROM official_exams WHERE kind = ? ORDER BY generation->>'createdAt' DESC NULLS LAST
            """, KIND);
    }

    public Map<String, Object> get(String examId) {
        var rows = withJson(jdbc.queryForList("""
            SELECT id, title, title_tr, title_en, cefr_level, skill, status, plan_role, time_limit_minutes,
                   scoring::text AS scoring, questions::text AS questions, generation::text AS generation
            FROM official_exams WHERE id = ? AND kind = ?
            """, examId, KIND), "scoring", "questions", "generation");
        return rows.isEmpty() ? null : rows.get(0);
    }

    /** Replaces a draft's content; the questions are rebuilt and the audio rendered again. */
    public Map<String, Object> update(String examId, Map<String, Object> draft) throws Exception {
        var current = get(examId);
        if (current == null) return null;
        if (!"draft".equals(current.get("status"))) throw new IllegalStateException("Unpublish the test before editing it");
        var result = TestDraftConverter.convert(draft, examId, converterConfig());
        jdbc.update("""
            UPDATE official_exams SET questions = CAST(? AS JSONB), title = COALESCE(NULLIF(?, ''), title),
              title_tr = COALESCE(NULLIF(?, ''), title_tr), title_en = COALESCE(NULLIF(?, ''), title_en),
              generation = generation || CAST(? AS JSONB)
            WHERE id = ?
            """,
            objectMapper.writeValueAsString(result.questions()), str(draft.get("title")), str(draft.get("titleTr")),
            str(draft.get("titleEn")),
            objectMapper.writeValueAsString(Map.of("draft", draft, "warnings", result.warnings(), "errors", result.errors(),
                "audio", Map.of("status", result.ok() && !result.audio().isEmpty() ? "pending" : "none"),
                "editedAt", new Date().toInstant().toString())),
            examId);
        examBank.invalidate();
        if (result.ok() && !result.audio().isEmpty()) renderLater(examId, result.audio());
        return get(examId);
    }

    @SuppressWarnings("unchecked")
    public Map<String, Object> rerender(String examId) {
        var current = get(examId);
        if (current == null) return null;
        var gen = (Map<String, Object>) current.get("generation");
        var result = TestDraftConverter.convert((Map<String, Object>) gen.get("draft"), examId, converterConfig());
        if (!result.ok()) throw new IllegalStateException("Fix the draft's errors first");
        if (result.audio().isEmpty()) return current;
        // The audio keys depend on voices and tempo, so the questions must point at the new files before the
        // old ones are cleaned up after rendering.
        try {
            jdbc.update("UPDATE official_exams SET questions = CAST(? AS JSONB) WHERE id = ?",
                objectMapper.writeValueAsString(result.questions()), examId);
        } catch (Exception e) {
            throw new IllegalStateException("Could not update the questions: " + e.getMessage());
        }
        examBank.invalidate();
        setAudio(examId, Map.of("status", "pending"));
        renderLater(examId, result.audio());
        return get(examId);
    }

    /** Makes a draft visible to learners; planRole 'practice' (default) lets the feed use it. */
    @SuppressWarnings("unchecked")
    public Map<String, Object> publish(String examId, String planRole) {
        var current = get(examId);
        if (current == null) return null;
        var gen = (Map<String, Object>) current.get("generation");
        if (!((List<?>) gen.getOrDefault("errors", List.of())).isEmpty()) throw new IllegalStateException("The draft still has errors");
        if (((List<?>) current.get("questions")).isEmpty()) throw new IllegalStateException("The draft has no questions");
        String audio = String.valueOf(((Map<String, Object>) gen.getOrDefault("audio", Map.of())).get("status"));
        if (!"ready".equals(audio) && !"none".equals(audio)) throw new IllegalStateException("The audio is not ready (" + audio + ")");
        String role = planRole == null ? ExamBankService.ROLE_PRACTICE : planRole;
        if (!role.isEmpty() && !ExamBankService.ROLE_PRACTICE.equals(role) && !ExamBankService.ROLE_GOAL.equals(role)) {
            throw new IllegalArgumentException("planRole must be practice, goal or empty");
        }
        jdbc.update("UPDATE official_exams SET status = 'published', plan_role = ? WHERE id = ?", role.isEmpty() ? null : role, examId);
        examBank.invalidate();
        return get(examId);
    }

    public Map<String, Object> unpublish(String examId) {
        if (get(examId) == null) return null;
        jdbc.update("UPDATE official_exams SET status = 'draft', plan_role = NULL WHERE id = ?", examId);
        examBank.invalidate();
        return get(examId);
    }

    /** Deletes a draft and its audio. Published tests must be unpublished first. */
    public boolean delete(String examId) {
        var current = get(examId);
        if (current == null) return false;
        if (!"draft".equals(current.get("status"))) throw new IllegalStateException("Unpublish the test before deleting it");
        jdbc.update("DELETE FROM exam_media WHERE media_key LIKE ?", "generated/" + examId + "/%");
        jdbc.update("DELETE FROM official_exams WHERE id = ? AND kind = ?", examId, KIND);
        examBank.invalidate();
        return true;
    }

    // ── Audio ──

    private void renderLater(String examId, List<TestDraftConverter.AudioJob> jobs) {
        audioWorker.submit(() -> {
            setAudio(examId, Map.of("status", "rendering", "files", jobs.size()));
            try {
                renderer.render(jobs);
                // Audio from earlier versions of the draft is no longer referenced
                List<String> keep = jobs.stream().map(TestDraftConverter.AudioJob::key).toList();
                jdbc.update("DELETE FROM exam_media WHERE media_key LIKE ? AND NOT (media_key = ANY(?))",
                    "generated/" + examId + "/%", keep.toArray(new String[0]));
                setAudio(examId, Map.of("status", "ready", "files", jobs.size(), "renderedAt", new Date().toInstant().toString()));
            } catch (Exception e) {
                log.warn("[test-gen] audio for {} failed: {}", examId, e.getMessage());
                setAudio(examId, Map.of("status", "failed", "error", String.valueOf(e.getMessage())));
            }
        });
    }

    private void setAudio(String examId, Map<String, Object> audio) {
        try {
            jdbc.update("UPDATE official_exams SET generation = jsonb_set(COALESCE(generation, '{}'::jsonb), '{audio}', CAST(? AS JSONB)) WHERE id = ?",
                objectMapper.writeValueAsString(audio), examId);
        } catch (Exception e) {
            log.warn("[test-gen] could not record audio state for {}: {}", examId, e.getMessage());
        }
    }

    private TestDraftConverter.Config converterConfig() {
        return new TestDraftConverter.Config(settings.list("generator.voices_female"), settings.list("generator.voices_male"),
            settings.count("generator.pause_ms"), settings.count("generator.max_questions_per_task"),
            settings.num("generator.speech_tempo"));
    }

    private List<Map<String, Object>> withJson(List<Map<String, Object>> rows, String... columns) {
        List<Map<String, Object>> out = new ArrayList<>();
        for (var r : rows) {
            Map<String, Object> m = new LinkedHashMap<>(r);
            for (String c : columns) {
                if (m.get(c) instanceof String s) {
                    try {
                        m.put(c, objectMapper.readValue(s, Object.class));
                    } catch (Exception ignored) {}
                }
            }
            out.add(m);
        }
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    private static String emptyToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
