import { getCachedJson, setCachedJson, TTL } from "./cache.js";
import { errMsg } from "../utils.js";

const UNSPLASH_API = "https://api.unsplash.com";
const MAX_REQUESTS_PER_HOUR = 40; // Unsplash free tier = 50/hr, keep 10 margin

export interface UnsplashPhoto {
  id: string;
  url: string;
  thumbUrl: string;
  description: string | null;
  photographer: string;
  photographerUrl: string;
  downloadUrl: string;
}

// Token bucket rate limiter
let tokens = MAX_REQUESTS_PER_HOUR;
let lastRefill = Date.now();
let rateLimitedUntil = 0;

function tryConsume(): boolean {
  const now = Date.now();

  // Back off if we got a 403/429 recently (5 min cooldown)
  if (now < rateLimitedUntil) return false;

  // Refill tokens based on elapsed time
  const elapsed = now - lastRefill;
  const refill = Math.floor(elapsed / (3_600_000 / MAX_REQUESTS_PER_HOUR));
  if (refill > 0) {
    tokens = Math.min(MAX_REQUESTS_PER_HOUR, tokens + refill);
    lastRefill = now;
  }

  if (tokens <= 0) return false;
  tokens--;
  return true;
}

export async function searchPhoto(
  query: string,
  accessKey: string,
): Promise<UnsplashPhoto | null> {
  const cacheKey = query.toLowerCase().trim();

  const cached = await getCachedJson<UnsplashPhoto>("unsplash", cacheKey, TTL.UNSPLASH);
  if (cached) return cached;

  // Skip API call if rate limited
  if (!tryConsume()) return null;

  try {
    const params = new URLSearchParams({
      query,
      per_page: "1",
      orientation: "landscape",
    });

    const res = await fetch(`${UNSPLASH_API}/search/photos?${params}`, {
      headers: { Authorization: `Client-ID ${accessKey}` },
      signal: AbortSignal.timeout(10_000),
    });

    if (res.status === 403 || res.status === 429) {
      rateLimitedUntil = Date.now() + 5 * 60_000; // back off 5 minutes
      tokens = 0;
      console.error(`[unsplash] Rate limited (${res.status}), backing off 5 min`);
      return null;
    }

    if (!res.ok) {
      console.error(`[unsplash] API error: ${res.status} ${res.statusText}`);
      return null;
    }

    const data = (await res.json()) as {
      results: Array<{
        id: string;
        urls: { regular: string; thumb: string };
        description: string | null;
        alt_description: string | null;
        user: { name: string; links: { html: string } };
        links: { download_location: string };
      }>;
    };

    if (data.results.length === 0) return null;

    const photo = data.results[0];
    const result: UnsplashPhoto = {
      id: photo.id,
      url: photo.urls.regular,
      thumbUrl: photo.urls.thumb,
      description: photo.description ?? photo.alt_description,
      photographer: photo.user.name,
      photographerUrl: photo.user.links.html,
      downloadUrl: photo.links.download_location,
    };

    setCachedJson("unsplash", cacheKey, result).catch(() => {});
    return result;
  } catch (err) {
    console.error(`[unsplash] Error searching for "${query}":`, errMsg(err));
    return null;
  }
}

export async function triggerDownload(
  downloadUrl: string,
  accessKey: string,
): Promise<void> {
  await fetch(downloadUrl, {
    headers: { Authorization: `Client-ID ${accessKey}` },
  }).catch(() => {});
}
