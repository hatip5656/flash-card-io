const API_BASE = "https://ekilex.ee/api";

export interface EkilexWord {
  wordId: number;
  wordValue: string;
  lang: string;
  cefrLevel: string | null;
  english: string | null;
  pos: string | null;
  usages: Array<{ estonian: string; english: string }>;
}

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

function extractEnglish(lexeme: any): string | null {
  const groups = lexeme.synonymLangGroups || [];
  for (const group of groups) {
    if (group.lang === "eng") {
      for (const syn of group.synonyms || []) {
        const words = syn.words || [];
        if (words.length > 0) return words[0].wordValue;
      }
    }
  }
  return null;
}

function extractUsages(lexeme: any): Array<{ estonian: string; english: string }> {
  const results: Array<{ estonian: string; english: string }> = [];
  for (const usage of lexeme.usages || []) {
    const est = (usage.value || usage.valuePrese || "").replace(/<[^>]*>/g, "");
    if (!est) continue;
    let eng = "";
    for (const t of usage.translations || []) {
      if (t.lang === "eng") {
        eng = (t.value || "").replace(/<[^>]*>/g, "");
        break;
      }
    }
    results.push({ estonian: est, english: eng || est });
  }
  return results;
}

export async function searchWord(word: string, apiKey: string): Promise<EkilexWord[]> {
  const data = await apiRequest<any>(`/word/search/${encodeURIComponent(word)}`, apiKey);
  if (!data || !data.words) return [];

  const results: EkilexWord[] = [];

  for (const w of data.words) {
    if (w.lang !== "est") continue;

    const details = await apiRequest<any>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    for (const lexeme of details.lexemes || []) {
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
  // Search with common Estonian letter patterns to discover new words
  const patterns = [
    "a*", "e*", "i*", "k*", "l*", "m*", "n*", "o*", "p*", "r*",
    "s*", "t*", "u*", "v*", "õ*", "ä*", "ö*", "ü*", "h*", "j*",
  ];

  const pattern = patterns[Math.floor(Math.random() * patterns.length)];
  const data = await apiRequest<any>(`/word/search/${encodeURIComponent(pattern)}`, apiKey);
  if (!data || !data.words) return null;

  // Shuffle results for variety
  const shuffled = data.words.sort(() => Math.random() - 0.5);

  for (const w of shuffled) {
    if (w.lang !== "est") continue;
    if (sentWordValues.has(w.wordValue)) continue;

    const details = await apiRequest<any>(`/word/details/${w.wordId}`, apiKey);
    if (!details) continue;

    for (const lexeme of details.lexemes || []) {
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
