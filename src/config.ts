export type CefrLevel = "A1" | "A2" | "B1" | "B2";

export interface AppConfig {
  telegramBotToken: string;
  unsplashAccessKey: string;
  cronSchedule: string;
  cronTimezone: string;
  cefrLevels: CefrLevel[];
  dbPath: string;
  featureTelegram: boolean;
  featureWhatsapp: boolean;
}

const VALID_LEVELS: CefrLevel[] = ["A1", "A2", "B1", "B2"];

export function loadConfig(): AppConfig {
  const featureTelegram = process.env.FEATURE_TELEGRAM !== "false";
  const featureWhatsapp = process.env.FEATURE_WHATSAPP === "true";

  if (featureTelegram && !process.env.TELEGRAM_BOT_TOKEN) {
    throw new Error("TELEGRAM_BOT_TOKEN is required when FEATURE_TELEGRAM is enabled");
  }

  if (!process.env.UNSPLASH_ACCESS_KEY) {
    throw new Error("UNSPLASH_ACCESS_KEY is required");
  }

  const rawLevels = (process.env.CEFR_LEVELS ?? "A1,A2").split(",").map((s) => s.trim());
  const cefrLevels = rawLevels.filter((l): l is CefrLevel => VALID_LEVELS.includes(l as CefrLevel));

  if (cefrLevels.length === 0) {
    throw new Error(`Invalid CEFR_LEVELS: ${rawLevels.join(",")}. Must be one of: ${VALID_LEVELS.join(",")}`);
  }

  return {
    telegramBotToken: process.env.TELEGRAM_BOT_TOKEN ?? "",
    unsplashAccessKey: process.env.UNSPLASH_ACCESS_KEY,
    cronSchedule: process.env.CRON_SCHEDULE ?? "0 9 * * *",
    cronTimezone: process.env.CRON_TIMEZONE ?? "Europe/Tallinn",
    cefrLevels,
    dbPath: process.env.DB_PATH ?? "./data/db/progress.db",
    featureTelegram,
    featureWhatsapp,
  };
}
