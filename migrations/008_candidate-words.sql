-- Candidate words: discovered from Ekilex, pending Turkish translation and approval

CREATE TABLE IF NOT EXISTS candidate_words (
  id SERIAL PRIMARY KEY,
  estonian TEXT NOT NULL UNIQUE,
  english TEXT NOT NULL,
  cefr_level TEXT NOT NULL,
  pos TEXT,
  discovered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  turkish TEXT,
  translated_at TIMESTAMPTZ,
  status TEXT NOT NULL DEFAULT 'pending'  -- pending, translated, approved, rejected
);

CREATE INDEX IF NOT EXISTS idx_candidate_status ON candidate_words (status, cefr_level);

CREATE TABLE IF NOT EXISTS candidate_sentences (
  id SERIAL PRIMARY KEY,
  candidate_id INTEGER NOT NULL REFERENCES candidate_words(id) ON DELETE CASCADE,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL,
  turkish TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);
