/**
 * Disk-based cache for expensive operations (TTS, Unsplash, Ekilex).
 * Stores files on disk to avoid memory pressure. TTL-based expiration.
 */

import { mkdir, readFile, writeFile, stat, readdir, unlink } from "fs/promises";
import { join } from "path";
import { createHash } from "crypto";

export const CACHE_DIR = process.env.CACHE_DIR || "./cache";

function hashKey(input: string): string {
  return createHash("sha256").update(input).digest("hex").slice(0, 16);
}

export async function ensureDir(dir: string): Promise<void> {
  await mkdir(dir, { recursive: true });
}

function namespacePath(namespace: string): string {
  return join(CACHE_DIR, namespace);
}

function filePath(namespace: string, key: string, ext: string): string {
  return join(CACHE_DIR, namespace, `${hashKey(key)}.${ext}`);
}

/**
 * Get a cached buffer (e.g. TTS audio).
 */
export async function getCachedBuffer(namespace: string, key: string, ext: string, ttlMs: number): Promise<Buffer | null> {
  const path = filePath(namespace, key, ext);
  try {
    const info = await stat(path);
    if (Date.now() - info.mtimeMs > ttlMs) return null; // expired
    return await readFile(path);
  } catch {
    return null;
  }
}

/**
 * Store a buffer on disk.
 */
export async function setCachedBuffer(namespace: string, key: string, ext: string, data: Buffer): Promise<void> {
  try {
    await ensureDir(namespacePath(namespace));
    await writeFile(filePath(namespace, key, ext), data);
  } catch (err) {
    console.error(`[cache] Write error (${namespace}):`, err instanceof Error ? err.message : err);
  }
}

/**
 * Get a cached JSON object.
 */
export async function getCachedJson<T>(namespace: string, key: string, ttlMs: number): Promise<T | null> {
  const path = filePath(namespace, key, "json");
  try {
    const info = await stat(path);
    if (Date.now() - info.mtimeMs > ttlMs) return null;
    const raw = await readFile(path, "utf-8");
    return JSON.parse(raw) as T;
  } catch {
    return null;
  }
}

/**
 * Store a JSON object on disk.
 */
export async function setCachedJson(namespace: string, key: string, data: unknown): Promise<void> {
  try {
    await ensureDir(namespacePath(namespace));
    await writeFile(filePath(namespace, key, "json"), JSON.stringify(data));
  } catch (err) {
    console.error(`[cache] Write error (${namespace}):`, err instanceof Error ? err.message : err);
  }
}

/**
 * Evict expired entries from a namespace. Call periodically during low-load windows.
 */
export async function evictExpired(namespace: string, ttlMs: number): Promise<number> {
  let evicted = 0;
  try {
    const dir = namespacePath(namespace);
    const files = await readdir(dir);
    const now = Date.now();
    for (const file of files) {
      const path = join(dir, file);
      try {
        const info = await stat(path);
        if (now - info.mtimeMs > ttlMs) {
          await unlink(path);
          evicted++;
        }
      } catch { /* skip */ }
    }
  } catch { /* dir doesn't exist yet */ }
  return evicted;
}

/**
 * Get cache stats for monitoring.
 */
export async function getCacheStats(): Promise<Record<string, { files: number; sizeBytes: number }>> {
  const stats: Record<string, { files: number; sizeBytes: number }> = {};
  try {
    const namespaces = await readdir(CACHE_DIR);
    for (const ns of namespaces) {
      const dir = join(CACHE_DIR, ns);
      try {
        const files = await readdir(dir);
        let totalSize = 0;
        for (const f of files) {
          try {
            const info = await stat(join(dir, f));
            totalSize += info.size;
          } catch { /* skip */ }
        }
        stats[ns] = { files: files.length, sizeBytes: totalSize };
      } catch { /* skip */ }
    }
  } catch { /* cache dir doesn't exist */ }
  return stats;
}

// TTL constants (in milliseconds)
export const TTL = {
  TTS: 30 * 24 * 60 * 60 * 1000,       // 30 days
  UNSPLASH: 7 * 24 * 60 * 60 * 1000,    // 7 days
  EKILEX: 90 * 24 * 60 * 60 * 1000,     // 90 days
  TATOEBA: 14 * 24 * 60 * 60 * 1000,    // 14 days
};
