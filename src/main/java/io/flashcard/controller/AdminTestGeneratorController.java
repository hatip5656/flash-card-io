package io.flashcard.controller;

import io.flashcard.service.TestDraftConverter;
import io.flashcard.service.TestGeneratorService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.concurrent.Callable;

/**
 * Generated tests. Gemini drafts come from /generate; Claude (MCP) or an admin posts drafts to /drafts in the
 * format from /format. Drafts are reviewed and published here; learners never see a draft.
 */
@RestController
@RequestMapping("/api/admin/test-generator")
public class AdminTestGeneratorController {

    private final TestGeneratorService generator;

    public AdminTestGeneratorController(TestGeneratorService generator) {
        this.generator = generator;
    }

    @GetMapping("/blueprints")
    public Map<String, Object> blueprints() {
        return Map.of("blueprints", generator.blueprints());
    }

    @PutMapping("/blueprints/{id}")
    public ResponseEntity<?> updateBlueprint(@PathVariable String id, @RequestBody Map<String, Object> body) throws Exception {
        if (!generator.updateBlueprint(id, body)) return ResponseEntity.status(404).body(Map.of("error", "Unknown blueprint"));
        return ResponseEntity.ok(generator.blueprint(id));
    }

    @GetMapping("/voices")
    public Map<String, Object> voices() {
        return generator.voices();
    }

    /** Body: {voice, text?}. Returns a short MP3 of that voice, to choose voices for speakers. */
    @PostMapping("/voices/preview")
    public ResponseEntity<?> previewVoice(@RequestBody Map<String, Object> body) {
        try {
            byte[] mp3 = generator.previewVoice(str(body.get("voice")), str(body.get("text")));
            return ResponseEntity.ok().contentType(org.springframework.http.MediaType.parseMediaType("audio/mpeg")).body(mp3);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(502).body(Map.of("error", "TTS failed: " + e.getMessage()));
        }
    }

    /** The draft format, plus (with blueprintId and level) the full instructions for that blueprint. */
    @GetMapping("/format")
    public ResponseEntity<?> format(@RequestParam(required = false) String blueprintId,
                                    @RequestParam(required = false) String level,
                                    @RequestParam(required = false) String topic) {
        if (blueprintId == null) return ResponseEntity.ok(Map.of("format", TestDraftConverter.FORMAT));
        var bp = generator.blueprint(blueprintId);
        if (bp == null) return ResponseEntity.status(404).body(Map.of("error", "Unknown blueprint"));
        return ResponseEntity.ok(Map.of("format", TestDraftConverter.FORMAT,
            "instructions", generator.prompt(bp, level != null ? level : "B1", topic)));
    }

    /** Body: {blueprintId, level, topic?}. Gemini writes the draft (takes up to a couple of minutes). */
    @PostMapping("/generate")
    public ResponseEntity<?> generate(@RequestBody Map<String, Object> body) {
        return handle(() -> generator.generateWithGemini(str(body.get("blueprintId")), str(body.get("level")), str(body.get("topic"))));
    }

    /** Body: {blueprintId, level, topic?, author?, draft}. For drafts written by Claude or an admin. */
    @PostMapping("/drafts")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> createDraft(@RequestBody Map<String, Object> body) {
        if (!(body.get("draft") instanceof Map<?, ?> draft)) return ResponseEntity.badRequest().body(Map.of("error", "draft object required"));
        String author = str(body.get("author")).isEmpty() ? "claude" : str(body.get("author"));
        return handle(() -> generator.createDraft(str(body.get("blueprintId")), str(body.get("level")),
            str(body.get("topic")), author, (Map<String, Object>) draft));
    }

    @GetMapping("/drafts")
    public Map<String, Object> list() {
        return Map.of("tests", generator.list());
    }

    @GetMapping("/drafts/{id}")
    public ResponseEntity<?> get(@PathVariable String id) {
        var t = generator.get(id);
        return t == null ? ResponseEntity.status(404).body(Map.of("error", "Not found")) : ResponseEntity.ok(t);
    }

    /** Body: {draft}. Rebuilds the questions and renders the audio again. */
    @PutMapping("/drafts/{id}")
    @SuppressWarnings("unchecked")
    public ResponseEntity<?> update(@PathVariable String id, @RequestBody Map<String, Object> body) {
        if (!(body.get("draft") instanceof Map<?, ?> draft)) return ResponseEntity.badRequest().body(Map.of("error", "draft object required"));
        return handle(() -> generator.update(id, (Map<String, Object>) draft));
    }

    @PostMapping("/drafts/{id}/render-audio")
    public ResponseEntity<?> rerender(@PathVariable String id) {
        return handle(() -> generator.rerender(id));
    }

    /** Body (optional): {planRole: 'practice' (default) | 'goal' | ''}. */
    @PostMapping("/drafts/{id}/publish")
    public ResponseEntity<?> publish(@PathVariable String id, @RequestBody(required = false) Map<String, Object> body) {
        String role = body != null && body.containsKey("planRole") ? str(body.get("planRole")) : null;
        return handle(() -> generator.publish(id, role));
    }

    @PostMapping("/drafts/{id}/unpublish")
    public ResponseEntity<?> unpublish(@PathVariable String id) {
        return handle(() -> generator.unpublish(id));
    }

    @DeleteMapping("/drafts/{id}")
    public ResponseEntity<?> delete(@PathVariable String id) {
        return handle(() -> generator.delete(id) ? Map.of("ok", true) : null);
    }

    private ResponseEntity<?> handle(Callable<Object> action) {
        try {
            Object result = action.call();
            return result == null ? ResponseEntity.status(404).body(Map.of("error", "Not found")) : ResponseEntity.ok(result);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(409).body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(Map.of("error", String.valueOf(e.getMessage())));
        }
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }
}
