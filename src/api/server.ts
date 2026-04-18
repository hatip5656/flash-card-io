import express from "express";
import { createApiRouter } from "./router.js";
import { errorHandler } from "./middleware/errors.js";

export function createApiApp(cronTimezone: string): express.Express {
  const app = express();

  app.set("cronTimezone", cronTimezone);

  // Parse JSON bodies
  app.use(express.json());

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

  // Mount API routes
  app.use("/api", createApiRouter());

  // Error handler
  app.use(errorHandler);

  return app;
}
