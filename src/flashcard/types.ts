import type { CefrLevel } from "../config.js";

export interface BundledSentence {
  estonian: string;
  english: string;
  turkish?: string;
}

export interface Word {
  id: string;
  estonian: string;
  english: string;
  turkish?: string;
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

export interface GrammarLesson {
  id: string;
  cefrLevel: string;
  topic: string;
  content: string;
}

export interface Flashcard {
  word: Word;
  sentence: { estonian: string; english: string };
  imageUrl: string | null;
  photographer: string | null;
  photographerUrl: string | null;
  caption: string;
  audio: Buffer | null;
}

// Grammar stories (mobile app)
export interface StorySlide {
  title: string;
  body: string;
  examples?: Array<{ estonian: string; english: string; turkish?: string }>;
  highlight?: string;
}

export interface GrammarStory {
  id: string;
  cefrLevel: CefrLevel;
  topic: string;
  icon: string;
  slides: StorySlide[];
}
