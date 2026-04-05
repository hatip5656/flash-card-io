import { searchPhoto, triggerDownload } from "../services/unsplash.js";
import { resolveSentence } from "../services/sentence.js";
import type { Word, Flashcard } from "./types.js";
import type { EkilexWord } from "../services/ekilex.js";

export async function buildFlashcard(
  word: Word,
  unsplashKey: string,
): Promise<Flashcard> {
  const sentence = await resolveSentence(word);
  const photo = await searchPhoto(word.imageQuery ?? word.english, unsplashKey);

  if (photo) {
    triggerDownload(photo.downloadUrl, unsplashKey).catch(() => {});
  }

  let caption = `📚 *${word.estonian}*\n🔄 ${word.english}\n🌐 ET → EN\n🏷️ ${word.cefrLevel}`;
  caption += `\n\n💬 _${sentence.estonian}_\n📝 _${sentence.english}_`;

  if (photo) {
    caption += `\n\n📷 [${photo.photographer}](${photo.photographerUrl}) / Unsplash`;
  }

  return {
    word,
    sentence,
    imageUrl: photo?.url ?? null,
    photographer: photo?.photographer ?? null,
    photographerUrl: photo?.photographerUrl ?? null,
    caption,
  };
}

export async function buildFlashcardFromEkilex(
  ekilexWord: EkilexWord,
  unsplashKey: string,
): Promise<Flashcard> {
  const photo = await searchPhoto(ekilexWord.english ?? ekilexWord.wordValue, unsplashKey);

  if (photo) {
    triggerDownload(photo.downloadUrl, unsplashKey).catch(() => {});
  }

  const sentence = ekilexWord.usages.length > 0
    ? ekilexWord.usages[Math.floor(Math.random() * ekilexWord.usages.length)]
    : { estonian: ekilexWord.wordValue, english: ekilexWord.english ?? "" };

  const word: Word = {
    id: `ekilex-${ekilexWord.wordId}`,
    estonian: ekilexWord.wordValue,
    english: ekilexWord.english ?? "",
    cefrLevel: (ekilexWord.cefrLevel ?? "A1") as Word["cefrLevel"],
    sentences: ekilexWord.usages,
  };

  let caption = `📚 *${ekilexWord.wordValue}*\n🔄 ${ekilexWord.english}\n🌐 ET → EN\n🏷️ ${ekilexWord.cefrLevel}`;
  caption += `\n\n💬 _${sentence.estonian}_\n📝 _${sentence.english}_`;

  if (photo) {
    caption += `\n\n📷 [${photo.photographer}](${photo.photographerUrl}) / Unsplash`;
  }

  caption += "\n\n📖 _Source: Ekilex/Sõnaveeb_";

  return {
    word,
    sentence,
    imageUrl: photo?.url ?? null,
    photographer: photo?.photographer ?? null,
    photographerUrl: photo?.photographerUrl ?? null,
    caption,
  };
}
