package net.augustus.ui.account;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.awt.Color;
import java.util.HashMap;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.codec.JsonCodecRegistryHolder;
import net.augustus.settings.property.codec.JsonPropertyCodec;
import net.augustus.ui.UiIconResources;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.MinotarAvatarCache;
import net.augustus.utils.TimeHelper;
import net.augustus.utils.TuplePair;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;

/**
 * Saved account row rendered with Augustus glass material, compact avatar frame,
 * crisp smaller Inter typography, status/type badges, and cursor-tracking hover glow.
 */
public class AccountEntry implements MC {
   private long lastLoginTime;
   private final HashMap bannedServerIcons;
   private boolean loggedIn;
   private final TimeHelper timeHelper = new TimeHelper();
   private final HashMap unbannedServerIcons;
   private long playTimeMillis;
   private boolean hovered;
   private JsonObject accountData = null;
   private AccountLoginType loginType = AccountLoginType.CRACKED;
   private HashMap unbannedServers;
   private String username = "";
   private long sessionStartTime;
   private boolean working;
   private HashMap bannedServers;
   private int renderY;
   private int renderHeight;
   private int renderWidth;
   private int renderX;
   private boolean marked = false;

   public boolean isBannedOnServer(String str) {
      return this.bannedServers.get(str) != null;
   }

   public int getRenderHeight() {
      return this.renderHeight;
   }

   private void loginAsCracked() {
      AccountAuthenticator.loginSavedCrackedAccount(this);
   }

   public AccountLoginType getLoginType() {
      return this.loginType;
   }

   public int getRenderWidth() {
      return this.renderWidth;
   }

   public void setWorking(boolean flag) {
      this.working = flag;
      if (!flag && (this.loginType == AccountLoginType.MICROSOFT_ACCESS_TOKEN
            || (Boolean)AccountManagerScreen.instance.removeInvalidAccounts.getValue())) {
         AccountManagerScreen.accounts.remove(this);
      }
   }

   public String getUsername() {
      return this.username;
   }

   public JsonObject getAccountData() {
      return this.accountData;
   }

   public AccountEntry() {
      this.working = true;
      this.lastLoginTime = 0L;
      this.playTimeMillis = 0L;
      this.sessionStartTime = 0L;
      this.bannedServers = new HashMap();
      this.unbannedServers = new HashMap();
      this.bannedServerIcons = new HashMap();
      this.unbannedServerIcons = new HashMap();
      this.loggedIn = false;
   }

   public HashMap getBannedServerIcons() {
      return this.bannedServerIcons;
   }

