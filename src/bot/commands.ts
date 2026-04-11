import type { Bot } from "grammy";
import type { CefrLevel } from "../config.js";
import { addSubscriber, removeSubscriber, setSubscriberLevel, setSubscriberSchedule, getStats, getSubscriberLevel, getSubscriberSchedule, getQuizStats, getQuizHistory, getStreak, getTodayActivity, getWordsDueForReview, getPreferences, updatePreference } from "../db/progress.js";
import { getWordsForLevel } from "../flashcard/word-bank.js";
import { getAllCategories } from "../flashcard/categories.js";
import { mainMenuKeyboard, levelPicker, schedulePicker, preferencesKeyboard, voicePicker } from "./keyboards.js";
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

interface TodayActivity {
  wordsLearned: number;
  quizzesTaken: number;
}

function formatSettings(
  level: CefrLevel,
  schedule: string,
  sent: number,
  quiz?: QuizStatsInfo,
  streak?: number,
  today?: TodayActivity,
): string {
  const totalForLevel = getWordsForLevel(level).length;
  const scheduleLabel = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === schedule)?.[1].label ?? schedule;
  const pct = totalForLevel > 0 ? Math.round((sent / totalForLevel) * 100) : 0;

  const streakEmoji = (streak ?? 0) >= 7 ? "🔥" : (streak ?? 0) >= 3 ? "⚡" : "📅";

  const lines = [
    "<b>🇪🇪 Flash Card IO</b>",
    "",
    `${streakEmoji} Streak: <b>${streak ?? 0} day${(streak ?? 0) !== 1 ? "s" : ""}</b>`,
    `🏷️ Level: <b>${level}</b>`,
    `⏰ Schedule: <b>${escapeHtml(scheduleLabel)}</b>`,
    `📚 Words learned: <b>${sent}</b>`,
    `📖 Local ${level} words: ${totalForLevel}`,
    `✅ Progress: ${pct}%`,
  ];

  if (today && (today.wordsLearned > 0 || today.quizzesTaken > 0)) {
    lines.push("");
    lines.push("<b>Today:</b>");
    if (today.wordsLearned > 0) lines.push(`  📚 ${today.wordsLearned} word${today.wordsLearned !== 1 ? "s" : ""} learned`);
    if (today.quizzesTaken > 0) lines.push(`  🧠 ${today.quizzesTaken} quiz${today.quizzesTaken !== 1 ? "zes" : ""} taken`);
  }

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

async function safeEditMessage(ctx: any, text: string, extra?: any): Promise<void> {
  try {
    await ctx.editMessageText(text, extra);
  } catch (err: any) {
    if (err?.description?.includes("message is not modified")) return;
    throw err;
  }
}

async function getSettingsText(chatId: number): Promise<string> {
  const [{ sent, level, schedule }, quiz, streak, today] = await Promise.all([
    getStats(chatId),
    getQuizStats(chatId),
    getStreak(chatId),
    getTodayActivity(chatId),
  ]);
  return formatSettings(level, schedule, sent, quiz, streak, today);
}

