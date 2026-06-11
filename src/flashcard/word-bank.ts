import type pg from "pg";
import type { CefrLevel } from "../config.js";
import type { Word } from "./types.js";

let words: Word[] = [];
let wordMap: Map<string, Word> = new Map();

export async function loadWordBankFromDb(pool: pg.Pool): Promise<void> {
  const wordsRes = await pool.query(
    `SELECT id, estonian, english, turkish, cefr_level, image_query FROM words ORDER BY id`,
  );
  const sentencesRes = await pool.query(
    `SELECT word_id, estonian, english, turkish FROM word_sentences ORDER BY word_id, sort_order`,
  );

  // Group sentences by word_id
  const sentenceMap = new Map<string, Array<{ estonian: string; english: string; turkish?: string }>>();
  for (const row of sentencesRes.rows) {
    if (!sentenceMap.has(row.word_id)) sentenceMap.set(row.word_id, []);
    sentenceMap.get(row.word_id)!.push({
      estonian: row.estonian,
      english: row.english,
      turkish: row.turkish ?? undefined,
    });
  }

  words = wordsRes.rows.map((r) => ({
    id: r.id,
    estonian: r.estonian,
    english: r.english,
    turkish: r.turkish ?? undefined,
    cefrLevel: r.cefr_level as CefrLevel,
    imageQuery: r.image_query ?? undefined,
    sentences: sentenceMap.get(r.id) ?? [],
  }));

  wordMap = new Map(words.map((w) => [w.id, w]));
  console.error(`[word-bank] Loaded ${words.length} words from database`);
}

// Keep the old JSON-based loader as fallback
export { loadWordBankFromDb as loadWordBank };

export function getWordsForLevel(level: CefrLevel): Word[] {
  return words.filter((w) => w.cefrLevel === level);
}

export function getWordById(id: string): Word | undefined {
  return wordMap.get(id);
}

export function getUnsent(level: CefrLevel, sentIds: string[]): Word[] {
  const sent = new Set(sentIds);
  return words.filter((w) => w.cefrLevel === level && !sent.has(w.id));
}

export function getAllWords(): Word[] {
  return words;
}
