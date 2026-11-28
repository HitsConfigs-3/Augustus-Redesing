package net.augustus.irc;

import com.mojang.authlib.GameProfile;
import java.awt.Color;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import net.augustus.Augustus;
import net.augustus.ui.account.AccountAuthenticator;
import net.augustus.ui.account.AccountEntry;
import net.augustus.ui.account.AccountLoginType;
import net.augustus.ui.account.AccountManagerScreen;
import net.augustus.ui.account.EasyMcAuthenticator;
import net.augustus.ui.notifications.NotificationLevel;
import net.augustus.ui.notifications.NotificationManager;
import net.augustus.utils.ChatUtil;
import net.augustus.utils.MinotarAvatarCache;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiNewChat;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.network.PacketThreadUtil;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.Session;

/**
 * Central Augustus IRC service: manages Premium account linking, auto-syncing the original
 * Minecraft nickname across client & servers, usage-time levels, live user identification,
 * and exclusive Augustus IRC chat routing.
 */
public final class IrcManager implements MC {
   private static final IrcManager INSTANCE = new IrcManager();
   private static final int MAX_HISTORY = 120;
   private static final long REMOTE_TIMEOUT_MS = 65_000L;

   private final IrcConfigFile config = new IrcConfigFile();
   private final String localNodeId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
   private final Map<String, IrcUserRecord> remoteUsers = new ConcurrentHashMap<String, IrcUserRecord>();
   private final CopyOnWriteArrayList<IrcChatMessage> chatHistory = new CopyOnWriteArrayList<IrcChatMessage>();
   private final IrcNetworkClient networkClient = new IrcNetworkClient(this);

   private volatile boolean initialized = false;
   private volatile boolean linkingInProgress = false;
   private volatile String linkStatusMessage = "";
   private volatile Session cachedLinkedPremiumSession = null;
   private volatile IrcUserRecord localUser;

   private long lastTickClockMs = 0L;
   private long lastSaveClockMs = 0L;
   private int lastKnownLevel = 1;

   private IrcManager() {
   }

   public static IrcManager getInstance() {
      return INSTANCE;
   }

   public IrcConfigFile getConfig() {
      return this.config;
   }

   public IrcUserRecord getLocalUser() {
      return this.localUser;
   }

   public synchronized void init() {
      if (this.initialized) {
         return;
      }
      this.initialized = true;
      this.lastTickClockMs = System.currentTimeMillis();
      this.lastSaveClockMs = this.lastTickClockMs;

      // Seed playtime once from previously tracked account playtime if IRC time is still 0.
      if (this.config.getTotalPlayTimeMillis() == 0L && AccountManagerScreen.accounts != null) {
         long maxAccountPlayTime = 0L;
         for (Object obj : AccountManagerScreen.accounts) {
            if (obj instanceof AccountEntry) {
               AccountEntry entry = (AccountEntry)obj;
               if (entry.getPlayTimeMillis() > maxAccountPlayTime) {
                  maxAccountPlayTime = entry.getPlayTimeMillis();
               }
            }
         }
         if (maxAccountPlayTime > 0L) {
            this.config.setTotalPlayTimeMillis(maxAccountPlayTime);
         }
      }

      this.lastKnownLevel = ClientLevelSystem.levelForPlayTime(this.config.getTotalPlayTimeMillis());
      this.refreshLocalUserSnapshot();
      this.autoLinkAndRestoreOnStartup();

      if (this.config.isIrcEnabled()) {
         this.networkClient.start();
      }
   }

   public synchronized void shutdown() {
      this.accumulateUsageTime();
      this.config.save(true);
      this.networkClient.stop();
   }

