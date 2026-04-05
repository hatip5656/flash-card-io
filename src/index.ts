#!/usr/bin/env node

import { loadConfig } from "./config.js";
import { initDb, getActiveSubscribers, getSentWordIds, getSentWordValues, markWordSent, getSubscriberLevel } from "./db/progress.js";
import { loadWordBank, getUnsent } from "./flashcard/word-bank.js";
import { buildFlashcard, buildFlashcardFromEkilex } from "./flashcard/builder.js";
import { startGlobalScheduler, syncUserJobs, getActiveJobCount } from "./flashcard/scheduler.js";
import { createTelegramChannel } from "./channels/telegram.js";
import { createWhatsAppChannel } from "./channels/whatsapp.js";
import { registerCommands } from "./bot/commands.js";
import { getRandomWordForLevel } from "./services/ekilex.js";
import type { DeliveryChannel } from "./channels/types.js";
import type { Bot } from "grammy";

const config = loadConfig();
const db = initDb(config.dbPath);

loadWordBank();

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
  const level = getSubscriberLevel(db, chatId);
  const sentIds = getSentWordIds(db, chatId);
  const unsent = getUnsent(level, sentIds);

  let flashcard;
  let wordId: string;
  let wordValue: string;

  if (unsent.length > 0) {
    const word = unsent[Math.floor(Math.random() * unsent.length)];
    console.error(`[main] Building flashcard for "${word.estonian}" (${word.cefrLevel}) from local → chat ${chatId}`);
    flashcard = await buildFlashcard(word, config.unsplashAccessKey);
    wordId = word.id;
    wordValue = word.estonian;
  } else if (config.ekilexApiKey) {
    console.error(`[main] Local ${level} words exhausted, querying Ekilex live → chat ${chatId}`);
    const sentValues = getSentWordValues(db, chatId);
    const ekilexWord = await getRandomWordForLevel(level, sentValues, config.ekilexApiKey);

    if (ekilexWord) {
      console.error(`[main] Ekilex found "${ekilexWord.wordValue}" (${ekilexWord.cefrLevel}) → chat ${chatId}`);
      flashcard = await buildFlashcardFromEkilex(ekilexWord, config.unsplashAccessKey);
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
      markWordSent(db, chatId, wordId, wordValue);
      console.error(`[main] Sent "${wordValue}" to ${chatId} via ${channel.name}`);
    }
  }
}

// Sync per-user schedules periodically
function refreshUserJobs(): void {
  const subscribers = getActiveSubscribers(db);
  syncUserJobs(subscribers, config.cronTimezone, deliverFlashcard);
  console.error(`[main] Synced ${getActiveJobCount()} user jobs`);
}

// Register bot commands
if (bot) {
  registerCommands(bot, db, deliverFlashcard, refreshUserJobs);

  bot.start({
    onStart: () => console.error("[main] Telegram bot polling started"),
  });
}

// Initial sync of user jobs
refreshUserJobs();

// Periodically refresh user jobs (picks up new subscribers + schedule changes)
const refreshInterval = setInterval(refreshUserJobs, 60_000);

// Global scheduler as fallback — syncs user jobs every tick too
const globalScheduler = startGlobalScheduler(
  config.cronSchedule,
  config.cronTimezone,
  async () => {
    refreshUserJobs();
  },
);

console.error(`[main] Flash Card IO started`);
console.error(`[main] Default schedule: ${config.cronSchedule} (${config.cronTimezone})`);
console.error(`[main] Levels: ${config.cefrLevels.join(", ")}`);
console.error(`[main] Channels: ${channels.map((c) => c.name).join(", ")}`);
console.error(`[main] Ekilex: ${config.ekilexApiKey ? "enabled" : "disabled"}`);
console.error(`[main] Per-user scheduling: enabled`);

// Graceful shutdown
const shutdown = () => {
  console.error("[main] Shutting down...");
  clearInterval(refreshInterval);
  globalScheduler.stop();
  if (bot) bot.stop();
  db.close();
  process.exit(0);
};

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
