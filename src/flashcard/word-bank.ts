import { readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";
import type { CefrLevel } from "../config.js";
import type { Word } from "./types.js";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "..", "..", "data", "words");

let words: Word[] = [];

export function loadWordBank(): void {
  words = [];
  const levels: CefrLevel[] = ["A1", "A2", "B1", "B2"];

  for (const level of levels) {
    const filePath = join(DATA_DIR, `${level.toLowerCase()}.json`);
    try {
      const raw = readFileSync(filePath, "utf-8");
      const parsed = JSON.parse(raw) as Word[];
      words.push(...parsed);
      console.error(`[word-bank] Loaded ${parsed.length} ${level} words`);
    } catch {
      console.error(`[word-bank] No word file for ${level} (${filePath})`);
    }
  }

  console.error(`[word-bank] Total: ${words.length} words loaded`);
}

export function getWordsForLevel(level: CefrLevel): Word[] {
  return words.filter((w) => w.cefrLevel === level);
}

export function getWordById(id: string): Word | undefined {
  return words.find((w) => w.id === id);
}

export function getUnsent(level: CefrLevel, sentIds: string[]): Word[] {
  const sent = new Set(sentIds);
  return words.filter((w) => w.cefrLevel === level && !sent.has(w.id));
}

export function getAllWords(): Word[] {
  return words;
}
