import type { Request, Response } from "express";
import { getSubscriberLevel, getSentWordIds, getWordsDueForReview, getSavedWordIds, markWordSent, logWordActivity } from "../../db/progress.js";
import { getUnsent, getWordById } from "../../flashcard/word-bank.js";
import { searchPhoto, triggerDownload } from "../../services/unsplash.js";
import { searchPexelsPhoto } from "../../services/pexels.js";
import type { Word } from "../../flashcard/types.js";

interface FeedWord {
  id: string;
  estonian: string;
  english: string;
  turkish?: string;
  cefrLevel: string;
  sentences: Array<{ estonian: string; english: string; turkish?: string }>;
}

interface FeedItem {
  word: FeedWord;
  imageUrl: string | null;
  photographer: string | null;
  isSaved: boolean;
  isNew: boolean;
}

interface FeedResponse {
  items: FeedItem[];
  nextCursor: string | null;
  hasMore: boolean;
}

async function fetchImage(
  query: string,
  unsplashKey: string | undefined,
  pexelsKey: string | undefined,
): Promise<{ url: string; photographer: string } | null> {
  // Try Pexels first (more reliable free tier)
  if (pexelsKey) {
    const photo = await searchPexelsPhoto(query, pexelsKey);
    if (photo) return { url: photo.url, photographer: photo.photographer };
  }

  // Fallback to Unsplash
  if (unsplashKey) {
    const photo = await searchPhoto(query, unsplashKey);
    if (photo) {
      triggerDownload(photo.downloadUrl, unsplashKey).catch(() => {});
      return { url: photo.url, photographer: photo.photographer };
    }
  }

  return null;
}

export async function getFeed(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const limit = Math.min(Number(req.query.limit) || 10, 20);
  const cursor = (req.query.cursor as string) || "new:0";
  const unsplashKey = req.app.get("unsplashAccessKey") as string | undefined;
  const pexelsKey = req.app.get("pexelsApiKey") as string | undefined;

  const [cursorMode, cursorOffset] = parseCursor(cursor);
  const [level, sentIds, savedIdsList] = await Promise.all([
    getSubscriberLevel(chatId),
    getSentWordIds(chatId),
    getSavedWordIds(chatId),
  ]);
  const savedIds = new Set(savedIdsList);

  const unsent = getUnsent(level, sentIds);
  const rawItems: Array<{ word: Word; isNew: boolean }> = [];
  let nextMode = cursorMode;
  let nextOffset = cursorOffset;

  if (cursorMode === "new") {
    const slice = unsent.slice(cursorOffset, cursorOffset + limit);
    for (const word of slice) {
      rawItems.push({ word, isNew: true });
    }
    nextOffset = cursorOffset + slice.length;

    if (rawItems.length < limit) {
      nextMode = "review";
      nextOffset = 0;
      const reviewNeeded = limit - rawItems.length;
      const reviewWords = await getWordsDueForReview(chatId, reviewNeeded);
      for (const rw of reviewWords) {
        const fullWord = getWordById(rw.wordId);
        if (fullWord) rawItems.push({ word: fullWord, isNew: false });
      }
      nextOffset = reviewWords.length;
    }
  } else {
    const reviewWords = await getWordsDueForReview(chatId, limit + cursorOffset);
    const slice = reviewWords.slice(cursorOffset, cursorOffset + limit);
    for (const rw of slice) {
      const fullWord = getWordById(rw.wordId);
      if (fullWord) rawItems.push({ word: fullWord, isNew: false });
    }
    nextOffset = cursorOffset + slice.length;
  }

  // Fetch images in parallel
  const items: FeedItem[] = await Promise.all(
    rawItems.map(async ({ word, isNew }) => {
      const query = word.imageQuery ?? word.english;
      const img = await fetchImage(query, unsplashKey, pexelsKey);

      return {
        word: {
          id: word.id,
          estonian: word.estonian,
          english: word.english,
          turkish: word.turkish,
          cefrLevel: word.cefrLevel,
          sentences: word.sentences,
        },
        imageUrl: img?.url ?? null,
        photographer: img?.photographer ?? null,
        isSaved: savedIds.has(word.id),
        isNew,
      };
    }),
  );

  const hasMore = items.length === limit;
  const nextCursor = hasMore ? `${nextMode}:${nextOffset}` : null;

  res.json({ items, nextCursor, hasMore } satisfies FeedResponse);
}

export async function markSeen(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const wordId = req.params.wordId as string;
  const { estonian, english } = req.body ?? {};
  await markWordSent(chatId, wordId, estonian ?? null, english ?? null);
  await logWordActivity(chatId);
  res.json({ seen: true, wordId });
}

function parseCursor(cursor: string): ["new" | "review", number] {
  const [mode, offsetStr] = cursor.split(":");
  return [
    mode === "review" ? "review" : "new",
    Math.max(0, parseInt(offsetStr, 10) || 0),
  ];
}