   /**
    * Called every client tick/loop regardless of whether in a world or main menu.
    */
   public void onClientLoopTick() {
      if (!this.initialized) {
         return;
      }

      this.accumulateUsageTime();
      this.refreshLocalUserSnapshot();

      long now = System.currentTimeMillis();
      if (now - this.lastSaveClockMs >= 60_000L) {
         this.lastSaveClockMs = now;
         if (Augustus.getInstance() != null && Augustus.getInstance().getConfigManager() != null) {
            Augustus.getInstance().getConfigManager().saveAllAsync();
         } else {
            this.config.save(true);
         }
      }

      // Prune stale remote peers
      for (Map.Entry<String, IrcUserRecord> entry : this.remoteUsers.entrySet()) {
         if (now - entry.getValue().getLastSeenMillis() > REMOTE_TIMEOUT_MS) {
            this.remoteUsers.remove(entry.getKey());
         }
      }

      if (this.config.isIrcEnabled()) {
         this.networkClient.tickHeartbeat(this.localUser);
      }
   }

   private void accumulateUsageTime() {
      long now = System.currentTimeMillis();
      if (this.lastTickClockMs <= 0L) {
         this.lastTickClockMs = now;
         return;
      }
      long delta = now - this.lastTickClockMs;
      this.lastTickClockMs = now;
      if (delta > 0L && delta <= 5_000L) {
         this.config.addPlayTimeMillis(delta);
         int newLevel = ClientLevelSystem.levelForPlayTime(this.config.getTotalPlayTimeMillis());
         if (newLevel > this.lastKnownLevel) {
            this.lastKnownLevel = newLevel;
            this.onLevelUp(newLevel);
         }
      }
   }

   private void onLevelUp(int newLevel) {
      String msg = "Subiste al Nivel " + newLevel + " (" + ClientLevelSystem.formatDuration(this.config.getTotalPlayTimeMillis()) + " de uso)";
      this.appendSystemHistory("Nivel " + newLevel + " alcanzado! Tiempo total: " + ClientLevelSystem.formatDuration(this.config.getTotalPlayTimeMillis()));
      if (mc != null && mc.thePlayer != null && mc.theWorld != null) {
         ChatUtil.sendPrefixedChat("IRC", "§d¡" + msg + "!");
         try {
            NotificationManager.pushNotification("Augustus IRC", "Nivel " + newLevel + " desbloqueado", NotificationLevel.INFO);
         } catch (Throwable ignored) {
         }
      }
      if (this.config.isIrcEnabled()) {
         this.refreshLocalUserSnapshot();
         this.networkClient.sendPresenceNow(this.localUser);
      }
   }

   public void refreshLocalUserSnapshot() {
      Session session = mc != null ? mc.getSession() : null;
      String currentNick = session != null && session.getUsername() != null ? session.getUsername() : "Player";
      boolean hasLinked = !this.config.getLinkedOriginalNick().isEmpty();
      String origNick = hasLinked ? this.config.getLinkedOriginalNick() : currentNick;
      String uuid = hasLinked ? this.config.getLinkedUuid() : (session != null ? session.getPlayerID() : "");
      int level = ClientLevelSystem.levelForPlayTime(this.config.getTotalPlayTimeMillis());
      String context = this.resolveCurrentServerContext();

      if (this.localUser == null) {
         this.localUser = new IrcUserRecord(
            this.localNodeId,
            origNick,
            currentNick,
            uuid,
            hasLinked,
            level,
            this.config.getTotalPlayTimeMillis(),
            context,
            true
         );
      } else {
         this.localUser.updateFrom(origNick, currentNick, uuid, hasLinked, level, this.config.getTotalPlayTimeMillis(), context);
      }
   }

   private String resolveCurrentServerContext() {
      if (!this.config.isShareServerStatus() || mc == null) {
         return "Augustus";
      }
      if (mc.isIntegratedServerRunning() || mc.isSingleplayer()) {
         return "Singleplayer";
      }
      if (mc.getCurrentServerData() != null && mc.getCurrentServerData().serverIP != null) {
         return mc.getCurrentServerData().serverIP;
      }
      return "Menu";
   }

   // ---------------------------------------------------------------------------------------------
   // Requirement 1: Premium Minecraft Account Link & Automatic Original Nick Sync
   // ---------------------------------------------------------------------------------------------

