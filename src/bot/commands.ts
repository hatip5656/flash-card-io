import type { Bot } from "grammy";
import type Database from "better-sqlite3";
import type { CefrLevel } from "../config.js";
import { addSubscriber, removeSubscriber, setSubscriberLevel, setSubscriberSchedule, getStats } from "../db/progress.js";
import { getWordsForLevel } from "../flashcard/word-bank.js";

const VALID_LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];

const SCHEDULE_PRESETS: Record<string, { cron: string; label: string }> = {
  "1h": { cron: "0 * * * *", label: "Every hour" },
  "2h": { cron: "0 */2 * * *", label: "Every 2 hours" },
  "4h": { cron: "0 */4 * * *", label: "Every 4 hours" },
  "morning": { cron: "0 9 * * *", label: "Daily at 9 AM" },
  "3x": { cron: "0 9,14,20 * * *", label: "3x daily (9 AM, 2 PM, 8 PM)" },
  "daily": { cron: "0 9 * * *", label: "Daily at 9 AM" },
};

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
      "/schedule 1h|2h|4h|morning|3x|daily — Set frequency\n" +
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
    const totalForLevel = getWordsForLevel(arg as CefrLevel).length;
    await ctx.reply(`Level set to ${arg}.\n📖 ${totalForLevel} local words available + live Ekilex queries for more.`);
  });

  bot.command("schedule", async (ctx) => {
    const arg = ctx.match?.trim().toLowerCase();
    if (!arg || !SCHEDULE_PRESETS[arg]) {
      const options = Object.entries(SCHEDULE_PRESETS)
        .map(([key, val]) => `  ${key} — ${val.label}`)
        .join("\n");
      await ctx.reply(`Usage: /schedule <preset>\n\nPresets:\n${options}`);
      return;
    }
    const preset = SCHEDULE_PRESETS[arg];
    setSubscriberSchedule(db, ctx.chat.id, preset.cron);
    await ctx.reply(`⏰ Schedule set to: ${preset.label}\n\nNote: The global scheduler runs on a fixed interval. Your personal schedule preference is saved for future per-user scheduling.`);
  });

  bot.command("stats", async (ctx) => {
    const { sent, level, schedule } = getStats(db, ctx.chat.id);
    const totalForLevel = getWordsForLevel(level).length;
    const scheduleLabel = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === schedule)?.[1].label ?? schedule;
    await ctx.reply(
      `📊 Your progress:\n` +
      `🏷️ Level: ${level}\n` +
      `📚 Words learned: ${sent}\n` +
      `📖 Local ${level} words: ${totalForLevel}\n` +
      `🌐 Ekilex: unlimited additional words\n` +
      `⏰ Schedule: ${scheduleLabel}\n` +
      `✅ Local progress: ${totalForLevel > 0 ? Math.round((sent / totalForLevel) * 100) : 0}%`,
    );
  });
}
