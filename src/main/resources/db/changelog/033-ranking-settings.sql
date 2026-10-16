--liquibase formatted sql

--changeset ranking-settings:1 splitStatements:true
-- How the planner ranks what to work on and mixes the feed (values the code used to hardcode).
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('ranking.exam_weak_base', '4', 'Priority of a grammar topic the learner missed in an exam and is weak at (highest wins)'),
  ('ranking.practice_weak_base', '3', 'Priority of a topic weak in practice'),
  ('ranking.recheck_base', '2', 'Priority of a topic whose recheck is due'),
  ('ranking.untested_base', '1.5', 'Priority of a topic the learner has read but never been tested on'),
  ('ranking.next_up_base', '1', 'Priority of the next unread topic in teaching order'),
  ('ranking.weakness_weight', '1', 'Added priority per point of missing mastery (× (1 − mastery)) for weak and due topics'),
  ('ranking.earlier_level_bonus', '0.1', 'Added priority per level a new topic is below the learner''s level (shore up foundations first)'),
  ('ranking.teaching_order_step', '0.001', 'Priority lost per position in teaching order, so earlier lessons come first among equals'),
  ('plan.practice_candidate_window', '5', 'How many top-ranked topics may supply practice questions when the focus topic has too few'),
  ('plan.practice_per_exam', '2', 'Grammar practice items shown before each exam item when mixing a plan'),
  ('exam_pick.random_jitter', '0.5', 'Largest random bonus when choosing exam questions, so the feed does not repeat the same order'),
  ('exam_pick.focus_rule_bonus', '2', 'Bonus for an exam question tagged with the plan''s focus grammar topic'),
  ('exam_pick.exam_skill_weight', '1', 'Bonus × (1 − accuracy) of the question''s exam skill (reading, listening, …): weaker skills come first'),
  ('exam_pick.sub_skill_weight', '0.5', 'Bonus × (1 − accuracy) of the question''s tagged sub-skill (vocabulary, detail, …)'),
  ('exam_pick.unknown_skill_accuracy', '0.5', 'Accuracy assumed for a skill the learner has not answered yet'),
  ('feed.review_every', '3', 'New words per due review when reviews are mixed into a feed page')
ON CONFLICT (setting_key) DO NOTHING;
