-- Performance indexes identified by backend audit
-- Covers hot query paths for 1000+ users

-- sent_words: SM-2 review scheduling
CREATE INDEX IF NOT EXISTS idx_sent_words_next_review
  ON sent_words (chat_id, next_review)
  WHERE next_review IS NOT NULL;

-- sent_words: feed recency sort
CREATE INDEX IF NOT EXISTS idx_sent_words_sent_at
  ON sent_words (chat_id, sent_at DESC);

-- sent_words: updateSm2, incrementQuizCount
CREATE INDEX IF NOT EXISTS idx_sent_words_word_value
  ON sent_words (chat_id, word_value)
  WHERE word_value IS NOT NULL;

-- sent_words: getWordsForReview (least-quizzed first)
CREATE INDEX IF NOT EXISTS idx_sent_words_quiz_count
  ON sent_words (chat_id, quiz_count ASC, sent_at ASC);

-- activity_log: getStreak, getTodayActivity
CREATE INDEX IF NOT EXISTS idx_activity_log_date
  ON activity_log (chat_id, activity_date DESC);

-- quiz_results: getQuizHistory, getQuizStats
CREATE INDEX IF NOT EXISTS idx_quiz_results_completed
  ON quiz_results (chat_id, completed_at DESC);

-- quiz_answers: JOIN in getQuizHistory
CREATE INDEX IF NOT EXISTS idx_quiz_answers_quiz_id
  ON quiz_answers (quiz_id);

-- subscribers: scheduler ticks (every minute)
CREATE INDEX IF NOT EXISTS idx_subscribers_delivery
  ON subscribers (next_delivery_at)
  WHERE active = TRUE AND schedule != 'off' AND next_delivery_at IS NOT NULL;

CREATE INDEX IF NOT EXISTS idx_subscribers_grammar
  ON subscribers (next_grammar_at)
  WHERE active = TRUE AND next_grammar_at IS NOT NULL;

-- word_comments: user comment lookup
CREATE INDEX IF NOT EXISTS idx_word_comments_chat
  ON word_comments (chat_id);

-- saved_words: bookmark display
CREATE INDEX IF NOT EXISTS idx_saved_words_saved_at
  ON saved_words (chat_id, saved_at DESC);

-- candidate_sentences: approval flow
CREATE INDEX IF NOT EXISTS idx_candidate_sentences_candidate
  ON candidate_sentences (candidate_id);
