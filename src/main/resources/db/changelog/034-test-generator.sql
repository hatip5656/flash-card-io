--liquibase formatted sql

--changeset test-generator:1 splitStatements:true
-- Generated tests are ordinary exams (kind 'generated') that start as drafts: learners see only published
-- exams. generation keeps how a test was made (blueprint, author, the editable draft, audio render state).
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'published';
ALTER TABLE official_exams ADD COLUMN IF NOT EXISTS generation JSONB;
CREATE INDEX IF NOT EXISTS idx_official_exams_status ON official_exams(status);

-- What a generated test contains and how to ask for it. prompt_template placeholders: {{level}}, {{topic}},
-- {{tasks}} (from spec.tasks) and {{format}} (the draft JSON format the server accepts).
CREATE TABLE IF NOT EXISTS test_blueprints (
    id TEXT PRIMARY KEY,
    title TEXT NOT NULL,
    test_type TEXT NOT NULL,
    skill TEXT NOT NULL,
    description TEXT NOT NULL,
    spec JSONB NOT NULL,
    prompt_template TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

--changeset test-generator:2 splitStatements:false
INSERT INTO test_blueprints (id, title, test_type, skill, description, spec, prompt_template) VALUES
('listening-standard', 'Listening: dialogue and announcement', 'listening', 'listening',
 'A two-person dialogue and a one-speaker text (announcement, voicemail, radio item), each with comprehension questions. Voiced by our Estonian TTS.',
 '{"timeLimitMinutes": 20, "passMark": 0.6, "tasks": [
    {"type": "listening_dialogue", "questions": 5, "questionTypes": ["multiple_choice", "true_false"], "speakers": 2},
    {"type": "listening_monologue", "questions": 5, "questionTypes": ["multiple_choice"]}
  ]}',
 $p$You write Estonian listening tests in the style of the Harno/TESTEST level exams for learners at CEFR level {{level}}.
Topic: {{topic}}.

Write the audio scripts as natural spoken Estonian at exactly level {{level}}: vocabulary, sentence length and speed of ideas must fit {{level}}. Dialogues are everyday situations (shop, doctor, work, travel, family, services); one-speaker texts are announcements, voicemails, short radio items or instructions. Each script should take 45 to 90 seconds to read aloud. Give every speaker a gender ("f" or "m") so the right voice is used.

Questions must be answerable only from the audio, test understanding of what was said (not general knowledge), and have exactly one correct answer. Distractors are plausible: they reuse words from the script but are wrong. Write instructions and questions in Estonian. Add a short explanation in English for each answer.

Tasks:
{{tasks}}

{{format}}$p$),

('reading-standard', 'Reading: text with questions and a gap text', 'reading', 'reading',
 'A short text (letter, article, notice) with comprehension questions, and a gap text where the learner chooses the right word for each gap.',
 '{"timeLimitMinutes": 30, "passMark": 0.6, "tasks": [
    {"type": "reading_text", "questions": 6, "questionTypes": ["multiple_choice", "true_false"]},
    {"type": "reading_text", "questions": 6, "questionTypes": ["multiple_choice"], "style": "gap text: the passage contains gaps marked (1), (2), … and each question chooses the word for one gap"}
  ]}',
 $p$You write Estonian reading tests in the style of the Harno/TESTEST level exams for learners at CEFR level {{level}}.
Topic: {{topic}}.

Texts are authentic-looking: personal letters or e-mails, short articles, notices, adverts, web posts. Length: about 120 words at A2, 200 at B1, 300 at B2, 400 at C1. Language must fit level {{level}}.

Questions test understanding of the text (details, main idea, the writer's intention) and have exactly one correct answer. For a gap text, mark the gaps in the passage as (1), (2), … and write one question per gap whose prompt is "Lünk (N)" and whose options are 4 word forms or words that fit the grammar or meaning differently; only one is correct in context. Write instructions and questions in Estonian; add a short English explanation per answer.

Tasks:
{{tasks}}

{{format}}$p$),

('grammar-vocab', 'Grammar and vocabulary: sentence items', 'grammar', 'grammar',
 'Sentence-level items like the placement test: choose the form or word that fits the gap. Can focus on one grammar topic.',
 '{"timeLimitMinutes": 15, "passMark": 0.6, "tasks": [
    {"type": "grammar_items", "questions": 15, "questionTypes": ["multiple_choice"]}
  ]}',
 $p$You write Estonian grammar and vocabulary items for learners at CEFR level {{level}}, like the TESTEST placement test ("Vali, milline vastus sobib lausesse.").
Focus: {{topic}}.

Each item is one natural Estonian sentence with one gap written as "...". The four options are forms of the same word (cases, verb forms, comparatives) or close words; exactly one is correct, and the others are typical learner mistakes at {{level}}. Vary the sentences and the vocabulary; do not repeat a sentence pattern. Add a short English explanation of why the answer is right.

Tasks:
{{tasks}}

{{format}}$p$),

('heard-sentence', 'Listening: which sentence did you hear?', 'dictation', 'listening',
 'TTS reads one sentence; the learner picks it among very similar sentences (minimal pairs: one ending, sound or word differs). Trains precise listening without typing.',
 '{"timeLimitMinutes": 10, "passMark": 0.7, "tasks": [
    {"type": "heard_sentence", "questions": 10}
  ]}',
 $p$You write Estonian "which sentence did you hear?" items for learners at CEFR level {{level}}.
Topic: {{topic}}.

For each item write one natural sentence at level {{level}} (the one that is read aloud) and 3 near-identical alternatives. Each alternative differs from it in exactly one small, audible detail that matters in Estonian: a case ending (linna / linnas / linnast), a verb person or tense (elan / elas / elab), vowel or consonant length (kala / kalla, tuli / tulli), or one short word. All four sentences must be real, grammatical Estonian. Never let two options be identical. Use a different sentence for every item.

Tasks:
{{tasks}}

{{format}}$p$)
ON CONFLICT (id) DO NOTHING;

--changeset test-generator:3 splitStatements:true
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('generator.voices_female', '["et_f_reporter", "et_f_rohemae", "et_f_sobak"]', 'TTS voices for female speakers in generated listening tests (assigned in order per test)'),
  ('generator.voices_male', '["et_m_kelmsaar", "et_m_kilumets", "et_m_maiberg"]', 'TTS voices for male speakers in generated listening tests'),
  ('generator.pause_ms', '700', 'Pause after each line of a generated listening script, in milliseconds'),
  ('generator.max_questions_per_task', '30', 'Most questions a generated task may have')
ON CONFLICT (setting_key) DO NOTHING;
