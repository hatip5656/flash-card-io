import { execFile } from "child_process";
import { promisify } from "util";
import { tmpdir } from "os";
import { join } from "path";
import { readFile, unlink } from "fs/promises";
import { randomBytes } from "crypto";

const execFileAsync = promisify(execFile);

export async function synthesizeSpeech(text: string): Promise<Buffer | null> {
  const tmpFile = join(tmpdir(), `tts-${randomBytes(6).toString("hex")}.wav`);
  try {
    await execFileAsync("espeak-ng", [
      "-v", "et",       // Estonian voice
      "-s", "140",      // Speed (words per minute) — slightly slower for learners
      "-w", tmpFile,    // Output to WAV file
      text,
    ], { timeout: 10_000 });

    const audio = await readFile(tmpFile);
    return audio;
  } catch (err) {
    console.error(`[tts] Error synthesizing "${text}":`, err instanceof Error ? err.message : err);
    return null;
  } finally {
    await unlink(tmpFile).catch(() => {});
  }
}
