package net.augustus.irc;

/**
 * Immutable snapshot of one message in the exclusive Augustus IRC feed.
 */
public final class IrcChatMessage {
   private final long timestampMillis;
   private final String senderOriginalNick;
   private final int senderLevel;
   private final boolean senderPremium;
   private final String content;
   private final boolean systemMessage;

   public IrcChatMessage(
      long timestampMillis,
      String senderOriginalNick,
      int senderLevel,
      boolean senderPremium,
      String content,
      boolean systemMessage
   ) {
      this.timestampMillis = timestampMillis;
      this.senderOriginalNick = senderOriginalNick == null ? "Augustus" : senderOriginalNick;
      this.senderLevel = Math.max(1, senderLevel);
      this.senderPremium = senderPremium;
      this.content = content == null ? "" : content;
      this.systemMessage = systemMessage;
   }

   public long getTimestampMillis() {
      return this.timestampMillis;
   }

   public String getSenderOriginalNick() {
      return this.senderOriginalNick;
   }

   public int getSenderLevel() {
      return this.senderLevel;
   }

   public boolean isSenderPremium() {
      return this.senderPremium;
   }

   public String getContent() {
      return this.content;
   }

   public boolean isSystemMessage() {
      return this.systemMessage;
   }
}
