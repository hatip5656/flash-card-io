#!/usr/bin/env node

import { loadConfig } from "./config.js";
import { initDb, getActiveSubscribers, getSentWordIds, markWordSent, getSubscriberLevel } from "./db/progress.js";
import { loadWordBank, getUnsent } from "./flashcard/word-bank.js";
import { buildFlashcard } from "./flashcard/builder.js";
import { startScheduler } from "./flashcard/scheduler.js";
import { createTelegramChannel } from "./channels/telegram.js";
import { createWhatsAppChannel } from "./channels/whatsapp.js";
import { registerCommands } from "./bot/commands.js";
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

async function deliverFlashcard(chatId: number): Promise<void> {
  const level = getSubscriberLevel(db, chatId);
  const sentIds = getSentWordIds(db, chatId);
  const unsent = getUnsent(level, sentIds);

  if (unsent.length === 0) {
    console.error(`[main] No unsent ${level} words for chat ${chatId}`);
    const channel = channels.find((c) => c.name === "telegram");
    if (channel) {
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

  const word = unsent[Math.floor(Math.random() * unsent.length)];
  console.error(`[main] Building flashcard for "${word.estonian}" (${word.cefrLevel}) → chat ${chatId}`);

  const flashcard = await buildFlashcard(word, config.unsplashAccessKey);

  for (const channel of channels) {
    const sent = await channel.sendFlashcard(chatId, flashcard);
    if (sent) {
      markWordSent(db, chatId, word.id);
      console.error(`[main] Sent "${word.estonian}" to ${chatId} via ${channel.name}`);
    }
  }
}

async function deliveryLoop(): Promise<void> {
  const subscribers = getActiveSubscribers(db);
  console.error(`[main] Delivery loop: ${subscribers.length} active subscribers`);

  for (const sub of subscribers) {
    await deliverFlashcard(sub.chatId);
  }
}

// Register bot commands
if (bot) {
  registerCommands(bot, db, deliverFlashcard);

  bot.start({
    onStart: () => console.error("[main] Telegram bot polling started"),
  });
}

// Start the scheduler
const scheduler = startScheduler(config.cronSchedule, config.cronTimezone, deliveryLoop);

console.error(`[main] Flash Card IO started`);
console.error(`[main] Schedule: ${config.cronSchedule} (${config.cronTimezone})`);
console.error(`[main] Levels: ${config.cefrLevels.join(", ")}`);
console.error(`[main] Channels: ${channels.map((c) => c.name).join(", ")}`);

// Graceful shutdown
const shutdown = () => {
  console.error("[main] Shutting down...");
  scheduler.stop();
  if (bot) bot.stop();
  db.close();
  process.exit(0);
};

process.on("SIGINT", shutdown);
process.on("SIGTERM", shutdown);