   public static boolean isPremiumSession(Session session) {
      if (session == null || session.getUsername() == null || session.getUsername().isEmpty()) {
         return false;
      }
      AccountEntry entry = session.getAccountEntry();
      if (entry != null) {
         AccountLoginType t = entry.getLoginType();
         if (t == AccountLoginType.CRACKED || t == AccountLoginType.THE_ALTENING) {
            return false;
         }
         return isMicrosoftType(t);
      }
      if (EasyMcAuthenticator.currentSessionInfo != null && EasyMcAuthenticator.previousSession != session) {
         return false;
      }
      String token = session.getToken();
      if (token == null || token.length() < 20 || "0".equals(token)) {
         return false;
      }
      return !IrcUserRecord.normalizeUuid(session.getPlayerID()).isEmpty();
   }

   public static boolean isMicrosoftType(AccountLoginType type) {
      return type == AccountLoginType.MICROSOFT_EXTERNAL_BROWSER
         || type == AccountLoginType.MICROSOFT_DEVICE_CODE
         || type == AccountLoginType.MICROSOFT_CREDENTIALS
         || type == AccountLoginType.MICROSOFT_ACCESS_TOKEN;
   }

   public void onSessionApplied(Session session) {
      if (session == null) {
         return;
      }
      if (isPremiumSession(session)) {
         String nick = session.getUsername();
         String uuid = session.getPlayerID();
         String typeName = session.getAccountEntry() != null ? session.getAccountEntry().getLoginType().name() : "LAUNCHER_SESSION";

         this.cachedLinkedPremiumSession = session;
         boolean nickChanged = !nick.equals(this.config.getLinkedOriginalNick());
         this.config.setLinkedOriginalNick(nick);
         this.config.setLinkedUuid(uuid);
         this.config.setLinkedAccountType(typeName);
         this.linkStatusMessage = "Vinculada: " + nick;
         this.linkingInProgress = false;
         MinotarAvatarCache.getAvatar(nick, 64);
         if (nickChanged && Augustus.getInstance() != null && Augustus.getInstance().getConfigManager() != null) {
            Augustus.getInstance().getConfigManager().saveAllAsync();
         }
      }
      this.refreshLocalUserSnapshot();
      this.networkClient.sendPresenceNow(this.localUser);
   }

   public void linkAccountEntry(AccountEntry entry) {
      if (entry == null || !isMicrosoftType(entry.getLoginType())) {
         this.linkStatusMessage = "Selecciona una cuenta Microsoft Premium";
         return;
      }
      this.config.setLinkedOriginalNick(entry.getUsername());
      this.config.setLinkedAccountType(entry.getLoginType().name());
      this.config.setAutoUseOriginalNick(true);
      this.linkStatusMessage = "Autenticando " + entry.getUsername() + "...";
      this.linkingInProgress = true;
      MinotarAvatarCache.getAvatar(entry.getUsername(), 64);
      this.authenticateSavedMicrosoftEntryAsync(entry);
   }

   public void startInteractiveMicrosoftLink() {
      if (this.linkingInProgress) {
         return;
      }
      this.linkingInProgress = true;
      this.linkStatusMessage = "Abriendo navegador para vincular cuenta Microsoft...";
      CompletableFuture.runAsync(() -> {
         try {
            AccountAuthenticator.loginExternalBrowser();
         } finally {
            this.linkingInProgress = false;
         }
      });
   }

   public void clearLinkedPremium() {
      this.config.setLinkedOriginalNick("");
      this.config.setLinkedUuid("");
      this.config.setLinkedAccountType("");
      this.cachedLinkedPremiumSession = null;
      this.linkStatusMessage = "Cuenta desvinculada";
      this.refreshLocalUserSnapshot();
      this.networkClient.sendPresenceNow(this.localUser);
   }

   private void autoLinkAndRestoreOnStartup() {
      Session launchSession = mc != null ? mc.getSession() : null;
      if (isPremiumSession(launchSession)) {
         this.onSessionApplied(launchSession);
         return;
      }

      if (!this.config.isAutoUseOriginalNick()) {
         return;
      }

      AccountEntry candidate = this.findLinkedOrFirstMicrosoftAccount();
      if (candidate != null) {
         this.linkStatusMessage = "Sincronizando " + candidate.getUsername() + "...";
         this.authenticateSavedMicrosoftEntryAsync(candidate);
      } else if (!this.config.getLinkedOriginalNick().isEmpty() && mc != null) {
         this.applyFallbackLinkedSessionIfPlaceholder();
      }
   }

