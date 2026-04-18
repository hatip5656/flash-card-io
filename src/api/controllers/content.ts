import type { Request, Response } from "express";
import { getAllCategories } from "../../flashcard/categories.js";
import { getWordsForLevel } from "../../flashcard/word-bank.js";
import { VALID_LEVELS } from "../../config.js";
import { IDIOMS } from "../../data/idioms.js";

export async function getCategories(_req: Request, res: Response): Promise<void> {
  res.json(getAllCategories());
}

export async function getRandomIdiom(_req: Request, res: Response): Promise<void> {
  res.json(IDIOMS[Math.floor(Math.random() * IDIOMS.length)]);
}

export async function getIdioms(_req: Request, res: Response): Promise<void> {
  res.json(IDIOMS);
}

export async function getLevels(_req: Request, res: Response): Promise<void> {
  const levels = VALID_LEVELS.map((level) => ({
    level,
    wordCount: getWordsForLevel(level).length,
  }));
  res.json(levels);
}
