-- Normalize grammar_stories: extract slides JSONB into proper tables

CREATE TABLE IF NOT EXISTS grammar_story_slides (
  id SERIAL PRIMARY KEY,
  story_id TEXT NOT NULL REFERENCES grammar_stories(id) ON DELETE CASCADE,
  title TEXT NOT NULL,
  body TEXT NOT NULL,
  highlight TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_grammar_slides_story ON grammar_story_slides (story_id, sort_order);

CREATE TABLE IF NOT EXISTS grammar_slide_examples (
  id SERIAL PRIMARY KEY,
  slide_id INTEGER NOT NULL REFERENCES grammar_story_slides(id) ON DELETE CASCADE,
  estonian TEXT NOT NULL,
  english TEXT NOT NULL,
  turkish TEXT,
  sort_order INTEGER NOT NULL DEFAULT 0
);

-- Migrate existing JSONB data into normalized tables
INSERT INTO grammar_story_slides (story_id, title, body, highlight, sort_order)
SELECT
  gs.id,
  slide->>'title',
  slide->>'body',
  slide->>'highlight',
  idx
FROM grammar_stories gs,
     jsonb_array_elements(gs.slides) WITH ORDINALITY AS t(slide, idx);

INSERT INTO grammar_slide_examples (slide_id, estonian, english, turkish, sort_order)
SELECT
  gss.id,
  ex->>'estonian',
  ex->>'english',
  ex->>'turkish',
  ex_idx
FROM grammar_story_slides gss
JOIN grammar_stories gs ON gs.id = gss.story_id,
     jsonb_array_elements(gs.slides) WITH ORDINALITY AS t(slide, slide_idx),
     jsonb_array_elements(slide->'examples') WITH ORDINALITY AS e(ex, ex_idx)
WHERE slide_idx = gss.sort_order
  AND slide->'examples' IS NOT NULL;

-- Drop the JSONB column
ALTER TABLE grammar_stories DROP COLUMN slides;
