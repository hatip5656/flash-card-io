--liquibase formatted sql

--changeset grammar-lesson-podcasts:1
-- One podcast script and podcast per grammar lesson per language (tr, en). Replaces
-- grammar_lessons.podcast_script / podcast_id / script_updated_at, which are copied here as the
-- Turkish rows and no longer used; a later changelog drops them.
CREATE TABLE grammar_lesson_podcasts (
    lesson_id         TEXT NOT NULL REFERENCES grammar_lessons(id) ON DELETE CASCADE,
    language          TEXT NOT NULL CHECK (language IN ('tr', 'en')),
    script            JSONB,
    script_updated_at TIMESTAMPTZ,
    podcast_id        TEXT,
    PRIMARY KEY (lesson_id, language)
);

CREATE INDEX idx_grammar_lesson_podcasts_podcast ON grammar_lesson_podcasts(podcast_id);

INSERT INTO grammar_lesson_podcasts (lesson_id, language, script, script_updated_at, podcast_id)
SELECT id, 'tr', podcast_script, script_updated_at, podcast_id
FROM grammar_lessons
WHERE podcast_script IS NOT NULL OR podcast_id IS NOT NULL
ON CONFLICT DO NOTHING;
