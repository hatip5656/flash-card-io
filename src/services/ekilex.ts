import type { WordForm } from "../flashcard/types.js";
import { getCachedJson, setCachedJson, TTL } from "./cache.js";
import { shuffle, errMsg } from "../utils.js";

const API_BASE = "https://ekilex.ee/api";
const MAX_DETAIL_LOOKUPS = 10;

// --- Ekilex API response types ---

interface EkilexSearchResponse {
  words: EkilexSearchWord[];
}

interface EkilexSearchWord {
  wordId: number;
  wordValue: string;
  lang: string;
}

interface EkilexWordDetails {
  paradigms?: EkilexParadigm[];
  lexemes: EkilexLexeme[];
  word?: { paradigms?: EkilexParadigm[] };
}

interface EkilexLexeme {
  lexemeProficiencyLevelCode?: string;
  pos?: Array<{ value: string }>;
  synonymLangGroups?: EkilexLangGroup[];
  usages?: EkilexUsage[];
  paradigms?: EkilexParadigm[];
  word?: { paradigms?: EkilexParadigm[] };
  lexemeWord?: { paradigms?: EkilexParadigm[] };
}

interface EkilexLangGroup {
  lang: string;
  synonyms?: Array<{ words?: Array<{ wordValue: string }> }>;
}

interface EkilexUsage {
  value?: string;
  valuePrese?: string;
  translations?: Array<{ lang: string; value?: string }>;
}

interface EkilexParadigm {
  forms?: Array<{ value?: string; morphCode?: string; morphValue?: string }>;
}

// --- Public types ---

export interface EkilexWord {
  wordId: number;
  wordValue: string;
  lang: string;
  cefrLevel: string | null;
  english: string | null;
  pos: string | null;
  usages: Array<{ estonian: string; english: string }>;
}

export interface WordFormResult {
  forms: WordForm[];
  english: string | null;
  pos: string | null;
  cefrLevel: string | null;
}

// --- Internal helpers ---

async function apiRequest<T>(path: string, apiKey: string): Promise<T | null> {
  try {
    const res = await fetch(`${API_BASE}${path}`, {
      headers: { "ekilex-api-key": apiKey },
    });
    if (!res.ok) {
      console.error(`[ekilex] API error ${res.status} for ${path}`);
      return null;
    }
    return (await res.json()) as T;
  } catch (err) {
    console.error(`[ekilex] Request failed for ${path}:`, errMsg(err));
    return null;
  }
}

function extractEnglish(lexeme: EkilexLexeme): string | null {
  for (const group of lexeme.synonymLangGroups ?? []) {
    if (group.lang === "eng") {
      for (const syn of group.synonyms ?? []) {
        const words = syn.words ?? [];
        if (words.length > 0) return words[0].wordValue;
      }
    }
  }
  return null;
}

function extractUsages(lexeme: EkilexLexeme): Array<{ estonian: string; english: string }> {
  const results: Array<{ estonian: string; english: string }> = [];
  for (const usage of lexeme.usages ?? []) {
    const est = (usage.value ?? usage.valuePrese ?? "").replace(/<[^>]*>/g, "");
    if (!est) continue;
    let eng = "";
    for (const t of usage.translations ?? []) {
      if (t.lang === "eng") {
        eng = (t.value ?? "").replace(/<[^>]*>/g, "");
        break;
      }
    }
    results.push({ estonian: est, english: eng });
  }
  return results;
}

function collectParadigms(details: EkilexWordDetails): EkilexParadigm[] {
  // Try every known path where paradigms might live
  const sources: EkilexParadigm[][] = [
    details.paradigms ?? [],
    details.word?.paradigms ?? [],
    ...details.lexemes.map((l) => l.paradigms ?? []),
    ...details.lexemes.map((l) => l.word?.paradigms ?? []),
    ...details.lexemes.map((l) => l.lexemeWord?.paradigms ?? []),
  ];
  const result = sources.flat();

  if (result.length === 0) {
    // Log sub-object keys to discover the correct path
    if (details.word) {
      console.error(`[ekilex] details.word keys: ${JSON.stringify(Object.keys(details.word))}`);
    }
    if (details.lexemes[0]?.word) {
      console.error(`[ekilex] lexeme.word keys: ${JSON.stringify(Object.keys(details.lexemes[0].word))}`);
    }
    if (details.lexemes[0]?.lexemeWord) {
      console.error(`[ekilex] lexeme.lexemeWord keys: ${JSON.stringify(Object.keys(details.lexemes[0].lexemeWord))}`);
    }
  }

  return result;
}

function extractForms(paradigms: EkilexParadigm[]): WordForm[] {
  const forms: WordForm[] = [];
  for (const paradigm of paradigms) {
    for (const form of paradigm.forms ?? []) {
      if (form.value) {
        forms.push({
          morphCode: form.morphCode ?? "",
          morphValue: form.morphValue ?? "",
          value: form.value,
        });
      }
    }
  }
  return forms;
}


