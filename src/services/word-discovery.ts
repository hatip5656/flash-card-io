import type pg from "pg";
import { errMsg } from "../utils.js";
import type { CefrLevel } from "../config.js";

const API_BASE = "https://ekilex.ee/api";
const LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];
const WORDS_PER_LEVEL = 10;

// Realistic Estonian 2-letter prefix starts
const ESTONIAN_PREFIXES = [
  "aa", "ab", "ae", "ah", "ai", "aj", "ak", "al", "am", "an", "ap", "ar", "as", "at", "au", "av",
  "ea", "eb", "ed", "ee", "eh", "ei", "ek", "el", "em", "en", "ep", "er", "es", "et", "ev",
  "ha", "he", "hi", "ho", "hu", "hä", "hü",
  "il", "im", "in", "is", "it",
  "ja", "je", "jo", "ju", "jä", "jõ", "jü",
  "ka", "ke", "ki", "ko", "ku", "kä", "kõ", "kö", "kü",
  "la", "le", "li", "lo", "lu", "lä", "lõ", "lö", "lü",
  "ma", "me", "mi", "mo", "mu", "mä", "mõ", "mö", "mü",
  "na", "ne", "ni", "no", "nu", "nä", "nõ", "nö", "nü",
  "oa", "od", "oh", "oi", "ok", "ol", "om", "on", "op", "or", "os", "ot", "ou",
  "pa", "pe", "pi", "po", "pu", "pä", "pö", "pü",
  "ra", "re", "ri", "ro", "ru", "rä", "rõ", "rü",
  "sa", "se", "si", "so", "su", "sä", "sõ", "sö", "sü",
  "ta", "te", "ti", "to", "tu", "tä", "tõ", "tö", "tü",
  "ua", "ud", "ue", "ui", "uk", "ul", "um", "un", "up", "ur", "us", "ut", "uu",
  "va", "ve", "vi", "vo", "vu", "vä", "võ", "vö",
  "õh", "õi", "õl", "õm", "õn", "õp", "õu",
  "äi", "äk", "är", "ät",
  "öi", "öö",
  "üh", "ük", "ül", "üm", "ür", "üt", "üü",
];

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

// In-memory caches — persist across cron runs, cleared on pod restart
const searchCache = new Map<string, EkilexSearchWord[]>();
const detailsCache = new Map<number, EkilexWordDetails | null>();
const searchedPrefixes = new Set<string>();

async function apiRequest<T>(path: string, apiKey: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      headers: { "ekilex-api-key": apiKey },
      signal: AbortSignal.timeout(30_000),
    });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

async function searchPrefix(prefix: string, apiKey: string): Promise<EkilexSearchWord[]> {
  if (searchCache.has(prefix)) return searchCache.get(prefix)!;

  const data = await apiRequest<{ totalCount: number; words: EkilexSearchWord[] }>(
    `/word/search/${encodeURIComponent(prefix)}`,
    apiKey,
  );

  const words = data?.words?.filter(w => w.lang === "est") ?? [];
  searchCache.set(prefix, words);
  return words;
}

async function getWordDetails(wordId: number, apiKey: string): Promise<EkilexWordDetails | null> {
  if (detailsCache.has(wordId)) return detailsCache.get(wordId)!;

  const details = await apiRequest<EkilexWordDetails>(`/word/details/${wordId}`, apiKey);
  detailsCache.set(wordId, details);
  return details;
}

/**
 * Discover new words from Ekilex and save to candidate_words table.
 * Uses in-memory cache for Ekilex responses — never calls the same prefix or word twice.
 */
export async function discoverCandidates(pool: pg.Pool, apiKey: string): Promise<number> {
  const existing = await pool.query("SELECT estonian FROM words");
  const candidatesDb = await pool.query("SELECT estonian FROM candidate_words");
  const known = new Set<string>();
  for (const r of existing.rows) known.add(r.estonian);
  for (const r of candidatesDb.rows) known.add(r.estonian);

  console.error(`[discovery] Starting... (${known.size} known, ${searchedPrefixes.size}/${ESTONIAN_PREFIXES.length} prefixes cached)`);

  let totalAdded = 0;
  let totalSearches = 0;
  let totalDetails = 0;
  const targetPerLevel: Record<string, number> = {};
  for (const l of LEVELS) targetPerLevel[l] = 0;

  // Pick unsearched prefixes first, then random from all
  const unsearched = ESTONIAN_PREFIXES.filter(p => !searchedPrefixes.has(p));
  const shuffled = unsearched.length > 0
    ? unsearched.sort(() => Math.random() - 0.5)
    : [...ESTONIAN_PREFIXES].sort(() => Math.random() - 0.5);

  const maxSearches = 10; // keep it light to avoid OOM

  for (const prefix of shuffled.slice(0, maxSearches)) {
    if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

    const fullPrefix = prefix + "*";
    totalSearches++;
    const words = await searchPrefix(fullPrefix, apiKey);
    searchedPrefixes.add(prefix);

    if (!words.length) continue;
    console.error(`[discovery] Prefix "${fullPrefix}": ${words.length} words`);

    // Shuffle and check candidates
    const candidates = words
      .filter(w => w.wordValue.length >= 3 && !w.wordValue.includes(" "))
      .sort(() => Math.random() - 0.5)
      .slice(0, 4);

    for (const w of candidates) {
      if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

      const est = w.wordValue.toLowerCase();
      if (known.has(est)) continue;

      totalDetails++;
      const details = await getWordDetails(w.wordId, apiKey);
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
              if (syn.words?.[0]) { english = syn.words[0].wordValue; break; }
            }
          }
          if (english) break;
        }
        if (!english) continue;

        // Extract usages — keep sentences even without English translation
        const usages: Array<{ estonian: string; english: string }> = [];
        for (const u of lexeme.usages ?? []) {
          const estSent = (u.value ?? u.valuePrese ?? "").replace(/<[^>]*>/g, "").trim();
          if (!estSent || estSent.length > 200) continue;
          let engSent = "";
          for (const t of u.translations ?? []) {
            if (t.lang === "eng") { engSent = (t.value ?? "").replace(/<[^>]*>/g, "").trim(); break; }
          }
          usages.push({ estonian: estSent, english: engSent });
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

  const summary = LEVELS.map(l => `${l}:${targetPerLevel[l]}`).join(", ");
  console.error(`[discovery] Done: ${totalAdded} added, ${totalSearches} searches, ${totalDetails} details (${summary})`);
  console.error(`[discovery] Cache: ${searchCache.size} prefix searches, ${detailsCache.size} word details`);

  return totalAdded;
}