   /**
    * Ensures the linked Premium original nickname & session are active before joining a server or world
    * when {@code autoUseOriginalNick} is enabled.
    */
   public void ensureOriginalNickBeforeConnect() {
      if (!this.config.isAutoUseOriginalNick() || mc == null) {
         return;
      }

      Session current = mc.getSession();
      String linkedNick = this.config.getLinkedOriginalNick();
      if (linkedNick.isEmpty()) {
         AccountEntry candidate = this.findLinkedOrFirstMicrosoftAccount();
         if (candidate != null) {
            linkedNick = candidate.getUsername();
         }
      }
      if (linkedNick.isEmpty()) {
         return;
      }

      if (current != null && linkedNick.equals(current.getUsername()) && isPremiumSession(current)) {
         return;
      }

      if (this.cachedLinkedPremiumSession != null && isPremiumSession(this.cachedLinkedPremiumSession)) {
         mc.setSession(this.cachedLinkedPremiumSession);
         this.refreshLocalUserSnapshot();
         this.networkClient.sendPresenceNow(this.localUser);
         return;
      }

      this.applyFallbackLinkedSessionIfPlaceholder();
   }

   private void applyFallbackLinkedSessionIfPlaceholder() {
      if (mc == null || this.config.getLinkedOriginalNick().isEmpty()) {
         return;
      }
      Session cur = mc.getSession();
      // Only overwrite placeholder ("Player..." / token "0" / empty) or non-matching session when auto-use is enabled
      String linkedNick = this.config.getLinkedOriginalNick();
      if (cur == null || !linkedNick.equals(cur.getUsername())) {
         String uuid = this.config.getLinkedUuid().isEmpty() ? linkedNick : this.config.getLinkedUuid();
         String token = cur != null && cur.getToken() != null ? cur.getToken() : "0";
         mc.setSession(new Session(linkedNick, uuid, token, "mojang", null, cur != null ? cur.getAccountEntry() : null));
         this.refreshLocalUserSnapshot();
      }
   }

   private AccountEntry findLinkedOrFirstMicrosoftAccount() {
      if (AccountManagerScreen.accounts == null) {
         return null;
      }
      String linkedNick = this.config.getLinkedOriginalNick();
      AccountEntry firstMs = null;
      for (Object obj : AccountManagerScreen.accounts) {
         if (!(obj instanceof AccountEntry)) {
            continue;
         }
         AccountEntry entry = (AccountEntry)obj;
         if (!isMicrosoftType(entry.getLoginType())) {
            continue;
         }
         if (!linkedNick.isEmpty() && linkedNick.equalsIgnoreCase(entry.getUsername())) {
            return entry;
         }
         if (firstMs == null && entry.isWorking()) {
            firstMs = entry;
         }
      }
      return firstMs;
   }

   private void authenticateSavedMicrosoftEntryAsync(AccountEntry entry) {
      if (entry == null) {
         return;
      }
      CompletableFuture.runAsync(() -> {
         try {
            switch (entry.getLoginType()) {
               case MICROSOFT_EXTERNAL_BROWSER:
                  AccountAuthenticator.refreshExternalBrowserAccount(entry);
                  break;
               case MICROSOFT_CREDENTIALS:
                  AccountAuthenticator.refreshCredentialsAccount(entry);
                  break;
               case MICROSOFT_DEVICE_CODE:
                  AccountAuthenticator.refreshDeviceCodeAccount(entry);
                  break;
               case MICROSOFT_ACCESS_TOKEN:
                  AccountAuthenticator.loginWithSavedAccessToken(entry);
                  break;
               default:
                  break;
            }
         } catch (Throwable t) {
            this.linkStatusMessage = "Sin conexión para refrescar Microsoft";
         } finally {
            this.linkingInProgress = false;
         }
      });
   }

