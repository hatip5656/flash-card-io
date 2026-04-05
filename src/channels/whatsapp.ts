import type { DeliveryChannel } from "./types.js";

export function createWhatsAppChannel(): DeliveryChannel {
  return {
    name: "whatsapp",
    async sendFlashcard(_chatId, _flashcard) {
      console.error("[whatsapp] WhatsApp delivery not yet implemented");
      return false;
    },
  };
}
