-- Enforce unique Estonian words at DB level (prevent duplicates across levels)
-- First, clean up any existing duplicates by keeping the one with lowest cefr_level
DELETE FROM word_sentences WHERE word_id IN (
  SELECT w.id FROM words w
  WHERE w.estonian IN (
    SELECT estonian FROM words GROUP BY estonian HAVING COUNT(*) > 1
  )
  AND w.id NOT IN (
    SELECT DISTINCT ON (estonian) id FROM words ORDER BY estonian, cefr_level
  )
);

DELETE FROM words WHERE id IN (
  SELECT w.id FROM words w
  WHERE w.estonian IN (
    SELECT estonian FROM words GROUP BY estonian HAVING COUNT(*) > 1
  )
  AND w.id NOT IN (
    SELECT DISTINCT ON (estonian) id FROM words ORDER BY estonian, cefr_level
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_words_estonian_unique ON words (estonian);
