package net.augustus.ui.menu;

import java.io.IOException;
import java.util.List;
import net.augustus.Augustus;
import net.augustus.irc.ClientLevelSystem;
import net.augustus.irc.IrcChatMessage;
import net.augustus.irc.IrcConfigFile;
import net.augustus.irc.IrcManager;
import net.augustus.irc.IrcUserRecord;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.UiIconResources;
import net.augustus.ui.modern.components.GlassMenuButton;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.MinotarAvatarCache;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

/**
 * Augustus IRC workspace tab: linked Premium account + automatic original nick,
 * client usage level & progress, live Augustus user roster, and exclusive nickname-only IRC chat.
 */
public class IrcMenuTab extends ClientMenuTab {
   private float leftX;
   private float leftW;
   private float rightX;
   private float rightW;
   private float cardPad;
   private float gap;

   private float profileY;
   private float profileH;
   private float networkY;
   private float networkH;

   private float usersY;
   private float usersH;
   private float usersRowsTop;
   private float usersRowsBottom;

   private float chatY;
   private float chatH;
   private float chatFeedTop;
   private float chatFeedBottom;

   private GuiTextField hostField;
   private GuiTextField channelField;
   private GuiTextField chatInputField;

   private GlassMenuButton linkBtn;
   private GlassMenuButton autoNickBtn;
   private GlassMenuButton exclusiveChatBtn;
   private GlassMenuButton nametagLogoBtn;
   private GlassMenuButton transportBtn;
   private GlassMenuButton applyNetBtn;
   private GlassMenuButton sendChatBtn;

   private float usersScrollPx = 0.0F;
   private float chatScrollPx = 0.0F;

