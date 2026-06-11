import type { Request, Response } from "express";
import { addComment, getComments } from "../../db/progress.js";

export async function getWordComments(req: Request, res: Response): Promise<void> {
  const wordId = req.params.wordId as string;
  const limit = Math.min(Number(req.query.limit) || 20, 50);
  const comments = await getComments(wordId, limit);
  res.json(comments);
}

export async function postComment(req: Request, res: Response): Promise<void> {
  const chatId = req.userId!;
  const wordId = req.params.wordId as string;
  const { comment } = req.body;

  if (!comment || typeof comment !== "string" || comment.trim().length === 0) {
    res.status(400).json({ error: "comment is required" });
    return;
  }
  if (comment.length > 500) {
    res.status(400).json({ error: "comment must be 500 characters or less" });
    return;
  }

  const result = await addComment(chatId, wordId, comment.trim());
  res.status(201).json(result);
}
