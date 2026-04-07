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

  // Migrations
  await pool.query(`ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS english TEXT`);
  await pool.query(`ALTER TABLE sent_words ADD COLUMN IF NOT EXISTS quiz_count INTEGER NOT NULL DEFAULT 0`);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS sent_grammar (
      chat_id BIGINT NOT NULL,
      lesson_id TEXT NOT NULL,
      sent_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
      PRIMARY KEY (chat_id, lesson_id)
    )
  `);

  console.error(`[db] Connected to PostgreSQL database "${dbName}"`);
  return pool;
}

export async function getSentGrammarIds(chatId: number): Promise<Set<string>> {
  const res = await pool.query(
    "SELECT lesson_id FROM sent_grammar WHERE chat_id = $1",
    [chatId],
  );
  return new Set(res.rows.map((r) => r.lesson_id));
}

export async function markGrammarSent(chatId: number, lessonId: string): Promise<void> {
  await pool.query(
    "INSERT INTO sent_grammar (chat_id, lesson_id) VALUES ($1, $2) ON CONFLICT DO NOTHING",
    [chatId, lessonId],
  );
}

export async function backfillEnglish(wordLookup: (wordId: string) => string | null): Promise<number> {
  const res = await pool.query(
    "SELECT chat_id, word_id, word_value FROM sent_words WHERE english IS NULL AND word_value IS NOT NULL",
  );
  let updated = 0;
  for (const row of res.rows) {
    const english = wordLookup(row.word_id);
    if (english) {
      await pool.query(
        "UPDATE sent_words SET english = $1 WHERE chat_id = $2 AND word_id = $3",
        [english, row.chat_id, row.word_id],
      );
      updated++;
    }
  }
  return updated;
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

export async function markWordSent(chatId: number, wordId: string, wordValue?: string, english?: string): Promise<void> {
  await pool.query(
    "INSERT INTO sent_words (chat_id, word_id, word_value, english) VALUES ($1, $2, $3, $4) ON CONFLICT DO NOTHING",
    [chatId, wordId, wordValue ?? null, english ?? null],
  );
}

export async function getRandomLearnedWord(chatId: number): Promise<{ wordId: string; wordValue: string } | null> {
  const res = await pool.query(
    "SELECT word_id, word_value FROM sent_words WHERE chat_id = $1 AND word_value IS NOT NULL ORDER BY RANDOM() LIMIT 1",
    [chatId],
  );
  if (res.rows.length === 0) return null;
  return { wordId: res.rows[0].word_id, wordValue: res.rows[0].word_value };
}

export async function getLearnedWordsForQuiz(chatId: number): Promise<Array<{ estonian: string; english: string; quizCount: number }>> {
  const res = await pool.query(
    "SELECT word_value, english, quiz_count FROM sent_words WHERE chat_id = $1 AND word_value IS NOT NULL AND english IS NOT NULL ORDER BY quiz_count ASC",
    [chatId],
  );
  return res.rows.map((r) => ({ estonian: r.word_value, english: r.english, quizCount: Number(r.quiz_count) }));
}

export async function incrementQuizCount(chatId: number, wordValues: string[]): Promise<void> {
  if (wordValues.length === 0) return;
  await pool.query(
    "UPDATE sent_words SET quiz_count = quiz_count + 1 WHERE chat_id = $1 AND word_value = ANY($2)",
    [chatId, wordValues],
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
  const res = await pool.query(
    `SELECT s.cefr_level, s.schedule, COUNT(sw.word_id) AS sent
     FROM subscribers s
     LEFT JOIN sent_words sw ON sw.chat_id = s.chat_id
     WHERE s.chat_id = $1
     GROUP BY s.cefr_level, s.schedule`,
    [chatId],
  );
  if (res.rows.length === 0) {
    return { sent: 0, level: "A1" as CefrLevel, schedule: "0 9 * * *" };
  }
  return {
    sent: Number(res.rows[0].sent),
    level: (res.rows[0].cefr_level ?? "A1") as CefrLevel,
    schedule: res.rows[0].schedule ?? "0 9 * * *",
  };
}