   public JsonObject toJson() {
      JsonObject json = new JsonObject();
      JsonPropertyCodec stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
      stringCodec.write("Name", this.username, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
      stringCodec.write("Type", this.loginType.name(), json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
      stringCodec.write("LastLoginTime", this.lastLoginTime, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
      stringCodec.write("PlayTime", this.playTimeMillis, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      stringCodec.write("Marked", this.marked, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      stringCodec.write("Working", this.working, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      stringCodec.write("LoggedIn", this.loggedIn, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonObjectCodec();
      stringCodec.write("AccountData", this.accountData, json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonArrayCodec();
      stringCodec.write("Banned", this.serializeServerMap(this.bannedServers), json);
      stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonArrayCodec();
      stringCodec.write("Unbanned", this.serializeServerMap(this.unbannedServers), json);
      return json;
   }

   public void drawEntry(int x, int y, int width, int height, int mouseX, int mouseY, boolean selected,
         ClientMenuTab owner) {
      this.renderX = x;
      this.renderY = y;
      this.renderWidth = width;
      this.renderHeight = height;
      this.loggedIn = mc.getSession() != null && mc.getSession().getUsername().equals(this.username)
            && mc.getSession().getAccountEntry() != null && mc.getSession().getAccountEntry().equals(this);

      String hoverKey = "acc." + System.identityHashCode(this);
      boolean isHovered = RenderUtil.isHovered((float)x, (float)y, (float)width, (float)height, mouseX, mouseY);
      this.hovered = isHovered;
      float hover = owner.menuHover(hoverKey, isHovered);

      owner.menuRow((float)x, (float)y, (float)width, (float)height, hover, 1.0F);
      float radius = Math.min(6.0F, Math.min((float)width, (float)height) * 0.5F);
      int accent = UiTheme.accentColor();
      if (selected) {
         GlassRenderer.drawRoundedRect((float)x, (float)y, (float)width, (float)height, radius,
               UiTheme.withAlpha(accent, 34));
         GlassRenderer.drawRoundedOutline((float)x, (float)y, (float)width, (float)height, radius, 1.4F,
               UiTheme.withAlpha(accent, 245));
         ClientMenuTab.menuAccentRail((float)x + 3.5F, (float)y, (float)height, 0.95F);
      }

      InterFontUtil titleFace = ClientMenuTab.menuBodyFont();
      InterFontUtil subFace = ClientMenuTab.menuSmallFont();
      if (titleFace == null || subFace == null) {
         this.drawEntryBitmap(mc.fontRendererObj, x, y);
         return;
      }

      // Avatar tile on left
      float head = 24.0F;
      float headX = (float)x + (selected ? 11.0F : 9.0F);
      float headY = (float)y + ((float)height - head) * 0.5F;
      GlassRenderer.drawRoundedBox(headX - 1.0F, headY - 1.0F, head + 2.0F, head + 2.0F, 4.5F,
            UiTheme.withAlpha(UiTheme.menuPillFillBottomArgb(), 0x66), 0x22FFFFFF, 0.75F);
      ResourceLocation location = (ResourceLocation)MinotarAvatarCache.avatarCache.get(this.username);
      if (location != null) {
         UiIconResources.drawIcon(location, (int)headX, (int)headY, (int)head, (int)head, 1.0);
      } else {
         LucideIcon.USER.drawCentered(headX + head * 0.5F, headY + head * 0.5F, 12.0F,
               UiTheme.scaleAlpha(ClientMenuTab.TEXT_SUB, 0.8F), false);
      }

      // Banned server icons on right
      float rightEdge = (float)x + (float)width - 9.0F;
      float iconsStart = rightEdge;
      for (TuplePair tuplePair : (Iterable<TuplePair>)(this.bannedServerIcons.values())) {
         if (tuplePair.getSecond() != null && iconsStart - 12.0F > headX + head + 60.0F) {
            iconsStart -= 12.0F;
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            this.drawServerIcon((int)iconsStart, (int)((float)y + (float)height * 0.5F - 5.0F),
                  (ResourceLocation)tuplePair.getFirst());
         }
      }

      float textX = headX + head + 9.0F;
      float availW = Math.max(40.0F, iconsStart - textX - 6.0F);
      boolean isCurrentSession = mc.getSession() != null && mc.getSession().getUsername().equals(this.username);
      boolean isIrcLinked = net.augustus.irc.IrcManager.getInstance().isLinkedAccount(this);

      int nameColor = isCurrentSession ? 0xFF4ADE80 : (this.isMarked() ? 0xFFFBBF24 : ClientMenuTab.TEXT_BASE);
      float line1Y = (float)y + 6.5F;
      float line2Y = (float)y + 20.5F;

      String safeName = ClientMenuTab.menuFit(titleFace, this.username, availW - (isCurrentSession ? 46.0F : 0.0F) - (isIrcLinked ? 34.0F : 0.0F));
      titleFace.drawStringWithShadow(safeName, (double)textX, (double)line1Y, nameColor);

      float badgeX = textX + titleFace.getStringWidth(safeName) + 6.0F;
      if (isCurrentSession && badgeX + 38.0F <= iconsStart) {
         badgeX += ClientMenuTab.menuBadge("ACTIVE", badgeX, line1Y - 1.0F, 0x334ADE80, 0xFF86EFAC) + 4.0F;
      } else if (this.isMarked() && badgeX + 42.0F <= iconsStart) {
         badgeX += ClientMenuTab.menuBadge("MARKED", badgeX, line1Y - 1.0F, 0x33FBBF24, 0xFFFDE68A) + 4.0F;
      }
      if (isIrcLinked && badgeX + 26.0F <= iconsStart) {
         ClientMenuTab.menuBadge("IRC", badgeX, line1Y - 1.0F, UiTheme.withAlpha(accent, 72), UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.40F));
      }

      String stateLabel = this.working ? "§aFunctional" : "§cInvalid";
      String subText = "§7" + this.loginType.getDisplayName() + "  •  " + stateLabel;
      String fittedSub = ClientMenuTab.menuFit(subFace, subText, availW);
      subFace.drawStringFormatted(fittedSub, (double)textX, (double)line2Y, ClientMenuTab.TEXT_SUB, true);
   }

   private void drawEntryBitmap(FontRenderer fontRendererObj, int n, int n2) {
      String str = mc.getSession() != null && mc.getSession().getUsername().equals(this.username)
            ? "§a" + this.username
            : (this.isMarked() ? "§6" + this.username : "§f" + this.username);
      fontRendererObj.drawString(str, (float)n + 8.0F, (float)n2 + 6.0F, Color.white.getRGB());
      fontRendererObj.drawString("§7" + this.loginType.getDisplayName() + " - " + (this.working ? "§aFunctional" : "§cInvalid"),
            (float)n + 8.0F, (float)n2 + 18.0F, Color.white.getRGB());
   }

   public boolean isWorking() {
      return this.working;
   }

   public void refreshServerIcons(HashMap map) {
      this.unbannedServerIcons.clear();
      for (String str : (Iterable<String>)(this.unbannedServers.keySet())) {
         TuplePair tuplePair = (TuplePair)map.get(str);
         if (tuplePair != null) {
            this.unbannedServerIcons.put(str, tuplePair);
         }
      }

      this.bannedServerIcons.clear();
      for (String str2 : (Iterable<String>)(this.bannedServers.keySet())) {
         TuplePair tuplePair2 = (TuplePair)map.get(str2);
         if (tuplePair2 != null) {
            this.bannedServerIcons.put(str2, tuplePair2);
         }
      }
   }

   public void fromJson(JsonObject json) {
      JsonPropertyCodec stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
      this.username = (String)stringCodec.read("Name", this.username, json);
      JsonPropertyCodec stringCodec2 = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
      this.loginType = AccountLoginType.valueOf((String)stringCodec2.read("Type", this.loginType.name(), json));
      JsonPropertyCodec longCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
      this.lastLoginTime = (Long)longCodec.read("LastLoginTime", this.lastLoginTime, json);
      longCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
      this.playTimeMillis = (Long)longCodec.read("PlayTime", this.playTimeMillis, json);
      JsonPropertyCodec booleanCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      this.marked = (Boolean)booleanCodec.read("Marked", this.marked, json);
      JsonPropertyCodec booleanCodec2 = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      this.working = (Boolean)booleanCodec2.read("Working", this.working, json);
      JsonPropertyCodec booleanCodec3 = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getBooleanCodec();
      this.loggedIn = (Boolean)booleanCodec3.read("LoggedIn", this.loggedIn, json);
      JsonPropertyCodec jsonObjectCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonObjectCodec();
      this.accountData = (JsonObject)jsonObjectCodec.read("AccountData", this.accountData, json);
      longCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonArrayCodec();
      this.bannedServers = this.deserializeServerMap((JsonArray)longCodec.read("Banned", (Object)null, json));
      longCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getJsonArrayCodec();
      this.unbannedServers = this.deserializeServerMap((JsonArray)longCodec.read("Unbanned", (Object)null, json));
      if (this.loggedIn) {
         this.authenticateAsync();
      }
   }

   public boolean isUnbannedOnServer(String str) {
      return this.unbannedServers.get(str) != null;
   }

   public synchronized void updateAccountData(JsonObject json, String str) {
      this.accountData = json;
      this.username = str;
      this.lastLoginTime = System.currentTimeMillis();
   }

   public int getRenderY() {
      return this.renderY;
   }

   private void loginWithCredentials() {
      AccountAuthenticator.refreshCredentialsAccount(this);
   }

   public JsonArray serializeServerMap(HashMap map) {
      JsonArray array = new JsonArray();
      for (Entry entry : (Iterable<Entry>)(map.entrySet())) {
         JsonObject json = new JsonObject();
         JsonPropertyCodec stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
         stringCodec.write("Key", entry.getKey(), json);
         stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
         stringCodec.write("Value", entry.getValue(), json);
         array.add(json);
      }
      return array;
   }

   public long getSessionStartTime() {
      return this.sessionStartTime;
   }

   private void loginWithDeviceCode() {
      AccountAuthenticator.refreshDeviceCodeAccount(this);
   }

   public void addPlayTime(long l) {
      this.playTimeMillis += l;
   }

   public void markUnbannedOnServer(String str) {
      this.bannedServers.remove(str);
      this.bannedServerIcons.remove(str);
      if (((Long)this.unbannedServers.get(str)) == null) {
         this.unbannedServers.put(str, System.currentTimeMillis());
         ResourceLocation location = new ResourceLocation("servers/" + str + "/icon");
         this.unbannedServerIcons.put(str, new TuplePair(location, (DynamicTexture)mc.getTextureManager().getTexture(location)));
      } else {
         this.unbannedServers.remove(str);
         this.unbannedServers.put(str, System.currentTimeMillis());
      }
   }

   public HashMap getBannedServers() {
      return this.bannedServers;
   }

   public int getRenderX() {
      return this.renderX;
   }

   public HashMap getUnbannedServers() {
      return this.unbannedServers;
   }

   public void markBannedOnServer(String str, Long val) {
      this.unbannedServers.remove(str);
      this.unbannedServerIcons.remove(str);
      if (((Long)this.bannedServers.get(str)) == null) {
         this.bannedServers.put(str, val);
         ResourceLocation location = new ResourceLocation("servers/" + str + "/icon");
         this.bannedServerIcons.put(str, new TuplePair(location, (DynamicTexture)mc.getTextureManager().getTexture(location)));
      } else {
         this.bannedServers.remove(str);
         this.bannedServers.put(str, val);
      }
   }

   public long getLastLoginTime() {
      return this.lastLoginTime;
   }

   public HashMap getUnbannedServerIcons() {
      return this.unbannedServerIcons;
   }

   public void setSessionStartTime(long l) {
      this.sessionStartTime = l;
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton == 0
            && RenderUtil.isHovered((float)this.renderX, (float)this.renderY, (float)this.renderWidth,
                  (float)this.renderHeight, mouseX, mouseY)) {
         if (!KeyboardInputUtil.isKeyDown(157) && !KeyboardInputUtil.isKeyDown(29)) {
            if (!this.timeHelper.reached(250L) && (AccountManagerScreen.instance.getActiveLoginTask() == null
                  || AccountManagerScreen.instance.getActiveLoginTask().isDone())) {
               this.authenticateAsync();
            }
            this.timeHelper.reset();
         } else {
            this.marked = !this.marked;
         }
      }
   }

   private void loginWithExternalBrowser() {
      AccountAuthenticator.refreshExternalBrowserAccount(this);
   }

   public long getPlayTimeMillis() {
      return this.playTimeMillis;
   }

   public AccountEntry(String str, JsonObject json, AccountLoginType accountLoginType) {
      this.working = true;
      this.lastLoginTime = 0L;
      this.playTimeMillis = 0L;
      this.sessionStartTime = 0L;
      this.bannedServers = new HashMap();
      this.unbannedServers = new HashMap();
      this.bannedServerIcons = new HashMap();
      this.unbannedServerIcons = new HashMap();
      this.loggedIn = false;
      this.username = str;
      this.accountData = json;
      this.loginType = accountLoginType;
      this.lastLoginTime = System.currentTimeMillis();
   }

   public boolean isMarked() {
      return this.marked;
   }

   private void authenticateAsync() {
      switch (this.loginType) {
         case MICROSOFT_CREDENTIALS:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> this.loginWithCredentials()));
            break;
         case MICROSOFT_DEVICE_CODE:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> this.loginWithDeviceCode()));
            break;
         case MICROSOFT_EXTERNAL_BROWSER:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> this.loginWithExternalBrowser()));
            break;
         case MICROSOFT_ACCESS_TOKEN:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> {
               AccountAuthenticator.loginWithSavedAccessToken(this);
            }));
            break;
         case CRACKED:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> this.loginAsCracked()));
            break;
         case THE_ALTENING:
            AccountManagerScreen.instance.setActiveLoginTask(CompletableFuture.runAsync(() -> {
               AccountAuthenticator.loginSavedTheAlteningAccount(this);
            }));
      }
   }

   private void drawServerIcon(int n, int n2, ResourceLocation location) {
      mc.getTextureManager().bindTexture(location);
      GlStateManager.enableBlend();
      Gui.drawModalRectWithCustomSizedTexture(n, n2, 0.0F, 0.0F, 10, 10, 10.0F, 10.0F);
      GlStateManager.disableBlend();
   }

   public HashMap deserializeServerMap(JsonArray array) {
      if (array == null) {
         return new HashMap();
      } else {
         HashMap map = new HashMap();
         for (JsonElement elem : array) {
            if (elem instanceof JsonObject) {
               try {
                  JsonObject json = (JsonObject)elem;
                  JsonPropertyCodec stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getStringCodec();
                  String str3 = (String)stringCodec.read("Key", "", json);
                  stringCodec = JsonCodecRegistryHolder.GSON_SERIALIZER_PROVIDER.getLongCodec();
                  Long val2 = (Long)stringCodec.read("Value", Long.MIN_VALUE, json);
                  if (!str3.isEmpty() && val2 != Long.MIN_VALUE) {
                     map.put(str3, val2);
                  }
               } catch (Exception e) {
               }
            }
         }
         return map;
      }
   }
}
