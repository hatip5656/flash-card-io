import type { WordForm } from "../flashcard/types.js";

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
}

interface EkilexLexeme {
  lexemeProficiencyLevelCode?: string;
  pos?: Array<{ value: string }>;
  synonymLangGroups?: EkilexLangGroup[];
  usages?: EkilexUsage[];
  paradigms?: EkilexParadigm[];
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
    console.error(`[ekilex] Request failed for ${path}:`, err instanceof Error ? err.message : err);
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

function extractForms(details: EkilexWordDetails): WordForm[] {
  const forms: WordForm[] = [];

  // Try top-level paradigms first, then per-lexeme paradigms
  const paradigms: EkilexParadigm[] =
    details.paradigms ??
    details.lexemes.flatMap((l) => l.paradigms ?? []);

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

  if (forms.length === 0) {
    console.error(`[ekilex] No paradigm forms found. Keys: ${JSON.stringify(Object.keys(details))}`);
    for (const lex of details.lexemes) {
      console.error(`[ekilex] Lexeme keys: ${JSON.stringify(Object.keys(lex))}`);
    }
  }

  return forms;
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

// --- Public API ---

export async function getWordFormsForValue(
  wordValue: string,
  apiKey: string,
): Promise<WordFormResult | null> {
  const data = await apiRequest<EkilexSearchResponse>(`/word/search/${encodeURIComponent(wordValue)}`, apiKey);
  if (!data?.words) return null;

  for (const w of data.words) {
    if (w.lang !== "est") continue;

    const details = await apiRequest<EkilexWordDetails>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    const forms = extractForms(details);

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
      return { forms, english, pos, cefrLevel };
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
