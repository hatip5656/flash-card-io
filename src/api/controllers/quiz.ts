import type { Request, Response } from "express";
import { getLearnedWordsForQuiz, getMostMissedWords, incrementQuizCount, saveQuizResult, logQuizActivity, getQuizHistory, getQuizStats } from "../../db/progress.js";
import { shuffle } from "../../utils.js";
import type { QuizAnswer } from "../../db/progress.js";

interface QuizQuestion {
  index: number;
  type: "est-to-eng" | "eng-to-est";
  prompt: string;
  options: string[];
  correctIndex: number;
}

interface QuizSession {
  questions: QuizQuestion[];
  answers: { chosen: number; correct: boolean }[];
  words: Array<{ estonian: string; english: string }>;
  createdAt: number;
}

const sessions = new Map<number, QuizSession>();
const SESSION_TTL = 10 * 60 * 1000; // 10 min

function cleanStaleSessions(): void {
  const now = Date.now();
  for (const [id, session] of sessions) {
    if (now - session.createdAt > SESSION_TTL) sessions.delete(id);
  }
}

export async function startQuiz(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  cleanStaleSessions();

  const allWords = await getLearnedWordsForQuiz(chatId);
  if (allWords.length < 4) {
    res.status(400).json({ error: "Need at least 4 learned words to start a quiz. Keep learning!" });
    return;
  }

  const missed = await getMostMissedWords(chatId, 10);
  const missedSet = new Set(missed.map((m) => m.estonian));

  // Prioritize missed words, then least-quizzed
  const prioritized = [
    ...allWords.filter((w) => missedSet.has(w.estonian)),
    ...allWords.filter((w) => !missedSet.has(w.estonian)),
  ];
  const selected = prioritized.slice(0, 5);

  const questions: QuizQuestion[] = selected.map((word, i) => {
    const isEstToEng = Math.random() > 0.5;
    const correct = isEstToEng ? word.english : word.estonian;
    const pool = allWords
      .filter((w) => w.estonian !== word.estonian)
      .map((w) => (isEstToEng ? w.english : w.estonian));
    const distractors = shuffle([...pool]).slice(0, 3);
    const options = shuffle([correct, ...distractors]);

    return {
      index: i,
      type: isEstToEng ? "est-to-eng" : "eng-to-est",
      prompt: isEstToEng ? word.estonian : word.english,
      options,
      correctIndex: options.indexOf(correct),
    };
  });

  const session: QuizSession = {
    questions,
    answers: [],
    words: selected.map((w) => ({ estonian: w.estonian, english: w.english })),
    createdAt: Date.now(),
  };
  sessions.set(chatId, session);

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
  const session = sessions.get(chatId);
  if (!session) {
    res.status(404).json({ error: "No active quiz session. Start one with POST /api/quiz/start" });
    return;
  }

  const qIndex = session.answers.length;
  if (qIndex >= session.questions.length) {
    res.status(400).json({ error: "Quiz already complete" });
    return;
  }

  const question = session.questions[qIndex];
  const { chosenIndex } = req.body;
  if (chosenIndex === undefined || !Number.isInteger(chosenIndex) || chosenIndex < 0 || chosenIndex >= question.options.length) {
    res.status(400).json({ error: `chosenIndex must be an integer between 0 and ${question.options.length - 1}` });
    return;
  }
  const correct = chosenIndex === question.correctIndex;
  session.answers.push({ chosen: chosenIndex, correct });

  const isComplete = session.answers.length >= session.questions.length;

  if (isComplete) {
    // Save results
    const score = session.answers.filter((a) => a.correct).length;
    const total = session.questions.length;
    const quizAnswers: QuizAnswer[] = session.questions.map((q, i) => ({
      estonian: session.words[i].estonian,
      correctAnswer: q.options[q.correctIndex],
      userAnswer: q.options[session.answers[i].chosen] ?? "",
      isCorrect: session.answers[i].correct,
    }));

    await saveQuizResult(chatId, score, total, quizAnswers);
    await logQuizActivity(chatId);
    await incrementQuizCount(chatId, session.words.map((w) => w.estonian));
    sessions.delete(chatId);

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

  // Next question
  const next = session.questions[qIndex + 1];
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
