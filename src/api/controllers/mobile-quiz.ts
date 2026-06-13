import type { Request, Response } from "express";
import { getLearnedWordsForQuiz, getMostMissedWords, incrementQuizCount, saveQuizResult, logQuizActivity, getPreferences } from "../../db/progress.js";
import { getAllWords } from "../../flashcard/word-bank.js";
import { shuffle } from "../../utils.js";
import type { QuizAnswer } from "../../db/progress.js";

// Lazily built and cached lookup: estonian → turkish
let turkishLookupCache: Map<string, string> | null = null;

function getTurkishLookup(): Map<string, string> {
  if (!turkishLookupCache) {
    turkishLookupCache = new Map();
    for (const w of getAllWords()) {
      if (w.turkish) turkishLookupCache.set(w.estonian, w.turkish);
    }
  }
  return turkishLookupCache;
}

interface MobileQuestion {
  index: number;
  type: string;
  prompt: string;
  options: string[];
  correctIndex: number;
  word: { estonian: string; english: string };
}

/**
 * GET /api/mobile/quiz/generate?count=10
 *
 * Returns all questions at once with correct answers.
 * The mobile app runs the quiz locally and submits results when done.
 */
export async function generateQuiz(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const count = Math.min(Math.max(Number(req.query.count) || 10, 5), 50);

  const prefs = await getPreferences(chatId);
  const useTurkish = prefs.nativeLanguage === "turkish";
  const turkishLookup = useTurkish ? getTurkishLookup() : null;

  const allWords = await getLearnedWordsForQuiz(chatId);
  if (allWords.length < 4) {
    const msg = useTurkish
      ? "Quiz başlatmak için en az 4 kelime öğrenmelisin."
      : "Need at least 4 learned words to start a quiz.";
    res.status(400).json({ error: msg });
    return;
  }

  const enriched = allWords.map((w) => ({
    ...w,
    turkish: turkishLookup?.get(w.estonian) ?? null,
  }));

  const nativeTrans = (w: typeof enriched[0]) =>
    useTurkish && w.turkish ? w.turkish : w.english;

  const missed = await getMostMissedWords(chatId, 10);
  const missedSet = new Set(missed.map((m) => m.estonian));

  // Max 30% missed words, rest are shuffled from least-quizzed pool
  const maxMissed = Math.ceil(count * 0.3);
  const missedWords = shuffle(enriched.filter((w) => missedSet.has(w.estonian))).slice(0, maxMissed);
  const missedIds = new Set(missedWords.map((w) => w.estonian));
  const otherWords = shuffle(enriched.filter((w) => !missedIds.has(w.estonian)));
  const selected = shuffle([...missedWords, ...otherWords.slice(0, count - missedWords.length)]);

  const typeLabel = useTurkish ? "tr" : "eng";

  const questions: MobileQuestion[] = selected.map((word, i) => {
    const isEstToNative = Math.random() > 0.5;
    const correct = isEstToNative ? nativeTrans(word) : word.estonian;
    const pool = enriched
      .filter((w) => w.estonian !== word.estonian)
      .map((w) => (isEstToNative ? nativeTrans(w) : w.estonian));
    const distractors = shuffle([...pool]).slice(0, 3);
    const options = shuffle([correct, ...distractors]);

    return {
      index: i,
      type: isEstToNative ? `est-to-${typeLabel}` : `${typeLabel}-to-est`,
      prompt: isEstToNative ? word.estonian : nativeTrans(word),
      options,
      correctIndex: options.indexOf(correct),
      word: { estonian: word.estonian, english: word.english },
    };
  });

  res.json({ totalQuestions: questions.length, questions });
}

/**
 * POST /api/mobile/quiz/submit
 *
 * Client submits completed quiz results for persistence.
 * Body: { answers: Array<{ estonian, correctAnswer, userAnswer, isCorrect }> }
 */
export async function submitQuiz(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const { answers } = req.body as { answers: QuizAnswer[] };

  if (!answers || !Array.isArray(answers) || answers.length === 0) {
    res.status(400).json({ error: "answers array required" });
    return;
  }

  const score = answers.filter((a) => a.isCorrect).length;
  const total = answers.length;

  await saveQuizResult(chatId, score, total, answers);
  await logQuizActivity(chatId);
  await incrementQuizCount(
    chatId,
    answers.map((a) => a.estonian),
  );

  res.json({
    score,
    total,
    percentage: Math.round((score / total) * 100),
  });
}
