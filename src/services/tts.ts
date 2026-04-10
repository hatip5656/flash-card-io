const TTS_API_URL = "https://texttospeech.googleapis.com/v1/text:synthesize";

// Estonian voices available on Google Cloud TTS
const ESTONIAN_VOICES = [
  "et-EE-Standard-A",
];

export async function synthesizeSpeech(
  text: string,
  apiKey: string,
): Promise<Buffer | null> {
  try {
    const res = await fetch(`${TTS_API_URL}?key=${apiKey}`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        input: { text },
        voice: {
          languageCode: "et-EE",
          name: ESTONIAN_VOICES[0],
        },
        audioConfig: {
          audioEncoding: "OGG_OPUS",
          speakingRate: 0.9,
        },
      }),
    });

    if (!res.ok) {
      console.error(`[tts] API error ${res.status}: ${await res.text()}`);
      return null;
    }

    const data = (await res.json()) as { audioContent: string };
    return Buffer.from(data.audioContent, "base64");
  } catch (err) {
    console.error(`[tts] Error synthesizing "${text}":`, err instanceof Error ? err.message : err);
    return null;
  }
}
