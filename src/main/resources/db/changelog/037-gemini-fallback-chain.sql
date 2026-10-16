--liquibase formatted sql

--changeset gemini-fallback-chain:1 splitStatements:true
-- One fallback was not enough: when gemini-3.8-flash was overloaded, so was the old preview model.
-- Different models have separate capacity, and Flash-Lite is the least contested.
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('llm.gemini_model_fallbacks', '["gemini-3.6-flash", "gemini-3.5-flash-lite", "gemini-3-flash-preview"]',
   'Gemini models tried in order when the configured model is overloaded, rate-limited or refused')
ON CONFLICT (setting_key) DO NOTHING;
DELETE FROM learning_settings WHERE setting_key = 'llm.gemini_model_fallback';
