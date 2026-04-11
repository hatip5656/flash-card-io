import { searchPhoto, triggerDownload } from "../services/unsplash.js";
import { resolveSentence } from "../services/sentence.js";
import { synthesizeSpeech } from "../services/tts.js";
import type { Word, Flashcard, WordForm } from "./types.js";
import { type EkilexWord, type WordFormResult, getWordFormsForValue } from "../services/ekilex.js";
import { selectForms } from "./grammar-builder.js";

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
  forms?: WordForm[];
  pos?: string | null;
  source?: string;
}

function buildCaption(params: CaptionParams): string {
  let caption = `📚 <b>${escapeHtml(params.estonian)}</b>\n🔄 <tg-spoiler>${escapeHtml(params.english)}</tg-spoiler>\n🌐 ET → EN\n🏷️ ${escapeHtml(params.cefrLevel)}`;

  caption += `\n\n💬 <i>${escapeHtml(params.sentence.estonian)}</i>`;
  if (params.sentence.english && params.sentence.english !== params.sentence.estonian) {
    caption += `\n📝 <tg-spoiler>${escapeHtml(params.sentence.english)}</tg-spoiler>`;
  }

  if (params.forms && params.forms.length > 0) {
    const selected = selectForms(params.forms, params.pos ?? null).slice(0, 5);
    if (selected.length > 0) {
      caption += "\n\n<b>Forms:</b>";
      for (const { label, value } of selected) {
        caption += `\n${escapeHtml(label)}: <code>${escapeHtml(value)}</code>`;
      }
    }
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
  ekilexApiKey?: string | null,
): Promise<Flashcard> {
  const sentence = await resolveSentence(word);
  const photo = await searchPhoto(word.imageQuery ?? word.english, unsplashKey);

  if (photo) {
    triggerDownload(photo.downloadUrl, unsplashKey).catch(() => {});
  }

  let wordForms: WordFormResult | null = null;
  if (ekilexApiKey) {
    wordForms = await getWordFormsForValue(word.estonian, ekilexApiKey).catch(() => null);
  }

  const caption = buildCaption({
    estonian: word.estonian,
    english: word.english,
    cefrLevel: word.cefrLevel,
    sentence,
    photo,
    forms: wordForms?.forms,
    pos: wordForms?.pos,
  });

  const audio = await synthesizeSpeech(word.estonian).catch(() => null);

  return {
    word,
    sentence,
    imageUrl: photo?.url ?? null,
    photographer: photo?.photographer ?? null,
    photographerUrl: photo?.photographerUrl ?? null,
    caption,
    audio,
  };
}

export async function buildFlashcardFromEkilex(
  ekilexWord: EkilexWord,
  unsplashKey: string,
  wordForms?: WordFormResult | null,
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
    forms: wordForms?.forms,
    pos: wordForms?.pos ?? ekilexWord.pos,
    source: "Source: Ekilex/Sõnaveeb",
  });

  const audio = await synthesizeSpeech(ekilexWord.wordValue).catch(() => null);

  return {
    word,
    sentence,
    imageUrl: photo?.url ?? null,
    photographer: photo?.photographer ?? null,
    photographerUrl: photo?.photographerUrl ?? null,
    caption,
    audio,
  };
}
