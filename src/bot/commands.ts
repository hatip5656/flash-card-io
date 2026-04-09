import type { Bot } from "grammy";
import type { CefrLevel } from "../config.js";
import { addSubscriber, removeSubscriber, setSubscriberLevel, setSubscriberSchedule, getStats, getSubscriberLevel, getSubscriberSchedule, getQuizStats, getQuizHistory } from "../db/progress.js";
import { getWordsForLevel } from "../flashcard/word-bank.js";
import { mainMenuKeyboard, levelPicker, schedulePicker } from "./keyboards.js";
import { startQuiz } from "./quiz.js";
import { escapeHtml } from "../flashcard/builder.js";

const VALID_LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];

export const SCHEDULE_PRESETS: Record<string, { cron: string; label: string }> = {
  "1h": { cron: "0 * * * *", label: "Every hour" },
  "2h": { cron: "0 */2 * * *", label: "Every 2 hours" },
  "4h": { cron: "0 */4 * * *", label: "Every 4 hours" },
  "morning": { cron: "0 9 * * *", label: "Daily at 9 AM" },
  "3x": { cron: "0 9,14,20 * * *", label: "3x daily (9 AM, 2 PM, 8 PM)" },
};

interface QuizStatsInfo {
  totalQuizzes: number;
  avgPercentage: number;
  recentTrend: number | null;
}

function formatSettings(level: CefrLevel, schedule: string, sent: number, quiz?: QuizStatsInfo): string {
  const totalForLevel = getWordsForLevel(level).length;
  const scheduleLabel = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === schedule)?.[1].label ?? schedule;
  const pct = totalForLevel > 0 ? Math.round((sent / totalForLevel) * 100) : 0;
  const lines = [
    "<b>🇪🇪 Flash Card IO</b>",
    "",
    `🏷️ Level: <b>${level}</b>`,
    `⏰ Schedule: <b>${escapeHtml(scheduleLabel)}</b>`,
    `📚 Words learned: <b>${sent}</b>`,
    `📖 Local ${level} words: ${totalForLevel}`,
    `✅ Progress: ${pct}%`,
  ];

  if (quiz && quiz.totalQuizzes > 0) {
    lines.push("");
    lines.push(`🧠 Quizzes taken: <b>${quiz.totalQuizzes}</b>`);
    lines.push(`📊 Average score: <b>${quiz.avgPercentage}%</b>`);
    if (quiz.recentTrend !== null) {
      const arrow = quiz.recentTrend > 0 ? "📈" : quiz.recentTrend < 0 ? "📉" : "➡️";
      const sign = quiz.recentTrend > 0 ? "+" : "";
      lines.push(`${arrow} Trend: <b>${sign}${quiz.recentTrend}%</b>`);
    }
  }

  return lines.join("\n");
}

async function getSettingsText(chatId: number): Promise<string> {
  const [{ sent, level, schedule }, quiz] = await Promise.all([
    getStats(chatId),
    getQuizStats(chatId),
  ]);
  return formatSettings(level, schedule, sent, quiz);
}

