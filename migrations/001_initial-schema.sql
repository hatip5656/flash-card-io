-- Initial schema: all existing tables

CREATE TABLE IF NOT EXISTS subscribers (
  chat_id BIGINT PRIMARY KEY,
  channel TEXT NOT NULL DEFAULT 'telegram',
  cefr_level TEXT NOT NULL DEFAULT 'A1',
  schedule TEXT NOT NULL DEFAULT '0 9 * * *',
  registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  active BOOLEAN NOT NULL DEFAULT TRUE,
  username TEXT,
  first_name TEXT,
  preferences JSONB NOT NULL DEFAULT '{}'::jsonb,
  next_delivery_at TIMESTAMPTZ,
  next_grammar_at TIMESTAMPTZ
);

CREATE TABLE IF NOT EXISTS sent_words (
  chat_id BIGINT NOT NULL,
  word_id TEXT NOT NULL,
  word_value TEXT,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  english TEXT,
  quiz_count INTEGER NOT NULL DEFAULT 0,
  ease_factor REAL NOT NULL DEFAULT 2.5,
  interval_days INTEGER NOT NULL DEFAULT 1,
  next_review DATE NOT NULL DEFAULT CURRENT_DATE,
  repetitions INTEGER NOT NULL DEFAULT 0,
  PRIMARY KEY (chat_id, word_id)
);

CREATE TABLE IF NOT EXISTS sent_grammar (
  chat_id BIGINT NOT NULL,
  lesson_id TEXT NOT NULL,
  sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (chat_id, lesson_id)
);

CREATE TABLE IF NOT EXISTS quiz_results (
  id SERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  score INTEGER NOT NULL,
  total INTEGER NOT NULL,
  percentage INTEGER NOT NULL,
  completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS quiz_answers (
  id SERIAL PRIMARY KEY,
  quiz_id INTEGER NOT NULL REFERENCES quiz_results(id),
  estonian TEXT NOT NULL,
  correct_answer TEXT NOT NULL,
  user_answer TEXT NOT NULL,
  is_correct BOOLEAN NOT NULL
);

CREATE TABLE IF NOT EXISTS activity_log (
  id SERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  activity_date DATE NOT NULL DEFAULT CURRENT_DATE,
  words_learned INTEGER NOT NULL DEFAULT 0,
  quizzes_taken INTEGER NOT NULL DEFAULT 0,
  UNIQUE (chat_id, activity_date)
);

CREATE INDEX IF NOT EXISTS idx_activity_log_chat_date ON activity_log (chat_id, activity_date DESC);

CREATE TABLE IF NOT EXISTS saved_words (
  chat_id BIGINT NOT NULL,
  word_id TEXT NOT NULL,
  saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (chat_id, word_id)
);

CREATE TABLE IF NOT EXISTS story_reads (
  chat_id BIGINT NOT NULL,
  story_id TEXT NOT NULL,
  read_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  PRIMARY KEY (chat_id, story_id)
);

CREATE TABLE IF NOT EXISTS word_comments (
  id SERIAL PRIMARY KEY,
  chat_id BIGINT NOT NULL,
  word_id TEXT NOT NULL,
  comment TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_word_comments_word ON word_comments (word_id, created_at DESC);
