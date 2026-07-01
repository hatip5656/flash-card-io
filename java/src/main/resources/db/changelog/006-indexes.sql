--liquibase formatted sql

--changeset flashcard:024 splitStatements:true
-- activity_log indexes
CREATE INDEX IF NOT EXISTS idx_activity_log_chat_date ON activity_log (chat_id, activity_date DESC);
CREATE INDEX IF NOT EXISTS idx_activity_log_date ON activity_log (activity_date DESC);

--changeset flashcard:025 splitStatements:true
-- word_comments indexes
CREATE INDEX IF NOT EXISTS idx_word_comments_word ON word_comments (word_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_word_comments_chat ON word_comments (chat_id);

--changeset flashcard:026 splitStatements:true
-- words indexes
CREATE INDEX IF NOT EXISTS idx_words_cefr ON words (cefr_level);
CREATE UNIQUE INDEX IF NOT EXISTS idx_words_estonian_unique ON words (estonian);

--changeset flashcard:027 splitStatements:true
-- word_sentences indexes
CREATE INDEX IF NOT EXISTS idx_word_sentences_word ON word_sentences (word_id, sort_order);

--changeset flashcard:028 splitStatements:true
-- grammar indexes
CREATE INDEX IF NOT EXISTS idx_grammar_stories_cefr ON grammar_stories (cefr_level);
CREATE INDEX IF NOT EXISTS idx_grammar_slides_story ON grammar_story_slides (story_id, sort_order);

--changeset flashcard:029 splitStatements:true
-- sent_words indexes
CREATE INDEX IF NOT EXISTS idx_sent_words_next_review ON sent_words (chat_id, next_review) WHERE next_review <= CURRENT_DATE;
CREATE INDEX IF NOT EXISTS idx_sent_words_sent_at ON sent_words (chat_id, sent_at DESC);
CREATE INDEX IF NOT EXISTS idx_sent_words_quiz_count ON sent_words (chat_id, quiz_count ASC);
CREATE INDEX IF NOT EXISTS idx_sent_words_feed ON sent_words (chat_id, last_fed_at ASC NULLS FIRST);
CREATE INDEX IF NOT EXISTS idx_sent_words_mastered ON sent_words (chat_id) WHERE mastered = TRUE;

--changeset flashcard:030 splitStatements:true
-- subscribers indexes
CREATE INDEX IF NOT EXISTS idx_subscribers_delivery ON subscribers (next_delivery_at) WHERE active = TRUE AND schedule != 'off';
CREATE INDEX IF NOT EXISTS idx_subscribers_grammar ON subscribers (next_grammar_at) WHERE active = TRUE;

--changeset flashcard:031 splitStatements:true
-- quiz indexes
CREATE INDEX IF NOT EXISTS idx_quiz_results_chat ON quiz_results (chat_id, completed_at DESC);
CREATE INDEX IF NOT EXISTS idx_quiz_answers_quiz ON quiz_answers (quiz_id);

--changeset flashcard:032 splitStatements:true
-- saved_words indexes
CREATE INDEX IF NOT EXISTS idx_saved_words_chat ON saved_words (chat_id, saved_at DESC);

--changeset flashcard:033 splitStatements:true
-- candidate indexes
CREATE INDEX IF NOT EXISTS idx_candidate_status ON candidate_words (status, cefr_level);
CREATE INDEX IF NOT EXISTS idx_candidate_sentences_candidate ON candidate_sentences (candidate_id);

--changeset flashcard:034 splitStatements:true
-- dialog indexes
CREATE INDEX IF NOT EXISTS idx_dialogs_level ON dialogs (cefr_level);
CREATE INDEX IF NOT EXISTS idx_dialog_lines_dialog ON dialog_lines (dialog_id, sort_order);

--changeset flashcard:035 splitStatements:true
-- quiz_sessions index
CREATE INDEX IF NOT EXISTS idx_quiz_sessions_created ON quiz_sessions (created_at);
