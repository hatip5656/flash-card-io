import { readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const CATEGORIES_PATH = join(__dirname, "..", "..", "data", "categories.json");

interface Category {
  label: string;
  emoji: string;
  words: string[];
}

let categories: Record<string, Category> = {};

export function loadCategories(): void {
  try {
    const raw = readFileSync(CATEGORIES_PATH, "utf-8");
    categories = JSON.parse(raw) as Record<string, Category>;
    console.error(`[categories] Loaded ${Object.keys(categories).length} categories`);
  } catch {
    console.error(`[categories] No categories file found (${CATEGORIES_PATH})`);
  }
}

export function getAllCategories(): Array<{ key: string; label: string; emoji: string; wordCount: number }> {
  return Object.entries(categories).map(([key, cat]) => ({
    key,
    label: cat.label,
    emoji: cat.emoji,
    wordCount: cat.words.length,
  }));
}

export function getCategoryWords(key: string): string[] {
  return categories[key]?.words ?? [];
}

export function getCategoryForWord(word: string): { key: string; label: string; emoji: string } | null {
  for (const [key, cat] of Object.entries(categories)) {
    if (cat.words.includes(word.toLowerCase())) {
      return { key, label: cat.label, emoji: cat.emoji };
    }
  }
  return null;
}