export function registerCommands(
  bot: Bot,
  deliverFlashcard: (chatId: number) => Promise<void>,
  deliverGrammarCard: (chatId: number) => Promise<void>,
  refreshUserJobs?: () => Promise<void>,
): void {
  bot.api.setMyCommands([
    { command: "start", description: "Start the bot / show menu" },
    { command: "next", description: "Get a flashcard now" },
    { command: "grammar", description: "Get a grammar card" },
    { command: "quiz", description: "Start a vocabulary quiz" },
    { command: "settings", description: "Open settings menu" },
    { command: "stats", description: "See your progress" },
    { command: "stop", description: "Stop receiving flashcards" },
  ]).catch((err) => console.error("[commands] Failed to set bot commands:", err instanceof Error ? err.message : err));

  // --- Slash commands ---

  bot.command("start", async (ctx) => {
    const chatId = ctx.chat.id;
    await addSubscriber(chatId, "telegram");
    refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
    await ctx.reply(await getSettingsText(chatId), {
      parse_mode: "HTML",
      reply_markup: mainMenuKeyboard(),
    });
  });

  bot.command("settings", async (ctx) => {
    await ctx.reply(await getSettingsText(ctx.chat.id), {
      parse_mode: "HTML",
      reply_markup: mainMenuKeyboard(),
    });
  });

  bot.command("stop", async (ctx) => {
    await removeSubscriber(ctx.chat.id);
    refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
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

  bot.command("grammar", async (ctx) => {
    try {
      await deliverGrammarCard(ctx.chat.id);
    } catch (err) {
      await ctx.reply("Failed to send grammar card. Try again later.");
      console.error("[commands] /grammar error:", err instanceof Error ? err.message : err);
    }
  });

  bot.command("level", async (ctx) => {
    const arg = ctx.match?.trim().toUpperCase();
    if (!arg || !VALID_LEVELS.includes(arg as CefrLevel)) {
      const current = await getSubscriberLevel(ctx.chat.id);
      await ctx.reply(`Select your level:`, {
        reply_markup: levelPicker(current),
      });
      return;
    }
    await setSubscriberLevel(ctx.chat.id, arg as CefrLevel);
    refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
    const totalForLevel = getWordsForLevel(arg as CefrLevel).length;
    await ctx.reply(`Level set to ${arg}.\n📖 ${totalForLevel} local words available + live Ekilex queries for more.`);
  });

  bot.command("schedule", async (ctx) => {
    const arg = ctx.match?.trim().toLowerCase();
    if (!arg || !SCHEDULE_PRESETS[arg]) {
      const currentCron = await getSubscriberSchedule(ctx.chat.id);
      await ctx.reply(`Select your schedule:`, {
        reply_markup: schedulePicker(currentCron),
      });
      return;
    }
    const preset = SCHEDULE_PRESETS[arg];
    await setSubscriberSchedule(ctx.chat.id, preset.cron);
    refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
    await ctx.reply(`⏰ Schedule set to: ${preset.label}`);
  });

  bot.command("stats", async (ctx) => {
    await ctx.reply(await getSettingsText(ctx.chat.id), {
      parse_mode: "HTML",
      reply_markup: mainMenuKeyboard(),
    });
  });

  // --- Callback queries for inline buttons ---

  bot.callbackQuery(/^action:/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    const action = ctx.callbackQuery.data!.split(":")[1];

    switch (action) {
      case "next":
        try {
          await ctx.answerCallbackQuery();
          await deliverFlashcard(chatId);
        } catch (err) {
          console.error("[commands] action:next error:", err instanceof Error ? err.message : err);
        }
        break;

      case "grammar":
        try {
          await ctx.answerCallbackQuery();
          await deliverGrammarCard(chatId);
        } catch (err) {
          console.error("[commands] action:grammar error:", err instanceof Error ? err.message : err);
        }
        break;

      case "quiz":
        await ctx.answerCallbackQuery();
        try {
          await startQuiz(bot, chatId);
        } catch (err) {
          console.error("[commands] action:quiz error:", err instanceof Error ? err.message : err);
        }
        break;

      case "stats": {
        await ctx.answerCallbackQuery();
        await ctx.editMessageText(await getSettingsText(chatId), {
          parse_mode: "HTML",
          reply_markup: mainMenuKeyboard(),
        });
        break;
      }

      case "stop":
        await removeSubscriber(chatId);
        refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
        await ctx.answerCallbackQuery({ text: "Stopped." });
        await ctx.editMessageText("Stopped. Send /start to resume.");
        break;
    }
  });

  bot.callbackQuery(/^edit_/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    const field = ctx.callbackQuery.data!.replace("edit_", "");
    await ctx.answerCallbackQuery();

    switch (field) {
      case "level": {
        const current = await getSubscriberLevel(chatId);
        await ctx.editMessageText(`Select your level (current: <b>${current}</b>):`, {
          parse_mode: "HTML",
          reply_markup: levelPicker(current),
        });
        break;
      }
      case "schedule": {
        const currentCron = await getSubscriberSchedule(chatId);
        const label = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === currentCron)?.[1].label ?? currentCron;
        await ctx.editMessageText(`Select your schedule (current: <b>${escapeHtml(label)}</b>):`, {
          parse_mode: "HTML",
          reply_markup: schedulePicker(currentCron),
        });
        break;
      }
    }
  });

  bot.callbackQuery(/^set:/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    const [, field, value] = ctx.callbackQuery.data!.split(":");

    switch (field) {
      case "level": {
        if (!VALID_LEVELS.includes(value as CefrLevel)) break;
        await setSubscriberLevel(chatId, value as CefrLevel);
        refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
        const totalForLevel = getWordsForLevel(value as CefrLevel).length;
        await ctx.answerCallbackQuery({ text: `Level set to ${value} (${totalForLevel} words)` });
        await ctx.editMessageText(await getSettingsText(chatId), {
          parse_mode: "HTML",
          reply_markup: mainMenuKeyboard(),
        });
        break;
      }
      case "schedule": {
        const preset = SCHEDULE_PRESETS[value];
        if (!preset) break;
        await setSubscriberSchedule(chatId, preset.cron);
        refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
        await ctx.answerCallbackQuery({ text: `Schedule: ${preset.label}` });
        await ctx.editMessageText(await getSettingsText(chatId), {
          parse_mode: "HTML",
          reply_markup: mainMenuKeyboard(),
        });
        break;
      }
    }
  });

  bot.callbackQuery("back_menu", async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    await ctx.answerCallbackQuery();
    await ctx.editMessageText(await getSettingsText(chatId), {
      parse_mode: "HTML",
      reply_markup: mainMenuKeyboard(),
    });
  });
}
