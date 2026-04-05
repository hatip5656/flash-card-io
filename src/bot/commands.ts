import type { Bot } from "grammy";
import type Database from "better-sqlite3";
import type { CefrLevel } from "../config.js";
import { addSubscriber, removeSubscriber, setSubscriberLevel, getStats } from "../db/progress.js";
import { getWordsForLevel } from "../flashcard/word-bank.js";

const VALID_LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];

export function registerCommands(
  bot: Bot,
  db: Database.Database,
  deliverFlashcard: (chatId: number) => Promise<void>,
): void {
  bot.command("start", async (ctx) => {
    const chatId = ctx.chat.id;
    addSubscriber(db, chatId, "telegram");
    await ctx.reply(
      "🇪🇪 Welcome to Flash Card IO!\n\n" +
      "You'll receive Estonian flashcards on a schedule.\n\n" +
      "Commands:\n" +
      "/next — Get a flashcard now\n" +
      "/level A1|A2|B1|B2 — Change difficulty\n" +
      "/stats — See your progress\n" +
      "/stop — Stop receiving flashcards",
    );
  });

  bot.command("stop", async (ctx) => {
    removeSubscriber(db, ctx.chat.id);
    await ctx.reply("Stopped. Send /start to resume.");
  });

  bot.command("next", async (ctx) => {
    try {
      await deliverFlashcard(ctx.chat.id);
    } catch (err) {
      await ctx.reply("Failed to send flashcard. Try again later.");
      console.error("[commands] /next error:", err instanceof Error ? err.message : err);
    }
  });

  bot.command("level", async (ctx) => {
    const arg = ctx.match?.trim().toUpperCase();
    if (!arg || !VALID_LEVELS.includes(arg as CefrLevel)) {
      await ctx.reply(`Usage: /level A1|A2|B1|B2\nValid levels: ${VALID_LEVELS.join(", ")}`);
      return;
    }
    setSubscriberLevel(db, ctx.chat.id, arg as CefrLevel);
    await ctx.reply(`Level set to ${arg}. You'll now receive ${arg} flashcards.`);
  });

  bot.command("stats", async (ctx) => {
    const { sent, level } = getStats(db, ctx.chat.id);
    const totalForLevel = getWordsForLevel(level).length;
    await ctx.reply(
      `📊 Your progress:\n` +
      `🏷️ Level: ${level}\n` +
      `📚 Words learned: ${sent}\n` +
      `📖 Available at ${level}: ${totalForLevel}\n` +
      `✅ Progress: ${totalForLevel > 0 ? Math.round((sent / totalForLevel) * 100) : 0}%`,
    );
  });
}
