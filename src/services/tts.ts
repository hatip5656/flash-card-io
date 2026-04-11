const TTS_API_URL = process.env.TTS_API_URL || "http://tts-api:8000";
const TTS_SPEAKER = process.env.TTS_SPEAKER || "mari";

export async function synthesizeSpeech(word: string, sentence?: string): Promise<Buffer | null> {
  try {
    // Synthesize word + sentence as one text with a pause marker
    const text = sentence && sentence !== word
      ? `${word}. ... ${sentence}`
      : word;

    const res = await fetch(`${TTS_API_URL}/v2`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text,
        speaker: TTS_SPEAKER,
        speed: 0.85,
      }),
      signal: AbortSignal.timeout(15_000),
    });

    if (!res.ok) {
      console.error(`[tts] API error ${res.status}: ${await res.text()}`);
      return null;
    }

    const arrayBuffer = await res.arrayBuffer();
    return Buffer.from(arrayBuffer);
  } catch (err) {
    console.error(`[tts] Error synthesizing "${word}":`, err instanceof Error ? err.message : err);
    return null;
  }
}
