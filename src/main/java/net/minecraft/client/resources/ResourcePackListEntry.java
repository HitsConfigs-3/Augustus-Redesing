package net.minecraft.client.resources;

import net.augustus.font.FontManager;
import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.gui.GuiListExtended;
import net.minecraft.client.gui.GuiYesNoCallback;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreenResourcePacks;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.ResourceLocation;

public abstract class ResourcePackListEntry implements GuiListExtended.IGuiListEntry {
   private static final ResourceLocation RESOURCE_PACKS_TEXTURE = new ResourceLocation("textures/gui/resource_packs.png");
   private static final IChatComponent INCOMPATIBLE = new ChatComponentTranslation("resourcePack.incompatible", new Object[0]);
   private static final IChatComponent INCOMPATIBLE_OLD = new ChatComponentTranslation("resourcePack.incompatible.old", new Object[0]);
   private static final IChatComponent INCOMPATIBLE_NEW = new ChatComponentTranslation("resourcePack.incompatible.new", new Object[0]);
   public final Minecraft mc;
   public final GuiScreenResourcePacks resourcePacksGUI;

   public boolean canMoveRight() {
      return !this.resourcePacksGUI.hasResourcePackEntry(this);
   }

   public void mouseReleased(int n, int n2, int n3, int n4, int n5, int n6) {
   }

