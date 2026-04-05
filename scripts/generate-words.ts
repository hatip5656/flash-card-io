#!/usr/bin/env tsx

/**
 * Generates word lists from Ekilex API.
 *
 * Usage:
 *   EKILEX_API_KEY=xxx tsx scripts/generate-words.ts
 *
 * Queries the Ekilex API for Estonian words with CEFR levels,
 * extracts English translations and usage examples,
 * and writes JSON word files to data/words/.
 */

import { writeFileSync, readFileSync } from "fs";
import { join, dirname } from "path";
import { fileURLToPath } from "url";

const __dirname = dirname(fileURLToPath(import.meta.url));
const DATA_DIR = join(__dirname, "..", "data", "words");
const API_BASE = "https://ekilex.ee/api";
const API_KEY = process.env.EKILEX_API_KEY;

if (!API_KEY) {
  console.error("EKILEX_API_KEY is required");
  process.exit(1);
}

interface WordEntry {
  id: string;
  estonian: string;
  english: string;
  cefrLevel: string;
  imageQuery?: string;
  sentences: Array<{ estonian: string; english: string }>;
}

interface EkilexSearchResult {
  totalCount: number;
  words: Array<{
    wordId: number;
    wordValue: string;
    wordValuePrese: string;
    homonymNr: number;
    lang: string;
  }>;
}

async function apiRequest<T>(path: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      headers: { "ekilex-api-key": API_KEY! },
    });
    if (!res.ok) {
      console.error(`API error ${res.status} for ${path}`);
      return null;
    }
    return (await res.json()) as T;
  } catch (err) {
    console.error(`Request failed for ${path}:`, err instanceof Error ? err.message : err);
    return null;
  }
}

function extractEnglishTranslation(lexeme: any): string | null {
  const synonymGroups = lexeme.synonymLangGroups || [];
  for (const group of synonymGroups) {
    if (group.lang === "eng") {
      const synonyms = group.synonyms || [];
      for (const syn of synonyms) {
        const words = syn.words || [];
        if (words.length > 0) {
          return words[0].wordValue;
        }
      }
    }
  }
  return null;
}

function extractUsages(lexeme: any): Array<{ estonian: string; english: string }> {
  const usages = lexeme.usages || [];
  const results: Array<{ estonian: string; english: string }> = [];

  for (const usage of usages) {
    const est = usage.value || usage.valuePrese;
    if (!est) continue;

    // Look for English translation in usage translations
    const translations = usage.translations || [];
    let eng = "";
    for (const t of translations) {
      if (t.lang === "eng") {
        eng = t.value || "";
        break;
      }
    }

    if (est) {
      // Clean any HTML-like tags from the text
      const cleanEst = est.replace(/<[^>]*>/g, "");
      const cleanEng = eng.replace(/<[^>]*>/g, "");
      results.push({ estonian: cleanEst, english: cleanEng || cleanEst });
    }
  }

  return results;
}

async function getWordDetails(wordId: number): Promise<any | null> {
  return apiRequest(`/word/details/${wordId}`);
}

async function searchWords(query: string): Promise<EkilexSearchResult | null> {
  return apiRequest<EkilexSearchResult>(`/word/search/${encodeURIComponent(query)}`);
}

// Common Estonian words to seed the search — high-frequency words
const SEED_WORDS = [
  // A1 basics
  "olema", "tegema", "saama", "minema", "tulema", "ütlema", "võtma", "andma",
  "teadma", "tahtma", "pidama", "nägema", "jääma", "panema", "hakkama",
  "viima", "tooma", "leidma", "mõtlema", "vaatama", "elama", "töötama",
  "rääkima", "kirjutama", "lugema", "õppima", "sööma", "jooma", "magama",
  // Common nouns
  "inimene", "aeg", "aasta", "päev", "elu", "käsi", "silm", "sõna",
  "mees", "naine", "riik", "linn", "tee", "maa", "keel", "raha",
  "töö", "koht", "asi", "laps", "pea", "kord", "osa", "pool",
  "süda", "jumal", "õhtu", "hommik", "öö", "tuba", "uks", "aken",
  "raamat", "kool", "pere", "sõber", "arst", "õpetaja",
  // More nouns
  "loom", "puu", "lill", "meri", "jõgi", "mägi", "järv", "mets",
  "ilm", "tuul", "pilv", "taevas", "päike", "kuu", "täht",
  "auto", "buss", "rong", "lennuk", "laev", "tänav", "sild",
  "pood", "restoran", "hotell", "haigla", "raamatukogu",
  // Adjectives
  "suur", "väike", "hea", "halb", "uus", "vana", "noor", "pikk",
  "lühike", "ilus", "kole", "tugev", "nõrk", "kiire", "aeglane",
  "kallis", "odav", "raske", "kerge", "külm", "soe", "kuum",
  "puhas", "must", "valge", "punane", "sinine", "roheline", "kollane",
  "oluline", "vajalik", "võimalik", "ohtlik", "turvaline", "huvitav",
  // Abstract/B1+
  "vabadus", "õnn", "armastus", "sõprus", "tervis", "haridus",
  "kultuur", "loodus", "keskkond", "majandus", "poliitika", "ühiskond",
  "teadus", "kunst", "muusika", "sport", "reis", "kogemus",
  "arvamus", "probleem", "lahendus", "tulemus", "eesmärk", "plaan",
  // B2 academic
  "analüüs", "uurimus", "hüpotees", "järeldus", "meetod", "protsess",
  "inflatsioon", "investeering", "eelarve", "reform", "demokraatia",
  // Verbs B1+
  "arutama", "kavatsema", "nõustuma", "keelduma", "kahtlema", "uskuma",
  "unustama", "mäletama", "kartma", "lootma", "ootama", "otsima",
  "leidma", "proovima", "alustama", "lõpetama", "jätkama", "muutma",
  "arenema", "kasvama", "vähenema", "suurenema", "paranema", "halvenema",
  // More words for coverage
  "number", "arv", "protsent", "pool", "veerand", "kolmandik",
  "reegel", "seadus", "õigus", "kohus", "politsei", "sõjavägi",
  "arvuti", "internet", "telefon", "ekraan", "programm",
  "haigus", "rohi", "valu", "operatsioon", "allergia",
  "köök", "vannituba", "elutuba", "magamistuba", "korrus",
  "kleit", "püksid", "mantel", "king", "müts", "sall",
];

