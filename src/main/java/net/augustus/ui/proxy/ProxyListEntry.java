package net.augustus.ui.proxy;

import java.awt.Color;
import net.augustus.Augustus;
import net.augustus.render.RenderUtil;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.TimeHelper;
import net.augustus.utils.animation.AnimationUtil;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.utils.interfaces.MC;
import net.augustus.utils.network.ProxyEntry;
import net.minecraft.client.gui.FontRenderer;

/**
 * Proxy list row rendered with Augustus frosted glass material, crisp smaller Inter typography,
 * protocol/ping badges, active/favorite indicators, and cursor-tracking hover glow.
 */
public class ProxyListEntry implements MC {
   private boolean hovered;
   private final TimeHelper timeHelper = new TimeHelper();
   private boolean selected;
   private int renderY;
   private int renderHeight;
   private int renderWidth;
   private final ProxyEntry proxyEntry;
   private final AnimationUtil hoverAnimation = new AnimationUtil(0.0F, 0.0F, 0.0F, 0.0F);
   private int renderX;

   public boolean isHovered() {
      return this.hovered;
   }

   public void drawEntry(int mouseX, int mouseY, int x, int y, int width, int height, boolean flag,
         ClientMenuTab owner) {
      this.renderX = x;
      this.renderY = y;
      this.renderWidth = width;
      this.renderHeight = height;
      this.selected = flag;

      boolean isHovered = RenderUtil.isHovered((float)x, (float)y, (float)width, (float)height, mouseX, mouseY);
      this.hovered = isHovered;
      float hover = owner.menuHover("proxy." + System.identityHashCode(this), isHovered);

      owner.menuRow((float)x, (float)y, (float)width, (float)height, hover, 1.0F);
      float radius = Math.min(6.0F, Math.min((float)width, (float)height) * 0.5F);
      int accent = UiTheme.accentColor();
      if (flag) {
         GlassRenderer.drawRoundedRect((float)x, (float)y, (float)width, (float)height, radius,
               UiTheme.withAlpha(accent, 34));
         GlassRenderer.drawRoundedOutline((float)x, (float)y, (float)width, (float)height, radius, 1.4F,
               UiTheme.withAlpha(accent, 245));
         ClientMenuTab.menuAccentRail((float)x + 3.5F, (float)y, (float)height, 0.95F);
      }

      ProxyEntry activeProxy = Augustus.getInstance().getProxy();
      boolean isActive = activeProxy != null && activeProxy.matches(this.proxyEntry);

      InterFontUtil titleFace = ClientMenuTab.menuBodyFont();
      InterFontUtil subFace = ClientMenuTab.menuSmallFont();
      if (titleFace == null || subFace == null) {
         FontRenderer fr = mc.fontRendererObj;
         fr.drawString(this.proxyEntry.getHost() + ":" + this.proxyEntry.getPort(), x + 10, y + 6, Color.white.getRGB());
         return;
      }

      // Leading icon badge
      float iconBox = 20.0F;
      float iconX = (float)x + (flag ? 11.0F : 9.0F);
      float iconY = (float)y + ((float)height - iconBox) * 0.5F;
      int badgeBg = isActive ? 0x2E4ADE80 : (this.isFavorite() ? 0x2EFBBF24 : UiTheme.withAlpha(accent, 36));
      int iconTint = isActive ? 0xFF4ADE80 : (this.isFavorite() ? 0xFFFBBF24 : UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.35F));
      GlassRenderer.drawRoundedBox(iconX, iconY, iconBox, iconBox, 5.0F, badgeBg,
            UiTheme.withAlpha(iconTint, 95), 0.75F);
      LucideIcon.GLOBE.drawCentered(iconX + iconBox * 0.5F, iconY + iconBox * 0.5F, 11.0F, iconTint, false);

      // Ping badge on the right
      int ping = this.proxyEntry.getPing();
      String pingText = ping >= 0 ? ping + " ms" : "N/A";
      int pingColor = ping < 0 ? 0x9994A3B8 : (ping < 120 ? 0xFF4ADE80 : (ping < 280 ? 0xFFFBBF24 : 0xFFF87171));
      float pingW = subFace.getStringWidth(pingText);
      float pingX = (float)x + (float)width - 10.0F - pingW;
      subFace.drawStringWithShadow(pingText, (double)pingX,
            (double)((float)y + ((float)height - subFace.getFontHeight()) * 0.5F), pingColor);

      // Host + port + protocol badge
      float textX = iconX + iconBox + 9.0F;
      float availW = Math.max(40.0F, pingX - textX - 10.0F);
      int hostColor = isActive ? 0xFF4ADE80 : (this.isFavorite() ? 0xFFFDE68A : ClientMenuTab.TEXT_BASE);

      String hostLine = ClientMenuTab.menuFit(titleFace, this.proxyEntry.getHost() + ":" + this.proxyEntry.getPort(), availW);
      titleFace.drawStringWithShadow(hostLine, (double)textX, (double)((float)y + 5.0F), hostColor);

      String rawProto = this.proxyEntry.getProxyType();
      String protoName = rawProto != null && !rawProto.isEmpty() ? rawProto : "SOCKS";
      String subInfo = protoName + (isActive ? "  •  Connected" : (this.isFavorite() ? "  •  Favorite" : ""));
      String fittedSub = ClientMenuTab.menuFit(subFace, subInfo, availW);
      subFace.drawStringWithShadow(fittedSub, (double)textX, (double)((float)y + 18.5F),
            isActive ? 0xCC86EFAC : ClientMenuTab.TEXT_SUB);
   }

   public void setFavorite(boolean favorite) {
      this.proxyEntry.setMarked(favorite);
   }

   public TimeHelper getTimeHelper() {
      return this.timeHelper;
   }

   public ProxyListEntry(ProxyEntry proxyEntry2) {
      this.proxyEntry = proxyEntry2;
   }

   public int getRenderHeight() {
      return this.renderHeight;
   }

   public int getRenderY() {
      return this.renderY;
   }

   public boolean isFavorite() {
      return this.proxyEntry.isMarked();
   }

   public void mouseClicked(int n, int n2, int n3, ProxyManagerScreen proxyManagerScreen) {
      if (n3 == 0 && RenderUtil.isHovered((float)this.renderX, (float)this.renderY, (float)this.renderWidth,
            (float)this.renderHeight, n, n2)) {
         if (KeyboardInputUtil.isKeyDown(157) || KeyboardInputUtil.isKeyDown(29)) {
            this.setFavorite(!this.isFavorite());
            return;
         }

         if (!this.timeHelper.reached(250L)) {
            String str = this.proxyEntry.getHost()
               + ":"
               + this.proxyEntry.getPort()
               + (
                  !this.proxyEntry.getUsername().equals("")
                     ? ":" + this.proxyEntry.getUsername()
                           + (!this.proxyEntry.getPassword().equals("") ? ":" + this.proxyEntry.getPassword() : "")
                     : ""
               );
            proxyManagerScreen.connectToProxy(str, true);
         }

         this.timeHelper.reset();
      }
   }

   public ProxyEntry getProxyEntry() {
      return this.proxyEntry;
   }

   public boolean isSelected() {
      return this.selected;
   }

   public int getRenderWidth() {
      return this.renderWidth;
   }

   public int getRenderX() {
      return this.renderX;
   }

   public AnimationUtil getHoverAnimation() {
      return this.hoverAnimation;
   }
}
