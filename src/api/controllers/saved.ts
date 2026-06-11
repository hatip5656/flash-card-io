import type { Request, Response } from "express";
import { saveWord, unsaveWord, getSavedWordIds } from "../../db/progress.js";
import { getWordById } from "../../flashcard/word-bank.js";

export async function getSavedWords(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const wordIds = await getSavedWordIds(chatId);
  const words = wordIds
    .map((id) => getWordById(id))
    .filter(Boolean)
    .map((w) => ({
      id: w!.id,
      estonian: w!.estonian,
      english: w!.english,
      turkish: w!.turkish,
      cefrLevel: w!.cefrLevel,
      sentences: w!.sentences,
    }));
  res.json(words);
}

export async function addSavedWord(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const wordId = req.params.wordId as string;
  await saveWord(chatId, wordId);
  res.json({ saved: true, wordId });
}

export async function removeSavedWord(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const wordId = req.params.wordId as string;
  await unsaveWord(chatId, wordId);
  res.json({ saved: false, wordId });
}
