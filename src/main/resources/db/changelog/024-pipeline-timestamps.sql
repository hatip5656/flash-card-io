--liquibase formatted sql

--changeset pipeline-timestamps:1
ALTER TABLE podcast_videos ADD COLUMN IF NOT EXISTS started_at TIMESTAMPTZ;

--changeset pipeline-timestamps:2
ALTER TABLE grammar_lessons ADD COLUMN IF NOT EXISTS script_updated_at TIMESTAMPTZ;
