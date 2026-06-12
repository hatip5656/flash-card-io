import type pg from "pg";
import { errMsg } from "../utils.js";
import type { CefrLevel } from "../config.js";

const API_BASE = "https://ekilex.ee/api";
const LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];
const WORDS_PER_LEVEL = 10;

// Common Estonian words to search — each search returns nearby words too
const SEARCH_SEEDS = [
  "koer", "kass", "maja", "auto", "raamat", "laps", "vesi", "tee", "söök", "töö",
  "aeg", "päev", "öö", "kool", "linn", "mets", "meri", "järv", "tuba", "uks",
  "aken", "tool", "laud", "voodi", "lamp", "pilt", "sõna", "keel", "raha", "pood",
  "arst", "haige", "tervis", "süda", "silm", "käsi", "jalg", "pea", "nina", "suu",
  "ilus", "suur", "väike", "vana", "noor", "hea", "halb", "kiire", "aeglane", "kallis",
  "odav", "kuum", "külm", "märg", "kuiv", "kerge", "raske", "pikk", "lühike", "lai",
  "tegema", "minema", "tulema", "olema", "saama", "andma", "võtma", "panema", "nägema", "kuulma",
  "lugema", "kirjutama", "rääkima", "ütlema", "mõtlema", "teadma", "oskama", "tahtma", "pidama", "hakkama",
  "sööma", "jooma", "magama", "istuma", "seisma", "kõndima", "jooksma", "ujuma", "laulma", "tantsima",
  "õppima", "õpetama", "töötama", "elama", "armastama", "meeldima", "kartma", "naerma", "nutma", "ootama",
  "otsima", "leidma", "ostma", "müüma", "maksma", "aitama", "küsima", "vastama", "alustama", "lõpetama",
  "pere", "ema", "isa", "vend", "õde", "sõber", "naaber", "õpetaja", "õpilane", "juht",
  "ilm", "päike", "pilv", "vihm", "lumi", "tuul", "suvi", "talv", "kevad", "sügis",
  "hommik", "lõuna", "õhtu", "nädal", "kuu", "aasta", "tund", "minut", "sekund", "homme",
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
      signal: AbortSignal.timeout(15000),
    });
    if (!res.ok) return null;
    return (await res.json()) as T;
  } catch {
    return null;
  }
}

/**
 * Discover new words from Ekilex and save to candidate_words table.
 * Uses specific word searches instead of wildcards to avoid massive responses.
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

  // Pick random seed words to search
  const shuffled = [...SEARCH_SEEDS].sort(() => Math.random() - 0.5);
  const seeds = shuffled.slice(0, 20); // search 20 seeds per run

  for (const seed of seeds) {
    // Stop if we have enough for all levels
    if (LEVELS.every(l => targetPerLevel[l] >= WORDS_PER_LEVEL)) break;

    const data = await apiRequest<{ words: EkilexSearchWord[] }>(
      `/word/search/${encodeURIComponent(seed)}`,
      apiKey,
    );
    if (!data?.words) continue;

    // Check first few Estonian words from results
    const estWords = data.words.filter(w => w.lang === "est").slice(0, 5);

    for (const w of estWords) {
      const est = w.wordValue.toLowerCase();
      if (known.has(est) || est.includes(" ") || est.length < 3) continue;

      const details = await apiRequest<EkilexWordDetails>(
        `/word/details/${w.wordId}`,
        apiKey,
      );
      if (!details) continue;

      for (const lexeme of details.lexemes) {
        const level = lexeme.lexemeProficiencyLevelCode as CefrLevel | undefined;
        if (!level || !LEVELS.includes(level)) continue;
        if (targetPerLevel[level] >= WORDS_PER_LEVEL) continue;

        // Extract English translation
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

        // Extract usage sentences
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

        break; // one lexeme per word is enough
      }
    }
  }

  if (totalAdded > 0) {
    const summary = LEVELS.map(l => `${l}:${targetPerLevel[l]}`).join(", ");
    console.error(`[discovery] Added ${totalAdded} candidates (${summary})`);
  }

  return totalAdded;
}
