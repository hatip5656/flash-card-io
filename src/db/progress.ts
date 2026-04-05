import Database from "better-sqlite3";
import { mkdirSync } from "fs";
import { dirname } from "path";
import type { CefrLevel } from "../config.js";

export interface Subscriber {
  chatId: number;
  channel: string;
  cefrLevel: CefrLevel;
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
      registered_at TEXT NOT NULL DEFAULT (datetime('now')),
      active INTEGER NOT NULL DEFAULT 1
    );

    CREATE TABLE IF NOT EXISTS sent_words (
      chat_id INTEGER NOT NULL,
      word_id TEXT NOT NULL,
      sent_at TEXT NOT NULL DEFAULT (datetime('now')),
      PRIMARY KEY (chat_id, word_id)
    );
  `);

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
  const rows = db.prepare("SELECT chat_id, channel, cefr_level, active FROM subscribers WHERE active = 1").all() as Array<{
    chat_id: number;
    channel: string;
    cefr_level: string;
    active: number;
  }>;

  return rows.map((r) => ({
    chatId: r.chat_id,
    channel: r.channel,
    cefrLevel: r.cefr_level as CefrLevel,
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

export function markWordSent(db: Database.Database, chatId: number, wordId: string): void {
  db.prepare("INSERT OR IGNORE INTO sent_words (chat_id, word_id) VALUES (?, ?)").run(chatId, wordId);
}

export function getSentWordIds(db: Database.Database, chatId: number): string[] {
  const rows = db.prepare("SELECT word_id FROM sent_words WHERE chat_id = ?").all(chatId) as Array<{ word_id: string }>;
  return rows.map((r) => r.word_id);
}

export function getStats(db: Database.Database, chatId: number): { sent: number; level: CefrLevel } {
  const level = getSubscriberLevel(db, chatId);
  const row = db.prepare("SELECT COUNT(*) as count FROM sent_words WHERE chat_id = ?").get(chatId) as { count: number };
  return { sent: row.count, level };
}
