import { readFileSync, existsSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";
import type pg from "pg";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "..", "..", "data");

interface Word {
  id: string;
  estonian: string;
  english: string;
  turkish?: string;
  cefrLevel: string;
  imageQuery?: string;
  sentences: Array<{ estonian: string; english: string; turkish?: string }>;
}

interface Story {
  id: string;
  cefrLevel: string;
  topic: string;
  icon: string;
  slides: any[];
}

export async function seedWords(pool: pg.Pool): Promise<number> {
  // Check if already seeded
  const check = await pool.query("SELECT COUNT(*) as cnt FROM words");
  if (Number(check.rows[0].cnt) > 0) {
    console.error("[seed] Words table already has data, skipping seed");
    return 0;
  }

  let total = 0;
  for (const level of ["a1", "a2", "b1", "b2"]) {
    const filePath = join(DATA_DIR, "words", `${level}.json`);
    if (!existsSync(filePath)) continue;

    const words: Word[] = JSON.parse(readFileSync(filePath, "utf-8"));

    for (const w of words) {
      await pool.query(
        `INSERT INTO words (id, estonian, english, turkish, cefr_level, image_query)
         VALUES ($1, $2, $3, $4, $5, $6)
         ON CONFLICT (id) DO UPDATE SET
           estonian = EXCLUDED.estonian,
           english = EXCLUDED.english,
           turkish = EXCLUDED.turkish,
           cefr_level = EXCLUDED.cefr_level,
           image_query = EXCLUDED.image_query`,
        [w.id, w.estonian, w.english, w.turkish ?? null, w.cefrLevel, w.imageQuery ?? null],
      );

      for (let i = 0; i < w.sentences.length; i++) {
        const s = w.sentences[i];
        await pool.query(
          `INSERT INTO word_sentences (word_id, estonian, english, turkish, sort_order)
           VALUES ($1, $2, $3, $4, $5)`,
          [w.id, s.estonian, s.english, s.turkish ?? null, i],
        );
      }
    }
    total += words.length;
    console.error(`[seed] Loaded ${words.length} ${level.toUpperCase()} words`);
  }

  return total;
}

export async function seedStories(pool: pg.Pool): Promise<number> {
  const check = await pool.query("SELECT COUNT(*) as cnt FROM grammar_stories");
  if (Number(check.rows[0].cnt) > 0) {
    console.error("[seed] Stories table already has data, skipping seed");
    return 0;
  }

  const filePath = join(DATA_DIR, "grammar", "stories.json");
  if (!existsSync(filePath)) return 0;

  const stories: Story[] = JSON.parse(readFileSync(filePath, "utf-8"));

  for (const s of stories) {
    await pool.query(
      `INSERT INTO grammar_stories (id, cefr_level, topic, icon, slides)
       VALUES ($1, $2, $3, $4, $5::jsonb)
       ON CONFLICT (id) DO UPDATE SET
         cefr_level = EXCLUDED.cefr_level,
         topic = EXCLUDED.topic,
         icon = EXCLUDED.icon,
         slides = EXCLUDED.slides`,
      [s.id, s.cefrLevel, s.topic, s.icon, JSON.stringify(s.slides)],
    );
  }

  console.error(`[seed] Loaded ${stories.length} grammar stories`);
  return stories.length;
}
