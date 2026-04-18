import type { Request, Response } from "express";
import { addSubscriber, removeSubscriber, getStats, getSubscriberLevel, setSubscriberLevel, getSubscriberSchedule, setSubscriberSchedule, getPreferences, updatePreference, updateNextDelivery, scheduleNextGrammar, SCHEDULE_OFF } from "../../db/progress.js";
import { invalidateQueue } from "../../services/prebuild.js";
import { getWordsForLevel } from "../../flashcard/word-bank.js";
import { SCHEDULE_PRESETS } from "../../bot/commands.js";
import { VALID_LEVELS, type CefrLevel } from "../../config.js";

export async function register(req: Request, res: Response): Promise<void> {
  const { userId, channel, username, firstName } = req.body;
  if (!userId || isNaN(Number(userId))) {
    res.status(400).json({ error: "userId is required" });
    return;
  }
  const chatId = Number(userId);
  await addSubscriber(chatId, channel ?? "api", username, firstName);

  const timezone = req.app.get("cronTimezone") as string;
  const schedule = await getSubscriberSchedule(chatId);
  await updateNextDelivery(chatId, schedule, timezone);
  await scheduleNextGrammar(chatId, timezone);

  const stats = await getStats(chatId);
  res.status(201).json({ userId: chatId, ...stats });
}

export async function getUser(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const [stats, level, schedule, prefs] = await Promise.all([
    getStats(chatId),
    getSubscriberLevel(chatId),
    getSubscriberSchedule(chatId),
    getPreferences(chatId),
  ]);
  const scheduleLabel = Object.entries(SCHEDULE_PRESETS).find(([, v]) => v.cron === schedule)?.[1].label ?? schedule;
  const totalForLevel = getWordsForLevel(level).length;
  res.json({ userId: chatId, level, schedule, scheduleLabel, totalForLevel, preferences: prefs, wordsLearned: stats.sent });
}

export async function setLevel(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const { level } = req.body;
  if (!level || !VALID_LEVELS.includes(level)) {
    res.status(400).json({ error: `Invalid level. Must be one of: ${VALID_LEVELS.join(", ")}` });
    return;
  }
  await setSubscriberLevel(chatId, level);
  await invalidateQueue(chatId);
  const totalForLevel = getWordsForLevel(level).length;
  res.json({ level, totalForLevel });
}

export async function setSchedule(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const { schedule: presetKey } = req.body;
  const preset = SCHEDULE_PRESETS[presetKey];
  if (!preset) {
    res.status(400).json({ error: `Invalid schedule. Options: ${Object.keys(SCHEDULE_PRESETS).join(", ")}` });
    return;
  }
  const currentCron = await getSubscriberSchedule(chatId);
  if (currentCron === preset.cron) {
    res.json({ schedule: presetKey, label: preset.label, changed: false });
    return;
  }
  await setSubscriberSchedule(chatId, preset.cron);
  const timezone = req.app.get("cronTimezone") as string;
  await updateNextDelivery(chatId, preset.cron, timezone);
  res.json({ schedule: presetKey, label: preset.label, changed: true });
}

export async function getUserPreferences(req: Request, res: Response): Promise<void> {
  const prefs = await getPreferences(req.userId!);
  res.json(prefs);
}

const VALID_PREF_KEYS = new Set(["audio", "voiceName", "wordForms", "grammarCards", "dailySummary", "weeklyReport"]);

export async function setPreference(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const { key, value } = req.body;
  if (!key || !VALID_PREF_KEYS.has(key)) {
    res.status(400).json({ error: `Invalid key. Must be one of: ${[...VALID_PREF_KEYS].join(", ")}` });
    return;
  }
  if (key === "voiceName" && typeof value !== "string") {
    res.status(400).json({ error: "voiceName must be a string" });
    return;
  }
  if (key !== "voiceName" && typeof value !== "boolean") {
    res.status(400).json({ error: `${key} must be a boolean` });
    return;
  }
  await updatePreference(chatId, key, value);
  const prefs = await getPreferences(chatId);
  res.json(prefs);
}

export async function unsubscribe(req: Request, res: Response): Promise<void> {
  await removeSubscriber(req.userId!);
  res.json({ unsubscribed: true });
}

export async function getSchedulePresets(_req: Request, res: Response): Promise<void> {
  const presets = Object.entries(SCHEDULE_PRESETS).map(([key, p]) => ({
    key,
    cron: p.cron,
    label: p.label,
    isOff: p.cron === SCHEDULE_OFF,
  }));
  res.json(presets);
}
