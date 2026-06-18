import type { Request, Response } from "express";
import { getStats, getQuizStats, getStreak, getTodayActivity, getPool } from "../../db/progress.js";
import { getWordsForLevel, getAllWords } from "../../flashcard/word-bank.js";
import { streakEmoji } from "../../utils.js";

export async function getUserStats(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const pool = getPool();
  const [stats, quiz, streak, today, wordCounts] = await Promise.all([
    getStats(chatId),
    getQuizStats(chatId),
    getStreak(chatId),
    getTodayActivity(chatId),
    pool.query(
      `SELECT
        COUNT(*) as seen,
        COUNT(*) FILTER (WHERE mastered = TRUE) as mastered,
        COUNT(*) FILTER (WHERE quiz_count > 0) as quizzed,
        COALESCE(SUM(quiz_count), 0) as total_quiz_answers,
        COALESCE(SUM(feed_count), 0) as total_feed_views,
        COALESCE(SUM(crush_count), 0) as total_crush_finds
      FROM sent_words WHERE chat_id = $1`,
      [chatId],
    ),
  ]);

  const savedCount = await pool.query("SELECT COUNT(*) as count FROM saved_words WHERE chat_id = $1", [chatId]);

  const totalForLevel = getWordsForLevel(stats.level).length;
  const totalWords = getAllWords().length;
  const progress = totalForLevel > 0 ? Math.round((stats.sent / totalForLevel) * 100) : 0;
  const counts = wordCounts.rows[0];

  res.json({
    level: stats.level,
    schedule: stats.schedule,
    wordsLearned: stats.sent,
    totalWordsForLevel: totalForLevel,
    totalWordsInCatalog: totalWords,
    progressPercent: progress,
    streak,
    streakEmoji: streakEmoji(streak),
    today: {
      wordsLearned: today.wordsLearned,
      quizzesTaken: today.quizzesTaken,
    },
    quiz: {
      totalQuizzes: quiz.totalQuizzes,
      avgPercentage: quiz.avgPercentage,
      recentTrend: quiz.recentTrend,
    },
    words: {
      seen: Number(counts.seen),
      mastered: Number(counts.mastered),
      quizzed: Number(counts.quizzed),
      saved: Number(savedCount.rows[0].count),
      totalQuizAnswers: Number(counts.total_quiz_answers),
      totalFeedViews: Number(counts.total_feed_views),
      totalCrushFinds: Number(counts.total_crush_finds),
    },
  });
}
