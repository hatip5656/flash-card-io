#!/usr/bin/env node

import { loadConfig } from "./config.js";
import { startHealthServer, setReady } from "./health.js";
import { initDb, closeDb, getActiveSubscribers, getSentWordIds, getSentWordValues, markWordSent, getSubscriberLevel, backfillEnglish, getSentGrammarIds, markGrammarSent } from "./db/progress.js";
import { loadWordBank, getUnsent, getWordById } from "./flashcard/word-bank.js";
import { loadGrammarBank, getRandomLesson } from "./flashcard/grammar-bank.js";
import { buildFlashcard, buildFlashcardFromEkilex } from "./flashcard/builder.js";
import { startGlobalScheduler, syncUserJobs, scheduleRandomGrammarJobs, scheduleGrammarJobForUser, stopAllGrammarJobs } from "./flashcard/scheduler.js";
import { createTelegramChannel } from "./channels/telegram.js";
import { createWhatsAppChannel } from "./channels/whatsapp.js";
import { registerCommands } from "./bot/commands.js";
import { registerQuiz } from "./bot/quiz.js";
import { getRandomWordForLevel, getWordFormsForValue } from "./services/ekilex.js";
import { Cron } from "croner";
import type { DeliveryChannel } from "./channels/types.js";
import type { Bot } from "grammy";

const config = loadConfig();

loadWordBank();
loadGrammarBank();

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

async function deliverFlashcard(chatId: number): Promise<void> {
  const level = await getSubscriberLevel(chatId);
  const sentIds = await getSentWordIds(chatId);
  const unsent = getUnsent(level, sentIds);

  let flashcard;
  let wordId: string;
  let wordValue: string;

  if (unsent.length > 0) {
    const word = unsent[Math.floor(Math.random() * unsent.length)];
    console.error(`[main] Building flashcard for "${word.estonian}" (${word.cefrLevel}) from local → chat ${chatId}`);
    flashcard = await buildFlashcard(word, config.unsplashAccessKey, config.ekilexApiKey);
    wordId = word.id;
    wordValue = word.estonian;
  } else if (config.ekilexApiKey) {
    console.error(`[main] Local ${level} words exhausted, querying Ekilex live → chat ${chatId}`);
    const sentValues = await getSentWordValues(chatId);
    const ekilexWord = await getRandomWordForLevel(level, sentValues, config.ekilexApiKey);

    if (ekilexWord) {
      console.error(`[main] Ekilex found "${ekilexWord.wordValue}" (${ekilexWord.cefrLevel}) → chat ${chatId}`);
      const wordForms = await getWordFormsForValue(ekilexWord.wordValue, config.ekilexApiKey).catch(() => null);
      flashcard = await buildFlashcardFromEkilex(ekilexWord, config.unsplashAccessKey, wordForms);
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
      });
    }
    return;
  }

  for (const channel of channels) {
    const sent = await channel.sendFlashcard(chatId, flashcard);
    if (sent) {
      await markWordSent(chatId, wordId, wordValue, flashcard.word.english);
      console.error(`[main] Sent "${wordValue}" to ${chatId} via ${channel.name}`);
    }
  }
}

async function deliverGrammarCard(chatId: number): Promise<void> {
  if (!bot) return;

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

  // Global scheduler as fallback
  const globalScheduler = startGlobalScheduler(
    config.cronSchedule,
    config.cronTimezone,
    async () => { await refreshUserJobs(); },
  );

  // Mark as ready — all systems initialized
  setReady(true);

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
