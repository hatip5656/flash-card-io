--liquibase formatted sql

--changeset learning-settings:1 splitStatements:true
-- Tuning values of the learner model, planner and feed. The code reads them from here (cached briefly) and
-- has no defaults of its own; change them with PUT /api/admin/learning-settings/{key}.
CREATE TABLE IF NOT EXISTS learning_settings (
    setting_key TEXT PRIMARY KEY,
    value JSONB NOT NULL,
    description TEXT NOT NULL,
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

--changeset learning-settings:2 splitStatements:true
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('readiness.prior_answers', '4', 'Pseudo-answers at 50% added to every skill estimate, so a few lucky answers do not look like readiness'),
  ('readiness.min_evidence', '10', 'Answers a skill needs before its estimate counts (fewer = not_enough_data)'),
  ('readiness.mock_evidence', '20', 'Answers a skill needs before its goal exam is recommended'),
  ('readiness.mock_threshold', '0.7', 'Estimated score (0..1) at which a skill''s goal exam is recommended'),
  ('grammar.weak_below', '0.6', 'Grammar mastery (0..1) below which a topic counts as weak'),
  ('grammar.practice_weak_min_attempts', '3', 'Practice attempts before a low-mastery topic is planned as practice_weak'),
  ('grammar.summary_weak_min_attempts', '2', 'Attempts before a low-mastery topic is listed as weak in readiness'),
  ('grammar.mastered_score', '0.8', 'Mastery (0..1) at which a topic counts as mastered in readiness'),
  ('grammar.mastered_min_attempts', '3', 'Attempts a topic needs before it can count as mastered'),
  ('mastery.alpha_floor', '0.2', 'Smallest weight of the newest answer in the running mastery mean (0.2 ≈ last 5 answers dominate)'),
  ('mastery.recheck_mid_from', '0.6', 'Mastery from which a topic is rechecked after recheck_days_mid instead of recheck_days_weak'),
  ('mastery.recheck_strong_from', '0.8', 'Mastery from which a topic is rechecked after recheck_days_strong'),
  ('mastery.recheck_days_weak', '1', 'Days until a weak topic is checked again'),
  ('mastery.recheck_days_mid', '3', 'Days until a middling topic is checked again'),
  ('mastery.recheck_days_strong', '7', 'Days until a strong topic is checked again'),
  ('plan.level_order', '["A1", "A2", "B1", "B2", "C1"]', 'CEFR levels from lowest to highest, as the planner ranks them'),
  ('plan.practice_items', '3', 'Grammar practice questions per plan'),
  ('plan.exam_items', '2', 'Exam practice questions per plan'),
  ('plan.exam_retry_after_days', '3', 'Days before a missed exam question comes back for a retry'),
  ('plan.exam_recent_days', '30', 'Days an answered exam question stays out of the feed'),
  ('vocabulary.ready_share', '0.6', 'Share of the level''s words that must be strong for vocabulary to count as ready'),
  ('vocabulary.ready_avg_ease', '2.0', 'Average SM-2 ease the strong words need for vocabulary to count as ready'),
  ('feed.review_share', '0.34', 'Largest share of a new-words feed page given to due reviews'),
  ('feed.review_cooldown_hours', '12', 'Hours before a word shown in the feed can come back as a review')
ON CONFLICT (setting_key) DO NOTHING;
