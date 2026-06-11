import { readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";
import type { GrammarStory } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const STORIES_PATH = join(__dirname, "..", "..", "data", "grammar", "stories.json");

let stories: GrammarStory[] = [];

export function loadStoryBank(): void {
  try {
    const raw = readFileSync(STORIES_PATH, "utf-8");
    stories = JSON.parse(raw) as GrammarStory[];
    console.error(`[story-bank] Loaded ${stories.length} grammar stories`);
  } catch {
    console.error(`[story-bank] No stories file found (${STORIES_PATH})`);
  }
}

export function getStoriesForLevel(level: string): GrammarStory[] {
  return stories.filter((s) => s.cefrLevel === level);
}

export function getAllStories(): GrammarStory[] {
  return stories;
}
