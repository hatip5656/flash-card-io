import { Bot, InlineKeyboard } from "grammy";
import { getLearnedWordsForQuiz, incrementQuizCount } from "../db/progress.js";
import { escapeHtml } from "../flashcard/builder.js";

interface QuizQuestion {
  estonian: string;
  correctAnswer: string;
  options: string[];
}

interface QuizSession {
  chatId: number;
  questions: QuizQuestion[];
  currentIndex: number;
  score: number;
  answers: Array<{ estonian: string; correct: string; chosen: string; isCorrect: boolean }>;
  answeredQuestions: Set<number>;
  startedAt: number;
}

const QUIZ_SIZE = 5;
const MIN_WORDS = 4;
const SESSION_TTL_MS = 10 * 60 * 1000; // 10 minutes
const CLEANUP_INTERVAL_MS = 5 * 60 * 1000; // 5 minutes

const quizSessions = new Map<number, QuizSession>();

// Periodic cleanup of expired sessions
setInterval(() => {
  for (const [chatId, session] of quizSessions) {
    if (isSessionExpired(session)) {
      quizSessions.delete(chatId);
    }
  }
}, CLEANUP_INTERVAL_MS);

function isSessionExpired(session: QuizSession): boolean {
  return Date.now() - session.startedAt > SESSION_TTL_MS;
}

function shuffle<T>(arr: T[]): T[] {
  const a = [...arr];
  for (let i = a.length - 1; i > 0; i--) {
    const j = Math.floor(Math.random() * (i + 1));
    [a[i], a[j]] = [a[j], a[i]];
  }
  return a;
}

function buildQuestions(words: Array<{ estonian: string; english: string; quizCount: number }>): QuizQuestion[] {
  // Words arrive sorted by quiz_count ASC — pick from least-quizzed pool, then shuffle
  const minCount = words[0]?.quizCount ?? 0;
  const leastQuizzed = words.filter((w) => w.quizCount <= minCount + 1);
  const pool = leastQuizzed.length >= QUIZ_SIZE ? leastQuizzed : words;
  const selected = shuffle(pool).slice(0, QUIZ_SIZE);

  return selected.map((word) => {
    const distractors = shuffle(words.filter((w) => w.english !== word.english))
      .slice(0, 3)
      .map((w) => w.english);

    const options = shuffle([word.english, ...distractors]);

    return {
      estonian: word.estonian,
      correctAnswer: word.english,
      options,
    };
  });
}

function buildQuestionText(session: QuizSession): string {
  const q = session.questions[session.currentIndex];
  return (
    `<b>Question ${session.currentIndex + 1}/${session.questions.length}</b>\n\n` +
    `What is the English translation of <b>${escapeHtml(q.estonian)}</b>?`
  );
}

function buildQuestionKeyboard(session: QuizSession): InlineKeyboard {
  const q = session.questions[session.currentIndex];
  const keyboard = new InlineKeyboard();

  q.options.forEach((opt, i) => {
    keyboard.text(opt, `q:${session.currentIndex}:${i}`);
    if (i % 2 === 1) keyboard.row();
  });

  return keyboard;
}

function buildSummary(session: QuizSession): string {
  const total = session.questions.length;
  const pct = Math.round((session.score / total) * 100);

  let emoji: string;
  if (pct === 100) emoji = "🏆";
  else if (pct >= 80) emoji = "🌟";
  else if (pct >= 60) emoji = "👍";
  else if (pct >= 40) emoji = "📚";
  else emoji = "💪";

  const filled = Math.round(pct / 10);
  const bar = "█".repeat(filled) + "░".repeat(10 - filled);

  let text =
    `${emoji} <b>Quiz Complete!</b>\n\n` +
    `Score: <b>${session.score}/${total}</b> (${pct}%)\n` +
    `${bar}\n\n` +
    `<b>Results:</b>\n`;

  for (const a of session.answers) {
    const icon = a.isCorrect ? "✅" : "❌";
    text += `${icon} ${escapeHtml(a.estonian)} = ${escapeHtml(a.correct)}`;
    if (!a.isCorrect) text += ` (you: ${escapeHtml(a.chosen)})`;
    text += "\n";
  }

  text += "\n/quiz to try again or /next to learn more words.";
  return text;
}

