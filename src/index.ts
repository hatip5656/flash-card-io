#!/usr/bin/env node

import { loadConfig, type CefrLevel } from "./config.js";
import { startHealthServer, setReady } from "./health.js";
import { initDb, closeDb, getActiveSubscribers, getSentWordIds, getSentWordValues, markWordSent, getSubscriberLevel, backfillEnglish, getSentGrammarIds, markGrammarSent, logWordActivity, getStreak, getTodayActivity, getStats, getQuizStats, getPreferences } from "./db/progress.js";
import { loadWordBank, getUnsent, getWordById } from "./flashcard/word-bank.js";
import { loadGrammarBank, getRandomLesson } from "./flashcard/grammar-bank.js";
import { loadCategories } from "./flashcard/categories.js";
import { buildFlashcard, buildFlashcardFromEkilex } from "./flashcard/builder.js";
import { startGlobalScheduler, syncUserJobs, scheduleRandomGrammarJobs, scheduleGrammarJobForUser, stopAllGrammarJobs } from "./flashcard/scheduler.js";
import { createTelegramChannel } from "./channels/telegram.js";
import { createWhatsAppChannel } from "./channels/whatsapp.js";
import { registerCommands } from "./bot/commands.js";
import { registerQuiz } from "./bot/quiz.js";
import { getRandomWordForLevel, getWordFormsForValue } from "./services/ekilex.js";
import { evictExpired, TTL, getCacheStats } from "./services/cache.js";
import { popPrebuilt, pushPrebuilt, getQueueSize, cleanupStaleEntries, QUEUE_SIZE } from "./services/prebuild.js";
import { Cron } from "croner";
import type { DeliveryChannel } from "./channels/types.js";
import type { Bot } from "grammy";
import { errMsg } from "./utils.js";

const config = loadConfig();

loadWordBank();
loadGrammarBank();
loadCategories();

const channels: DeliveryChannel[] = [];
let bot: Bot | undefined;

if (config.featureTelegram) {
  const telegram = createTelegramChannel(config.telegramBotToken);
  channels.push(telegram.channel);
  bot = telegram.bot;
  console.error("[main] Telegram channel enabled");
}

if (config.featureWhatsapp) {
  channels.push(createWhatsAppChannel());
  console.error("[main] WhatsApp channel enabled (stub)");
}

if (config.ekilexApiKey) {
  console.error("[main] Ekilex live queries enabled");
}


// Concurrency limiter — prevent OOM from too many simultaneous flashcard builds
// Each build can use ~30-50MB (image fetch + TTS audio + ffmpeg)
const MAX_CONCURRENT_BUILDS = 3;
let activeBuildCount = 0;
const buildQueue: Array<{ resolve: () => void }> = [];

async function acquireBuildSlot(): Promise<void> {
  if (activeBuildCount < MAX_CONCURRENT_BUILDS) {
    activeBuildCount++;
    return;
  }
  return new Promise((resolve) => {
    buildQueue.push({ resolve });
  });
}

function releaseBuildSlot(): void {
  activeBuildCount--;
  const next = buildQueue.shift();
  if (next) {
    activeBuildCount++;
    next.resolve();
  }
}

async function deliverFlashcard(chatId: number): Promise<void> {
  const level = await getSubscriberLevel(chatId);

  const prebuilt = await popPrebuilt(chatId, level);
  if (prebuilt) {
    console.error(`[main] Serving pre-built "${prebuilt.wordValue}" → chat ${chatId}`);
    await sendAndRecord(chatId, prebuilt.flashcard, prebuilt.wordId, prebuilt.wordValue, prebuilt.english);
    refillQueue(chatId).catch(() => {});
    return;
  }

  await acquireBuildSlot();
  try {
    await _deliverFlashcard(chatId, level);
  } finally {
    releaseBuildSlot();
  }
  refillQueue(chatId).catch(() => {});
}