   /**
    * One pack as a row of the pack list, painted with the same option material as every other list of this
    * round - the plate, the single accent ring and the cursor light - with its name and description resting
    * on the menu ink.
    *
    * <p>WHY the plate is placed on {@code (n2 - 2, n3, n4, n5)}: the row's own box is the list box - GuiSlot
    * hit-tests a row at {@code [left + (width - getListWidth())/2, +getListWidth()]}, which is also the
    * rectangle the stock selection plate used to fill - while the x this entry receives carries the 2 px
    * {@code GuiSlot.drawScreen} adds to the drawing origin ({@code left + width/2 - getListWidth()/2 + 2}).
    * Painting on the row box therefore means starting at {@code n2 - 2} and spanning the full list width, so
    * the 200 px plate ends exactly on the column's edge instead of leaning 2 px over it. The server list needs
    * exactly the same correction.</p>
    *
    * <p>Two pieces of the stock row are deliberately kept on top of the plate, because they are information
    * and not chrome: the opaque maroon band (colour {@code 0xFF770000}) an incompatible pack gets across the
    * whole row, and the 30 % black square plus arrow glyphs {@code showHoverOverlay()} puts over the 32x32
    * icon while the pack is under the pointer - that overlay is the drag affordance of the only list in the
    * client with real drag handling. Both keep their exact rectangles and colours; on an incompatible pack the
    * band simply covers the plate, which is the warning doing its job.</p>
    */
   public void drawEntry(int n, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
      // Row rect and its motion. The hover key is the entry's IDENTITY and not its row index: this is the one
      // screen whose rows move between the two columns - that is what the list is for - so an index key would
      // hand one row's hover envelope to whichever pack slid into that slot next. AccountEntry keys its rows
      // by identity for the same reason.
      float rowX = (float)(n2 - 2);
      float rowY = (float)n3;
      float rowW = (float)n4;
      float rowH = (float)n5;
      String hoverKey = "augustus.resourcepack.row." + System.identityHashCode(this);
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

      int resourcePackFormat = this.getResourcePackFormat();
      if (resourcePackFormat != 1) {
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         Gui.drawRect(n2 - 1, n3 - 1, n2 + n4 - 9, n3 + n5 + 1, -8978432);
      }

      int iconX = n2 + 2;
      int iconY = n3 + (n5 - 32) / 2;
      this.bindResourcePackIcon();
      GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
      Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0.0F, 0.0F, 32, 32, 32.0F, 32.0F);
      String resourcePackName = this.getResourcePackName();
      String str = this.getResourcePackDescription();
      if ((this.mc.gameSettings.touchscreen || flag) && this.showHoverOverlay()) {
         this.mc.getTextureManager().bindTexture(RESOURCE_PACKS_TEXTURE);
         Gui.drawRect(iconX, iconY, iconX + 32, iconY + 32, -1601138544);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         int n8 = n6 - iconX;
         int n9 = n7 - iconY;
         if (resourcePackFormat < 1) {
            resourcePackName = INCOMPATIBLE.getFormattedText();
            str = INCOMPATIBLE_OLD.getFormattedText();
         } else if (resourcePackFormat > 1) {
            resourcePackName = INCOMPATIBLE.getFormattedText();
            str = INCOMPATIBLE_NEW.getFormattedText();
         }

         if (this.canMoveRight()) {
            if (n8 < 32) {
               Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0.0F, 32.0F, 32, 32, 256.0F, 256.0F);
            } else {
               Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 0.0F, 0.0F, 32, 32, 256.0F, 256.0F);
            }
         } else {
            if (this.canMoveLeft()) {
               if (n8 < 16) {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 32.0F, 32.0F, 32, 32, 256.0F, 256.0F);
               } else {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 32.0F, 0.0F, 32, 32, 256.0F, 256.0F);
               }
            }

            if (this.canMoveUp()) {
               if (n8 < 32 && n8 > 16 && n9 < 16) {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 96.0F, 32.0F, 32, 32, 256.0F, 256.0F);
               } else {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 96.0F, 0.0F, 32, 32, 256.0F, 256.0F);
               }
            }

            if (this.canMoveDown()) {
               if (n8 < 32 && n8 > 16 && n9 > 16) {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 64.0F, 32.0F, 32, 32, 256.0F, 256.0F);
               } else {
                  Gui.drawModalRectWithCustomSizedTexture(iconX, iconY, 64.0F, 0.0F, 32, 32, 256.0F, 256.0F);
               }
            }
         }
      }

      float textX = (float)(iconX + 36);
      float availW = Math.max(50.0F, rowW - (textX - rowX) - 12.0F);
      InterFontUtil titleFace = FontManager.uiBody();
      InterFontUtil descFace = FontManager.uiSmall();
      String fittedTitle = FontManager.uiFit(titleFace, resourcePackName, availW);
      VanillaScreenText.drawString(fittedTitle, Math.round(textX), n3 + 4, 16777215);

      List wrappedDesc = FontManager.uiWrap(descFace, str, availW);
      for (int i = 0; i < 2 && i < wrappedDesc.size(); i++) {
         String line = FontManager.uiFit(descFace, (String)wrappedDesc.get(i), availW);
         VanillaScreenText.drawSmallString(line, textX, (float)n3 + 15.5F + 9.5F * (float)i, 0xFFB8B0D0);
      }
   }

   public boolean canMoveUp() {
      List listContaining = this.resourcePacksGUI.getListContaining(this);
      int n = listContaining.indexOf(this);
      return n > 0 && ((ResourcePackListEntry)listContaining.get(n - 1)).showHoverOverlay();
   }

   public boolean mousePressed(int n, int n2, int n3, int n4, int n5, int n6) {
      if (this.showHoverOverlay() && n5 <= 32) {
         if (this.canMoveRight()) {
            this.resourcePacksGUI.markChanged();
            int resourcePackFormat = this.getResourcePackFormat();
            if (resourcePackFormat != 1) {
               String str = I18n.format("resourcePack.incompatible.confirm.title", new Object[0]);
               String str2 = I18n.format("resourcePack.incompatible.confirm." + (resourcePackFormat > 1 ? "new" : "old"), new Object[0]);
               this.mc.displayGuiScreen(new GuiYesNo(new ResourcePackListEntry.DefaultGuiYesNoCallback(this), str, str2, 0));
            } else {
               this.resourcePacksGUI.getListContaining(this).remove(this);
               this.resourcePacksGUI.getSelectedResourcePacks().add(0, this);
            }

            return true;
         }

         if (n5 < 16 && this.canMoveLeft()) {
            this.resourcePacksGUI.getListContaining(this).remove(this);
            this.resourcePacksGUI.getAvailableResourcePacks().add(0, this);
            this.resourcePacksGUI.markChanged();
            return true;
         }

         if (n5 > 16 && n6 < 16 && this.canMoveUp()) {
            List listContaining = this.resourcePacksGUI.getListContaining(this);
            int n7 = listContaining.indexOf(this);
            listContaining.remove(this);
            listContaining.add(n7 - 1, this);
            this.resourcePacksGUI.markChanged();
            return true;
         }

         if (n5 > 16 && n6 > 16 && this.canMoveDown()) {
            List listContaining2 = this.resourcePacksGUI.getListContaining(this);
            int n8 = listContaining2.indexOf(this);
            listContaining2.remove(this);
            listContaining2.add(n8 + 1, this);
            this.resourcePacksGUI.markChanged();
            return true;
         }
      }

      return false;
   }

   public abstract String getResourcePackName();

   public boolean canMoveDown() {
      List listContaining = this.resourcePacksGUI.getListContaining(this);
      int n = listContaining.indexOf(this);
      return n >= 0 && n < listContaining.size() - 1 && ((ResourcePackListEntry)listContaining.get(n + 1)).showHoverOverlay();
   }

   public abstract int getResourcePackFormat();

   public ResourcePackListEntry(GuiScreenResourcePacks guiScreenResourcePacks) {
      this.resourcePacksGUI = guiScreenResourcePacks;
      this.mc = Minecraft.getMinecraft();
   }

   public boolean showHoverOverlay() {
      return true;
   }

   public void setSelected(int n, int n2, int n3) {
   }

   public abstract void bindResourcePackIcon();

   public boolean canMoveLeft() {
      return this.resourcePacksGUI.hasResourcePackEntry(this);
   }

   public abstract String getResourcePackDescription();



   public static class DefaultGuiYesNoCallback implements GuiYesNoCallback {
      public final ResourcePackListEntry entry;

      public DefaultGuiYesNoCallback(ResourcePackListEntry resourcePackListEntry) {
         this.entry = resourcePackListEntry;
      }

      @Override
      public void confirmClicked(boolean flag, int n) {
         List listContaining = this.entry.resourcePacksGUI.getListContaining(this.entry);
         GuiScreenResourcePacks resourcePacksGUI = this.entry.resourcePacksGUI;
         this.entry.mc.displayGuiScreen(resourcePacksGUI);
         if (flag) {
            listContaining.remove(this.entry);
            this.entry.resourcePacksGUI.getSelectedResourcePacks().add(0, this.entry);
         }
      }

   }
}
