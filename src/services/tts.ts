import { execFile } from "child_process";
import { promisify } from "util";
import { tmpdir } from "os";
import { join } from "path";
import { readFile, writeFile, unlink } from "fs/promises";
import { randomBytes } from "crypto";

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
        "silenceremove=start_periods=1:start_silence=0.05:start_threshold=-40dB",  // trim leading silence/buzz until speech starts
        "asetpts=PTS-STARTPTS",    // reset timestamps after trim
        "highpass=f=100",           // cut low-frequency hum
        "lowpass=f=7500",           // cut high-frequency hiss
        "afftdn=nf=-25",           // aggressive FFT noise reduction
        "agate=threshold=0.01:attack=5:release=50",  // gate quiet buzz between words
        "afade=t=in:d=0.03",       // 30ms fade-in to smooth start
        "loudnorm=I=-16:TP=-1.5",  // normalize loudness
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
  try {
    const text = sentence && sentence !== word
      ? `${word}. ... ${sentence}`
      : word;

    const res = await fetch(`${TTS_API_URL}/v2`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        text,
        speaker: voiceName || TTS_SPEAKER,
        speed: 0.85,
      }),
      signal: AbortSignal.timeout(30_000),
    });

    if (!res.ok) {
      console.error(`[tts] API error ${res.status}: ${await res.text()}`);
      return null;
    }

    const wavBuffer = Buffer.from(await res.arrayBuffer());

    // Convert WAV to OGG Opus for clean Telegram playback
    try {
      return await convertWavToOgg(wavBuffer);
    } catch {
      // If ffmpeg not available, send WAV as fallback
      return wavBuffer;
    }
  } catch (err) {
    console.error(`[tts] Error synthesizing "${word}":`, err instanceof Error ? err.message : err);
    return null;
  }
}
