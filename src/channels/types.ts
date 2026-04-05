import type { Flashcard } from "../flashcard/types.js";

export interface DeliveryChannel {
  name: string;
  sendFlashcard(chatId: number, flashcard: Flashcard): Promise<boolean>;
}
