/**
 * Pre-build queue — prepares flashcards ahead of time so /next is instant.
 * Stores pre-built flashcards on disk (JSON + audio files per user).
 */

import { readFile, writeFile, readdir, unlink, rename, stat } from "fs/promises";
import { join } from "path";
import { CACHE_DIR, ensureDir } from "./cache.js";
import type { Flashcard } from "../flashcard/types.js";
import { errMsg } from "../utils.js";

const QUEUE_DIR = join(CACHE_DIR, "prebuild");
export const QUEUE_SIZE = 5;
const STALE_MS = 24 * 60 * 60 * 1000;

export interface PrebuiltEntry {
  flashcard: Flashcard;
  wordId: string;
  wordValue: string;
  english: string;
}

interface StoredCard {
  wordId: string;
  wordValue: string;
  english: string;
  level: string;
  createdAt: number;
  audioFile?: string;
}

function userDir(chatId: number): string {
  return join(QUEUE_DIR, String(chatId));
}

/**
 * Pop one pre-built flashcard for a user. Returns null if queue is empty.
 * Uses atomic rename to prevent race conditions from concurrent /next presses.
 */
export async function popPrebuilt(chatId: number, currentLevel: string): Promise<PrebuiltEntry | null> {
  const dir = userDir(chatId);
  const files = await readdir(dir).catch(() => [] as string[]);
  const jsonFiles = files.filter(f => f.endsWith(".json")).sort();

  for (const file of jsonFiles) {
    const path = join(dir, file);
    const claimedPath = path + ".claimed";

    // Atomic claim — if another concurrent call already renamed it, this fails
    try {
      await rename(path, claimedPath);
    } catch {
      continue;
    }

    try {
      const raw = await readFile(claimedPath, "utf-8");
      const stored: StoredCard = JSON.parse(raw);

      if (Date.now() - stored.createdAt > STALE_MS || stored.level !== currentLevel) {
        await unlink(claimedPath).catch(() => {});
        if (stored.audioFile) await unlink(join(dir, stored.audioFile)).catch(() => {});
        continue;
      }

      let audio: Buffer | null = null;
      if (stored.audioFile) {
        audio = await readFile(join(dir, stored.audioFile)).catch(() => null);
        await unlink(join(dir, stored.audioFile)).catch(() => {});
      }

      await unlink(claimedPath).catch(() => {});

      // Reconstruct Flashcard — caption/imageUrl/etc stored in a sidecar
      // We store the full flashcard JSON minus audio as {id}.meta.json
      const metaPath = path.replace(".json", ".meta.json");
      let flashcardData: Omit<Flashcard, "audio">;
      try {
        flashcardData = JSON.parse(await readFile(metaPath, "utf-8"));
        await unlink(metaPath).catch(() => {});
      } catch {
        // meta file missing — skip this entry
        continue;
      }

      return {
        flashcard: { ...flashcardData, audio },
        wordId: stored.wordId,
        wordValue: stored.wordValue,
        english: stored.english,
      };
    } catch {
      await unlink(claimedPath).catch(() => {});
      continue;
    }
  }
  return null;
}

/**
 * Push a pre-built flashcard to a user's queue.
 */
export async function pushPrebuilt(chatId: number, entry: PrebuiltEntry & { level: string }): Promise<void> {
  try {
    const dir = userDir(chatId);
    await ensureDir(dir);

    const files = await readdir(dir).catch(() => [] as string[]);
    if (files.filter(f => f.endsWith(".json")).length >= QUEUE_SIZE) return;

    const ts = Date.now();
    const id = `${ts}-${Math.random().toString(36).slice(2, 8)}`;

    // Store audio as separate binary file
    let audioFile: string | undefined;
    if (entry.flashcard.audio) {
      audioFile = `${id}.ogg`;
      await writeFile(join(dir, audioFile), entry.flashcard.audio);
    }

    // Store flashcard metadata (everything except audio buffer)
    const { audio: _audio, ...flashcardMeta } = entry.flashcard;
    await writeFile(join(dir, `${id}.meta.json`), JSON.stringify(flashcardMeta));

    // Store queue entry (word info + pointers)
    const stored: StoredCard = {
      wordId: entry.wordId,
      wordValue: entry.wordValue,
      english: entry.english,
      level: entry.level,
      createdAt: ts,
      audioFile,
    };
    await writeFile(join(dir, `${id}.json`), JSON.stringify(stored));
  } catch (err) {
    console.error(`[prebuild] Push error for ${chatId}:`, errMsg(err));
  }
}

export async function getQueueSize(chatId: number): Promise<number> {
  const files = await readdir(userDir(chatId)).catch(() => [] as string[]);
  return files.filter(f => f.endsWith(".json") && !f.endsWith(".meta.json")).length;
}

export async function invalidateQueue(chatId: number): Promise<void> {
  const dir = userDir(chatId);
  const files = await readdir(dir).catch(() => [] as string[]);
  await Promise.all(files.map(f => unlink(join(dir, f)).catch(() => {})));
}

/**
 * Clean stale entries across all users. Derives audio filename from JSON
 * filename instead of parsing file contents.
 */
export async function cleanupStaleEntries(): Promise<number> {
  let cleaned = 0;
  const users = await readdir(QUEUE_DIR).catch(() => [] as string[]);
  for (const userId of users) {
    const dir = join(QUEUE_DIR, userId);
    const files = await readdir(dir).catch(() => [] as string[]);
    const now = Date.now();
    for (const f of files) {
      if (!f.endsWith(".json")) continue;
      try {
        const info = await stat(join(dir, f));
        if (now - info.mtimeMs > STALE_MS) {
          const base = f.replace(".json", "");
          // Delete all related files: .json, .meta.json, .ogg, .claimed
          await Promise.all([
            unlink(join(dir, f)),
            unlink(join(dir, `${base}.meta.json`)),
            unlink(join(dir, `${base}.ogg`)),
            unlink(join(dir, f + ".claimed")),
          ].map(p => p.catch(() => {})));
          cleaned++;
        }
      } catch { /* skip */ }
    }
  }
  return cleaned;
}
