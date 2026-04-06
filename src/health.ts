import { createServer } from "http";

let ready = false;

export function setReady(value: boolean): void {
  ready = value;
}

export function startHealthServer(port = 8080): void {
  const server = createServer((req, res) => {
    if (req.url === "/healthz") {
      // Liveness: is the process alive and not stuck?
      res.writeHead(200);
      res.end("ok");
    } else if (req.url === "/readyz") {
      // Readiness: is the app ready to do work?
      if (ready) {
        res.writeHead(200);
        res.end("ok");
      } else {
        res.writeHead(503);
        res.end("not ready");
      }
    } else {
      res.writeHead(404);
      res.end();
    }
  });

  server.listen(port, () => {
    console.error(`[health] Health server listening on :${port}`);
  });
}
