import type { Request, Response } from "express";
import { getPreferences, getSubscriberLevel } from "../../db/progress.js";
import { getAllStories } from "../../flashcard/story-bank.js";
import { shuffle } from "../../utils.js";

interface Example {
  estonian: string;
  english: string;
  turkish?: string;
}

interface PracticeQuestion {
  index: number;
  type: "fill-blank" | "translate" | "choose-form";
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation?: string;
  storyTopic: string;
}

/**
 * GET /api/mobile/grammar/practice?storyId=...&count=10
 *
 * Generate grammar exercises from a specific story's examples.
 * If no storyId, picks from all stories at the user's level.
 */
export async function generatePractice(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const storyId = req.query.storyId as string | undefined;
  const count = Math.min(Math.max(Number(req.query.count) || 10, 5), 30);

  const prefs = await getPreferences(chatId);
  const useTurkish = prefs.nativeLanguage === "turkish";
  const level = await getSubscriberLevel(chatId);

  const allStories = getAllStories();

  // Collect examples from relevant stories
  const levelStories = allStories.filter((s) => s.cefrLevel === level);
  const targetStories = storyId
    ? allStories.filter((s) => s.id === storyId)
    : levelStories;

  if (targetStories.length === 0) {
    res.status(404).json({ error: "No stories found" });
    return;
  }

  // Gather all examples at this level (used as distractor pool)
  const allLevelExamples: Example[] = [];
  for (const story of levelStories) {
    for (const slide of story.slides) {
      if (slide.examples) allLevelExamples.push(...slide.examples);
    }
  }

  // Gather target story examples (primary questions)
  const pool: Array<{ example: Example; topic: string; allExamples: Example[] }> = [];
  for (const story of targetStories) {
    const storyExamples: Example[] = [];
    for (const slide of story.slides) {
      if (slide.examples) storyExamples.push(...slide.examples);
    }
    for (const ex of storyExamples) {
      pool.push({ example: ex, topic: story.topic, allExamples: allLevelExamples });
    }
  }

  if (pool.length < 3) {
    res.status(400).json({ error: useTurkish ? "Yeterli alıştırma verisi yok." : "Not enough practice data." });
    return;
  }

  // Multiply pool by using each example with different question types to reach count
  const expandedPool: typeof pool = [];
  const types: Array<"fill-blank" | "translate" | "choose-form"> = ["fill-blank", "translate", "choose-form"];
  for (let t = 0; expandedPool.length < count && t < types.length; t++) {
    for (const item of pool) {
      expandedPool.push(item);
      if (expandedPool.length >= count) break;
    }
  }

  const nativeTrans = (ex: Example) =>
    useTurkish && ex.turkish ? ex.turkish : ex.english;

  const shuffled = shuffle([...expandedPool]);
  const selected = shuffled.slice(0, count);

  const questions: PracticeQuestion[] = selected.map((item, i) => {
    const { example, topic, allExamples } = item;
    const typeRoll = Math.random();

    if (typeRoll < 0.35) {
      // Fill in the blank: remove a word from Estonian sentence
      return makeFillBlank(i, example, allExamples, topic);
    } else if (typeRoll < 0.7) {
      // Translate: show native, pick correct Estonian
      return makeTranslate(i, example, allExamples, nativeTrans, topic);
    } else {
      // Choose correct translation of Estonian sentence
      return makeChooseTranslation(i, example, allExamples, nativeTrans, topic);
    }
  });

  res.json({ totalQuestions: questions.length, questions, storyId: storyId ?? null });
}

function makeFillBlank(
  index: number,
  example: Example,
  pool: Example[],
  topic: string,
): PracticeQuestion {
  const words = example.estonian.split(/\s+/);
  // Pick a word to blank out (prefer longer/meaningful words)
  const candidates = words
    .map((w, i) => ({ word: w, idx: i }))
    .filter((w) => w.word.length > 2);
  const target = candidates.length > 0
    ? candidates[Math.floor(Math.random() * candidates.length)]
    : { word: words[0], idx: 0 };

  const blanked = words.map((w, i) => (i === target.idx ? "___" : w)).join(" ");

  // Generate distractors from other examples' words
  const otherWords = new Set<string>();
  for (const ex of pool) {
    for (const w of ex.estonian.split(/\s+/)) {
      if (w.length > 2 && w.toLowerCase() !== target.word.toLowerCase()) {
        otherWords.add(w.toLowerCase());
      }
    }
  }
  const distractors = shuffle([...otherWords]).slice(0, 3);
  while (distractors.length < 3) {
    distractors.push("---");
  }

  const options = shuffle([target.word.toLowerCase(), ...distractors]);

  return {
    index,
    type: "fill-blank",
    prompt: blanked,
    options,
    correctIndex: options.indexOf(target.word.toLowerCase()),
    explanation: example.estonian,
    storyTopic: topic,
  };
}

function makeTranslate(
  index: number,
  example: Example,
  pool: Example[],
  nativeTrans: (ex: Example) => string,
  topic: string,
): PracticeQuestion {
  const correct = example.estonian;
  const distractors = shuffle(
    pool.filter((ex) => ex.estonian !== correct).map((ex) => ex.estonian),
  ).slice(0, 3);
  while (distractors.length < 3) {
    distractors.push("---");
  }
  const options = shuffle([correct, ...distractors]);

  return {
    index,
    type: "translate",
    prompt: nativeTrans(example),
    options,
    correctIndex: options.indexOf(correct),
    explanation: `${example.estonian} = ${nativeTrans(example)}`,
    storyTopic: topic,
  };
}

function makeChooseTranslation(
  index: number,
  example: Example,
  pool: Example[],
  nativeTrans: (ex: Example) => string,
  topic: string,
): PracticeQuestion {
  const correct = nativeTrans(example);
  const distractors = shuffle(
    pool.filter((ex) => nativeTrans(ex) !== correct).map((ex) => nativeTrans(ex)),
  ).slice(0, 3);
  while (distractors.length < 3) {
    distractors.push("---");
  }
  const options = shuffle([correct, ...distractors]);

  return {
    index,
    type: "choose-form",
    prompt: example.estonian,
    options,
    correctIndex: options.indexOf(correct),
    explanation: `${example.estonian} = ${correct}`,
    storyTopic: topic,
  };
}
