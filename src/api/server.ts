import express from "express";
import { createApiRouter } from "./router.js";
import { errorHandler } from "./middleware/errors.js";
import { globalLimiter } from "./middleware/rate-limit.js";

export function createApiApp(cronTimezone: string, unsplashAccessKey?: string, pexelsApiKey?: string): express.Express {
  const app = express();

  app.set("cronTimezone", cronTimezone);
  if (unsplashAccessKey) app.set("unsplashAccessKey", unsplashAccessKey);
  if (pexelsApiKey) app.set("pexelsApiKey", pexelsApiKey);

  // Parse JSON bodies with size limit
  app.use(express.json({ limit: "16kb" }));

  // CORS for mobile/web clients
  app.use((_req, res, next) => {
    res.set("Access-Control-Allow-Origin", "*");
    res.set("Access-Control-Allow-Methods", "GET, POST, PATCH, DELETE, OPTIONS");
    res.set("Access-Control-Allow-Headers", "Content-Type, X-User-Id");
    if (_req.method === "OPTIONS") {
      res.sendStatus(204);
      return;
    }
    next();
  });

  // Global rate limit
  app.use("/api", globalLimiter);

  // Mount API routes
  app.use("/api", createApiRouter());

  // Error handler
  app.use(errorHandler);

  return app;
}
