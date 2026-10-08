--liquibase formatted sql

--changeset word-audio-cache:1
ALTER TABLE words ADD COLUMN IF NOT EXISTS audio_cache_key TEXT;
