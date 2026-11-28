package net.augustus.irc;

import java.util.Locale;

/**
 * Live presence record for a connected Augustus IRC user.
 */
public final class IrcUserRecord {
   private final String nodeId;
   private volatile String originalNick;
   private volatile String currentNick;
   private volatile String uuid;
   private volatile boolean premiumLinked;
   private volatile int level;
   private volatile long playTimeMillis;
   private volatile String serverContext;
   private volatile long lastSeenMillis;
   private final boolean localUser;

   public IrcUserRecord(
      String nodeId,
      String originalNick,
      String currentNick,
      String uuid,
      boolean premiumLinked,
      int level,
      long playTimeMillis,
      String serverContext,
      boolean localUser
   ) {
      this.nodeId = nodeId;
      this.originalNick = safeNick(originalNick);
      this.currentNick = safeNick(currentNick);
      this.uuid = normalizeUuid(uuid);
      this.premiumLinked = premiumLinked;
      this.level = Math.max(1, level);
      this.playTimeMillis = Math.max(0L, playTimeMillis);
      this.serverContext = serverContext == null ? "Menu" : serverContext;
      this.lastSeenMillis = System.currentTimeMillis();
      this.localUser = localUser;
   }

   public String getNodeId() {
      return this.nodeId;
   }

   public String getOriginalNick() {
      return this.originalNick;
   }

   public String getCurrentNick() {
      return this.currentNick;
   }

   public String getDisplayOriginalNick() {
      return !this.originalNick.isEmpty() ? this.originalNick : this.currentNick;
   }

   public String getUuid() {
      return this.uuid;
   }

   public boolean isPremiumLinked() {
      return this.premiumLinked;
   }

   public int getLevel() {
      return this.level;
   }

   public long getPlayTimeMillis() {
      return this.playTimeMillis;
   }

   public String getServerContext() {
      return this.serverContext;
   }

   public long getLastSeenMillis() {
      return this.lastSeenMillis;
   }

   public boolean isLocalUser() {
      return this.localUser;
   }

   public boolean usesDifferentServerNick() {
      return !this.originalNick.isEmpty()
         && !this.currentNick.isEmpty()
         && !this.originalNick.equalsIgnoreCase(this.currentNick);
   }

   public void updateFrom(
      String originalNick,
      String currentNick,
      String uuid,
      boolean premiumLinked,
      int level,
      long playTimeMillis,
      String serverContext
   ) {
      String cleanOrig = safeNick(originalNick);
      String cleanCur = safeNick(currentNick);
      if (!cleanOrig.isEmpty()) {
         this.originalNick = cleanOrig;
      }
      if (!cleanCur.isEmpty()) {
         this.currentNick = cleanCur;
      }
      String normUuid = normalizeUuid(uuid);
      if (!normUuid.isEmpty()) {
         this.uuid = normUuid;
      }
      this.premiumLinked = premiumLinked;
      this.level = Math.max(1, level);
      this.playTimeMillis = Math.max(0L, playTimeMillis);
      if (serverContext != null && !serverContext.isEmpty()) {
         this.serverContext = serverContext;
      }
      this.lastSeenMillis = System.currentTimeMillis();
   }

   public boolean matchesPlayer(String gameNick, String playerUuid) {
      String normUuid = normalizeUuid(playerUuid);
      if (!normUuid.isEmpty() && !this.uuid.isEmpty() && normUuid.equals(this.uuid)) {
         return true;
      }
      if (gameNick == null || gameNick.isEmpty()) {
         return false;
      }
      return gameNick.equalsIgnoreCase(this.currentNick) || gameNick.equalsIgnoreCase(this.originalNick);
   }

   public static String normalizeUuid(String raw) {
      if (raw == null) {
         return "";
      }
      String stripped = raw.replace("-", "").trim().toLowerCase(Locale.ROOT);
      return stripped.length() == 32 ? stripped : "";
   }

   private static String safeNick(String raw) {
      return raw == null ? "" : raw.trim();
   }
}
