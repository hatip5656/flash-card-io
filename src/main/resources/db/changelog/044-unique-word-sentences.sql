--liquibase formatted sql

--changeset unique-word-sentences:1
-- 921 example sentences were stored twice for the same word (identical text and translations), so
-- grammar lessons listed them twice. Keep the oldest row of each, then forbid repeats.
DELETE FROM word_sentences ws
USING word_sentences keep
WHERE keep.word_id = ws.word_id
  AND keep.estonian = ws.estonian
  AND keep.id < ws.id;

CREATE UNIQUE INDEX uq_word_sentences_word_estonian ON word_sentences (word_id, estonian);
