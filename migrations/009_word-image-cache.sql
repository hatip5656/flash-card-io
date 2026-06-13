-- Cache image URLs directly on words table to avoid external API calls during feed
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_url TEXT;
ALTER TABLE words ADD COLUMN IF NOT EXISTS image_photographer TEXT;
