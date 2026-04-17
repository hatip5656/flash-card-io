import { execFile } from "child_process";
import { promisify } from "util";
import { tmpdir } from "os";
import { join } from "path";
import { readFile, writeFile, unlink } from "fs/promises";
import { randomBytes } from "crypto";
import { getCachedBuffer, setCachedBuffer, TTL } from "./cache.js";
import { errMsg } from "../utils.js";

const execFileAsync = promisify(execFile);

const TTS_API_URL = process.env.TTS_API_URL || "http://tts-api:8000";
const TTS_SPEAKER = process.env.TTS_SPEAKER || "mari";

async function convertWavToOgg(wavBuffer: Buffer): Promise<Buffer> {
  const id = randomBytes(6).toString("hex");
  const wavFile = join(tmpdir(), `tts-${id}.wav`);
  const oggFile = join(tmpdir(), `tts-${id}.ogg`);

  try {
    await writeFile(wavFile, wavBuffer);
    await execFileAsync("ffmpeg", [
      "-i", wavFile,
      "-af", [
        "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-40dB",
        "asetpts=PTS-STARTPTS",
        "highpass=f=100",
        "lowpass=f=7500",
        "afftdn=nf=-25",
        "agate=threshold=0.01:attack=5:release=50",
        "afade=t=in:d=0.03",
        "loudnorm=I=-16:TP=-1.5",
      ].join(","),
      "-c:a", "libopus",
      "-b:a", "64k",
      "-y",
      oggFile,
    ], { timeout: 10_000 });
    return await readFile(oggFile);
  } finally {
    await unlink(wavFile).catch(() => {});
    await unlink(oggFile).catch(() => {});
  }
}

export async function synthesizeSpeech(word: string, sentence?: string, voiceName?: string): Promise<Buffer | null> {
  const voice = voiceName || TTS_SPEAKER;
  const text = sentence && sentence !== word ? `${word}. ... ${sentence}` : word;
  const cacheKey = `${text}\0${voice}`;

  // Check disk cache first
  const cached = await getCachedBuffer("tts", cacheKey, "ogg", TTL.TTS);
  if (cached) {
    console.error(`[tts] Cache hit for "${word}" (${voice})`);
    return cached;
  }

  const body = JSON.stringify({ text, speaker: voice, speed: 0.85 });

  for (let attempt = 0; attempt < 2; attempt++) {
    try {
      const res = await fetch(`${TTS_API_URL}/v2`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body,
        signal: AbortSignal.timeout(25_000),
      });

      if (!res.ok) {
        console.error(`[tts] API error ${res.status} (attempt ${attempt + 1}): ${await res.text()}`);
        continue;
      }

      const wavBuffer = Buffer.from(await res.arrayBuffer());

      let audio: Buffer;
      try {
        audio = await convertWavToOgg(wavBuffer);
      } catch {
        audio = wavBuffer;
      }

      // Cache to disk (fire-and-forget)
      setCachedBuffer("tts", cacheKey, "ogg", audio).catch(() => {});

      return audio;
    } catch (err) {
      console.error(`[tts] Error synthesizing "${word}" (attempt ${attempt + 1}):`, errMsg(err));
    }
  }

  return null;
}
