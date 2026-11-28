package net.augustus.irc;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.augustus.config.JsonConfigFile;

/**
 * Persistent configuration and client usage-time storage for the Augustus IRC system.
 */
public class IrcConfigFile extends JsonConfigFile {
   private boolean ircEnabled = true;
   private boolean autoUseOriginalNick = true;
   private String linkedOriginalNick = "";
   private String linkedUuid = "";
   private String linkedAccountType = "";
   private long totalPlayTimeMillis = 0L;
   private boolean exclusiveChatMode = false;
   private boolean showIrcInGlobalChat = true;
   private boolean showLogoOnNametags = true;
   private boolean showOriginalNickOnNametags = true;
   private boolean showLevelOnNametags = true;
   private boolean showOwnNametagLogo = true;
   private boolean showTabBadges = true;
   private boolean shareServerStatus = true;
   private String transportMode = "Auto";
   private String ircHost = "";
   private int ircPort = 6667;
   private boolean ircUseTls = false;
   private String ircChannel = "#augustus-redesing";
   private String ircPassword = "";
   private int localHubPort = 26877;

   @Override
   public Class getJsonType() {
      return JsonObject.class;
   }

   @Override
   public String getSubDirectory() {
      return "settings";
   }

   @Override
   public String getName() {
      return "Irc";
   }

   @Override
   public boolean markLoadedEvenIfEmpty() {
      return true;
   }

   @Override
   public boolean alwaysReserializeOnSave() {
      return true;
   }

   @Override
   public JsonElement serialize() {
      JsonObject json = new JsonObject();
      json.addProperty("IrcEnabled", this.ircEnabled);
      json.addProperty("AutoUseOriginalNick", this.autoUseOriginalNick);
      json.addProperty("LinkedOriginalNick", this.linkedOriginalNick);
      json.addProperty("LinkedUuid", this.linkedUuid);
      json.addProperty("LinkedAccountType", this.linkedAccountType);
      json.addProperty("TotalPlayTimeMillis", this.totalPlayTimeMillis);
      json.addProperty("ExclusiveChatMode", this.exclusiveChatMode);
      json.addProperty("ShowIrcInGlobalChat", this.showIrcInGlobalChat);
      json.addProperty("ShowLogoOnNametags", this.showLogoOnNametags);
      json.addProperty("ShowOriginalNickOnNametags", this.showOriginalNickOnNametags);
      json.addProperty("ShowLevelOnNametags", this.showLevelOnNametags);
      json.addProperty("ShowOwnNametagLogo", this.showOwnNametagLogo);
      json.addProperty("ShowTabBadges", this.showTabBadges);
      json.addProperty("ShareServerStatus", this.shareServerStatus);
      json.addProperty("TransportMode", this.transportMode);
      json.addProperty("IrcHost", this.ircHost);
      json.addProperty("IrcPort", this.ircPort);
      json.addProperty("IrcUseTls", this.ircUseTls);
      json.addProperty("IrcChannel", this.ircChannel);
      json.addProperty("IrcPassword", this.ircPassword);
      json.addProperty("LocalHubPort", this.localHubPort);
      return json;
   }

   @Override
   public void deserialize(JsonElement elem) {
      if (elem == null || !elem.isJsonObject()) {
         return;
      }
      JsonObject json = elem.getAsJsonObject();
      this.ircEnabled = getBool(json, "IrcEnabled", this.ircEnabled);
      this.autoUseOriginalNick = getBool(json, "AutoUseOriginalNick", this.autoUseOriginalNick);
      this.linkedOriginalNick = getString(json, "LinkedOriginalNick", this.linkedOriginalNick);
      this.linkedUuid = getString(json, "LinkedUuid", this.linkedUuid);
      this.linkedAccountType = getString(json, "LinkedAccountType", this.linkedAccountType);
      this.totalPlayTimeMillis = Math.max(0L, getLong(json, "TotalPlayTimeMillis", this.totalPlayTimeMillis));
      this.exclusiveChatMode = getBool(json, "ExclusiveChatMode", this.exclusiveChatMode);
      this.showIrcInGlobalChat = getBool(json, "ShowIrcInGlobalChat", this.showIrcInGlobalChat);
      this.showLogoOnNametags = getBool(json, "ShowLogoOnNametags", this.showLogoOnNametags);
      this.showOriginalNickOnNametags = getBool(json, "ShowOriginalNickOnNametags", this.showOriginalNickOnNametags);
      this.showLevelOnNametags = getBool(json, "ShowLevelOnNametags", this.showLevelOnNametags);
      this.showOwnNametagLogo = getBool(json, "ShowOwnNametagLogo", this.showOwnNametagLogo);
      this.showTabBadges = getBool(json, "ShowTabBadges", this.showTabBadges);
      this.shareServerStatus = getBool(json, "ShareServerStatus", this.shareServerStatus);
      this.transportMode = getString(json, "TransportMode", this.transportMode);
      this.ircHost = getString(json, "IrcHost", this.ircHost);
      this.ircPort = getInt(json, "IrcPort", this.ircPort);
      this.ircUseTls = getBool(json, "IrcUseTls", this.ircUseTls);
      this.ircChannel = getString(json, "IrcChannel", this.ircChannel);
      this.ircPassword = getString(json, "IrcPassword", this.ircPassword);
      this.localHubPort = getInt(json, "LocalHubPort", this.localHubPort);
   }

   private static boolean getBool(JsonObject json, String key, boolean fallback) {
      return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsBoolean() : fallback;
   }

   private static String getString(JsonObject json, String key, String fallback) {
      return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsString() : fallback;
   }