   public boolean isLinkedAccount(AccountEntry entry) {
      if (entry == null || this.config.getLinkedOriginalNick().isEmpty() || !isMicrosoftType(entry.getLoginType())) {
         return false;
      }
      return entry.getUsername().equalsIgnoreCase(this.config.getLinkedOriginalNick());
   }

   // ---------------------------------------------------------------------------------------------
   // Requirement 2 & 5: User Identification & Nametag / Tab Helpers
   // ---------------------------------------------------------------------------------------------

   public IrcUserRecord resolveUserForPlayer(EntityPlayer player) {
      if (player == null) {
         return null;
      }
      GameProfile profile = player.getGameProfile();
      String name = profile != null ? profile.getName() : player.getName();
      String uuid = profile != null && profile.getId() != null ? profile.getId().toString() : "";
      return this.resolveUserByNameOrUuid(name, uuid);
   }

   public IrcUserRecord resolveUserByNameOrUuid(String gameNick, String uuid) {
      if (!this.config.isIrcEnabled()) {
         return null;
      }
      if (this.localUser != null && this.localUser.matchesPlayer(gameNick, uuid)) {
         return this.localUser;
      }
      for (IrcUserRecord user : this.remoteUsers.values()) {
         if (user.matchesPlayer(gameNick, uuid)) {
            return user;
         }
      }
      return null;
   }

   public List<IrcUserRecord> getOnlineUsersSorted() {
      List<IrcUserRecord> list = new ArrayList<IrcUserRecord>();
      if (this.localUser != null) {
         list.add(this.localUser);
      }
      list.addAll(this.remoteUsers.values());
      Collections.sort(list, new Comparator<IrcUserRecord>() {
         @Override
         public int compare(IrcUserRecord a, IrcUserRecord b) {
            if (a.isLocalUser() != b.isLocalUser()) {
               return a.isLocalUser() ? -1 : 1;
            }
            int lvlCmp = Integer.compare(b.getLevel(), a.getLevel());
            if (lvlCmp != 0) {
               return lvlCmp;
            }
            return a.getDisplayOriginalNick().compareToIgnoreCase(b.getDisplayOriginalNick());
         }
      });
      return list;
   }

   public int getOnlineUserCount() {
      return (this.localUser != null ? 1 : 0) + this.remoteUsers.size();
   }

   void upsertRemoteUser(String nodeId, String orig, String cur, String uuid, boolean prem, int level, long playMs, String server) {
      if (nodeId == null || nodeId.isEmpty() || nodeId.equals(this.localNodeId)) {
         return;
      }
      IrcUserRecord existing = this.remoteUsers.get(nodeId);
      if (existing == null) {
         IrcUserRecord created = new IrcUserRecord(nodeId, orig, cur, uuid, prem, level, playMs, server, false);
         this.remoteUsers.put(nodeId, created);
         if (!created.getDisplayOriginalNick().isEmpty()) {
            MinotarAvatarCache.getAvatar(created.getDisplayOriginalNick(), 40);
         }
      } else {
         existing.updateFrom(orig, cur, uuid, prem, level, playMs, server);
      }
   }

   void removeRemoteUser(String nodeId) {
      if (nodeId != null) {
         this.remoteUsers.remove(nodeId);
      }
   }

   void clearRemoteUsers() {
      this.remoteUsers.clear();
   }

   IrcUserRecord getRemoteUser(String nodeId) {
      return this.remoteUsers.get(nodeId);
   }

   // ---------------------------------------------------------------------------------------------
   // Requirement 3: Exclusive Augustus IRC Chat
   // ---------------------------------------------------------------------------------------------

   public List<IrcChatMessage> getChatHistory() {
      return this.chatHistory;
   }

   public boolean sendIrcChat(String rawMessage) {
      if (rawMessage == null) {
         return false;
      }
      String msg = rawMessage.trim();
      if (msg.isEmpty()) {
         return false;
      }
      if (!this.config.isIrcEnabled()) {
         this.appendSystemHistory("El IRC está desactivado.");
         return false;
      }

      this.refreshLocalUserSnapshot();
      IrcUserRecord local = this.localUser;
      IrcChatMessage outgoing = new IrcChatMessage(
         System.currentTimeMillis(),
         local.getDisplayOriginalNick(),
         local.getLevel(),
         local.isPremiumLinked(),
         msg,
         false
      );
      this.recordChatMessage(outgoing, true);
      this.networkClient.sendChat(local, msg);
      return true;
   }

