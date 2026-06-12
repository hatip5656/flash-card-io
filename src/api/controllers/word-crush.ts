import type { Request, Response } from "express";
import { getSubscriberLevel, getPreferences } from "../../db/progress.js";
import { getWordsForLevel, getAllWords } from "../../flashcard/word-bank.js";
import { shuffle } from "../../utils.js";
import type { CefrLevel } from "../../config.js";

interface CrushWord {
  estonian: string;
  english: string;
  turkish?: string;
  cefrLevel: string;
}

const DIFFICULTY_CONFIG: Record<string, { gridSize: number; wordCount: number; timer: number; levels: CefrLevel[] }> = {
  easy:   { gridSize: 8,  wordCount: 8,  timer: 120, levels: ["A1"] },
  medium: { gridSize: 10, wordCount: 12, timer: 90,  levels: ["A1", "A2"] },
  hard:   { gridSize: 12, wordCount: 16, timer: 75,  levels: ["A1", "A2", "B1"] },
  expert: { gridSize: 14, wordCount: 20, timer: 60,  levels: ["A1", "A2", "B1", "B2"] },
};

/**
 * GET /api/mobile/word-crush?difficulty=easy
 *
 * Returns words for the grid and game config.
 * The grid generation happens client-side for zero-latency gameplay.
 */
export async function getWordCrushData(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const difficulty = (req.query.difficulty as string) || "easy";
  const config = DIFFICULTY_CONFIG[difficulty] ?? DIFFICULTY_CONFIG.easy;

  const prefs = await getPreferences(chatId);
  const useTurkish = prefs.nativeLanguage === "turkish";

  // Gather words from allowed levels
  const pool: CrushWord[] = [];
  for (const level of config.levels) {
    const words = getWordsForLevel(level);
    for (const w of words) {
      // Only include words that fit in the grid (length <= gridSize)
      if (w.estonian.length <= config.gridSize && w.estonian.length >= 3 && !w.estonian.includes(" ")) {
        pool.push({
          estonian: w.estonian.toLowerCase(),
          english: w.english,
          turkish: w.turkish,
          cefrLevel: w.cefrLevel,
        });
      }
    }
  }

  // Select words — prefer shorter for easy, mix for harder
  const shuffled = shuffle([...pool]);
  // Pick a good mix: some short (3-4), some medium (5-6), some longer (7+)
  const short = shuffled.filter(w => w.estonian.length <= 4);
  const medium = shuffled.filter(w => w.estonian.length >= 5 && w.estonian.length <= 6);
  const long = shuffled.filter(w => w.estonian.length >= 7);

  const selected: CrushWord[] = [];
  const targetCount = config.wordCount;

  // 40% short, 40% medium, 20% long
  const shortCount = Math.ceil(targetCount * 0.4);
  const mediumCount = Math.ceil(targetCount * 0.4);
  const longCount = targetCount - shortCount - mediumCount;

  selected.push(...short.slice(0, shortCount));
  selected.push(...medium.slice(0, mediumCount));
  selected.push(...long.slice(0, longCount));

  // Fill remaining if not enough in any category
  const selectedSet = new Set(selected.map(w => w.estonian));
  for (const w of shuffled) {
    if (selected.length >= targetCount) break;
    if (!selectedSet.has(w.estonian)) {
      selected.push(w);
      selectedSet.add(w.estonian);
    }
  }

  // Also provide a larger "valid words" set for free-form discovery
  const validWords: Record<string, { english: string; turkish?: string; cefrLevel: string }> = {};
  for (const w of pool) {
    validWords[w.estonian] = { english: w.english, turkish: w.turkish, cefrLevel: w.cefrLevel };
  }

  res.json({
    difficulty,
    gridSize: config.gridSize,
    timer: config.timer,
    words: selected,
    validWords,
    nativeLanguage: prefs.nativeLanguage,
  });
}
