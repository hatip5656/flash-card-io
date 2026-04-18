import type { Request, Response, NextFunction } from "express";

/** Wraps async route handlers so rejected promises are forwarded to Express error handler. */
export function asyncHandler(fn: (req: Request, res: Response, next: NextFunction) => Promise<void>) {
  return (req: Request, res: Response, next: NextFunction): void => {
    fn(req, res, next).catch(next);
  };
}
