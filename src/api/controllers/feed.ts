import type { Request, Response } from "express";
import { getSubscriberLevel, getSentWordIds, getSeenWordsByRecency, getSavedWordIds, markWordSent, logWordActivity, getPool } from "../../db/progress.js";
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
      const seenWords = await getSeenWordsByRecency(chatId, reviewNeeded, 0);
      for (const sw of seenWords) {
        const fullWord = getWordById(sw.wordId);
        if (fullWord) rawItems.push({ word: fullWord, isNew: false });
      }
      nextOffset = seenWords.length;
    }
  } else {
    const seenWords = await getSeenWordsByRecency(chatId, limit, cursorOffset);
    for (const sw of seenWords) {
      const fullWord = getWordById(sw.wordId);
      if (fullWord) rawItems.push({ word: fullWord, isNew: false });
    }
    nextOffset = cursorOffset + seenWords.length;
  }

  // Check DB-cached images first, fetch missing ones in parallel
  const pool = getPool();
  const wordIds = rawItems.map(r => r.word.id);
  const cachedImages = await pool.query(
    `SELECT id, image_url, image_photographer FROM words WHERE id = ANY($1)`,
    [wordIds],
  );
  const imageCache = new Map<string, { url: string; photographer: string }>();
  for (const row of cachedImages.rows) {
    if (row.image_url) imageCache.set(row.id, { url: row.image_url, photographer: row.image_photographer ?? "" });
  }

  const items: FeedItem[] = await Promise.all(
    rawItems.map(async ({ word, isNew }) => {
      let img = imageCache.get(word.id) ?? null;

      // If no cached image, fetch and save to DB in background
      if (!img) {
        const query = word.imageQuery ?? word.english;
        const fetched = await fetchImage(query, unsplashKey, pexelsKey);
        if (fetched) {
          img = fetched;
          // Save to DB for next time (fire and forget)
          pool.query(
            "UPDATE words SET image_url = $1, image_photographer = $2 WHERE id = $3",
            [fetched.url, fetched.photographer, word.id],
          ).catch(() => {});
        }
      }

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
