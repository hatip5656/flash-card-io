import { getCachedJson, setCachedJson, TTL } from "./cache.js";
import { errMsg } from "../utils.js";

const PEXELS_API = "https://api.pexels.com/v1";

export interface PexelsPhoto {
  id: number;
  url: string;
  photographer: string;
  photographerUrl: string;
}

export async function searchPexelsPhoto(
  query: string,
  apiKey: string,
): Promise<PexelsPhoto | null> {
  const cacheKey = `pexels:${query.toLowerCase().trim()}`;

  const cached = await getCachedJson<PexelsPhoto>("pexels", cacheKey, TTL.UNSPLASH);
  if (cached) return cached;

  try {
    const params = new URLSearchParams({
      query,
      per_page: "1",
      orientation: "landscape",
    });

    const res = await fetch(`${PEXELS_API}/search?${params}`, {
      headers: { Authorization: apiKey },
      signal: AbortSignal.timeout(10_000),
    });

    if (!res.ok) {
      console.error(`[pexels] API error: ${res.status} ${res.statusText}`);
      return null;
    }

    const data = (await res.json()) as {
      photos: Array<{
        id: number;
        src: { landscape: string; medium: string };
        photographer: string;
        photographer_url: string;
      }>;
    };

    if (data.photos.length === 0) return null;

    const photo = data.photos[0];
    const result: PexelsPhoto = {
      id: photo.id,
      url: photo.src.landscape,
      photographer: photo.photographer,
      photographerUrl: photo.photographer_url,
    };

    setCachedJson("pexels", cacheKey, result).catch(() => {});
    return result;
  } catch (err) {
    console.error(`[pexels] Error searching for "${query}":`, errMsg(err));
    return null;
  }
}
