import type { Request, Response } from "express";
import { getSubscriberLevel, getReadStoryIds, markStoryRead } from "../../db/progress.js";
import { getStoriesForLevel } from "../../flashcard/story-bank.js";

export async function getStories(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const level = await getSubscriberLevel(chatId);
  const readIds = await getReadStoryIds(chatId);
  const stories = getStoriesForLevel(level);

  const result = stories.map((s) => ({
    id: s.id,
    topic: s.topic,
    icon: s.icon,
    cefrLevel: s.cefrLevel,
    slideCount: s.slides.length,
    isRead: readIds.has(s.id),
    slides: s.slides,
  }));

  // Unread first, then read
  result.sort((a, b) => {
    if (a.isRead !== b.isRead) return a.isRead ? 1 : -1;
    return 0;
  });

  res.json(result);
}

export async function markStoryAsRead(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const storyId = req.params.storyId as string;
  await markStoryRead(chatId, storyId);
  res.json({ storyId, read: true });
}
