package io.flashcard.service;

import java.util.*;

/**
 * Turns a generated test draft (the JSON Gemini or Claude writes, see {@link #FORMAT}) into exam questions in
 * the official_exams format plus the audio that has to be rendered for them. Structural problems are errors
 * (the draft is rejected); doubtful content is a warning shown to the reviewer.
 */
public final class TestDraftConverter {

    /** The draft format: sent to Gemini in the prompt and to Claude through the MCP tools. */
    public static final String FORMAT = """
        Reply with one JSON object only, no prose, in this format:
        {
          "title": "Estonian title of the test",
          "titleEn": "English title", "titleTr": "Turkish title",
          "tasks": [
            {
              "type": "listening_dialogue | listening_monologue | reading_text | grammar_items | heard_sentence",
              "instruction": "What the learner must do, in Estonian (e.g. 'Kuula dialoogi ja vali õige vastus.')",
              "passage": "reading_text only: the text the learner reads",
              "script": [ {"speaker": "A", "gender": "f", "text": "One line of the audio, in Estonian"} ],
              "questions": [
                {"type": "multiple_choice", "prompt": "Question", "options": ["…", "…", "…", "…"], "correctIndex": 0, "explanation": "Why, in English"},
                {"type": "true_false", "prompt": "Statement about the text or audio", "answer": true, "explanation": "…"},
                {"type": "heard_sentence", "sentence": "The sentence that is read aloud.", "options": ["The sentence that is read aloud.", "A near-identical alternative.", "…", "…"], "explanation": "What differs"}
              ]
            }
          ]
        }
        Rules: "script" is required for listening_dialogue and listening_monologue (a dialogue has two speakers,
        a monologue one; "gender" is "f" or "m"). "passage" is required for reading_text. heard_sentence tasks use
        only heard_sentence questions, and their options must contain the sentence exactly once. Every
        multiple_choice question has 2 to 6 different options and correctIndex points at the one correct option.
        """;

    public static final Set<String> TASK_TYPES = Set.of(
        "listening_dialogue", "listening_monologue", "reading_text", "grammar_items", "heard_sentence");
    private static final String TRUE = "Õige";
    private static final String FALSE = "Vale";

    /** Voices for speakers, the pause after each script line, and the question cap per task. */
    public record Config(List<String> femaleVoices, List<String> maleVoices, int pauseMs, int maxQuestionsPerTask) {}

    public record Segment(String text, String voice, int pauseAfterMs) {}

    /** One audio file to render: its exam_media key and the TTS segments it is made of. */
    public record AudioJob(String key, List<Segment> segments) {}

    public record Result(List<Map<String, Object>> questions, List<AudioJob> audio, List<String> warnings,
                         List<String> errors) {
        public boolean ok() {
            return errors.isEmpty();
        }
    }

    private TestDraftConverter() {}

