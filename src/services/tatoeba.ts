import { errMsg } from "../utils.js";

export interface TatoebaSentence {
  id: number;
  estonian: string;
  english: string;
}

export async function searchSentences(
  word: string,
  limit = 5,
): Promise<TatoebaSentence[]> {
  try {
    const params = new URLSearchParams({
      from: "est",
      to: "eng",
      query: word,
      per_page: String(limit),
    });

    const res = await fetch(`https://tatoeba.org/en/api_v0/search?${params}`, {
      headers: { "User-Agent": "flash-card-io/1.0" },
    });

    if (!res.ok) {
      console.error(`[tatoeba] API error: ${res.status}`);
      return [];
    }

    const data = (await res.json()) as {
      results: Array<{
        id: number;
        text: string;
        translations: Array<Array<{ lang: string; text: string }>>;
      }>;
    };

    const sentences: TatoebaSentence[] = [];

    for (const result of data.results) {
      const englishTranslations = result.translations.flat().filter((t) => t.lang === "eng");
      if (englishTranslations.length > 0) {
        sentences.push({
          id: result.id,
          estonian: result.text,
          english: englishTranslations[0].text,
        });
      }
    }

    return sentences;
  } catch (err) {
    console.error(`[tatoeba] Error searching for "${word}":`, errMsg(err));
    return [];
  }
}
