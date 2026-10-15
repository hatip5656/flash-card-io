--liquibase formatted sql

--changeset exam-media:1 splitStatements:true
-- Audio and pictures exam questions and tasks reference by key ("testest/mm_823/track.mp3").
CREATE TABLE IF NOT EXISTS exam_media (
    media_key TEXT PRIMARY KEY,
    content_type TEXT NOT NULL,
    bytes BYTEA NOT NULL,
    size_bytes INTEGER NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);

--changeset exam-tasks:1 splitStatements:true
-- Writing and speaking tasks: a set (one test variant) holds its tasks as JSON, each with prompt,
-- pictures/audio, and the model answer and examiner comment where the source has them. Not graded.
CREATE TABLE IF NOT EXISTS exam_tasks (
    id TEXT PRIMARY KEY,
    kind TEXT NOT NULL,
    skill TEXT NOT NULL,
    cefr_level TEXT NOT NULL,
    variant INTEGER,
    title TEXT NOT NULL,
    title_tr TEXT,
    title_en TEXT,
    source TEXT,
    sort_order INTEGER,
    time_limit_minutes INTEGER,
    intro TEXT,
    tasks JSONB NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_exam_tasks_level ON exam_tasks(cefr_level);

-- What a learner wrote for a task; kept so it can be reviewed or given feedback later.
CREATE TABLE IF NOT EXISTS exam_task_responses (
    id BIGSERIAL PRIMARY KEY,
    chat_id BIGINT NOT NULL,
    task_set_id TEXT NOT NULL REFERENCES exam_tasks(id) ON DELETE CASCADE,
    task_index INTEGER NOT NULL,
    response TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS idx_exam_task_responses_user ON exam_task_responses(chat_id, task_set_id);
