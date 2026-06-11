import type pg from "pg";
import type { GrammarStory } from "./types.js";

let stories: GrammarStory[] = [];

export async function loadStoryBankFromDb(pool: pg.Pool): Promise<void> {
  const res = await pool.query(
    `SELECT id, cefr_level, topic, icon, slides FROM grammar_stories ORDER BY id`,
  );
  stories = res.rows.map((r) => ({
    id: r.id,
    cefrLevel: r.cefr_level,
    topic: r.topic,
    icon: r.icon,
    slides: r.slides,
  }));
  console.error(`[story-bank] Loaded ${stories.length} grammar stories from database`);
}

export { loadStoryBankFromDb as loadStoryBank };

export function getStoriesForLevel(level: string): GrammarStory[] {
  return stories.filter((s) => s.cefrLevel === level);
}

export function getAllStories(): GrammarStory[] {
  return stories;
}
