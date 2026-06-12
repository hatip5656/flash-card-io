import type pg from "pg";
import { errMsg } from "../utils.js";
import type { CefrLevel } from "../config.js";

const API_BASE = "https://ekilex.ee/api";
const LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];
const WORDS_PER_LEVEL = 10;

// Estonian alphabet for generating random prefixes
const ESTONIAN_CHARS = "abdefghijklmnoprsšzžtuvõäöü";

interface EkilexSearchWord {
  wordId: number;
  wordValue: string;
  lang: string;
}

interface EkilexLexeme {
  lexemeProficiencyLevelCode?: string;
  pos?: Array<{ value: string }>;
  synonymLangGroups?: Array<{
    lang: string;
    synonyms?: Array<{ words?: Array<{ wordValue: string }> }>;
  }>;
  usages?: Array<{
    value?: string;
    valuePrese?: string;
    translations?: Array<{ lang: string; value?: string }>;
  }>;
}

interface EkilexWordDetails {
  lexemes: EkilexLexeme[];
}

async function apiRequest<T>(path: string, apiKey: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      headers: { "ekilex-api-key": apiKey },
      signal: AbortSignal.timeout(60_000),
    });
    if (!res.ok) {
      console.error(`[discovery] Ekilex ${res.status} for ${path}`);
      return null;
    }
    return (await res.json()) as T;
  } catch (err) {
    console.error(`[discovery] Request failed for ${path}:`, errMsg(err));
    return null;
  }
}

/** Generate a random 3-letter Estonian prefix with wildcard for Ekilex search */
function randomPrefix(): string {
  const len = 3; // 3-letter prefix keeps results manageable (<500)
  let prefix = "";
  for (let i = 0; i < len; i++) {
    prefix += ESTONIAN_CHARS[Math.floor(Math.random() * ESTONIAN_CHARS.length)];
  }
  return prefix + "*"; // Ekilex requires * for prefix matching
}

/**
 * Discover new words from Ekilex and save to candidate_words table.
 * Uses random 2-3 letter prefixes to search broadly across the dictionary.
 */
export async function discoverCandidates(pool: pg.Pool, apiKey: string): Promise<number> {
  const existing = await pool.query("SELECT estonian FROM words");
  const candidatesDb = await pool.query("SELECT estonian FROM candidate_words");
  const known = new Set<string>();
  for (const r of existing.rows) known.add(r.estonian);
  for (const r of candidatesDb.rows) known.add(r.estonian);

  let totalAdded = 0;
  const targetPerLevel: Record<string, number> = {};
  for (const l of LEVELS) targetPerLevel[l] = 0;

  const maxSearches = 30;

  for (let search = 0; search < maxSearches; search++) {
    if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

    const prefix = randomPrefix();
    const data = await apiRequest<{ totalCount: number; words: EkilexSearchWord[] }>(
      `/word/search/${encodeURIComponent(prefix)}`,
      apiKey,
    );
    if (!data?.words?.length) continue;

    // Shuffle results and check first batch
    const estWords = data.words
      .filter(w => w.lang === "est" && w.wordValue.length >= 3 && !w.wordValue.includes(" "))
      .sort(() => Math.random() - 0.5)
      .slice(0, 8);

    for (const w of estWords) {
      if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

      const est = w.wordValue.toLowerCase();
      if (known.has(est)) continue;

      const details = await apiRequest<EkilexWordDetails>(
        `/word/details/${w.wordId}`,
        apiKey,
      );
      if (!details) continue;

      for (const lexeme of details.lexemes) {
        const level = lexeme.lexemeProficiencyLevelCode as CefrLevel | undefined;
        if (!level || !LEVELS.includes(level)) continue;
        if (targetPerLevel[level] >= WORDS_PER_LEVEL) continue;

        // Extract English
        let english: string | null = null;
        for (const group of lexeme.synonymLangGroups ?? []) {
          if (group.lang === "eng") {
            for (const syn of group.synonyms ?? []) {
              if (syn.words?.[0]) {
                english = syn.words[0].wordValue;
                break;
              }
            }
          }
          if (english) break;
        }
        if (!english) continue;

        // Extract usages
        const usages: Array<{ estonian: string; english: string }> = [];
        for (const u of lexeme.usages ?? []) {
          const estSent = (u.value ?? u.valuePrese ?? "").replace(/<[^>]*>/g, "");
          if (!estSent) continue;
          let engSent = "";
          for (const t of u.translations ?? []) {
            if (t.lang === "eng") {
              engSent = (t.value ?? "").replace(/<[^>]*>/g, "");
              break;
            }
          }
          if (estSent && engSent) usages.push({ estonian: estSent, english: engSent });
        }

        const pos = lexeme.pos?.[0]?.value ?? null;

        try {
          const result = await pool.query(
            `INSERT INTO candidate_words (estonian, english, cefr_level, pos)
             VALUES ($1, $2, $3, $4)
             ON CONFLICT (estonian) DO NOTHING
             RETURNING id`,
            [est, english.toLowerCase(), level, pos],
          );

          if (result.rowCount && result.rowCount > 0) {
            const candidateId = result.rows[0].id;
            for (let i = 0; i < usages.length && i < 3; i++) {
              await pool.query(
                `INSERT INTO candidate_sentences (candidate_id, estonian, english, sort_order)
                 VALUES ($1, $2, $3, $4)`,
                [candidateId, usages[i].estonian, usages[i].english, i],
              );
            }
            known.add(est);
            targetPerLevel[level]++;
            totalAdded++;
          }
        } catch {
          // duplicate, skip
        }

        break;
      }
    }
  }

  if (totalAdded > 0) {
    const summary = LEVELS.map(l => `${l}:${targetPerLevel[l]}`).join(", ");
    console.error(`[discovery] Added ${totalAdded} candidates (${summary})`);
  }

  return totalAdded;
}
