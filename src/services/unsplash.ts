import { getCachedJson, setCachedJson, TTL } from "./cache.js";
import { errMsg } from "../utils.js";

const UNSPLASH_API = "https://api.unsplash.com";

export interface UnsplashPhoto {
  id: string;
  url: string;
  thumbUrl: string;
  description: string | null;
  photographer: string;
  photographerUrl: string;
  downloadUrl: string;
}

export async function searchPhoto(
  query: string,
  accessKey: string,
): Promise<UnsplashPhoto | null> {
  const cacheKey = query.toLowerCase().trim();

  // Check disk cache first (saves rate-limited API calls)
  const cached = await getCachedJson<UnsplashPhoto>("unsplash", cacheKey, TTL.UNSPLASH);
  if (cached) return cached;

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

    // Cache to disk (fire-and-forget)
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