   private static long getLong(JsonObject json, String key, long fallback) {
      try {
         return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsLong() : fallback;
      } catch (Exception ignored) {
         return fallback;
      }
   }

   private static int getInt(JsonObject json, String key, int fallback) {
      try {
         return json.has(key) && json.get(key).isJsonPrimitive() ? json.get(key).getAsInt() : fallback;
      } catch (Exception ignored) {
         return fallback;
      }
   }

   public boolean isIrcEnabled() {
      return this.ircEnabled;
   }

   public void setIrcEnabled(boolean ircEnabled) {
      this.ircEnabled = ircEnabled;
   }

   public boolean isAutoUseOriginalNick() {
      return this.autoUseOriginalNick;
   }

   public void setAutoUseOriginalNick(boolean autoUseOriginalNick) {
      this.autoUseOriginalNick = autoUseOriginalNick;
   }

   public String getLinkedOriginalNick() {
      return this.linkedOriginalNick;
   }

   public void setLinkedOriginalNick(String linkedOriginalNick) {
      this.linkedOriginalNick = linkedOriginalNick == null ? "" : linkedOriginalNick.trim();
   }

   public String getLinkedUuid() {
      return this.linkedUuid;
   }

   public void setLinkedUuid(String linkedUuid) {
      this.linkedUuid = linkedUuid == null ? "" : linkedUuid.trim();
   }

   public String getLinkedAccountType() {
      return this.linkedAccountType;
   }

   public void setLinkedAccountType(String linkedAccountType) {
      this.linkedAccountType = linkedAccountType == null ? "" : linkedAccountType.trim();
   }

   public long getTotalPlayTimeMillis() {
      return this.totalPlayTimeMillis;
   }

   public void setTotalPlayTimeMillis(long totalPlayTimeMillis) {
      this.totalPlayTimeMillis = Math.max(0L, totalPlayTimeMillis);
   }

   public synchronized void addPlayTimeMillis(long delta) {
      if (delta > 0L) {
         this.totalPlayTimeMillis += delta;
      }
   }

   public boolean isExclusiveChatMode() {
      return this.exclusiveChatMode;
   }

   public void setExclusiveChatMode(boolean exclusiveChatMode) {
      this.exclusiveChatMode = exclusiveChatMode;
   }

   public boolean isShowIrcInGlobalChat() {
      return this.showIrcInGlobalChat;
   }

   public void setShowIrcInGlobalChat(boolean showIrcInGlobalChat) {
      this.showIrcInGlobalChat = showIrcInGlobalChat;
   }

   public boolean isShowLogoOnNametags() {
      return this.showLogoOnNametags;
   }

   public void setShowLogoOnNametags(boolean showLogoOnNametags) {
      this.showLogoOnNametags = showLogoOnNametags;
   }

   public boolean isShowOriginalNickOnNametags() {
      return this.showOriginalNickOnNametags;
   }

   public void setShowOriginalNickOnNametags(boolean showOriginalNickOnNametags) {
      this.showOriginalNickOnNametags = showOriginalNickOnNametags;
   }

   public boolean isShowLevelOnNametags() {
      return this.showLevelOnNametags;
   }

   public void setShowLevelOnNametags(boolean showLevelOnNametags) {
      this.showLevelOnNametags = showLevelOnNametags;
   }

   public boolean isShowOwnNametagLogo() {
      return this.showOwnNametagLogo;
   }

   public void setShowOwnNametagLogo(boolean showOwnNametagLogo) {
      this.showOwnNametagLogo = showOwnNametagLogo;
   }

   public boolean isShowTabBadges() {
      return this.showTabBadges;
   }

   public void setShowTabBadges(boolean showTabBadges) {
      this.showTabBadges = showTabBadges;
   }

   public boolean isShareServerStatus() {
      return this.shareServerStatus;
   }

   public void setShareServerStatus(boolean shareServerStatus) {
      this.shareServerStatus = shareServerStatus;
   }

   public String getTransportMode() {
      return this.transportMode;
   }

   public void setTransportMode(String transportMode) {
      this.transportMode = transportMode == null ? "Auto" : transportMode;
   }

   public String getIrcHost() {
      return this.ircHost;
   }

   public void setIrcHost(String ircHost) {
      this.ircHost = ircHost == null ? "" : ircHost.trim();
   }

   public int getIrcPort() {
      return this.ircPort;
   }

   public void setIrcPort(int ircPort) {
      this.ircPort = ircPort > 0 && ircPort <= 65535 ? ircPort : 6667;
   }

   public boolean isIrcUseTls() {
      return this.ircUseTls;
   }

   public void setIrcUseTls(boolean ircUseTls) {
      this.ircUseTls = ircUseTls;
   }

   public String getIrcChannel() {
      return this.ircChannel;
   }

   public void setIrcChannel(String ircChannel) {
      if (ircChannel == null || ircChannel.trim().isEmpty()) {
         this.ircChannel = "#augustus-redesing";
      } else {
         String clean = ircChannel.trim();
         this.ircChannel = clean.startsWith("#") ? clean : ("#" + clean);
      }
   }

   public String getIrcPassword() {
      return this.ircPassword;
   }

   public void setIrcPassword(String ircPassword) {
      this.ircPassword = ircPassword == null ? "" : ircPassword;
   }

   public int getLocalHubPort() {
      return this.localHubPort;
   }

   public void setLocalHubPort(int localHubPort) {
      this.localHubPort = localHubPort > 1024 && localHubPort <= 65535 ? localHubPort : 26877;
   }
}
