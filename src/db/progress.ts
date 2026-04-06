import pg from "pg";
import type { CefrLevel } from "../config.js";

const { Pool } = pg;

export interface Subscriber {
  chatId: number;
  channel: string;
  cefrLevel: CefrLevel;
  schedule: string;
  active: boolean;
}

let pool: pg.Pool;

export async function initDb(connectionString: string): Promise<pg.Pool> {
  // Connect to default 'postgres' DB first to create our DB if needed
  const parsed = new URL(connectionString);
  const dbName = parsed.pathname.slice(1); // remove leading /
  const adminUrl = `${parsed.protocol}//${parsed.username}:${parsed.password}@${parsed.host}/postgres`;

  const adminPool = new Pool({ connectionString: adminUrl });
  try {
    const res = await adminPool.query(
      "SELECT 1 FROM pg_database WHERE datname = $1",
      [dbName],
    );
    if (res.rowCount === 0) {
      await adminPool.query(`CREATE DATABASE "${dbName}"`);
      console.error(`[db] Created database "${dbName}"`);
    }
  } finally {
    await adminPool.end();
  }

  // Now connect to the actual database
  pool = new Pool({ connectionString });
  await pool.query(`
    CREATE TABLE IF NOT EXISTS subscribers (
      chat_id BIGINT PRIMARY KEY,
      channel TEXT NOT NULL DEFAULT 'telegram',
      cefr_level TEXT NOT NULL DEFAULT 'A1',
      schedule TEXT NOT NULL DEFAULT '0 9 * * *',
      registered_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      active BOOLEAN NOT NULL DEFAULT TRUE
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS sent_words (
      chat_id BIGINT NOT NULL,
      word_id TEXT NOT NULL,
      word_value TEXT,
      sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (chat_id, word_id)
    )
  `);

  console.error(`[db] Connected to PostgreSQL database "${dbName}"`);
  return pool;
}

export async function closeDb(): Promise<void> {
  if (pool) await pool.end();
}

export async function addSubscriber(chatId: number, channel = "telegram"): Promise<void> {
  await pool.query(`
    INSERT INTO subscribers (chat_id, channel) VALUES ($1, $2)
    ON CONFLICT (chat_id) DO UPDATE SET active = TRUE, channel = EXCLUDED.channel
  `, [chatId, channel]);
}

export async function removeSubscriber(chatId: number): Promise<void> {
  await pool.query("UPDATE subscribers SET active = FALSE WHERE chat_id = $1", [chatId]);
}

export async function getActiveSubscribers(): Promise<Subscriber[]> {
  const res = await pool.query(
    "SELECT chat_id, channel, cefr_level, schedule, active FROM subscribers WHERE active = TRUE",
  );
  return res.rows.map((r) => ({
    chatId: Number(r.chat_id),
    channel: r.channel,
    cefrLevel: r.cefr_level as CefrLevel,
    schedule: r.schedule || "0 9 * * *",
    active: r.active,
  }));
}

export async function getSubscriberLevel(chatId: number): Promise<CefrLevel> {
  const res = await pool.query("SELECT cefr_level FROM subscribers WHERE chat_id = $1", [chatId]);
  return (res.rows[0]?.cefr_level ?? "A1") as CefrLevel;
}

export async function setSubscriberLevel(chatId: number, level: CefrLevel): Promise<void> {
  await pool.query("UPDATE subscribers SET cefr_level = $1 WHERE chat_id = $2", [level, chatId]);
}

export async function getSubscriberSchedule(chatId: number): Promise<string> {
  const res = await pool.query("SELECT schedule FROM subscribers WHERE chat_id = $1", [chatId]);
  return res.rows[0]?.schedule ?? "0 9 * * *";
}

export async function setSubscriberSchedule(chatId: number, schedule: string): Promise<void> {
  await pool.query("UPDATE subscribers SET schedule = $1 WHERE chat_id = $2", [schedule, chatId]);
}

export async function markWordSent(chatId: number, wordId: string, wordValue?: string): Promise<void> {
  await pool.query(
    "INSERT INTO sent_words (chat_id, word_id, word_value) VALUES ($1, $2, $3) ON CONFLICT DO NOTHING",
    [chatId, wordId, wordValue ?? null],
  );
}

export async function getSentWordIds(chatId: number): Promise<string[]> {
  const res = await pool.query("SELECT word_id FROM sent_words WHERE chat_id = $1", [chatId]);
  return res.rows.map((r) => r.word_id);
}

export async function getSentWordValues(chatId: number): Promise<Set<string>> {
  const res = await pool.query(
    "SELECT word_value FROM sent_words WHERE chat_id = $1 AND word_value IS NOT NULL",
    [chatId],
  );
  return new Set(res.rows.map((r) => r.word_value));
}

export async function getStats(chatId: number): Promise<{ sent: number; level: CefrLevel; schedule: string }> {
  const level = await getSubscriberLevel(chatId);
  const schedule = await getSubscriberSchedule(chatId);
  const res = await pool.query("SELECT COUNT(*) as count FROM sent_words WHERE chat_id = $1", [chatId]);
  return { sent: Number(res.rows[0].count), level, schedule };
}
