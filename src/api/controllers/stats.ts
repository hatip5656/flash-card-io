import type { Request, Response } from "express";
import { getStats, getQuizStats, getStreak, getTodayActivity } from "../../db/progress.js";
import { getWordsForLevel } from "../../flashcard/word-bank.js";
import { streakEmoji } from "../../utils.js";

export async function getUserStats(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const [stats, quiz, streak, today] = await Promise.all([
    getStats(chatId),
    getQuizStats(chatId),
    getStreak(chatId),
    getTodayActivity(chatId),
  ]);

  const totalForLevel = getWordsForLevel(stats.level).length;
  const progress = totalForLevel > 0 ? Math.round((stats.sent / totalForLevel) * 100) : 0;

  res.json({
    level: stats.level,
    schedule: stats.schedule,
    wordsLearned: stats.sent,
    totalWordsForLevel: totalForLevel,
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
  });
}
