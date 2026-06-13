import type { Request, Response } from "express";
import { getLearnedWordsForQuiz, getMostMissedWords, incrementQuizCount, saveQuizResult, logQuizActivity, getQuizHistory, getQuizStats, getPreferences, getQuizSession, upsertQuizSession, pushQuizAnswer, deleteQuizSession } from "../../db/progress.js";
import { getAllWords } from "../../flashcard/word-bank.js";
import { shuffle } from "../../utils.js";
import type { QuizAnswer } from "../../db/progress.js";

interface QuizQuestion {
  index: number;
  type: string;
  prompt: string;
  options: string[];
  correctIndex: number;
}

// Lazily built and cached lookup: estonian -> turkish
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

/** Call after words are added/modified to invalidate the cache. */
export function invalidateTurkishCache(): void {
  turkishLookupCache = null;
}

export async function startQuiz(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;

  const prefs = await getPreferences(chatId);
  const useTurkish = prefs.nativeLanguage === "turkish";
  const turkishLookup = useTurkish ? getTurkishLookup() : null;

  const allWords = await getLearnedWordsForQuiz(chatId);
  if (allWords.length < 4) {
    const msg = useTurkish
      ? "Quiz baslatmak icin en az 4 kelime ogrenmelisin. Ogrenmeye devam et!"
      : "Need at least 4 learned words to start a quiz. Keep learning!";
    res.status(400).json({ error: msg });
    return;
  }

  // Enrich with Turkish translations
  const enriched = allWords.map((w) => ({
    ...w,
    turkish: turkishLookup?.get(w.estonian) ?? null,
  }));

  // Get the translation in the user's native language
  const nativeTrans = (w: typeof enriched[0]) =>
    useTurkish && w.turkish ? w.turkish : w.english;

  const count = Math.min(Math.max(Number(req.query.count) || 5, 5), 50);

  const missed = await getMostMissedWords(chatId, Math.min(count, 20));
  const missedSet = new Set(missed.map((m) => m.estonian));

  // Prioritize missed words, then least-quizzed
  const prioritized = [
    ...enriched.filter((w) => missedSet.has(w.estonian)),
    ...enriched.filter((w) => !missedSet.has(w.estonian)),
  ];
  const selected = prioritized.slice(0, count);

  const typeLabel = useTurkish ? "tr" : "eng";

  const questions: QuizQuestion[] = selected.map((word, i) => {
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
    };
  });

  const words = selected.map((w) => ({ estonian: w.estonian, english: w.english }));

  // Store session in DB
  await upsertQuizSession(chatId, questions, words);

  res.json({
    totalQuestions: questions.length,
    question: {
      index: 0,
      type: questions[0].type,
      prompt: questions[0].prompt,
      options: questions[0].options,
    },
  });
}

export async function submitAnswer(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const session = await getQuizSession(chatId);
  if (!session) {
    res.status(404).json({ error: "No active quiz session. Start one with POST /api/quiz/start" });
    return;
  }

  const questions = session.questions as QuizQuestion[];
  const answers = session.answers as Array<{ chosen: number; correct: boolean }>;
  const qIndex = answers.length;
  if (qIndex >= questions.length) {
    res.status(400).json({ error: "Quiz already complete" });
    return;
  }

  const question = questions[qIndex];
  const { chosenIndex } = req.body;
  if (chosenIndex === undefined || !Number.isInteger(chosenIndex) || chosenIndex < 0 || chosenIndex >= question.options.length) {
    res.status(400).json({ error: `chosenIndex must be an integer between 0 and ${question.options.length - 1}` });
    return;
  }
  const correct = chosenIndex === question.correctIndex;
  const newAnswer = { chosen: chosenIndex, correct };
  await pushQuizAnswer(chatId, newAnswer);

  const allAnswers = [...answers, newAnswer];
  const isComplete = allAnswers.length >= questions.length;

  if (isComplete) {
    const score = allAnswers.filter((a) => a.correct).length;
    const total = questions.length;
    const words = session.words as Array<{ estonian: string; english: string }>;
    const quizAnswers: QuizAnswer[] = questions.map((q, i) => ({
      estonian: words[i].estonian,
      correctAnswer: q.options[q.correctIndex],
      userAnswer: q.options[allAnswers[i].chosen] ?? "",
      isCorrect: allAnswers[i].correct,
    }));

    await saveQuizResult(chatId, score, total, quizAnswers);
    await logQuizActivity(chatId);
    await incrementQuizCount(chatId, words.map((w) => w.estonian));
    await deleteQuizSession(chatId);

    res.json({
      correct,
      correctAnswer: question.options[question.correctIndex],
      chosenAnswer: question.options[chosenIndex],
      complete: true,
      score,
      total,
      percentage: Math.round((score / total) * 100),
      results: quizAnswers,
    });
    return;
  }

  const next = questions[qIndex + 1];
  res.json({
    correct,
    correctAnswer: question.options[question.correctIndex],
    chosenAnswer: question.options[chosenIndex],
    complete: false,
    nextQuestion: {
      index: next.index,
      type: next.type,
      prompt: next.prompt,
      options: next.options,
    },
  });
}

export async function getHistory(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const limit = Math.min(Number(req.query.limit) || 5, 20);
  const history = await getQuizHistory(chatId, limit);
  res.json(history);
}

export async function getStats(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const stats = await getQuizStats(chatId);
  res.json(stats);
}

export async function getMissedWords(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const limit = Math.min(Number(req.query.limit) || 20, 50);
  const missed = await getMostMissedWords(chatId, limit);
  res.json(missed);
}
