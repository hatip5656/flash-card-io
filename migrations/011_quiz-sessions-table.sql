-- Move quiz sessions from in-memory Map to PostgreSQL for horizontal scaling
CREATE TABLE IF NOT EXISTS quiz_sessions (
  chat_id BIGINT PRIMARY KEY,
  questions JSONB NOT NULL,
  answers JSONB NOT NULL DEFAULT '[]',
  words JSONB NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Auto-expire stale sessions (older than 10 minutes)
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_created
  ON quiz_sessions (created_at);
