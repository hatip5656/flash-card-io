--liquibase formatted sql

--changeset dialogue-voices:1 splitStatements:true
INSERT INTO learning_settings (setting_key, value, description) VALUES
  ('generator.speech_tempo', '0.9', 'Speed of generated listening audio (1 = as the TTS speaks; 0.9 = 10% slower, pitch unchanged)')
ON CONFLICT (setting_key) DO NOTHING;

--changeset dialogue-voices:2 splitStatements:true
-- Dialogues may have two or three people. Only touches the seeded blueprint text, not an edited one.
UPDATE test_blueprints
SET spec = jsonb_set(spec, '{tasks,0,speakers}', '"2-3"'),
    prompt_template = replace(prompt_template,
      'Give every speaker a gender ("f" or "m") so the right voice is used.',
      'Dialogues have two or three people with clearly different roles and personalities (for example a customer, a shop assistant and a friend; a young person and an older one). Give every speaker a name and a gender ("f" or "m"); each speaker gets a voice of their own.'),
    updated_at = NOW()
WHERE id = 'listening-standard'
  AND prompt_template LIKE '%Give every speaker a gender ("f" or "m") so the right voice is used.%';
