import { readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";
import type { GrammarLesson } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const LESSONS_PATH = join(__dirname, "..", "..", "data", "grammar", "lessons.json");

let lessons: GrammarLesson[] = [];

export function loadGrammarBank(): void {
  try {
    const raw = readFileSync(LESSONS_PATH, "utf-8");
    lessons = JSON.parse(raw) as GrammarLesson[];
    console.error(`[grammar-bank] Loaded ${lessons.length} grammar lessons`);
  } catch {
    console.error(`[grammar-bank] No grammar lessons file found (${LESSONS_PATH})`);
  }
}

export function getLessonsForLevel(level: string): GrammarLesson[] {
  return lessons.filter((l) => l.cefrLevel === level);
}

export function getRandomLesson(level: string, sentIds: Set<string>): GrammarLesson | null {
  const available = lessons.filter((l) => l.cefrLevel === level && !sentIds.has(l.id));
  if (available.length === 0) {
    // If all lessons for this level are sent, allow re-sending
    const all = lessons.filter((l) => l.cefrLevel === level);
    if (all.length === 0) return null;
    return all[Math.floor(Math.random() * all.length)];
  }
  return available[Math.floor(Math.random() * available.length)];
}

export function getAllLessons(): GrammarLesson[] {
  return lessons;
}
