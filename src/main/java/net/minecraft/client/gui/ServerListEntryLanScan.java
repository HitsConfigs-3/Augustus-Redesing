package net.minecraft.client.gui;

import net.augustus.ui.VanillaScreenText;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.I18n;

public class ServerListEntryLanScan implements GuiListExtended.IGuiListEntry {
   private final Minecraft mc = Minecraft.getMinecraft();

   /**
    * The LAN scan placeholder, drawn with the menu's face.
    *
    * <p>WHY this row gets no option plate while every world and server row does: it is not an option. Its
    * {@code mousePressed} returns false and it is skipped by the selection logic in {@code GuiMultiplayer},
    * so a plate here would promise a click that nothing handles - and it would sit in the same list as real
    * rows, reading as one more selectable server. The labels are moved to the ink anyway, because the
    * bitmap face was the only thing that still made this row look like a different application.</p>
    */
   @Override
   public void drawEntry(int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
      int centerY = n3 + n5 / 2;
      VanillaScreenText.drawCenteredString(
         I18n.format("lanServer.scanning", new Object[0]),
         this.mc.currentScreen.width / 2,
         centerY - 11,
         0xFFF8FAFC
      );
      String str;
      switch ((int)(Minecraft.getSystemTime() / 300L % 4L)) {
         case 0:
         default:
            str = "O o o";
            break;
         case 1:
         case 3:
            str = "o O o";
            break;
         case 2:
            str = "o o O";
      }

      VanillaScreenText.drawSmallCenteredString(str, (float)this.mc.currentScreen.width * 0.5F, (float)(centerY + 2), 0xCCB8B0D0);
   }

   @Override
   public boolean mousePressed(int n, int n2, int n3, int n4, int n5, int n6) {
      return false;
   }

   @Override
   public void mouseReleased(int n, int n2, int n3, int n4, int n5, int n6) {
   }

   @Override
   public void setSelected(int n, int n2, int n3) {
   }
}