async function processWord(wordValue: string, existingIds: Set<string>): Promise<WordEntry[]> {
  const searchResult = await searchWords(wordValue);
  if (!searchResult || searchResult.words.length === 0) return [];

  const entries: WordEntry[] = [];

  for (const word of searchResult.words) {
    if (word.lang !== "est") continue;

    const details = await getWordDetails(word.wordId);
    if (!details) continue;

    const lexemes = details.lexemes || [];

    for (const lexeme of lexemes) {
      const cefrLevel = lexeme.lexemeProficiencyLevelCode;
      if (!cefrLevel || !["A1", "A2", "B1", "B2"].includes(cefrLevel)) continue;

      const english = extractEnglishTranslation(lexeme);
      if (!english) continue;

      const id = `${cefrLevel.toLowerCase()}-${word.wordValue.replace(/\s+/g, "-")}`;
      if (existingIds.has(id)) continue;

      const usages = extractUsages(lexeme);

      // If no usages from API, skip — we need at least one sentence
      // (Tatoeba fallback will handle it at runtime)
      const sentences = usages.length > 0
        ? usages.slice(0, 3)
        : [{ estonian: word.wordValue, english }];

      entries.push({
        id,
        estonian: word.wordValue,
        english: english.toLowerCase(),
        cefrLevel,
        sentences,
      });

      existingIds.add(id);
      break; // One entry per CEFR level per word
    }
  }

  return entries;
}

async function main() {
  const words: Record<string, WordEntry[]> = {
    A1: [], A2: [], B1: [], B2: [],
  };

  const existingIds = new Set<string>();

  // Load existing words to avoid duplicates
  for (const level of ["a1", "a2", "b1", "b2"]) {
    try {
      const existing = JSON.parse(readFileSync(join(DATA_DIR, `${level}.json`), "utf-8")) as WordEntry[];
      for (const w of existing) {
        existingIds.add(w.id);
        words[level.toUpperCase()].push(w);
      }
      console.error(`[existing] ${level.toUpperCase()}: ${existing.length} words`);
    } catch {
      console.error(`[existing] No existing file for ${level}`);
    }
  }

  console.error(`\nProcessing ${SEED_WORDS.length} seed words...\n`);

  let processed = 0;
  for (const seedWord of SEED_WORDS) {
    const entries = await processWord(seedWord, existingIds);
    for (const entry of entries) {
      words[entry.cefrLevel].push(entry);
    }
    processed++;
    if (processed % 20 === 0) {
      const counts = Object.entries(words).map(([k, v]) => `${k}:${v.length}`).join(" ");
      console.error(`[progress] ${processed}/${SEED_WORDS.length} — ${counts}`);
    }

    // Rate limit: small delay between requests
    await new Promise((r) => setTimeout(r, 200));
  }

  // Write output
  for (const [level, entries] of Object.entries(words)) {
    const filePath = join(DATA_DIR, `${level.toLowerCase()}.json`);
    writeFileSync(filePath, JSON.stringify(entries, null, 2) + "\n");
    console.error(`[output] ${level}: ${entries.length} words → ${filePath}`);
  }

  const total = Object.values(words).reduce((sum, arr) => sum + arr.length, 0);
  console.error(`\nDone! Total: ${total} words`);
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});
