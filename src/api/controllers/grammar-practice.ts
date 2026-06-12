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
  type: "fill-blank" | "translate" | "choose-form" | "match-sentence";
  prompt: string;
  options: string[];
  correctIndex: number;
  explanation: string;
  storyTopic: string;
}

/**
 * GET /api/mobile/grammar/practice?storyId=...
 *
 * Generate structured grammar exercises from a story's examples.
 * No count param — generates all meaningful questions for the story.
 */
export async function generatePractice(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const storyId = req.query.storyId as string | undefined;

  const prefs = await getPreferences(chatId);
  const useTurkish = prefs.nativeLanguage === "turkish";
  const level = await getSubscriberLevel(chatId);

  const allStories = getAllStories();
  const levelStories = allStories.filter((s) => s.cefrLevel === level);

  const targetStories = storyId
    ? allStories.filter((s) => s.id === storyId)
    : levelStories;

  if (targetStories.length === 0) {
    res.status(404).json({ error: "No stories found" });
    return;
  }

  // Collect all examples at this level for distractors
  const allLevelExamples: Example[] = [];
  for (const story of levelStories) {
    for (const slide of story.slides) {
      if (slide.examples) allLevelExamples.push(...slide.examples);
    }
  }

  // Collect target story examples
  const storyExamples: Example[] = [];
  const topic = targetStories[0].topic;
  for (const story of targetStories) {
    for (const slide of story.slides) {
      if (slide.examples) storyExamples.push(...slide.examples);
    }
  }

  if (storyExamples.length < 2) {
    res.status(400).json({
      error: useTurkish ? "Yeterli alıştırma verisi yok." : "Not enough practice data.",
    });
    return;
  }

  const nativeTrans = (ex: Example) =>
    useTurkish && ex.turkish ? ex.turkish : ex.english;

  // Generate structured questions — one of each type per example
  const questions: PracticeQuestion[] = [];
  let idx = 0;

  // Round 1: Translate native → Estonian (recognition)
  for (const ex of storyExamples) {
    const correct = ex.estonian;
    const distractors = shuffle(
      allLevelExamples
        .filter((e) => e.estonian !== correct)
        .map((e) => e.estonian),
    ).slice(0, 3);
    if (distractors.length < 2) continue;
    while (distractors.length < 3) distractors.push("—");
    const options = shuffle([correct, ...distractors]);
    questions.push({
      index: idx++,
      type: "translate",
      prompt: nativeTrans(ex),
      options,
      correctIndex: options.indexOf(correct),
      explanation: `${ex.estonian} = ${nativeTrans(ex)}`,
      storyTopic: topic,
    });
  }

  // Round 2: Choose correct translation (Estonian → native)
  for (const ex of storyExamples) {
    const correct = nativeTrans(ex);
    const distractors = shuffle(
      allLevelExamples
        .filter((e) => nativeTrans(e) !== correct)
        .map((e) => nativeTrans(e)),
    ).slice(0, 3);
    if (distractors.length < 2) continue;
    while (distractors.length < 3) distractors.push("—");
    const options = shuffle([correct, ...distractors]);
    questions.push({
      index: idx++,
      type: "choose-form",
      prompt: ex.estonian,
      options,
      correctIndex: options.indexOf(correct),
      explanation: `${ex.estonian} = ${correct}`,
      storyTopic: topic,
    });
  }

  // Round 3: Fill in the blank — target grammatical words (verbs, key forms)
  for (const ex of storyExamples) {
    const words = ex.estonian.split(/\s+/);
    if (words.length < 3) continue;

    // Target grammatical words: verbs and short function words
    // Prefer words that appear in other examples (grammatical elements)
    const wordFreq = new Map<string, number>();
    for (const other of storyExamples) {
      for (const w of other.estonian.split(/\s+/)) {
        wordFreq.set(w.toLowerCase(), (wordFreq.get(w.toLowerCase()) || 0) + 1);
      }
    }

    // Pick the most "grammatical" word (appears in multiple examples, not the first/last)
    const candidates = words
      .map((w, i) => ({ word: w, idx: i, freq: wordFreq.get(w.toLowerCase()) || 0 }))
      .filter((w) => w.word.length > 1)
      .sort((a, b) => b.freq - a.freq);

    const target = candidates[0] || { word: words[1], idx: 1 };
    const blanked = words.map((w, i) => (i === target.idx ? "____" : w)).join(" ");

    // Distractors: other grammatical words from the same level
    const otherGrammar = new Set<string>();
    for (const other of allLevelExamples) {
      for (const w of other.estonian.split(/\s+/)) {
        if (w.length > 1 && w.toLowerCase() !== target.word.toLowerCase()) {
          otherGrammar.add(w.toLowerCase());
        }
      }
    }
    const distractors = shuffle([...otherGrammar]).slice(0, 3);
    while (distractors.length < 3) distractors.push("—");
    const options = shuffle([target.word.toLowerCase(), ...distractors]);

    questions.push({
      index: idx++,
      type: "fill-blank",
      prompt: blanked,
      options,
      correctIndex: options.indexOf(target.word.toLowerCase()),
      explanation: ex.estonian,
      storyTopic: topic,
    });
  }

  res.json({ totalQuestions: questions.length, questions, storyId: storyId ?? null });
}