    @SuppressWarnings("unchecked")
    public static Result convert(Map<String, Object> draft, String examId, Config config) {
        List<Map<String, Object>> questions = new ArrayList<>();
        List<AudioJob> audio = new ArrayList<>();
        List<String> warnings = new ArrayList<>();
        List<String> errors = new ArrayList<>();

        if (draft == null || !(draft.get("tasks") instanceof List<?> tasks) || tasks.isEmpty()) {
            errors.add("draft needs a non-empty tasks list");
            return new Result(questions, audio, warnings, errors);
        }
        if (blank(draft.get("title"))) errors.add("draft needs a title");

        for (int t = 0; t < tasks.size(); t++) {
            String where = "task " + (t + 1);
            if (!(tasks.get(t) instanceof Map<?, ?> rawTask)) {
                errors.add(where + " is not an object");
                continue;
            }
            Map<String, Object> task = (Map<String, Object>) rawTask;
            String type = str(task.get("type"));
            if (!TASK_TYPES.contains(type)) {
                errors.add(where + ": unknown type '" + type + "'");
                continue;
            }
            String section = (t + 1) + ". ülesanne";
            String group = examId + "-t" + (t + 1);
            String instruction = str(task.get("instruction"));
            if (instruction.isEmpty()) warnings.add(where + ": no instruction");

            // Task-level audio (listening) or passage (reading)
            List<String> taskAudio = List.of();
            String transcript = null;
            String context = instruction.isEmpty() ? null : instruction;
            if (type.startsWith("listening_")) {
                var script = task.get("script") instanceof List<?> l ? l : List.of();
                if (script.isEmpty()) {
                    errors.add(where + ": a listening task needs a script");
                    continue;
                }
                var segments = segments(script, config, where, errors, warnings);
                long speakers = script.stream().filter(Map.class::isInstance)
                    .map(s -> str(((Map<?, ?>) s).get("speaker"))).distinct().count();
                if (type.equals("listening_dialogue") && speakers < 2) warnings.add(where + ": a dialogue with one speaker");
                if (type.equals("listening_monologue") && speakers > 1) warnings.add(where + ": a monologue with several speakers");
                String key = mediaKey(examId, "t" + (t + 1));
                audio.add(new AudioJob(key, segments));
                taskAudio = List.of(key);
                transcript = transcript(script);
            } else if (type.equals("reading_text")) {
                String passage = str(task.get("passage"));
                if (passage.isEmpty()) {
                    errors.add(where + ": a reading task needs a passage");
                    continue;
                }
                context = context == null ? passage : context + "\n\n" + passage;
            }

            var qs = task.get("questions") instanceof List<?> l ? l : List.of();
            if (qs.isEmpty()) errors.add(where + ": no questions");
            if (qs.size() > config.maxQuestionsPerTask()) {
                errors.add(where + ": " + qs.size() + " questions, at most " + config.maxQuestionsPerTask());
            }
            for (int i = 0; i < qs.size(); i++) {
                String qWhere = where + " question " + (i + 1);
                if (!(qs.get(i) instanceof Map<?, ?> rawQ)) {
                    errors.add(qWhere + " is not an object");
                    continue;
                }
                Map<String, Object> q = (Map<String, Object>) rawQ;
                Map<String, Object> out = new LinkedHashMap<>();
                out.put("section", section);
                out.put("group", group);
                if (context != null) out.put("context", context);
                if (!taskAudio.isEmpty()) out.put("audio", taskAudio);
                if (transcript != null) out.put("transcript", transcript);
                out.put("explanation", str(q.get("explanation")));
                String qType = str(q.get("type"));
                if (type.equals("heard_sentence") && !qType.equals("heard_sentence")) {
                    errors.add(qWhere + ": heard_sentence tasks take only heard_sentence questions");
                    continue;
                }
                switch (qType) {
                    case "multiple_choice" -> {
                        List<String> options = strings(q.get("options"));
                        Integer ci = q.get("correctIndex") instanceof Number n ? n.intValue() : null;
                        String prompt = str(q.get("prompt"));
                        if (prompt.isEmpty()) errors.add(qWhere + ": empty prompt");
                        checkOptions(options, qWhere, errors);
                        if (ci == null || ci < 0 || ci >= options.size()) errors.add(qWhere + ": correctIndex out of range");
                        out.put("type", "multiple_choice");
                        out.put("prompt", prompt);
                        out.put("options", options);
                        out.put("correctIndex", ci == null ? 0 : ci);
                    }
                    case "true_false" -> {
                        String prompt = str(q.get("prompt"));
                        if (prompt.isEmpty()) errors.add(qWhere + ": empty statement");
                        if (!(q.get("answer") instanceof Boolean answer)) {
                            errors.add(qWhere + ": answer must be true or false");
                            continue;
                        }
                        out.put("type", "multiple_choice");
                        out.put("prompt", prompt);
                        out.put("options", List.of(TRUE, FALSE));
                        out.put("correctIndex", answer ? 0 : 1);
                    }
                    case "heard_sentence" -> {
                        if (!type.equals("heard_sentence")) {
                            errors.add(qWhere + ": heard_sentence questions belong in a heard_sentence task");
                            continue;
                        }
                        String sentence = str(q.get("sentence"));
                        List<String> options = strings(q.get("options"));
                        checkOptions(options, qWhere, errors);
                        long hits = options.stream().filter(sentence::equals).count();
                        if (sentence.isEmpty() || hits != 1) {
                            errors.add(qWhere + ": the options must contain the sentence exactly once");
                            continue;
                        }
                        for (String o : options) {
                            if (o.equals(sentence)) continue;
                            int words = wordDifference(sentence, o);
                            if (words > 2) warnings.add(qWhere + ": '" + o + "' differs in " + words + " words — not a near-identical pair");
                        }
                        String key = mediaKey(examId, "t" + (t + 1) + "q" + (i + 1));
                        audio.add(new AudioJob(key, List.of(new Segment(sentence, voiceFor("f", 0, config), 0))));
                        out.put("type", "multiple_choice");
                        out.put("prompt", str(q.get("prompt")).isEmpty() ? "Vali lause, mida kuulsid." : str(q.get("prompt")));
                        out.put("options", options);
                        out.put("correctIndex", options.indexOf(sentence));
                        out.put("audio", List.of(key));
                        out.put("transcript", sentence);
                    }
                    default -> {
                        errors.add(qWhere + ": unknown question type '" + qType + "'");
                        continue;
                    }
                }
                questions.add(out);
            }
        }
        return new Result(questions, audio, warnings, errors);
    }

