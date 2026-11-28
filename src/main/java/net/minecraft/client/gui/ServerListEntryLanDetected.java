package net.minecraft.client.gui;

import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.network.LanServerDetector;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class ServerListEntryLanDetected implements GuiListExtended.IGuiListEntry {
   private final GuiMultiplayer screen;
   public final Minecraft mc;
   public final LanServerDetector.LanServer lanServer;
   private long lastClickTime = 0L;

   @Override
   public void drawEntry(int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
      // A LAN world is the same option object as an internet server - same plate, same ring, same light -
      // so the two halves of one list cannot look like two different lists. The plate is placed on the
      // row's box (n2 - 2 with the full list width), exactly like ServerListEntryNormal: see the WHY there.
      float rowX = (float)(n2 - 2);
      float rowY = (float)n3;
      float rowW = (float)n4;
      float rowH = (float)n5;
      String hoverKey = "augustus.multiplayer.lan." + n;
      MenuScreenSkin.drawOptionSurface(
         rowX,
         rowY,
         rowW,
         rowH,
         MenuScreenSkin.optionRadius(rowW, rowH),
         UiTheme.smoothState(hoverKey, flag ? 1.0F : 0.0F),
         UiTheme.smoothPress(hoverKey + ".press", 0.0F, flag && UiAnimation.isPrimaryMouseDown()),
         true,
         (float)n6,
         (float)n7,
         UiTheme.accentColor()
      );
      // A LAN world can be the selected row too, and it is marked exactly like an internet server: one accent
      // ring plus the rail, never the stock grey/black rectangle pair. The entry holds the screen directly, so
      // the selection index is read from it (see GuiMultiplayer.getSelectedSlotIndex).
      if (this.screen.getSelectedSlotIndex() == n) {
         MenuScreenSkin.drawSelectionRing(rowX, rowY, rowW, rowH, MenuScreenSkin.optionRadius(rowW, rowH));
      }
      // Strings, offsets and colours untouched: the white LAN title takes the menu ink through
      // VanillaScreenText, the grey MOTD and the dark address keep the values they always had.
      VanillaScreenText.drawString(I18n.format("lanServer.title", new Object[0]), n2 + 32 + 3, n3 + 1, 16777215);
      VanillaScreenText.drawString(this.lanServer.getServerMotd(), n2 + 32 + 3, n3 + 12, 8421504);
      if (this.mc.gameSettings.hideServerAddress) {
         VanillaScreenText.drawString(I18n.format("selectServer.hiddenAddress", new Object[0]), n2 + 32 + 3, n3 + 12 + 11, 3158064);
      } else {
         VanillaScreenText.drawString(this.lanServer.getServerIpPort(), n2 + 32 + 3, n3 + 12 + 11, 3158064);
      }
   }

   @Override
   public void mouseReleased(int n, int n2, int n3, int n4, int n5, int n6) {
   }

   @Override
   public void setSelected(int n, int n2, int n3) {
   }

   public ServerListEntryLanDetected(GuiMultiplayer guiMultiplayer, LanServerDetector.LanServer lanServer2) {
      this.screen = guiMultiplayer;
      this.lanServer = lanServer2;
      this.mc = Minecraft.getMinecraft();
   }

   public LanServerDetector.LanServer getLanServer() {
      return this.lanServer;
   }

   @Override
   public boolean mousePressed(int n, int n2, int n3, int n4, int n5, int n6) {
      this.screen.selectServer(n);
      if (Minecraft.getSystemTime() - this.lastClickTime < 250L) {
         this.screen.connectToSelected();
      }

      this.lastClickTime = Minecraft.getSystemTime();
      return false;
   }
}
