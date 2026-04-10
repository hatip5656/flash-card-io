import { Bot, InlineKeyboard } from "grammy";
import { getLearnedWordsForQuiz, incrementQuizCount, saveQuizResult, getMostMissedWords, logQuizActivity } from "../db/progress.js";
import { getWordFormsForValue } from "../services/ekilex.js";
import { escapeHtml } from "../flashcard/builder.js";
import { selectForms } from "../flashcard/grammar-builder.js";

// --- Types ---

type QuizType = "est-to-eng" | "eng-to-est" | "type-answer" | "case-form" | "fill-blank";

interface QuizQuestion {
  type: QuizType;
  prompt: string;
  estonian: string;
  correctAnswer: string;
  options?: string[];
  sentence?: string;
}

interface QuizSession {
  chatId: number;
  questions: QuizQuestion[];
  currentIndex: number;
  score: number;
  answers: Array<{ estonian: string; correct: string; chosen: string; isCorrect: boolean }>;
  answeredQuestions: Set<number>;
  awaitingTextInput: boolean;
  startedAt: number;
}

// --- Constants ---

const QUIZ_SIZE = 5;
const MIN_WORDS = 4;
const SESSION_TTL_MS = 10 * 60 * 1000;
const CLEANUP_INTERVAL_MS = 5 * 60 * 1000;

const quizSessions = new Map<number, QuizSession>();

setInterval(() => {
  for (const [chatId, session] of quizSessions) {
    if (isSessionExpired(session)) quizSessions.delete(chatId);
  }
}, CLEANUP_INTERVAL_MS);

// --- Helpers ---

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

