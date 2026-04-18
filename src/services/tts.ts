import { execFile } from "child_process";
import { promisify } from "util";
import { tmpdir } from "os";
import { join } from "path";
import { readFile, writeFile, unlink, mkdir } from "fs/promises";
import { randomBytes } from "crypto";
import { getCachedBuffer, setCachedBuffer, TTL } from "./cache.js";
import { errMsg } from "../utils.js";

const execFileAsync = promisify(execFile);

const TTS_API_URL = process.env.TTS_API_URL || "http://tts-api:8000";
const TTS_SPEAKER = process.env.TTS_SPEAKER || "mari";
const DEEPFILTER_ENABLED = process.env.FEATURE_DEEPFILTER === "true";
// Bump this when the audio processing pipeline changes to invalidate cached audio
const CACHE_VERSION = DEEPFILTER_ENABLED ? "v4-df" : "v4";

/** Run DeepFilterNet enhancement on a WAV file. */
async function enhanceWithDeepFilter(wavFile: string, outDir: string): Promise<string> {
  // Use python3 -m df to avoid PATH issues with the deep-filter entry point
  // First run downloads model (~50MB), so allow 60s; subsequent runs take <1s
  await execFileAsync("python3", ["-m", "df", wavFile, "-o", outDir], { timeout: 60_000 });
  // deep-filter writes to outDir with same filename
  const enhanced = join(outDir, wavFile.split("/").pop()!);
  return enhanced;
}

async function convertWavToOgg(wavBuffer: Buffer): Promise<Buffer> {
  const id = randomBytes(6).toString("hex");
  const wavFile = join(tmpdir(), `tts-${id}.wav`);
  const enhancedFile = join(tmpdir(), `tts-${id}-enhanced.wav`);
  const oggFile = join(tmpdir(), `tts-${id}.ogg`);

  try {
    await writeFile(wavFile, wavBuffer);

    // Speech enhancement via DeepFilterNet (if enabled)
    let inputFile = wavFile;
    if (DEEPFILTER_ENABLED) {
      try {
        const dfOutDir = join(tmpdir(), `df-${id}`);
        await mkdir(dfOutDir, { recursive: true });
        inputFile = await enhanceWithDeepFilter(wavFile, dfOutDir);
      } catch (err: any) {
        const stderr = err?.stderr || "";
        const stdout = err?.stdout || "";
        console.error("[tts] DeepFilterNet failed:", errMsg(err), stderr ? `\nstderr: ${stderr}` : "", stdout ? `\nstdout: ${stdout}` : "");
        inputFile = wavFile;
      }
    }

    await execFileAsync("ffmpeg", [
      "-i", inputFile,
      "-af", [
        "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-40dB",
        "asetpts=PTS-STARTPTS",
        "aresample=out_sample_rate=48000",
        "highpass=f=80",
        "lowpass=f=12000",
        "equalizer=f=3000:t=q:w=2.0:g=1.5",
        "acompressor=threshold=0.1:ratio=2:attack=10:release=150:makeup=1",
        "afade=t=in:d=0.02",
        "loudnorm=I=-16:TP=-1.5:LRA=11",
      ].join(","),
      "-c:a", "libopus",
      "-b:a", "32k",
      "-application", "voip",
      "-vbr", "on",
      "-compression_level", "10",
      "-frame_duration", "20",
      "-ar", "48000",
      "-y",
      oggFile,
    ], { timeout: 10_000 });
    return await readFile(oggFile);
  } finally {
    await unlink(wavFile).catch(() => {});
    await unlink(oggFile).catch(() => {});
    // Clean up DeepFilterNet output dir
    const dfOutDir = join(tmpdir(), `df-${id}`);
    const dfFile = join(dfOutDir, `tts-${id}.wav`);
    await unlink(dfFile).catch(() => {});
    await unlink(dfOutDir).catch(() => {}); // rmdir empty dir
  }
}

export async function synthesizeSpeech(word: string, sentence?: string, voiceName?: string): Promise<Buffer | null> {
  const voice = voiceName || TTS_SPEAKER;
  const text = sentence && sentence !== word ? `${word}. ... ${sentence}` : word;
  const cacheKey = `${CACHE_VERSION}\0${text}\0${voice}`;

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