// --- Public API ---

export async function getWordFormsForValue(
  wordValue: string,
  apiKey: string,
): Promise<WordFormResult | null> {
  const cacheKey = wordValue.toLowerCase();

  // Check disk cache first (eliminates 2-5 sequential API calls)
  const cached = await getCachedJson<WordFormResult>("ekilex", cacheKey, TTL.EKILEX);
  if (cached) return cached;

  const data = await apiRequest<EkilexSearchResponse>(`/word/search/${encodeURIComponent(wordValue)}`, apiKey);
  if (!data?.words) return null;

  for (const w of data.words) {
    if (w.lang !== "est") continue;

    const details = await apiRequest<EkilexWordDetails>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    // Try extracting paradigms from the details response
    let paradigms = collectParadigms(details);
    let forms = extractForms(paradigms);

    // Fallback: try dedicated paradigms endpoints
    if (forms.length === 0) {
      for (const path of [
        `/word/paradigms/${w.wordId}`,
        `/paradigm/word/${w.wordId}`,
        `/word/${w.wordId}/paradigms`,
      ]) {
        const paradigmData = await apiRequest<EkilexParadigm[]>(path, apiKey);
        if (paradigmData && Array.isArray(paradigmData) && paradigmData.length > 0) {
          forms = extractForms(paradigmData);
          console.error(`[ekilex] Found forms via ${path}`);
          break;
        }
      }
    }

    if (forms.length === 0) {
      console.error(`[ekilex] No forms for "${wordValue}" (wordId=${w.wordId}). Detail keys: ${JSON.stringify(Object.keys(details))}`);
    }

    let english: string | null = null;
    let pos: string | null = null;
    let cefrLevel: string | null = null;

    for (const lexeme of details.lexemes) {
      english = extractEnglish(lexeme);
      pos = lexeme.pos?.[0]?.value ?? null;
      cefrLevel = lexeme.lexemeProficiencyLevelCode ?? null;
      if (english) break;
    }

    if (forms.length > 0 || english) {
      const result = { forms, english, pos, cefrLevel };
      setCachedJson("ekilex", cacheKey, result).catch(() => {});
      return result;
    }
  }

  return null;
}

export async function searchWord(word: string, apiKey: string): Promise<EkilexWord[]> {
  const data = await apiRequest<EkilexSearchResponse>(`/word/search/${encodeURIComponent(word)}`, apiKey);
  if (!data?.words) return [];

  const results: EkilexWord[] = [];

  for (const w of data.words) {
    if (w.lang !== "est") continue;

    const details = await apiRequest<EkilexWordDetails>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    for (const lexeme of details.lexemes) {
      const cefrLevel = lexeme.lexemeProficiencyLevelCode;
      if (!cefrLevel || !["A1", "A2", "B1", "B2"].includes(cefrLevel)) continue;

      const english = extractEnglish(lexeme);
      if (!english) continue;

      const pos = lexeme.pos?.[0]?.value ?? null;
      const usages = extractUsages(lexeme);

      results.push({
        wordId: w.wordId,
        wordValue: w.wordValue,
        lang: "est",
        cefrLevel,
        english: english.toLowerCase(),
        pos,
        usages: usages.slice(0, 3),
      });
    }
  }

  return results;
}

export async function getRandomWordForLevel(
  level: string,
  sentWordValues: Set<string>,
  apiKey: string,
): Promise<EkilexWord | null> {
  const patterns = [
    "a*", "e*", "i*", "k*", "l*", "m*", "n*", "o*", "p*", "r*",
    "s*", "t*", "u*", "v*", "õ*", "ä*", "ö*", "ü*", "h*", "j*",
  ];

  const pattern = patterns[Math.floor(Math.random() * patterns.length)];
  const data = await apiRequest<EkilexSearchResponse>(`/word/search/${encodeURIComponent(pattern)}`, apiKey);
  if (!data?.words) return null;

  const shuffled = shuffle(data.words);
  let lookups = 0;

  for (const w of shuffled) {
    if (w.lang !== "est") continue;
    if (sentWordValues.has(w.wordValue)) continue;
    if (++lookups > MAX_DETAIL_LOOKUPS) break;

    const details = await apiRequest<EkilexWordDetails>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    for (const lexeme of details.lexemes) {
      const cefrLevel = lexeme.lexemeProficiencyLevelCode;
      if (cefrLevel !== level) continue;

      const english = extractEnglish(lexeme);
      if (!english) continue;

      const pos = lexeme.pos?.[0]?.value ?? null;
      const usages = extractUsages(lexeme);

      return {
        wordId: w.wordId,
        wordValue: w.wordValue,
        lang: "est",
        cefrLevel,
        english: english.toLowerCase(),
        pos,
        usages: usages.slice(0, 3),
      };
    }
  }

  return null;
}
