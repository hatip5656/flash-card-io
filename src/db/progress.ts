import Database from "better-sqlite3";
import { mkdirSync } from "fs";
import { dirname } from "path";
import type { CefrLevel } from "../config.js";

export interface Subscriber {
  chatId: number;
  channel: string;
  cefrLevel: CefrLevel;
  schedule: string;
  active: boolean;
}

export function initDb(dbPath: string): Database.Database {
  mkdirSync(dirname(dbPath), { recursive: true });
  const db = new Database(dbPath);

  db.exec(`
    CREATE TABLE IF NOT EXISTS subscribers (
      chat_id INTEGER PRIMARY KEY,
      channel TEXT NOT NULL DEFAULT 'telegram',
      cefr_level TEXT NOT NULL DEFAULT 'A1',
      schedule TEXT NOT NULL DEFAULT '0 9 * * *',
      registered_at TEXT NOT NULL DEFAULT (datetime('now')),
      active INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS sent_words (
      chat_id INTEGER NOT NULL,
      word_id TEXT NOT NULL,
      word_value TEXT,
      sent_at TEXT NOT NULL DEFAULT (datetime('now')),
      PRIMARY KEY (chat_id, word_id)
    );
  `);

  // Migration: add schedule column if missing
  const cols = db.pragma("table_info(subscribers)") as Array<{ name: string }>;
  if (!cols.some((c) => c.name === "schedule")) {
    db.exec("ALTER TABLE subscribers ADD COLUMN schedule TEXT NOT NULL DEFAULT '0 9 * * *'");
  }
  // Migration: add word_value column if missing
  const wordCols = db.pragma("table_info(sent_words)") as Array<{ name: string }>;
  if (!wordCols.some((c) => c.name === "word_value")) {
    db.exec("ALTER TABLE sent_words ADD COLUMN word_value TEXT");
  }

  return db;
}

export function addSubscriber(db: Database.Database, chatId: number, channel = "telegram"): void {
  db.prepare(`
    INSERT INTO subscribers (chat_id, channel) VALUES (?, ?)
    ON CONFLICT(chat_id) DO UPDATE SET active = 1, channel = excluded.channel
  `).run(chatId, channel);
}

export function removeSubscriber(db: Database.Database, chatId: number): void {
  db.prepare("UPDATE subscribers SET active = 0 WHERE chat_id = ?").run(chatId);
}

export function getActiveSubscribers(db: Database.Database): Subscriber[] {
  const rows = db.prepare("SELECT chat_id, channel, cefr_level, schedule, active FROM subscribers WHERE active = 1").all() as Array<{
    chat_id: number;
    channel: string;
    cefr_level: string;
    schedule: string;
    active: number;
  }>;

  return rows.map((r) => ({
    chatId: r.chat_id,
    channel: r.channel,
    cefrLevel: r.cefr_level as CefrLevel,
    schedule: r.schedule || "0 9 * * *",
    active: r.active === 1,
  }));
}

export function getSubscriberLevel(db: Database.Database, chatId: number): CefrLevel {
  const row = db.prepare("SELECT cefr_level FROM subscribers WHERE chat_id = ?").get(chatId) as { cefr_level: string } | undefined;
  return (row?.cefr_level ?? "A1") as CefrLevel;
}

export function setSubscriberLevel(db: Database.Database, chatId: number, level: CefrLevel): void {
  db.prepare("UPDATE subscribers SET cefr_level = ? WHERE chat_id = ?").run(level, chatId);
}

export function getSubscriberSchedule(db: Database.Database, chatId: number): string {
  const row = db.prepare("SELECT schedule FROM subscribers WHERE chat_id = ?").get(chatId) as { schedule: string } | undefined;
  return row?.schedule ?? "0 9 * * *";
}

export function setSubscriberSchedule(db: Database.Database, chatId: number, schedule: string): void {
  db.prepare("UPDATE subscribers SET schedule = ? WHERE chat_id = ?").run(schedule, chatId);
}

export function markWordSent(db: Database.Database, chatId: number, wordId: string, wordValue?: string): void {
  db.prepare("INSERT OR IGNORE INTO sent_words (chat_id, word_id, word_value) VALUES (?, ?, ?)").run(chatId, wordId, wordValue ?? null);
}

export function getSentWordIds(db: Database.Database, chatId: number): string[] {
  const rows = db.prepare("SELECT word_id FROM sent_words WHERE chat_id = ?").all(chatId) as Array<{ word_id: string }>;
  return rows.map((r) => r.word_id);
}

export function getSentWordValues(db: Database.Database, chatId: number): Set<string> {
  const rows = db.prepare("SELECT word_value FROM sent_words WHERE chat_id = ? AND word_value IS NOT NULL").all(chatId) as Array<{ word_value: string }>;
  return new Set(rows.map((r) => r.word_value));
}

export function getStats(db: Database.Database, chatId: number): { sent: number; level: CefrLevel; schedule: string } {
  const level = getSubscriberLevel(db, chatId);
  const schedule = getSubscriberSchedule(db, chatId);
  const row = db.prepare("SELECT COUNT(*) as count FROM sent_words WHERE chat_id = ?").get(chatId) as { count: number };
  return { sent: row.count, level, schedule };
}
