import { searchPhoto, triggerDownload } from "../services/unsplash.js";
import { resolveSentence } from "../services/sentence.js";
import type { Word, Flashcard } from "./types.js";
import type { EkilexWord } from "../services/ekilex.js";

export function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;");
}

interface CaptionParams {
  estonian: string;
  english: string;
  cefrLevel: string;
  sentence: { estonian: string; english: string };
  photo: { photographer: string; photographerUrl: string } | null;
  source?: string;
}

function buildCaption(params: CaptionParams): string {
  let caption = `📚 <b>${escapeHtml(params.estonian)}</b>\n🔄 <tg-spoiler>${escapeHtml(params.english)}</tg-spoiler>\n🌐 ET → EN\n🏷️ ${escapeHtml(params.cefrLevel)}`;

  caption += `\n\n💬 <i>${escapeHtml(params.sentence.estonian)}</i>`;
  if (params.sentence.english && params.sentence.english !== params.sentence.estonian) {
    caption += `\n📝 <tg-spoiler>${escapeHtml(params.sentence.english)}</tg-spoiler>`;
  }

  if (params.photo) {
    caption += `\n\n📷 <a href="${escapeHtml(params.photo.photographerUrl)}">${escapeHtml(params.photo.photographer)}</a> / Unsplash`;
  }

  if (params.source) {
    caption += `\n\n📖 <i>${escapeHtml(params.source)}</i>`;
  }

  return caption;
}

export async function buildFlashcard(
  word: Word,
  unsplashKey: string,
): Promise<Flashcard> {
  const sentence = await resolveSentence(word);
  const photo = await searchPhoto(word.imageQuery ?? word.english, unsplashKey);

  if (photo) {
    triggerDownload(photo.downloadUrl, unsplashKey).catch(() => {});
  }

  const caption = buildCaption({
    estonian: word.estonian,
    english: word.english,
    cefrLevel: word.cefrLevel,
    sentence,
    photo,
  });

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

  const caption = buildCaption({
    estonian: ekilexWord.wordValue,
    english: ekilexWord.english ?? "",
    cefrLevel: ekilexWord.cefrLevel ?? "A1",
    sentence,
    photo,
    source: "Source: Ekilex/Sõnaveeb",
  });

  return {
    word,
    sentence,
    imageUrl: photo?.url ?? null,
    photographer: photo?.photographer ?? null,
    photographerUrl: photo?.photographerUrl ?? null,
    caption,
  };
}
