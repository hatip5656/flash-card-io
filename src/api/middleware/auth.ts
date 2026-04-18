import type { Request, Response, NextFunction } from "express";

/**
 * Simple auth middleware — extracts userId from header or query.
 * Mobile/web clients pass their user ID (same as Telegram chatId).
 * In production, replace with JWT or session-based auth.
 */
export function authenticate(req: Request, res: Response, next: NextFunction): void {
  const userId = req.headers["x-user-id"] as string | undefined
    ?? req.query.userId as string | undefined;

  if (!userId || isNaN(Number(userId))) {
    res.status(401).json({ error: "Missing or invalid X-User-Id header" });
    return;
  }

  req.userId = Number(userId);
  next();
}

// Extend Express Request type
declare global {
  namespace Express {
    interface Request {
      userId?: number;
    }
  }
}
