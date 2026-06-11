import type pg from "pg";
import type { GrammarStory } from "./types.js";

let stories: GrammarStory[] = [];

export async function loadStoryBankFromDb(pool: pg.Pool): Promise<void> {
  const storiesRes = await pool.query(
    `SELECT id, cefr_level, topic, icon FROM grammar_stories ORDER BY id`,
  );
  const slidesRes = await pool.query(
    `SELECT s.id AS slide_id, s.story_id, s.title, s.body, s.highlight, s.sort_order
     FROM grammar_story_slides s
     ORDER BY s.story_id, s.sort_order`,
  );
  const examplesRes = await pool.query(
    `SELECT e.slide_id, e.estonian, e.english, e.turkish, e.sort_order
     FROM grammar_slide_examples e
     ORDER BY e.slide_id, e.sort_order`,
  );

  // Group examples by slide_id
  const exampleMap = new Map<number, Array<{ estonian: string; english: string; turkish?: string }>>();
  for (const row of examplesRes.rows) {
    if (!exampleMap.has(row.slide_id)) exampleMap.set(row.slide_id, []);
    exampleMap.get(row.slide_id)!.push({
      estonian: row.estonian,
      english: row.english,
      turkish: row.turkish ?? undefined,
    });
  }

  // Group slides by story_id
  const slideMap = new Map<string, Array<{ title: string; body: string; highlight?: string; examples?: Array<{ estonian: string; english: string; turkish?: string }> }>>();
  for (const row of slidesRes.rows) {
    if (!slideMap.has(row.story_id)) slideMap.set(row.story_id, []);
    const slide: any = { title: row.title, body: row.body };
    if (row.highlight) slide.highlight = row.highlight;
    const examples = exampleMap.get(row.slide_id);
    if (examples?.length) slide.examples = examples;
    slideMap.get(row.story_id)!.push(slide);
  }

  stories = storiesRes.rows.map((r) => ({
    id: r.id,
    cefrLevel: r.cefr_level,
    topic: r.topic,
    icon: r.icon,
    slides: slideMap.get(r.id) ?? [],
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