async function sendAndRecord(chatId: number, flashcard: any, wordId: string, wordValue: string, english: string): Promise<void> {
  for (const channel of channels) {
    const sent = await channel.sendFlashcard(chatId, flashcard);
    if (sent) {
      await markWordSent(chatId, wordId, wordValue, english);
      const { milestone } = await logWordActivity(chatId).catch(() => ({ totalWords: 0, milestone: null }));
      if (milestone && bot) {
        await bot.api.sendMessage(chatId, `🎉 <b>Milestone!</b> You've learned <b>${milestone}</b> words!`, { parse_mode: "HTML" });
      }
      console.error(`[main] Sent "${wordValue}" to ${chatId} via ${channel.name}`);
    }
  }
}

async function _deliverFlashcard(chatId: number, level: CefrLevel): Promise<void> {
  const prefs = await getPreferences(chatId);
  const buildOpts = { audioEnabled: prefs.audio, voiceName: prefs.voiceName, wordFormsEnabled: prefs.wordForms };
  const sentIds = await getSentWordIds(chatId);
  const unsent = getUnsent(level, sentIds);

  let flashcard;
  let wordId: string;
  let wordValue: string;

  if (unsent.length > 0) {
    const word = unsent[Math.floor(Math.random() * unsent.length)];
    console.error(`[main] Building flashcard for "${word.estonian}" (${word.cefrLevel}) from local → chat ${chatId}`);
    flashcard = await buildFlashcard(word, config.unsplashAccessKey, config.ekilexApiKey, buildOpts);
    wordId = word.id;
    wordValue = word.estonian;
  } else if (config.ekilexApiKey) {
    console.error(`[main] Local ${level} words exhausted, querying Ekilex live → chat ${chatId}`);
    const sentValues = await getSentWordValues(chatId);
    const ekilexWord = await getRandomWordForLevel(level, sentValues, config.ekilexApiKey);

    if (ekilexWord) {
      console.error(`[main] Ekilex found "${ekilexWord.wordValue}" (${ekilexWord.cefrLevel}) → chat ${chatId}`);
      const wordForms = await getWordFormsForValue(ekilexWord.wordValue, config.ekilexApiKey).catch(() => null);
      flashcard = await buildFlashcardFromEkilex(ekilexWord, config.unsplashAccessKey, wordForms, buildOpts);
      wordId = `ekilex-${ekilexWord.wordId}`;
      wordValue = ekilexWord.wordValue;
    } else {
      console.error(`[main] No new ${level} words found in Ekilex for chat ${chatId}`);
      for (const channel of channels) {
        await channel.sendFlashcard(chatId, {
          word: { id: "", estonian: "", english: "", cefrLevel: level, sentences: [] },
          sentence: { estonian: "", english: "" },
          imageUrl: null,
          photographer: null,
          photographerUrl: null,
          caption: `🎉 You've learned all available ${level} words! Use /level to try a different level.`,
          audio: null,

        });
      }
      return;
    }
  } else {
    console.error(`[main] All local ${level} words sent for chat ${chatId}, no Ekilex key`);
    for (const channel of channels) {
      await channel.sendFlashcard(chatId, {
        word: { id: "", estonian: "", english: "", cefrLevel: level, sentences: [] },
        sentence: { estonian: "", english: "" },
        imageUrl: null,
        photographer: null,
        photographerUrl: null,
        caption: `🎉 You've completed all ${level} words! Use /level to move to the next level.`,
        audio: null,
      });
    }
    return;
  }

  await sendAndRecord(chatId, flashcard, wordId, wordValue, flashcard.word.english);
}