export function registerCommands(
  bot: Bot,
  deliverFlashcard: (chatId: number) => Promise<void>,
  deliverGrammarCard: (chatId: number) => Promise<void>,
  refreshUserJobs?: () => Promise<void>,
  ekilexApiKey?: string | null,
): void {
  bot.api.setMyCommands([
    { command: "start", description: "Start the bot / show menu" },
    { command: "next", description: "Get a flashcard now" },
    { command: "grammar", description: "Get a grammar card" },
    { command: "quiz", description: "Start a vocabulary quiz" },
    { command: "quizhistory", description: "View past quiz results & mistakes" },
    { command: "review", description: "Review words due today (SM-2)" },
    { command: "topics", description: "Browse vocabulary topics" },
    { command: "idiom", description: "Random Estonian idiom" },
    { command: "settings", description: "Open settings menu" },
    { command: "stats", description: "See your progress" },
    { command: "stop", description: "Stop receiving flashcards" },
  ]).catch((err) => console.error("[commands] Failed to set bot commands:", err instanceof Error ? err.message : err));

  // --- Slash commands ---

  bot.command("start", async (ctx) => {
    const chatId = ctx.chat.id;
    await addSubscriber(chatId, "telegram", ctx.from?.username, ctx.from?.first_name);
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

  bot.command("quizhistory", async (ctx) => {
    const history = await getQuizHistory(ctx.chat.id, 5);
    if (history.length === 0) {
      await ctx.reply("No quiz history yet. Use /quiz to take your first quiz!");
      return;
    }

    let text = "<b>📋 Quiz History</b>\n";

    for (const quiz of history) {
      const date = quiz.completedAt.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" });
      const emoji = quiz.percentage >= 80 ? "🌟" : quiz.percentage >= 60 ? "👍" : "📚";

      text += `\n${emoji} <b>${date}</b> — ${quiz.score}/${quiz.total} (${quiz.percentage}%)`;

      const mistakes = quiz.answers.filter((a) => !a.isCorrect);
      if (mistakes.length > 0) {
        for (const m of mistakes) {
          text += `\n   ❌ ${escapeHtml(m.estonian)} = ${escapeHtml(m.correctAnswer)} <i>(you: ${escapeHtml(m.userAnswer)})</i>`;
        }
      } else {
        text += "\n   ✅ Perfect score!";
      }
    }

    text += "\n\n<i>Showing last 5 quizzes</i>";

    await ctx.reply(text, { parse_mode: "HTML" });
  });

  bot.command("review", async (ctx) => {
    const words = await getWordsDueForReview(ctx.chat.id, 5);
    if (words.length === 0) {
      await ctx.reply("No words due for review! All caught up. Come back later or use /quiz to practice.");
      return;
    }

    let text = "<b>📝 Words Due for Review</b>\n\n";
    text += "How many do you remember?\n\n";

    for (const w of words) {
      text += `<b>${escapeHtml(w.wordValue)}</b> — <tg-spoiler>${escapeHtml(w.english)}</tg-spoiler>\n`;
    }

    text += "\n<i>Tap the spoilers to check your memory. Use /quiz to test yourself!</i>";

    await ctx.reply(text, { parse_mode: "HTML" });
  });

  bot.command("topics", async (ctx) => {
    const cats = getAllCategories();
    if (cats.length === 0) {
      await ctx.reply("No topic categories available.");
      return;
    }
    let text = "<b>📚 Vocabulary Topics</b>\n\n";
    for (const cat of cats) {
      text += `${cat.emoji} <b>${escapeHtml(cat.label)}</b> — ${cat.wordCount} words\n`;
    }
    text += "\n<i>Topics help organize your learning. Words from all topics appear in flashcards and quizzes.</i>";
    await ctx.reply(text, { parse_mode: "HTML" });
  });

  bot.command("idiom", async (ctx) => {
    const idioms = [
      { estonian: "Iga algus on raske.", english: "Every beginning is hard.", meaning: "Starting something new is always difficult." },
      { estonian: "Harjutamine teeb meistriks.", english: "Practice makes a master.", meaning: "Practice makes perfect." },
      { estonian: "Kes otsib, see leiab.", english: "Who seeks, finds.", meaning: "If you look for something, you'll find it." },
      { estonian: "Hommik on õhtust targem.", english: "Morning is wiser than evening.", meaning: "Sleep on it before making decisions." },
      { estonian: "Üheksa korda mõõda, üks kord lõika.", english: "Measure nine times, cut once.", meaning: "Think carefully before acting." },
      { estonian: "Kus suitsu, seal tuld.", english: "Where there's smoke, there's fire.", meaning: "Rumors usually have some truth." },
      { estonian: "Aeg on raha.", english: "Time is money.", meaning: "Don't waste time." },
      { estonian: "Kes teisele auku kaevab, see ise sisse kukub.", english: "Who digs a hole for another falls in themselves.", meaning: "Karma — what goes around comes around." },
      { estonian: "Õnn tuleb magades.", english: "Luck comes while sleeping.", meaning: "Good things come to those who wait." },
      { estonian: "Küla hull on küla ilu.", english: "The village fool is the village's beauty.", meaning: "Every community needs its characters." },
      { estonian: "Oma silm on kuningas.", english: "Your own eye is king.", meaning: "Seeing is believing." },
      { estonian: "Rumal rääkigu palju, tark kuulab ja teab.", english: "The fool talks a lot, the wise listens and knows.", meaning: "Listen more than you speak." },
      { estonian: "Igal oinal oma mihklipäev.", english: "Every ram has its Michaelmas.", meaning: "Everyone gets what's coming to them." },
      { estonian: "Vana karu ei tantsi.", english: "An old bear doesn't dance.", meaning: "Old habits die hard." },
      { estonian: "Kes kannatab, see kaua elab.", english: "Who endures, lives long.", meaning: "Patience is a virtue." },
    ];
    const pick = idioms[Math.floor(Math.random() * idioms.length)];
    const text =
      `🇪🇪 <b>Estonian Idiom</b>\n\n` +
      `💬 <i>${escapeHtml(pick.estonian)}</i>\n` +
      `📝 <tg-spoiler>${escapeHtml(pick.english)}</tg-spoiler>\n\n` +
      `💡 ${escapeHtml(pick.meaning)}`;
    await ctx.reply(text, { parse_mode: "HTML" });
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
          await startQuiz(bot, chatId, ekilexApiKey);
        } catch (err) {
          console.error("[commands] action:quiz error:", err instanceof Error ? err.message : err);
        }
        break;

      case "stats": {
        await ctx.answerCallbackQuery();
        await safeEditMessage(ctx,await getSettingsText(chatId), {
          parse_mode: "HTML",
          reply_markup: mainMenuKeyboard(),
        });
        break;
      }

      case "stop":
        await removeSubscriber(chatId);
        refreshUserJobs?.().catch((err) => console.error("[commands] refreshUserJobs error:", err instanceof Error ? err.message : err));
        await ctx.answerCallbackQuery({ text: "Stopped." });
        await safeEditMessage(ctx,"Stopped. Send /start to resume.");
        break;

      case "preferences": {
        const prefs = await getPreferences(chatId);
        await ctx.answerCallbackQuery();
        await safeEditMessage(ctx,"<b>⚙️ Preferences</b>\n\nTap to toggle:", {
          parse_mode: "HTML",
          reply_markup: preferencesKeyboard(prefs),
        });
        break;
      }
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
        await safeEditMessage(ctx,`Select your level (current: <b>${current}</b>):`, {
          parse_mode: "HTML",
          reply_markup: levelPicker(current),
        });
        break;
      }
      case "schedule": {
        const currentCron = await getSubscriberSchedule(chatId);
        const label = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === currentCron)?.[1].label ?? currentCron;
        await safeEditMessage(ctx,`Select your schedule (current: <b>${escapeHtml(label)}</b>):`, {
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
        await safeEditMessage(ctx,await getSettingsText(chatId), {
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
        await safeEditMessage(ctx,await getSettingsText(chatId), {
          parse_mode: "HTML",
          reply_markup: mainMenuKeyboard(),
        });
        break;
      }
    }
  });

  bot.callbackQuery(/^pref:/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    const key = ctx.callbackQuery.data!.replace("pref:", "");

    if (key === "voicePicker") {
      const prefs = await getPreferences(chatId);
      await ctx.answerCallbackQuery();
      await safeEditMessage(ctx,"<b>🎤 Select Voice</b>\n\nChoose an Estonian voice:", {
        parse_mode: "HTML",
        reply_markup: voicePicker(prefs.voiceName),
      });
      return;
    }

    // Toggle boolean preferences
    const prefs = await getPreferences(chatId);
    const current = (prefs as any)[key];
    if (typeof current === "boolean") {
      await updatePreference(chatId, key, !current);
      const updated = await getPreferences(chatId);
      await ctx.answerCallbackQuery({ text: `${key}: ${!current ? "ON" : "OFF"}` });
      await safeEditMessage(ctx,"<b>⚙️ Preferences</b>\n\nTap to toggle:", {
        parse_mode: "HTML",
        reply_markup: preferencesKeyboard(updated),
      });
    }
  });

  bot.callbackQuery(/^set:voice:/, async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    const voice = ctx.callbackQuery.data!.replace("set:voice:", "");
    await updatePreference(chatId, "voiceName", voice);
    await ctx.answerCallbackQuery({ text: `Voice: ${voice}` });
    const prefs = await getPreferences(chatId);
    await safeEditMessage(ctx,"<b>⚙️ Preferences</b>\n\nTap to toggle:", {
      parse_mode: "HTML",
      reply_markup: preferencesKeyboard(prefs),
    });
  });

  bot.callbackQuery("back_menu", async (ctx) => {
    const chatId = ctx.chat?.id;
    if (!chatId) return;
    await ctx.answerCallbackQuery();
    await safeEditMessage(ctx,await getSettingsText(chatId), {
      parse_mode: "HTML",
      reply_markup: mainMenuKeyboard(),
    });
  });
}