   void onIncomingRemoteMessage(IrcChatMessage msg) {
      this.recordChatMessage(msg, this.config.isShowIrcInGlobalChat() || this.config.isExclusiveChatMode());
   }

   private void appendSystemHistory(String text) {
      IrcChatMessage notice = new IrcChatMessage(System.currentTimeMillis(), "Augustus", this.getCurrentLevel(), true, text, true);
      this.recordChatMessage(notice, false);
   }

   private void recordChatMessage(IrcChatMessage msg, boolean printToInGameChat) {
      this.chatHistory.add(msg);
      while (this.chatHistory.size() > MAX_HISTORY) {
         this.chatHistory.remove(0);
      }

      if (printToInGameChat && mc != null && mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
         final String line = formatExclusiveChatLine(msg);
         Runnable task = () -> {
            if (mc != null && mc.ingameGUI != null && mc.ingameGUI.getChatGUI() != null) {
               IChatComponent component = ChatUtil.parseChatComponent(line);
               mc.ingameGUI.getChatGUI().printIrcChatMessage(component);
            }
         };
         if (mc.isCallingFromMinecraftThread()) {
            task.run();
         } else {
            PacketThreadUtil.runOnMainThreadImmediate(task);
         }
      }
   }

   public static String formatExclusiveChatLine(IrcChatMessage msg) {
      String levelColor = ClientLevelSystem.minecraftColorCode(msg.getSenderLevel());
      if (msg.isSystemMessage()) {
         return "§5§lA §8» §d" + msg.getContent();
      }
      // Exclusively shows the Augustus user's original nickname and level (no server tags, alts or clutter).
      return "§5§lIRC §8[" + levelColor + "Lv." + msg.getSenderLevel() + "§8] §f" + msg.getSenderOriginalNick() + " §8» §7" + msg.getContent();
   }

   // ---------------------------------------------------------------------------------------------
   // Accessors & Settings Sync
   // ---------------------------------------------------------------------------------------------

   public int getCurrentLevel() {
      return ClientLevelSystem.levelForPlayTime(this.config.getTotalPlayTimeMillis());
   }

   public long getPlayTimeMillis() {
      return this.config.getTotalPlayTimeMillis();
   }

   public String getConnectionStateLabel() {
      if (!this.config.isIrcEnabled()) {
         return "Desactivado";
      }
      return this.networkClient.getState().getLabel();
   }

   public Color getConnectionStatusColor() {
      switch (this.networkClient.getState()) {
         case CONNECTED_EXTERNAL:
         case CONNECTED_HUB_HOST:
         case CONNECTED_HUB_PEER:
            return new Color(74, 222, 128);
         case CONNECTING:
            return Color.ORANGE;
         default:
            return new Color(248, 113, 113);
      }
   }

   public String getActiveServerLabel() {
      return this.networkClient.getActiveServerLabel();
   }

   public String getLinkStatusMessage() {
      if (!this.linkStatusMessage.isEmpty()) {
         return this.linkStatusMessage;
      }
      if (!this.config.getLinkedOriginalNick().isEmpty()) {
         return "Vinculada: " + this.config.getLinkedOriginalNick();
      }
      return "Sin cuenta Premium vinculada";
   }

   public boolean isLinkingInProgress() {
      return this.linkingInProgress;
   }

   public void setEnabled(boolean enabled) {
      this.config.setIrcEnabled(enabled);
      if (enabled) {
         this.refreshLocalUserSnapshot();
         this.networkClient.start();
      } else {
         this.networkClient.stop();
         this.remoteUsers.clear();
      }
   }

   public void reconnect() {
      this.refreshLocalUserSnapshot();
      if (this.config.isIrcEnabled()) {
         this.networkClient.restart();
      } else {
         this.setEnabled(true);
      }
   }
}
