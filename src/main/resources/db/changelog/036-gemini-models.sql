--liquibase formatted sql

--changeset gemini-models:1 splitStatements:true
-- Which Gemini model each feature uses, so a model upgrade is a settings change rather than a deploy.
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('llm.gemini_model_chat', '"gemini-3.8-flash"', 'Gemini model for the chatbot, the Telegram bot and personal podcasts'),
  ('llm.gemini_model_generation', '"gemini-3.8-flash"', 'Gemini model for generated tests (JSON mode)'),
  ('llm.gemini_model_fallback', '"gemini-3-flash-preview"', 'Gemini model tried once when the configured model is refused (not found or not available to the key)')
ON CONFLICT (setting_key) DO NOTHING;
