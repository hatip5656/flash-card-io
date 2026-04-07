import type { CefrLevel } from "../config.js";

export interface BundledSentence {
  estonian: string;
  english: string;
}

export interface Word {
  id: string;
  estonian: string;
  english: string;
  cefrLevel: CefrLevel;
  imageQuery?: string;
  sentences: BundledSentence[];
}

export interface WordForm {
  morphCode: string;
  morphValue: string;
  value: string;
}

export interface GrammarCard {
  wordValue: string;
  english: string;
  pos: string | null;
  cefrLevel: string;
  forms: WordForm[];
  caption: string;
}

export interface Flashcard {
  word: Word;
  sentence: { estonian: string; english: string };
  imageUrl: string | null;
  photographer: string | null;
  photographerUrl: string | null;
  caption: string;
}
