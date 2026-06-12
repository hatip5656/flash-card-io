import type pg from "pg";
import { errMsg } from "../utils.js";
import type { CefrLevel } from "../config.js";

const API_BASE = "https://ekilex.ee/api";
const LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];
const WORDS_PER_LEVEL = 10;

// Realistic Estonian 2-letter prefix starts (consonant+vowel, vowel+consonant patterns)
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

/** Pick a random realistic Estonian prefix with wildcard */
function randomPrefix(): string {
  const base = ESTONIAN_PREFIXES[Math.floor(Math.random() * ESTONIAN_PREFIXES.length)];
  return base + "*";
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

  console.error(`[discovery] Starting... (${known.size} words already known)`);

  let totalAdded = 0;
  let totalSearches = 0;
  let totalDetails = 0;
  let totalSkipped = 0;
  const targetPerLevel: Record<string, number> = {};
  for (const l of LEVELS) targetPerLevel[l] = 0;

  const maxSearches = 30;

  for (let search = 0; search < maxSearches; search++) {
    if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

    const prefix = randomPrefix();
    totalSearches++;
    const data = await apiRequest<{ totalCount: number; words: EkilexSearchWord[] }>(
      `/word/search/${encodeURIComponent(prefix)}`,
      apiKey,
    );
    if (!data?.words?.length) {
      console.error(`[discovery] Prefix "${prefix}": no results`);
      continue;
    }
    console.error(`[discovery] Prefix "${prefix}": ${data.words.length} words`);

    // Shuffle results and check first batch
    const estWords = data.words
      .filter(w => w.lang === "est" && w.wordValue.length >= 3 && !w.wordValue.includes(" "))
      .sort(() => Math.random() - 0.5)
      .slice(0, 8);

    for (const w of estWords) {
      if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

      const est = w.wordValue.toLowerCase();
      if (known.has(est)) continue;

      totalDetails++;
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

  const summary = LEVELS.map(l => `${l}:${targetPerLevel[l]}`).join(", ");
  console.error(`[discovery] Done: ${totalAdded} added, ${totalSearches} searches, ${totalDetails} detail lookups (${summary})`);

  return totalAdded;
}
