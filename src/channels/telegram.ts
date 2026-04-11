import { Bot } from "grammy";
import type { DeliveryChannel } from "./types.js";
import type { Flashcard } from "../flashcard/types.js";

export function createTelegramChannel(token: string): { channel: DeliveryChannel; bot: Bot } {
  const bot = new Bot(token);

  const channel: DeliveryChannel = {
    name: "telegram",

    async sendFlashcard(chatId: number, flashcard: Flashcard): Promise<boolean> {
      try {
        const caption = flashcard.caption;

        if (flashcard.imageUrl && caption.length <= 1024) {
          await bot.api.sendPhoto(chatId, flashcard.imageUrl, {
            caption,
            parse_mode: "HTML",
          });
        } else if (flashcard.imageUrl) {
          // Caption too long for photo — send photo first, then caption as text
          await bot.api.sendPhoto(chatId, flashcard.imageUrl);
          await bot.api.sendMessage(chatId, caption, { parse_mode: "HTML" });
        } else {
          await bot.api.sendMessage(chatId, caption, {
            parse_mode: "HTML",
          });
        }

        return true;
      } catch (err) {
        console.error(`[telegram] Failed to send to ${chatId}:`, err instanceof Error ? err.message : err);
        return false;
      }
    },
  };

  return { channel, bot };
}
