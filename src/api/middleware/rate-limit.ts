import rateLimit from "express-rate-limit";

/** Key by X-User-Id header, falling back to a constant (IP-based limiting is handled by globalLimiter). */
function userKey(req: any): string {
  return (req.headers["x-user-id"] as string) || "anonymous";
}

/** Global rate limit: 100 requests per minute per IP. */
export const globalLimiter = rateLimit({
  windowMs: 60_000,
  max: 100,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many requests, please try again later" },
});

/** Flashcard builds are expensive — 10 per minute per user. */
export const flashcardLimiter = rateLimit({
  windowMs: 60_000,
  max: 10,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many flashcard requests, please wait" },
  keyGenerator: userKey,
  validate: false,
});

/** Quiz endpoints — 30 per minute per user. */
export const quizLimiter = rateLimit({
  windowMs: 60_000,
  max: 30,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many quiz requests, please wait" },
  keyGenerator: userKey,
  validate: false,
});

/** Admin endpoints — 20 per minute per IP. */
export const adminLimiter = rateLimit({
  windowMs: 60_000,
  max: 20,
  standardHeaders: true,
  legacyHeaders: false,
  message: { error: "Too many admin requests, please wait" },
});
