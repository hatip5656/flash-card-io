import { createServer } from "http";
import type { Express } from "express";

let ready = false;

export function setReady(value: boolean): void {
  ready = value;
}

export function startHealthServer(port = 8080, apiApp?: Express): void {
  if (apiApp) {
    // Mount health endpoints on the Express app
    apiApp.get("/healthz", (_req, res) => { res.send("ok"); });
    apiApp.get("/readyz", (_req, res) => {
      if (ready) res.send("ok");
      else res.status(503).send("not ready");
    });

    apiApp.listen(port, () => {
      console.error(`[health] Server listening on :${port} (health + API)`);
    });
  } else {
    // Standalone health server (no API)
    const server = createServer((req, res) => {
      if (req.url === "/healthz") {
        res.writeHead(200);
        res.end("ok");
      } else if (req.url === "/readyz") {
        res.writeHead(ready ? 200 : 503);
        res.end(ready ? "ok" : "not ready");
      } else {
        res.writeHead(404);
        res.end();
      }
    });
    server.listen(port, () => {
      console.error(`[health] Health server listening on :${port}`);
    });
  }
}