    private static List<Segment> segments(List<?> script, Config config, String where, List<String> errors,
                                          List<String> warnings) {
        // Speakers get voices in order of appearance, female and male pools kept apart
        Map<String, String> voiceBySpeaker = new LinkedHashMap<>();
        int female = 0, male = 0;
        List<Segment> out = new ArrayList<>();
        for (int i = 0; i < script.size(); i++) {
            if (!(script.get(i) instanceof Map<?, ?> line)) {
                errors.add(where + " script line " + (i + 1) + " is not an object");
                continue;
            }
            String text = str(line.get("text"));
            if (text.isEmpty()) {
                errors.add(where + " script line " + (i + 1) + " is empty");
                continue;
            }
            if (text.length() > 2000) errors.add(where + " script line " + (i + 1) + " is longer than 2000 characters");
            String speaker = str(line.get("speaker")).isEmpty() ? "A" : str(line.get("speaker"));
            String gender = "m".equals(str(line.get("gender"))) ? "m" : "f";
            if (!voiceBySpeaker.containsKey(speaker)) {
                voiceBySpeaker.put(speaker, "m".equals(gender) ? voiceFor("m", male++, config) : voiceFor("f", female++, config));
            }
            out.add(new Segment(text, voiceBySpeaker.get(speaker), config.pauseMs()));
        }
        if (out.size() > 60) errors.add(where + ": " + out.size() + " script lines, the TTS takes at most 60");
        return out;
    }

    private static String voiceFor(String gender, int index, Config config) {
        List<String> pool = "m".equals(gender) ? config.maleVoices() : config.femaleVoices();
        if (pool.isEmpty()) return "default";
        return pool.get(index % pool.size());
    }

    private static void checkOptions(List<String> options, String where, List<String> errors) {
        if (options.size() < 2 || options.size() > 6) errors.add(where + ": needs 2 to 6 options, has " + options.size());
        Set<String> seen = new HashSet<>();
        for (String o : options) {
            if (o.isEmpty()) errors.add(where + ": an empty option");
            else if (!seen.add(ExamBankService.normalise(o))) errors.add(where + ": duplicate option '" + o + "'");
        }
    }

    /** Words that differ between two sentences, position by position (plus any length difference). */
    static int wordDifference(String a, String b) {
        String[] x = a.trim().split("\\s+"), y = b.trim().split("\\s+");
        int diff = Math.abs(x.length - y.length);
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            if (!ExamBankService.normalise(x[i]).equals(ExamBankService.normalise(y[i]))) diff++;
        }
        return diff;
    }

    private static String transcript(List<?> script) {
        StringBuilder sb = new StringBuilder();
        for (Object o : script) {
            if (!(o instanceof Map<?, ?> line)) continue;
            if (!sb.isEmpty()) sb.append('\n');
            String speaker = str(line.get("speaker"));
            if (!speaker.isEmpty()) sb.append(speaker).append(": ");
            sb.append(str(line.get("text")));
        }
        return sb.toString();
    }

    /** exam_media keys are lowercase paths; generated exam ids already are. */
    static String mediaKey(String examId, String name) {
        return "generated/" + examId + "/" + name + ".mp3";
    }

    private static List<String> strings(Object o) {
        List<String> out = new ArrayList<>();
        if (o instanceof List<?> l) for (Object v : l) out.add(v == null ? "" : v.toString().strip());
        return out;
    }

    private static String str(Object o) {
        return o == null ? "" : o.toString().strip();
    }

    private static boolean blank(Object o) {
        return str(o).isEmpty();
    }
}