async function deliverGrammarCard(chatId: number): Promise<void> {
  if (!bot) return;

  const prefs = await getPreferences(chatId);
  if (!prefs.grammarCards) return;

  const level = await getSubscriberLevel(chatId);
  const sentIds = await getSentGrammarIds(chatId);
  const lesson = getRandomLesson(level, sentIds);

  if (lesson) {
    await bot.api.sendMessage(chatId, lesson.content, { parse_mode: "HTML" });
    await markGrammarSent(chatId, lesson.id);
    console.error(`[main] Sent grammar lesson "${lesson.topic}" → chat ${chatId}`);
    return;
  }

  await bot.api.sendMessage(chatId, "You've seen all grammar lessons for your level! Use /level to try a different level.");
}

async function refreshUserJobs(): Promise<void> {
  const subscribers = await getActiveSubscribers();
  syncUserJobs(subscribers, config.cronTimezone, deliverFlashcard);
  // Ensure new subscribers get grammar cards without waiting for midnight reroll
  for (const sub of subscribers) {
    scheduleGrammarJobForUser(sub.chatId, config.cronTimezone, deliverGrammarCard);
  }
}

/**
 * Refill a user's pre-build queue with upcoming flashcards.
 * Runs in background — errors are logged, never thrown.
 */
async function refillQueue(chatId: number): Promise<void> {
  const queueSize = await getQueueSize(chatId);
  if (queueSize >= QUEUE_SIZE) return;

  const [level, prefs] = await Promise.all([getSubscriberLevel(chatId), getPreferences(chatId)]);
  const buildOpts = { audioEnabled: prefs.audio, voiceName: prefs.voiceName, wordFormsEnabled: prefs.wordForms };
  const sentIds = await getSentWordIds(chatId);
  const unsent = getUnsent(level, sentIds);
  const toFill = QUEUE_SIZE - queueSize;

  // Pre-build from local words
  if (unsent.length > 0) {
    const localCount = Math.min(toFill, unsent.length);
    for (let i = 0; i < localCount; i++) {
      const j = i + Math.floor(Math.random() * (unsent.length - i));
      [unsent[i], unsent[j]] = [unsent[j], unsent[i]];
    }
    for (let i = 0; i < localCount; i++) {
      await acquireBuildSlot();
      try {
        const word = unsent[i];
        const flashcard = await buildFlashcard(word, config.unsplashAccessKey, config.ekilexApiKey, buildOpts);
        await pushPrebuilt(chatId, { flashcard, wordId: word.id, wordValue: word.estonian, english: word.english, level });
      } catch (err) {
        console.error(`[prebuild] Build error for ${chatId}:`, errMsg(err));
      } finally {
        releaseBuildSlot();
      }
    }
    return;
  }

  // Local words exhausted — pre-build from Ekilex if available
  if (!config.ekilexApiKey) return;
  const sentValues = await getSentWordValues(chatId);

  for (let i = 0; i < toFill; i++) {
    await acquireBuildSlot();
    try {
      const ekilexWord = await getRandomWordForLevel(level, sentValues, config.ekilexApiKey);
      if (!ekilexWord) break;
      const wordForms = await getWordFormsForValue(ekilexWord.wordValue, config.ekilexApiKey).catch(() => null);
      const flashcard = await buildFlashcardFromEkilex(ekilexWord, config.unsplashAccessKey, wordForms, buildOpts);
      const wordId = `ekilex-${ekilexWord.wordId}`;
      await pushPrebuilt(chatId, { flashcard, wordId, wordValue: ekilexWord.wordValue, english: flashcard.word.english, level });
      sentValues.add(ekilexWord.wordValue); // prevent duplicates within this refill batch
    } catch (err) {
      console.error(`[prebuild] Ekilex build error for ${chatId}:`, errMsg(err));
    } finally {
      releaseBuildSlot();
    }
  }
}

/**
 * Warm pre-build queues for all active subscribers during idle time.
 */
async function warmAllQueues(): Promise<void> {
  try {
    const subs = await getActiveSubscribers();
    for (const sub of subs) {
      await refillQueue(sub.chatId);
    }
  } catch (err) {
    console.error("[prebuild] Warm-up error:", errMsg(err));
  }
}

