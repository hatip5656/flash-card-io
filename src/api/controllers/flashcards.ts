import type { Request, Response } from "express";
import { getSubscriberLevel, markWordSent, logWordActivity, getSentWordIds, getWordsDueForReview, updateSm2, getSentGrammarIds, markGrammarSent } from "../../db/progress.js";
import { popPrebuilt } from "../../services/prebuild.js";
import type { Flashcard } from "../../flashcard/types.js";
import { getRandomLesson } from "../../flashcard/grammar-bank.js";

/** TTL audio cache — stores last audio per user for 5 minutes. */
const audioCache = new Map<number, { buffer: Buffer; expires: number }>();
const AUDIO_TTL = 5 * 60 * 1000;

function storeAudio(chatId: number, buffer: Buffer): void {
  audioCache.set(chatId, { buffer, expires: Date.now() + AUDIO_TTL });
  // Evict expired entries periodically
  if (audioCache.size > 100) {
    const now = Date.now();
    for (const [id, entry] of audioCache) {
      if (entry.expires < now) audioCache.delete(id);
    }
  }
}

/** Serialize flashcard for JSON (strip Buffer). */
function serializeFlashcard(fc: Flashcard): Record<string, unknown> {
  return {
    word: fc.word,
    sentence: fc.sentence,
    imageUrl: fc.imageUrl,
    photographer: fc.photographer,
    photographerUrl: fc.photographerUrl,
    caption: fc.caption,
    hasAudio: fc.audio !== null,
  };
}

export async function getNextFlashcard(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const level = await getSubscriberLevel(chatId);
  const sentIds = new Set(await getSentWordIds(chatId));

  // Try pre-built queue (skip already-sent words)
  let prebuilt = await popPrebuilt(chatId, level);
  while (prebuilt && sentIds.has(prebuilt.wordId)) {
    prebuilt = await popPrebuilt(chatId, level);
  }

  if (prebuilt) {
    await markWordSent(chatId, prebuilt.wordId, prebuilt.wordValue, prebuilt.english);
    await logWordActivity(chatId);
    if (prebuilt.flashcard.audio) storeAudio(chatId, prebuilt.flashcard.audio);
    res.json({
      source: "prebuilt",
      flashcard: serializeFlashcard(prebuilt.flashcard),
      audioUrl: prebuilt.flashcard.audio ? "/api/flashcards/audio/latest" : null,
    });
    return;
  }

  // Live build via the app's deliverFlashcard function
  const buildFn = req.app.get("buildFlashcardFn") as ((chatId: number) => Promise<Flashcard | null>) | undefined;
  if (buildFn) {
    const fc = await buildFn(chatId);
    if (fc) {
      if (fc.audio) storeAudio(chatId, fc.audio);
      res.json({
        source: "live",
        flashcard: serializeFlashcard(fc),
        audioUrl: fc.audio ? "/api/flashcards/audio/latest" : null,
      });
      return;
    }
  }

  res.status(503).json({ error: "Unable to build flashcard. Try again later." });
}

export async function getAudio(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const entry = audioCache.get(chatId);
  if (!entry || entry.expires < Date.now()) {
    audioCache.delete(chatId);
    res.status(404).json({ error: "No audio available" });
    return;
  }
  res.set("Content-Type", "audio/ogg");
  res.set("Content-Disposition", "inline; filename=pronunciation.ogg");
  res.send(entry.buffer);
}

export async function getGrammarCard(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const level = await getSubscriberLevel(chatId);
  const sentIds = await getSentGrammarIds(chatId);
  const lesson = getRandomLesson(level, sentIds);

  if (!lesson) {
    res.status(404).json({ error: "No grammar lessons available for your level" });
    return;
  }

  await markGrammarSent(chatId, lesson.id);
  res.json({
    id: lesson.id,
    topic: lesson.topic,
    cefrLevel: lesson.cefrLevel,
    content: lesson.content,
  });
}

export async function getDueWords(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const limit = Math.min(Number(req.query.limit) || 10, 50);
  const words = await getWordsDueForReview(chatId, limit);
  res.json(words);
}

export async function submitRecall(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const { wordValue, quality } = req.body;
  if (!wordValue || quality === undefined || quality < 0 || quality > 5) {
    res.status(400).json({ error: "wordValue and quality (0-5) are required" });
    return;
  }
  await updateSm2(chatId, wordValue, quality);
  res.json({ wordValue, quality, updated: true });
}