   public IrcMenuTab() {
      super("Augustus IRC");
   }

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.USERS_GROUP;
   }

   private void layoutCards() {
      float pageX = TabbedMenuScreen.contentX();
      float pageW = TabbedMenuScreen.contentW();
      this.gap = TabbedMenuScreen.gap();
      float row = TabbedMenuScreen.rowH();
      float top = TabbedMenuScreen.bodyTop();
      float bottom = TabbedMenuScreen.bodyBottom();
      float totalH = Math.max(220.0F, bottom - top);

      this.cardPad = clamp(Math.round(row * 0.45F), 9.0F, 12.0F);
      this.leftW = clamp(Math.round(pageW * 0.42F), 165.0F, 355.0F);
      if (this.leftW > pageW - 180.0F) {
         this.leftW = Math.max(150.0F, pageW - 180.0F);
      }
      this.leftX = pageX;
      this.rightX = this.leftX + this.leftW + this.gap;
      this.rightW = Math.max(170.0F, pageX + pageW - this.rightX);

      // Left Card 1: Profile & Level
      this.profileY = top;
      this.profileH = clamp(Math.round(totalH * 0.50F), 138.0F, 210.0F);

      // Left Card 2: Network & Display Settings
      this.networkY = this.profileY + this.profileH + this.gap;
      this.networkH = Math.max(105.0F, bottom - this.networkY);

      // Right Top: Online Augustus Users
      this.usersY = top;
      this.usersH = clamp(Math.round(totalH * 0.37F), 100.0F, 185.0F);
      this.usersRowsTop = this.usersY + this.cardPad + menuCaptionHeight() + 7.0F;
      this.usersRowsBottom = this.usersY + this.usersH - this.cardPad;

      // Right Bottom: Exclusive Augustus Chat
      this.chatY = this.usersY + this.usersH + this.gap;
      this.chatH = Math.max(115.0F, bottom - this.chatY);
      this.chatFeedTop = this.chatY + this.cardPad + menuCaptionHeight() + 7.0F;
      this.chatFeedBottom = this.chatY + this.chatH - this.cardPad - row - 8.0F;
   }

   @Override
   public void initContent() {
      super.initContent();
      this.layoutCards();

      IrcManager irc = IrcManager.getInstance();
      IrcConfigFile cfg = irc.getConfig();
      float row = TabbedMenuScreen.rowH();
      float colGap = clamp(Math.round(this.gap * 0.65F), 6.0F, 8.0F);
      float innerLeftX = this.leftX + this.cardPad;
      float innerLeftW = this.leftW - this.cardPad * 2.0F;
      float halfLeftW = (innerLeftW - colGap) * 0.5F;

      // Profile bottom buttons
      float profBtnY = this.profileY + this.profileH - this.cardPad - row;
      this.linkBtn = this.addGlassButton(
         Math.round(innerLeftX), Math.round(profBtnY), Math.round(halfLeftW), Math.round(row),
         cfg.getLinkedOriginalNick().isEmpty() ? "Vincular Premium" : "Sincronizar Nick",
         btn -> this.onLinkClick()
      ).withIcon(LucideIcon.LINK).withVariant(GlassMenuButton.Variant.PRIMARY);

      this.autoNickBtn = this.addGlassButton(
         Math.round(innerLeftX + halfLeftW + colGap), Math.round(profBtnY), Math.round(halfLeftW), Math.round(row),
         this.formatToggle("Auto Nick", cfg.isAutoUseOriginalNick()),
         btn -> this.onToggleAutoNick()
      ).withIcon(LucideIcon.USER).withToggleState(cfg.isAutoUseOriginalNick());

      // Network card buttons & inputs
      float netCurY = this.networkY + this.cardPad + menuCaptionHeight() + 7.0F;
      this.exclusiveChatBtn = this.addGlassButton(
         Math.round(innerLeftX), Math.round(netCurY), Math.round(halfLeftW), Math.round(row),
         this.formatToggle("Chat IRC", cfg.isExclusiveChatMode()),
         btn -> this.onToggleExclusiveChat()
      ).withIcon(LucideIcon.USERS_GROUP).withToggleState(cfg.isExclusiveChatMode());

      this.nametagLogoBtn = this.addGlassButton(
         Math.round(innerLeftX + halfLeftW + colGap), Math.round(netCurY), Math.round(halfLeftW), Math.round(row),
         this.formatToggle("Logo Tag", cfg.isShowLogoOnNametags()),
         btn -> this.onToggleNametagLogo()
      ).withIcon(LucideIcon.EYE).withToggleState(cfg.isShowLogoOnNametags());

      float fieldsY = netCurY + row + 7.0F;
      float hostW = Math.round(innerLeftW * 0.62F);
      float chanX = innerLeftX + hostW + colGap;
      float chanW = Math.max(55.0F, innerLeftW - hostW - colGap);

      this.hostField = this.addTextField(Math.round(innerLeftX), Math.round(fieldsY), Math.round(hostW), Math.round(row));
      this.hostField.setCustomFont(menuBodyFont());
      this.hostField.setMaxStringLength(96);
      if (!cfg.getIrcHost().isEmpty()) {
         this.hostField.setText(cfg.getIrcHost() + ":" + cfg.getIrcPort());
      }

      this.channelField = this.addTextField(Math.round(chanX), Math.round(fieldsY), Math.round(chanW), Math.round(row));
      this.channelField.setCustomFont(menuBodyFont());
      this.channelField.setMaxStringLength(40);
      this.channelField.setText(cfg.getIrcChannel());

      float netBottomY = Math.max(fieldsY + row + 7.0F, this.networkY + this.networkH - this.cardPad - row);
      this.applyNetBtn = this.addGlassButton(
         Math.round(innerLeftX), Math.round(netBottomY), Math.round(halfLeftW), Math.round(row),
         "Conectar", btn -> this.onApplyNetwork()
      ).withIcon(LucideIcon.REFRESH_CW);

      this.transportBtn = this.addGlassButton(
         Math.round(innerLeftX + halfLeftW + colGap), Math.round(netBottomY), Math.round(halfLeftW), Math.round(row),
         "Modo: " + cfg.getTransportMode(), btn -> this.onCycleTransportMode()
      ).withIcon(LucideIcon.BROADCAST);

      // Exclusive Chat bottom input + Send button
      float chatBarY = this.chatY + this.chatH - this.cardPad - row;
      float sendW = 68.0F;
      float chatFieldX = this.rightX + this.cardPad;
      float chatFieldW = Math.max(80.0F, this.rightW - this.cardPad * 2.0F - sendW - colGap);

      this.chatInputField = this.addTextField(Math.round(chatFieldX), Math.round(chatBarY), Math.round(chatFieldW), Math.round(row));
      this.chatInputField.setCustomFont(menuBodyFont());
      this.chatInputField.setMaxStringLength(260);

      this.sendChatBtn = this.addGlassButton(
         Math.round(chatFieldX + chatFieldW + colGap), Math.round(chatBarY), Math.round(sendW), Math.round(row),
         "Enviar", btn -> this.submitMenuChat()
      ).withIcon(LucideIcon.ARROW_RIGHT).withVariant(GlassMenuButton.Variant.PRIMARY);
   }

   @Override
   public void drawChromeBehind() {
      this.layoutCards();
      this.menuGlassPanel(this.leftX, this.profileY, this.leftW, this.profileH, 0.0F, 1.0F);
      this.menuGlassPanel(this.leftX, this.networkY, this.leftW, this.networkH, 0.0F, 1.0F);
      this.menuGlassPanel(this.rightX, this.usersY, this.rightW, this.usersH, 0.0F, 1.0F);
      this.menuGlassPanel(this.rightX, this.chatY, this.rightW, this.chatH, 0.0F, 1.0F);

      menuSectionLabel("PERFIL & NIVEL AUGUSTUS", this.leftX + this.cardPad, this.profileY + this.cardPad, 1.0F);
      menuSectionLabel("CONEXIÓN & CHAT IRC", this.leftX + this.cardPad, this.networkY + this.cardPad, 1.0F);
      menuSectionLabel("USUARIOS CONECTADOS", this.rightX + this.cardPad, this.usersY + this.cardPad, 1.0F);
      menuSectionLabel("CHAT EXCLUSIVO AUGUSTUS", this.rightX + this.cardPad, this.chatY + this.cardPad, 1.0F);
   }

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      IrcManager irc = IrcManager.getInstance();
      IrcConfigFile cfg = irc.getConfig();
      this.setStatus(irc.getConnectionStateLabel(), irc.getConnectionStatusColor());

      super.drawContent(mouseX, mouseY, partialTicks);
      this.layoutCards();
      this.refreshButtonStates(cfg);

      this.drawProfileDetails(irc, cfg);
      this.drawFieldPlaceholders();
      this.drawUsersList(irc, mouseX, mouseY);
      this.drawExclusiveChatFeed(irc);
   }

   private void refreshButtonStates(IrcConfigFile cfg) {
      if (this.linkBtn != null) {
         this.linkBtn.displayString = cfg.getLinkedOriginalNick().isEmpty() ? "Vincular Premium" : "Sincronizar Nick";
      }
      if (this.autoNickBtn != null) {
         this.autoNickBtn.displayString = this.formatToggle("Auto Nick", cfg.isAutoUseOriginalNick());
         this.autoNickBtn.setToggleState(cfg.isAutoUseOriginalNick());
      }
      if (this.exclusiveChatBtn != null) {
         this.exclusiveChatBtn.displayString = this.formatToggle("Chat IRC", cfg.isExclusiveChatMode());
         this.exclusiveChatBtn.setToggleState(cfg.isExclusiveChatMode());
      }
      if (this.nametagLogoBtn != null) {
         this.nametagLogoBtn.displayString = this.formatToggle("Logo Tag", cfg.isShowLogoOnNametags());
         this.nametagLogoBtn.setToggleState(cfg.isShowLogoOnNametags());
      }
      if (this.transportBtn != null) {
         this.transportBtn.displayString = "Modo: " + cfg.getTransportMode();
      }
   }

   private void drawProfileDetails(IrcManager irc, IrcConfigFile cfg) {
      float x = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;
      float y = this.profileY + this.cardPad + menuCaptionHeight() + 8.0F;
      int accent = UiTheme.accentColor();

      IrcUserRecord local = irc.getLocalUser();
      String origNick = local != null ? local.getDisplayOriginalNick() : "Player";
      String curNick = local != null ? local.getCurrentNick() : origNick;
      boolean linked = !cfg.getLinkedOriginalNick().isEmpty();

      float head = 28.0F;
      GlassRenderer.drawRoundedBox(
         x - 1.0F, y - 1.0F, head + 2.0F, head + 2.0F, 5.0F,
         UiTheme.withAlpha(UiTheme.menuPillFillBottomArgb(), 0x80),
         UiTheme.withAlpha(accent, linked ? 185 : 70), 0.9F
      );
      ResourceLocation avatar = (ResourceLocation)MinotarAvatarCache.avatarCache.get(origNick);
      if (avatar != null) {
         UiIconResources.drawIcon(avatar, (int)x, (int)y, (int)head, (int)head, 1.0);
      } else {
         LucideIcon.USER.drawCentered(x + head * 0.5F, y + head * 0.5F, 14.0F, TEXT_SUB, false);
      }

      float tx = x + head + 9.0F;
      float availW = Math.max(40.0F, innerW - head - 9.0F);
      InterFontUtil titleFace = menuBodyFont();
      InterFontUtil smallFace = menuSmallFont();

      String fittedNick = menuFit(titleFace, origNick, Math.max(30.0F, availW - 56.0F));
      if (titleFace != null) {
         titleFace.drawStringWithShadow(fittedNick, (double)tx, (double)(y + 1.0F), TEXT_BASE);
         float badgeX = tx + titleFace.getStringWidth(fittedNick) + 6.0F;
         if (linked) {
            menuBadge("PREMIUM", badgeX, y, 0x334ADE80, 0xFF86EFAC);
         } else {
            menuBadge("LOCAL", badgeX, y, 0x3394A3B8, 0xFFCBD5E1);
         }
      }

      String sub = linked
         ? ("En cliente/servidor: §f" + curNick)
         : irc.getLinkStatusMessage();
      if (smallFace != null) {
         smallFace.drawStringFormatted(menuFit(smallFace, sub, availW), (double)tx, (double)(y + 15.0F), TEXT_SUB, true);
      }

      // Client usage Level & Progress Bar
      long playMs = irc.getPlayTimeMillis();
      int level = irc.getCurrentLevel();
      float progress = ClientLevelSystem.progressWithinLevel(playMs);
      int lvlColor = ClientLevelSystem.badgeAccentArgb(level, accent);

      float lvlY = y + head + 10.0F;
      float lvlBadgeW = menuBadge("NIVEL " + level, x, lvlY, UiTheme.withAlpha(lvlColor, 48), UiTheme.lerpArgb(lvlColor, 0xFFFFFFFF, 0.35F));
      if (smallFace != null) {
         String timeLabel = "Uso: " + ClientLevelSystem.formatDuration(playMs)
            + "  •  Sig: " + ClientLevelSystem.formatDuration(ClientLevelSystem.millisToNextLevel(playMs));
         smallFace.drawStringWithShadow(
            menuFit(smallFace, timeLabel, Math.max(30.0F, innerW - lvlBadgeW - 8.0F)),
            (double)(x + lvlBadgeW + 7.0F), (double)(lvlY + 2.0F), TEXT_SUB
         );
      }

      float barY = lvlY + 17.0F;
      float barH = 6.0F;
      GlassRenderer.drawRoundedRect(x, barY, innerW, barH, 3.0F, 0x55090B12);
      float fillW = Math.max(progress > 0.01F ? 6.0F : 0.0F, innerW * progress);
      if (fillW > 0.0F) {
         GlassRenderer.drawRoundedGradientBoxHorizontal(
            x, barY, fillW, barH, 3.0F,
            UiTheme.withAlpha(accent, 235), UiTheme.withAlpha(UiTheme.accentSoftGlow(), 245), 1.0F
         );
      }
   }

   private void drawFieldPlaceholders() {
      int hintColor = UiTheme.scaleAlpha(TEXT_SUB, 0.58F);
      float row = TabbedMenuScreen.rowH();
      if (this.hostField != null && this.hostField.getText().isEmpty() && !this.hostField.isFocused()) {
         this.drawFieldPlaceholder("Host:Puerto (vacío = Hub LAN)", this.hostField.xPosition, this.hostField.yPosition, Math.round(row), hintColor, menuBodyFont(), 7.0F);
      }
      if (this.channelField != null && this.channelField.getText().isEmpty() && !this.channelField.isFocused()) {
         this.drawFieldPlaceholder("#canal", this.channelField.xPosition, this.channelField.yPosition, Math.round(row), hintColor, menuBodyFont(), 7.0F);
      }
      if (this.chatInputField != null && this.chatInputField.getText().isEmpty() && !this.chatInputField.isFocused()) {
         this.drawFieldPlaceholder("Mensaje al chat exclusivo de Augustus...", this.chatInputField.xPosition, this.chatInputField.yPosition, Math.round(row), hintColor, menuBodyFont(), 7.0F);
      }
   }

   private void drawUsersList(IrcManager irc, int mouseX, int mouseY) {
      List<IrcUserRecord> users = irc.getOnlineUsersSorted();
      InterFontUtil cap = menuCaptionFont();
      if (cap != null) {
         String cnt = String.valueOf(users.size());
         cap.drawStringWithShadow(cnt, (double)(this.rightX + this.rightW - this.cardPad - cap.getStringWidth(cnt)),
            (double)(this.usersY + this.cardPad), UiTheme.scaleAlpha(TEXT_SUB, 0.85F));
      }

      float viewH = Math.max(10.0F, this.usersRowsBottom - this.usersRowsTop);
      float rowH = 26.0F;
      float pitch = 30.0F;
      float maxScroll = Math.max(0.0F, (float)users.size() * pitch - viewH);
      this.usersScrollPx = clamp(this.usersScrollPx, 0.0F, maxScroll);

      this.enableScissor(
         Math.round(this.rightX + 2.0F), Math.round(this.usersRowsTop),
         Math.round(this.rightX + this.rightW - 2.0F), Math.round(this.usersRowsBottom)
      );

      float rowX = this.rightX + this.cardPad;
      float rowW = Math.max(60.0F, this.rightW - this.cardPad * 2.0F);
      float y = this.usersRowsTop - this.usersScrollPx;
      InterFontUtil body = menuBodyFont();
      InterFontUtil small = menuSmallFont();
      int accent = UiTheme.accentColor();

      for (IrcUserRecord user : users) {
         if (y + rowH >= this.usersRowsTop - 4.0F && y <= this.usersRowsBottom + 4.0F) {
            boolean hovered = menuHovered(rowX, y, rowW, rowH, mouseX, mouseY);
            float hover = this.menuHover("user." + user.getNodeId(), hovered);
            this.menuRow(rowX, y, rowW, rowH, hover, 1.0F);

            float head = 18.0F;
            float hx = rowX + 6.0F;
            float hy = y + (rowH - head) * 0.5F;
            ResourceLocation avatar = (ResourceLocation)MinotarAvatarCache.avatarCache.get(user.getDisplayOriginalNick());
            if (avatar != null) {
               UiIconResources.drawIcon(avatar, (int)hx, (int)hy, (int)head, (int)head, 1.0);
            } else {
               LucideIcon.USER.drawCentered(hx + head * 0.5F, hy + head * 0.5F, 10.0F, TEXT_SUB, false);
            }

            int lvlColor = ClientLevelSystem.badgeAccentArgb(user.getLevel(), accent);
            String lvlText = "Lv." + user.getLevel();
            float rightReserve = 42.0F;
            float tx = hx + head + 7.0F;
            float availNameW = Math.max(40.0F, rowW - (tx - rowX) - rightReserve - 8.0F);

            String orig = user.getDisplayOriginalNick();
            String displayLine = user.usesDifferentServerNick()
               ? (orig + " §8(§7" + user.getCurrentNick() + "§8)")
               : orig;
            if (body != null) {
               body.drawStringFormatted(menuFit(body, displayLine, availNameW), (double)tx, (double)(y + 4.0F), TEXT_BASE, true);
            }
            if (small != null) {
               String meta = (user.isPremiumLinked() ? "§a✔ Premium" : "§7Usuario") + "  •  §7" + user.getServerContext();
               small.drawStringFormatted(menuFit(small, meta, availNameW), (double)tx, (double)(y + 15.0F), TEXT_SUB, true);
            }

            menuBadge(lvlText, rowX + rowW - rightReserve - 4.0F, y + (rowH - 13.0F) * 0.5F,
               UiTheme.withAlpha(lvlColor, 42), UiTheme.lerpArgb(lvlColor, 0xFFFFFFFF, 0.30F));
         }
         y += pitch;
      }

      this.disableScissor();
   }

   private void drawExclusiveChatFeed(IrcManager irc) {
      InterFontUtil cap = menuCaptionFont();
      if (cap != null) {
         String tag = "Solo nicknames";
         cap.drawStringWithShadow(tag, (double)(this.rightX + this.rightW - this.cardPad - cap.getStringWidth(tag)),
            (double)(this.chatY + this.cardPad), UiTheme.scaleAlpha(TEXT_SUB, 0.80F));
      }

      List<IrcChatMessage> messages = irc.getChatHistory();
      float feedX = this.rightX + this.cardPad;
      float feedW = Math.max(60.0F, this.rightW - this.cardPad * 2.0F);
      float viewH = Math.max(12.0F, this.chatFeedBottom - this.chatFeedTop);
      InterFontUtil face = menuSmallFont();
      float lineH = face != null ? Math.max(12.0F, face.getFontHeight() + 3.0F) : 12.0F;

      if (messages.isEmpty()) {
         this.menuEmptyState(
            feedX, this.chatFeedTop, feedW, viewH,
            LucideIcon.USERS_GROUP, "Sin mensajes en IRC",
            "Escribe abajo o usa el chat exclusivo dentro del juego.", false
         );
         return;
      }

      float contentH = (float)messages.size() * lineH;
      float maxScroll = Math.max(0.0F, contentH - viewH);
      this.chatScrollPx = clamp(this.chatScrollPx, 0.0F, maxScroll);

      this.enableScissor(
         Math.round(this.rightX + 2.0F), Math.round(this.chatFeedTop),
         Math.round(this.rightX + this.rightW - 2.0F), Math.round(this.chatFeedBottom)
      );

      // Anchor newest messages to bottom (minus chatScrollPx when scrolling up)
      float startY = Math.max(this.chatFeedTop, this.chatFeedBottom - contentH) + this.chatScrollPx;
      float y = startY;
      for (IrcChatMessage msg : messages) {
         if (y + lineH >= this.chatFeedTop - 2.0F && y <= this.chatFeedBottom + 2.0F && face != null) {
            String lvlCol = ClientLevelSystem.minecraftColorCode(msg.getSenderLevel());
            String line = msg.isSystemMessage()
               ? ("§d• " + msg.getContent())
               : (lvlCol + "[Lv." + msg.getSenderLevel() + "] §f" + msg.getSenderOriginalNick() + " §8» §7" + msg.getContent());
            face.drawStringFormatted(menuFit(face, line, feedW), (double)feedX, (double)y, TEXT_BASE, true);
         }
         y += lineH;
      }

      this.disableScissor();
   }

   private void onLinkClick() {
      IrcManager.getInstance().startInteractiveMicrosoftLink();
   }

   private void onToggleAutoNick() {
      IrcManager irc = IrcManager.getInstance();
      IrcConfigFile cfg = irc.getConfig();
      boolean next = !cfg.isAutoUseOriginalNick();
      cfg.setAutoUseOriginalNick(next);
      if (next) {
         irc.ensureOriginalNickBeforeConnect();
      }
      this.syncModuleFromConfig();
   }

   private void onToggleExclusiveChat() {
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();
      cfg.setExclusiveChatMode(!cfg.isExclusiveChatMode());
      this.syncModuleFromConfig();
   }

   private void onToggleNametagLogo() {
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();
      cfg.setShowLogoOnNametags(!cfg.isShowLogoOnNametags());
      this.syncModuleFromConfig();
   }

   private void onCycleTransportMode() {
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();
      String cur = cfg.getTransportMode();
      String next = "Auto".equalsIgnoreCase(cur) ? "External IRC" : ("External IRC".equalsIgnoreCase(cur) ? "Local Hub" : "Auto");
      cfg.setTransportMode(next);
   }

   private void onApplyNetwork() {
      IrcConfigFile cfg = IrcManager.getInstance().getConfig();
      if (this.hostField != null) {
         String raw = this.hostField.getText().trim();
         if (raw.isEmpty()) {
            cfg.setIrcHost("");
            if ("External IRC".equalsIgnoreCase(cfg.getTransportMode())) {
               cfg.setTransportMode("Auto");
            }
         } else {
            int colon = raw.lastIndexOf(':');
            if (colon > 0 && colon < raw.length() - 1) {
               cfg.setIrcHost(raw.substring(0, colon));
               try {
                  cfg.setIrcPort(Integer.parseInt(raw.substring(colon + 1).trim()));
               } catch (NumberFormatException ignored) {
               }
            } else {
               cfg.setIrcHost(raw);
            }
         }
      }
      if (this.channelField != null) {
         cfg.setIrcChannel(this.channelField.getText());
      }
      IrcManager.getInstance().reconnect();
      Augustus.getInstance().getConfigManager().saveAllAsync();
   }

   private void submitMenuChat() {
      if (this.chatInputField == null) {
         return;
      }
      String text = this.chatInputField.getText().trim();
      if (!text.isEmpty()) {
         IrcManager.getInstance().sendIrcChat(text);
         this.chatInputField.setText("");
         this.chatScrollPx = 0.0F;
      }
   }

   private void syncModuleFromConfig() {
      if (Augustus.getInstance() != null && Augustus.getInstance().getModuleManager() != null && Augustus.getInstance().getModuleManager().irc != null) {
         Augustus.getInstance().getModuleManager().irc.syncFromConfig();
      }
      if (Augustus.getInstance() != null && Augustus.getInstance().getConfigManager() != null) {
         Augustus.getInstance().getConfigManager().saveAllAsync();
      }
   }

   private String formatToggle(String name, boolean on) {
      return name + ": " + (on ? "§aON" : "§cOFF");
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if ((keyCode == 28 || keyCode == 156) && this.chatInputField != null && this.chatInputField.isFocused()) {
         this.submitMenuChat();
         return;
      }
      super.keyTyped(typedChar, keyCode);
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      int wheel = Mouse.getEventDWheel();
      if (wheel != 0) {
         float delta = wheel > 0 ? -18.0F : 18.0F;
         float mx = TabbedMenuScreen.mouseX();
         float my = TabbedMenuScreen.mouseY();
         if (mx >= this.rightX && mx <= this.rightX + this.rightW) {
            if (my >= this.usersY && my <= this.usersY + this.usersH) {
               this.usersScrollPx = Math.max(0.0F, this.usersScrollPx + delta);
            } else if (my >= this.chatY && my <= this.chatY + this.chatH) {
               this.chatScrollPx = Math.max(0.0F, this.chatScrollPx - delta);
            }
         }
      }
   }
}
