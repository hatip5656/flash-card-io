--liquibase formatted sql

--changeset orphan-sent-words:1 splitStatements:false
-- Word ids carry their level (a2-telefon); re-levelled or renamed words left learners' sent_words rows
-- pointing at ids that no longer exist. Those rows could not be shown in the feed and, sorted first by the
-- review query, emptied it. Where the same Estonian word exists under exactly one current id, move the row
-- there (keeping its review history), unless the learner already has a row for that id.
WITH orphans AS (
    SELECT s.chat_id, s.word_id AS old_id, MIN(w.id) AS new_id
    FROM sent_words s
    JOIN words w ON lower(w.estonian) = lower(s.word_value)
    WHERE s.word_value IS NOT NULL
      AND NOT EXISTS (SELECT 1 FROM words cur WHERE cur.id = s.word_id)
    GROUP BY s.chat_id, s.word_id
    HAVING COUNT(DISTINCT w.id) = 1
),
targets AS (
    -- two orphans of one learner can map to the same new id: move only one
    SELECT chat_id, old_id, new_id,
           ROW_NUMBER() OVER (PARTITION BY chat_id, new_id ORDER BY old_id) AS rn
    FROM orphans o
    WHERE NOT EXISTS (SELECT 1 FROM sent_words t WHERE t.chat_id = o.chat_id AND t.word_id = o.new_id)
)
UPDATE sent_words s SET word_id = t.new_id
FROM targets t
WHERE t.rn = 1 AND s.chat_id = t.chat_id AND s.word_id = t.old_id;
