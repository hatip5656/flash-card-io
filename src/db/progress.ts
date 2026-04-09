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

  await pool.query(`
    CREATE TABLE IF NOT EXISTS quiz_results (
      id SERIAL PRIMARY KEY,
      chat_id BIGINT NOT NULL,
      score INTEGER NOT NULL,
      total INTEGER NOT NULL,
      percentage INTEGER NOT NULL,
      completed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
    )
  `);

  await pool.query(`
    CREATE TABLE IF NOT EXISTS quiz_answers (
      id SERIAL PRIMARY KEY,
      quiz_id INTEGER NOT NULL REFERENCES quiz_results(id),
      estonian TEXT NOT NULL,
      correct_answer TEXT NOT NULL,
      user_answer TEXT NOT NULL,
      is_correct BOOLEAN NOT NULL
    )
  `);

  console.error(`[db] Connected to PostgreSQL database "${dbName}"`);
  return pool;
}

export interface QuizAnswer {
  estonian: string;
  correctAnswer: string;
  userAnswer: string;
  isCorrect: boolean;
}

export interface QuizResult {
  id: number;
  score: number;
  total: number;
  percentage: number;
  completedAt: Date;
  answers: QuizAnswer[];
}

export async function saveQuizResult(
  chatId: number,
  score: number,
  total: number,
  answers: QuizAnswer[],
): Promise<void> {
  const percentage = Math.round((score / total) * 100);
  const res = await pool.query(
    "INSERT INTO quiz_results (chat_id, score, total, percentage) VALUES ($1, $2, $3, $4) RETURNING id",
    [chatId, score, total, percentage],
  );
  const quizId = res.rows[0].id;

  for (const a of answers) {
    await pool.query(
      "INSERT INTO quiz_answers (quiz_id, estonian, correct_answer, user_answer, is_correct) VALUES ($1, $2, $3, $4, $5)",
      [quizId, a.estonian, a.correctAnswer, a.userAnswer, a.isCorrect],
    );
  }
}

export async function getQuizHistory(chatId: number, limit = 5): Promise<QuizResult[]> {
  const res = await pool.query(
    "SELECT id, score, total, percentage, completed_at FROM quiz_results WHERE chat_id = $1 ORDER BY completed_at DESC LIMIT $2",
    [chatId, limit],
  );

  const results: QuizResult[] = [];
  for (const r of res.rows) {
    const answersRes = await pool.query(
      "SELECT estonian, correct_answer, user_answer, is_correct FROM quiz_answers WHERE quiz_id = $1 ORDER BY id",
      [r.id],
    );
    results.push({
      id: Number(r.id),
      score: Number(r.score),
      total: Number(r.total),
      percentage: Number(r.percentage),
      completedAt: new Date(r.completed_at),
      answers: answersRes.rows.map((a) => ({
        estonian: a.estonian,
        correctAnswer: a.correct_answer,
        userAnswer: a.user_answer,
        isCorrect: a.is_correct,
      })),
    });
  }

  return results;
}

export async function getQuizStats(chatId: number): Promise<{ totalQuizzes: number; avgPercentage: number; recentTrend: number | null }> {
  const res = await pool.query(
    "SELECT COUNT(*) as count, COALESCE(AVG(percentage), 0) as avg_pct FROM quiz_results WHERE chat_id = $1",
    [chatId],
  );
  const totalQuizzes = Number(res.rows[0].count);
  const avgPercentage = Math.round(Number(res.rows[0].avg_pct));

  // Recent trend: compare last 5 quizzes avg vs previous 5
  let recentTrend: number | null = null;
  if (totalQuizzes >= 4) {
    const recent = await pool.query(
      "SELECT percentage FROM quiz_results WHERE chat_id = $1 ORDER BY completed_at DESC LIMIT 5",
      [chatId],
    );
    const older = await pool.query(
      "SELECT percentage FROM quiz_results WHERE chat_id = $1 ORDER BY completed_at DESC LIMIT 5 OFFSET 5",
      [chatId],
    );
    if (older.rows.length > 0) {
      const recentAvg = recent.rows.reduce((s: number, r: any) => s + Number(r.percentage), 0) / recent.rows.length;
      const olderAvg = older.rows.reduce((s: number, r: any) => s + Number(r.percentage), 0) / older.rows.length;
      recentTrend = Math.round(recentAvg - olderAvg);
    }
  }

  return { totalQuizzes, avgPercentage, recentTrend };
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
