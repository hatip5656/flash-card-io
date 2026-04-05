import { searchSentences } from "./tatoeba.js";
import type { Word } from "../flashcard/types.js";

export async function resolveSentence(
  word: Word,
): Promise<{ estonian: string; english: string }> {
  // Try Tatoeba first
  const tatoeba = await searchSentences(word.estonian, 5);
  if (tatoeba.length > 0) {
    const pick = tatoeba[Math.floor(Math.random() * tatoeba.length)];
    return { estonian: pick.estonian, english: pick.english };
  }

  // Fallback to bundled sentences
  if (word.sentences.length > 0) {
    const pick = word.sentences[Math.floor(Math.random() * word.sentences.length)];
    return { estonian: pick.estonian, english: pick.english };
  }

  // Last resort
  return { estonian: word.estonian, english: word.english };
}