async function main(): Promise<void> {
  // Start health server early so k8s can probe during startup
  startHealthServer(8080);

  await initDb(config.databaseUrl);

  // Backfill english column for words learned before quiz feature
  const backfilled = await backfillEnglish((wordId) => {
    const word = getWordById(wordId);
    return word?.english ?? null;
  });
  if (backfilled > 0) {
    console.error(`[main] Backfilled english for ${backfilled} previously learned words`);
  }

  // Register bot commands
  if (bot) {
    registerCommands(bot, deliverFlashcard, deliverGrammarCard, refreshUserJobs, config.ekilexApiKey);
    registerQuiz(bot, config.ekilexApiKey);
    bot.start({
      onStart: () => console.error("[main] Telegram bot polling started"),
    });
  }

  // Initial sync of user jobs
  await refreshUserJobs();

  // Schedule random grammar card deliveries
  const subscribers = await getActiveSubscribers();
  scheduleRandomGrammarJobs(subscribers, config.cronTimezone, deliverGrammarCard);

  // Periodically refresh user jobs
  const refreshInterval = setInterval(() => refreshUserJobs(), 60_000);

  // Daily reroll of random grammar times at 00:05
  const grammarRerollJob = new Cron("5 0 * * *", { timezone: config.cronTimezone }, async () => {
    console.error(`[scheduler] Rerolling grammar card times at ${new Date().toISOString()}`);
    const subs = await getActiveSubscribers();
    scheduleRandomGrammarJobs(subs, config.cronTimezone, deliverGrammarCard);
  });

  // Weekly progress report — Sundays at 10 AM
  const weeklyReportJob = new Cron("0 10 * * 0", { timezone: config.cronTimezone }, async () => {
    if (!bot) return;
    const subs = await getActiveSubscribers();
    for (const sub of subs) {
      try {
        const prefs = await getPreferences(sub.chatId);
        if (!prefs.weeklyReport) continue;
        const [{ sent, level }, quiz, streak] = await Promise.all([
          getStats(sub.chatId),
          getQuizStats(sub.chatId),
          getStreak(sub.chatId),
        ]);
        if (sent === 0) continue;
        const streakEmoji = streak >= 7 ? "🔥" : streak >= 3 ? "⚡" : "📅";
        let msg = `📊 <b>Weekly Progress Report</b>\n\n`;
        msg += `${streakEmoji} Streak: <b>${streak} day${streak !== 1 ? "s" : ""}</b>\n`;
        msg += `🏷️ Level: <b>${level}</b>\n`;
        msg += `📚 Total words learned: <b>${sent}</b>\n`;
        if (quiz.totalQuizzes > 0) {
          msg += `🧠 Quizzes taken: <b>${quiz.totalQuizzes}</b>\n`;
          msg += `📊 Average score: <b>${quiz.avgPercentage}%</b>\n`;
          if (quiz.recentTrend !== null) {
            const arrow = quiz.recentTrend > 0 ? "📈" : quiz.recentTrend < 0 ? "📉" : "➡️";
            const sign = quiz.recentTrend > 0 ? "+" : "";
            msg += `${arrow} Trend: <b>${sign}${quiz.recentTrend}%</b>\n`;
          }
        }
        msg += `\nKeep learning! Use /review to practice words due today.`;
        await bot.api.sendMessage(sub.chatId, msg, { parse_mode: "HTML" });
      } catch (err) {
        console.error(`[main] Weekly report error for ${sub.chatId}:`, errMsg(err));
      }
    }
  });

  // Daily summary at 9 PM
  const dailySummaryJob = new Cron("0 21 * * *", { timezone: config.cronTimezone }, async () => {
    if (!bot) return;
    const subs = await getActiveSubscribers();
    for (const sub of subs) {
      try {
        const prefs = await getPreferences(sub.chatId);
        if (!prefs.dailySummary) continue;
        const [streak, today] = await Promise.all([getStreak(sub.chatId), getTodayActivity(sub.chatId)]);
        if (today.wordsLearned === 0 && today.quizzesTaken === 0) continue; // Skip inactive users
        const streakEmoji = streak >= 7 ? "🔥" : streak >= 3 ? "⚡" : "📅";
        let msg = `${streakEmoji} <b>Daily Summary</b>\n\n`;
        msg += `📚 Words learned today: <b>${today.wordsLearned}</b>\n`;
        msg += `🧠 Quizzes taken: <b>${today.quizzesTaken}</b>\n`;
        msg += `${streakEmoji} Streak: <b>${streak} day${streak !== 1 ? "s" : ""}</b>\n`;
        if (streak >= 3) msg += `\nKeep it up! 💪`;
        await bot.api.sendMessage(sub.chatId, msg, { parse_mode: "HTML" });
      } catch (err) {
        console.error(`[main] Daily summary error for ${sub.chatId}:`, errMsg(err));
      }
    }
  });

  // Global scheduler as fallback
  const globalScheduler = startGlobalScheduler(
    config.cronSchedule,
    config.cronTimezone,
    async () => { await refreshUserJobs(); },
  );

  // Mark as ready — all systems initialized
  setReady(true);

  // Evict expired cache entries every 6 hours
  const cacheEvictionJob = new Cron("0 */6 * * *", { timezone: config.cronTimezone }, async () => {
    const tts = await evictExpired("tts", TTL.TTS);
    const unsplash = await evictExpired("unsplash", TTL.UNSPLASH);
    const ekilex = await evictExpired("ekilex", TTL.EKILEX);
    if (tts + unsplash + ekilex > 0) {
      console.error(`[cache] Evicted ${tts} TTS, ${unsplash} Unsplash, ${ekilex} Ekilex expired entries`);
    }
  });

  // Pre-build warm-up: fill queues at 3 AM (low-traffic) and clean stale entries
  const prebuildWarmupJob = new Cron("0 3 * * *", { timezone: config.cronTimezone }, async () => {
    const cleaned = await cleanupStaleEntries();
    if (cleaned > 0) console.error(`[prebuild] Cleaned ${cleaned} stale entries`);
    await warmAllQueues();
  });

  // Also warm queues 30 seconds after startup (once initial load settles)
  setTimeout(() => warmAllQueues().catch(() => {}), 30_000);

  // Log cache stats on startup (async, non-blocking)
  getCacheStats().then((stats) => {
    const entries = Object.entries(stats);
    if (entries.length > 0) {
      const summary = entries.map(([ns, s]) => `${ns}: ${s.files} files (${(s.sizeBytes / 1024).toFixed(0)}KB)`).join(", ");
      console.error(`[cache] ${summary}`);
    }
  }).catch(() => {});

  console.error(`[main] Flash Card IO started`);
  console.error(`[main] Database: PostgreSQL`);
  console.error(`[main] Default schedule: ${config.cronSchedule} (${config.cronTimezone})`);
  console.error(`[main] Levels: ${config.cefrLevels.join(", ")}`);
  console.error(`[main] Channels: ${channels.map((c) => c.name).join(", ")}`);
  console.error(`[main] Ekilex: ${config.ekilexApiKey ? "enabled" : "disabled"}`);

  // Graceful shutdown
  const shutdown = async () => {
    console.error("[main] Shutting down...");
    setReady(false);
    clearInterval(refreshInterval);
    weeklyReportJob.stop();
    cacheEvictionJob.stop();
    prebuildWarmupJob.stop();
    dailySummaryJob.stop();
    grammarRerollJob.stop();
    stopAllGrammarJobs();
    globalScheduler.stop();
    if (bot) bot.stop();
    await closeDb();
    process.exit(0);
  };

  process.on("SIGINT", shutdown);
  process.on("SIGTERM", shutdown);
}

main().catch((err) => {
  console.error("Fatal:", err);
  process.exit(1);
});
