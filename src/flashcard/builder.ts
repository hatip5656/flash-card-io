import { searchPhoto, triggerDownload } from "../services/unsplash.js";
import { resolveSentence } from "../services/sentence.js";
import type { Word, Flashcard } from "./types.js";

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
