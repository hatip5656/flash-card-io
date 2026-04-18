import type { Request, Response, NextFunction } from "express";
import { errMsg } from "../../utils.js";

export function errorHandler(err: unknown, _req: Request, res: Response, _next: NextFunction): void {
  console.error("[api] Unhandled error:", errMsg(err));
  res.status(500).json({ error: "Internal server error" });
}