function normalize(text: string): string {
  return text.trim().toLowerCase().replace(/[.!?,;:'"]/g, "");
}

// --- Question Builders ---

interface WordEntry {
  estonian: string;
  english: string;
  quizCount: number;
}

function buildEstToEng(word: WordEntry, allWords: WordEntry[]): QuizQuestion {
  const distractors = shuffle(allWords.filter((w) => w.english !== word.english))
    .slice(0, 3)
    .map((w) => w.english);
  return {
    type: "est-to-eng",
    prompt: `What is the English translation of <b>${escapeHtml(word.estonian)}</b>?`,
    estonian: word.estonian,
    correctAnswer: word.english,
    options: shuffle([word.english, ...distractors]),
  };
}

function buildEngToEst(word: WordEntry, allWords: WordEntry[]): QuizQuestion {
  const distractors = shuffle(allWords.filter((w) => w.estonian !== word.estonian))
    .slice(0, 3)
    .map((w) => w.estonian);
  return {
    type: "eng-to-est",
    prompt: `What is <b>${escapeHtml(word.english)}</b> in Estonian?`,
    estonian: word.estonian,
    correctAnswer: word.estonian,
    options: shuffle([word.estonian, ...distractors]),
  };
}

function buildTypeAnswer(word: WordEntry): QuizQuestion {
  // Randomly pick direction
  const estToEng = Math.random() < 0.5;
  return {
    type: "type-answer",
    prompt: estToEng
      ? `Type the English translation of <b>${escapeHtml(word.estonian)}</b>:`
      : `Type the Estonian word for <b>${escapeHtml(word.english)}</b>:`,
    estonian: word.estonian,
    correctAnswer: estToEng ? word.english : word.estonian,
  };
}

function buildCaseForm(
  word: WordEntry,
  forms: Array<{ label: string; value: string }>,
): QuizQuestion | null {
  if (forms.length < 2) return null;
  const target = forms[Math.floor(Math.random() * forms.length)];
  const distractors = shuffle(forms.filter((f) => f.value !== target.value))
    .slice(0, 3)
    .map((f) => f.value);
  if (distractors.length < 2) return null;
  return {
    type: "case-form",
    prompt: `What is the <b>${escapeHtml(target.label)}</b> of <b>${escapeHtml(word.estonian)}</b>?`,
    estonian: word.estonian,
    correctAnswer: target.value,
    options: shuffle([target.value, ...distractors]),
  };
}

function buildFillBlank(word: WordEntry, allWords: WordEntry[]): QuizQuestion | null {
  // We don't have sentences in the quiz word data, so use a simple pattern
  // "The Estonian word for '<english>' is ___"
  const distractors = shuffle(allWords.filter((w) => w.estonian !== word.estonian))
    .slice(0, 3)
    .map((w) => w.estonian);
  if (distractors.length < 3) return null;
  return {
    type: "fill-blank",
    prompt: `Fill in: The Estonian word for "<b>${escapeHtml(word.english)}</b>" is ___`,
    estonian: word.estonian,
    correctAnswer: word.estonian,
    options: shuffle([word.estonian, ...distractors]),
    sentence: `___ = ${word.english}`,
  };
}

// --- Question Selection with Spaced Repetition ---

async function selectWords(
  words: WordEntry[],
  chatId: number,
): Promise<WordEntry[]> {
  const missed = await getMostMissedWords(chatId, 20).catch(() => []);
  const missedSet = new Set(missed.map((m) => m.estonian));

  // Prioritize: missed words first, then least-quizzed
  const missedWords = words.filter((w) => missedSet.has(w.estonian));
  const minCount = words[0]?.quizCount ?? 0;
  const leastQuizzed = words.filter((w) => w.quizCount <= minCount + 1 && !missedSet.has(w.estonian));

  // Mix: up to 2 missed words + rest from least-quizzed
  const fromMissed = shuffle(missedWords).slice(0, 2);
  const remaining = QUIZ_SIZE - fromMissed.length;
  const pool = leastQuizzed.length >= remaining ? leastQuizzed : words;
  const fromPool = shuffle(pool.filter((w) => !fromMissed.some((m) => m.estonian === w.estonian))).slice(0, remaining);

  return shuffle([...fromMissed, ...fromPool]);
}

async function buildQuestions(
  words: WordEntry[],
  selected: WordEntry[],
  ekilexApiKey: string | null,
): Promise<QuizQuestion[]> {
  const questions: QuizQuestion[] = [];

  // Available types — weight them
  const types: QuizType[] = ["est-to-eng", "eng-to-est", "type-answer", "fill-blank"];
  // Add case-form if we have ekilex
  if (ekilexApiKey) types.push("case-form");

  for (const word of selected) {
    const type = types[Math.floor(Math.random() * types.length)];
    let question: QuizQuestion | null = null;

    switch (type) {
      case "est-to-eng":
        question = buildEstToEng(word, words);
        break;
      case "eng-to-est":
        question = buildEngToEst(word, words);
        break;
      case "type-answer":
        question = buildTypeAnswer(word);
        break;
      case "case-form": {
        if (!ekilexApiKey) break;
        const result = await getWordFormsForValue(word.estonian, ekilexApiKey).catch(() => null);
        if (result && result.forms.length > 0) {
          const simplified = selectForms(result.forms, result.pos);
          question = buildCaseForm(word, simplified);
        }
        break;
      }
      case "fill-blank":
        question = buildFillBlank(word, words);
        break;
    }

    // Fallback to est-to-eng if the chosen type failed
    if (!question) {
      question = buildEstToEng(word, words);
    }

    questions.push(question);
  }

  return questions;
}

// --- Display ---

function buildQuestionText(session: QuizSession): string {
  const q = session.questions[session.currentIndex];
  const header = `<b>Question ${session.currentIndex + 1}/${session.questions.length}</b>`;
  const typeLabel = q.type === "type-answer" ? " ⌨️" : q.type === "case-form" ? " 📖" : "";
  return `${header}${typeLabel}\n\n${q.prompt}`;
}

function buildQuestionKeyboard(session: QuizSession): InlineKeyboard | undefined {
  const q = session.questions[session.currentIndex];
  if (q.type === "type-answer" || !q.options) return undefined;

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

// --- Session Management ---

async function sendNextQuestion(bot: Bot, session: QuizSession): Promise<void> {
  const text = buildQuestionText(session);
  const keyboard = buildQuestionKeyboard(session);
  const q = session.questions[session.currentIndex];

  session.awaitingTextInput = q.type === "type-answer";

  await bot.api.sendMessage(session.chatId, text, {
    parse_mode: "HTML",
    reply_markup: keyboard,
  });
}

async function processAnswer(
  bot: Bot,
  session: QuizSession,
  chosen: string,
  editMessageCallback?: (text: string) => Promise<void>,
): Promise<void> {
  const question = session.questions[session.currentIndex];
  const isCorrect = question.type === "type-answer"
    ? normalize(chosen) === normalize(question.correctAnswer)
    : chosen === question.correctAnswer;

  if (isCorrect) session.score++;

  session.answers.push({
    estonian: question.estonian,
    correct: question.correctAnswer,
    chosen,
    isCorrect,
  });

  session.awaitingTextInput = false;

  // Show result for the answered question
  const resultText = isCorrect
    ? `✅ <b>${escapeHtml(question.estonian)}</b> = ${escapeHtml(question.correctAnswer)}`
    : `❌ <b>${escapeHtml(question.estonian)}</b> = ${escapeHtml(question.correctAnswer)} (you: ${escapeHtml(chosen)})`;

  if (editMessageCallback) {
    await editMessageCallback(resultText);
  } else {
    await bot.api.sendMessage(session.chatId, resultText, { parse_mode: "HTML" });
  }

  session.currentIndex++;

  if (session.currentIndex >= session.questions.length) {
    const quizzedWords = session.questions.map((q) => q.estonian);
    const quizAnswers = session.answers.map((a) => ({
      estonian: a.estonian,
      correctAnswer: a.correct,
      userAnswer: a.chosen,
      isCorrect: a.isCorrect,
    }));
    await Promise.all([
      incrementQuizCount(session.chatId, quizzedWords),
      saveQuizResult(session.chatId, session.score, session.questions.length, quizAnswers),
      logQuizActivity(session.chatId),
    ]).catch((err) =>
      console.error("[quiz] Failed to save quiz data:", err instanceof Error ? err.message : err),
    );
    await bot.api.sendMessage(session.chatId, buildSummary(session), { parse_mode: "HTML" });
    quizSessions.delete(session.chatId);
  } else {
    await sendNextQuestion(bot, session);
  }
}

// --- Public API ---

export async function startQuiz(bot: Bot, chatId: number, ekilexApiKey?: string | null): Promise<void> {
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

  const selected = await selectWords(words, chatId);
  const questions = await buildQuestions(words, selected, ekilexApiKey ?? null);

  const session: QuizSession = {
    chatId,
    questions,
    currentIndex: 0,
    score: 0,
    answers: [],
    answeredQuestions: new Set(),
    awaitingTextInput: false,
    startedAt: Date.now(),
  };

  quizSessions.set(chatId, session);
  await sendNextQuestion(bot, session);
}

export function registerQuiz(bot: Bot, ekilexApiKey?: string | null): void {
  bot.command("quiz", async (ctx) => {
    await startQuiz(bot, ctx.chat.id, ekilexApiKey);
  });

  // Handle button answers
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

    if (session.answeredQuestions.has(qIdx)) {
      await ctx.answerCallbackQuery({ text: "Already answered." });
      return;
    }

    if (qIdx !== session.currentIndex) {
      await ctx.answerCallbackQuery({ text: "Already answered." });
      return;
    }

    const question = session.questions[qIdx];
    if (!question.options || oIdx < 0 || oIdx >= question.options.length) {
      await ctx.answerCallbackQuery();
      return;
    }

    session.answeredQuestions.add(qIdx);
    const chosen = question.options[oIdx];

    await ctx.answerCallbackQuery({
      text: chosen === question.correctAnswer ? "Correct! ✓" : `Wrong. Answer: ${question.correctAnswer}`,
    });

    await processAnswer(bot, session, chosen, async (text) => {
      await ctx.editMessageText(text, { parse_mode: "HTML" });
    });
  });

  // Handle typed answers
  bot.on("message:text", async (ctx, next) => {
    const chatId = ctx.chat.id;
    const session = quizSessions.get(chatId);

    if (!session || !session.awaitingTextInput || isSessionExpired(session)) {
      await next();
      return;
    }

    const text = ctx.message.text;

    // Don't intercept commands
    if (text.startsWith("/")) {
      session.awaitingTextInput = false;
      quizSessions.delete(chatId);
      await next();
      return;
    }

    session.answeredQuestions.add(session.currentIndex);
    await processAnswer(bot, session, text);
  });
}