export async function startQuiz(bot: Bot, chatId: number): Promise<void> {
  const existing = quizSessions.get(chatId);
  if (existing && !isSessionExpired(existing)) {
    await bot.api.sendMessage(chatId, "You already have an active quiz. Finish it first or wait for it to expire.");
    return;
  }

  const words = await getLearnedWordsForQuiz(chatId);
  if (words.length < MIN_WORDS) {
    await bot.api.sendMessage(
      chatId,
      `You need at least ${MIN_WORDS} learned words to take a quiz. ` +
      `You have ${words.length} so far. Keep using /next to learn more!`,
    );
    return;
  }

  const questions = buildQuestions(words);
  const session: QuizSession = {
    chatId,
    questions,
    currentIndex: 0,
    score: 0,
    answers: [],
    answeredQuestions: new Set(),
    startedAt: Date.now(),
  };

  quizSessions.set(chatId, session);

  await bot.api.sendMessage(chatId, buildQuestionText(session), {
    parse_mode: "HTML",
    reply_markup: buildQuestionKeyboard(session),
  });
}

export function registerQuiz(bot: Bot): void {
  bot.command("quiz", async (ctx) => {
    await startQuiz(bot, ctx.chat.id);
  });

  bot.callbackQuery(/^q:\d+:\d+$/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;

    const session = quizSessions.get(chatId);
    if (!session || isSessionExpired(session)) {
      quizSessions.delete(chatId);
      await ctx.answerCallbackQuery({ text: "Quiz expired. Use /quiz to start a new one." });
      return;
    }

    const parts = ctx.callbackQuery.data!.split(":");
    const qIdx = Number(parts[1]);
    const oIdx = Number(parts[2]);

    // Guard: already answered this question (race condition protection)
    if (session.answeredQuestions.has(qIdx)) {
      await ctx.answerCallbackQuery({ text: "Already answered." });
      return;
    }

    if (qIdx !== session.currentIndex) {
      await ctx.answerCallbackQuery({ text: "Already answered." });
      return;
    }

    const question = session.questions[qIdx];

    // Guard: option index out of bounds
    if (oIdx < 0 || oIdx >= question.options.length) {
      await ctx.answerCallbackQuery();
      return;
    }

    // Mark as answered before any async work (race condition protection)
    session.answeredQuestions.add(qIdx);

    const chosen = question.options[oIdx];
    const isCorrect = chosen === question.correctAnswer;

    if (isCorrect) session.score++;

    session.answers.push({
      estonian: question.estonian,
      correct: question.correctAnswer,
      chosen,
      isCorrect,
    });

    await ctx.answerCallbackQuery({
      text: isCorrect ? "Correct! ✓" : `Wrong. Answer: ${question.correctAnswer}`,
    });

    // Edit the answered question to show result
    const resultText = isCorrect
      ? `✅ <b>${escapeHtml(question.estonian)}</b> = ${escapeHtml(question.correctAnswer)}`
      : `❌ <b>${escapeHtml(question.estonian)}</b> = ${escapeHtml(question.correctAnswer)} (you: ${escapeHtml(chosen)})`;

    await ctx.editMessageText(resultText, { parse_mode: "HTML" });

    session.currentIndex++;

    if (session.currentIndex >= session.questions.length) {
      // Increment quiz count for all words that appeared in this quiz
      const quizzedWords = session.questions.map((q) => q.estonian);
      await incrementQuizCount(chatId, quizzedWords).catch((err) =>
        console.error("[quiz] Failed to increment quiz counts:", err instanceof Error ? err.message : err),
      );
      await bot.api.sendMessage(chatId, buildSummary(session), { parse_mode: "HTML" });
      quizSessions.delete(chatId);
    } else {
      await bot.api.sendMessage(chatId, buildQuestionText(session), {
        parse_mode: "HTML",
        reply_markup: buildQuestionKeyboard(session),
      });
    }
  });
}
