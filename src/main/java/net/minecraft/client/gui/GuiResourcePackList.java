package net.minecraft.client.gui;

import java.util.List;
import net.augustus.ui.VanillaScreenText;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.resources.ResourcePackListEntry;

public abstract class GuiResourcePackList extends GuiListExtended {
   public final Minecraft mc;
   public final List list;

   @Override
   public int getScrollBarX() {
      return this.right - 6;
   }

   public abstract String getListHeader();

   @Override
   public int getListWidth() {
      return this.width;
   }

   public GuiResourcePackList(Minecraft mc2, int n, int n2, List list2) {
      super(mc2, n, n2, 38, n2 - 60, 40);
      this.mc = mc2;
      this.list = list2;
      this.centerListVertically = false;
      this.setHasListHeader(true, 22);
   }

   public ResourcePackListEntry getListEntry(int n) {
      return (ResourcePackListEntry)this.getList().get(n);
   }

   @Override
   public GuiListExtended.IGuiListEntry getEntry(int n) {
      return this.getListEntry(n);
   }

   @Override
   public void drawListHeader(int n, int n2, Tessellator tessellator) {
      float headerY = (float)Math.min(this.top + 5, n2 + 2);
      LucideIcon icon = this instanceof GuiResourcePackSelected ? LucideIcon.CIRCLE_CHECK : LucideIcon.LIST;
      int iconColor = this instanceof GuiResourcePackSelected ? UiTheme.accentColor() : UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC);
      String title = this.getListHeader();
      float textW = (float)VanillaScreenText.getStringWidth(title);
      float totalW = 11.0F + 5.0F + textW;
      float startX = (float)n + ((float)this.width - totalW) * 0.5F;
      icon.draw(startX, headerY - 0.5F, 10.5F, iconColor, false);
      VanillaScreenText.drawString(title, Math.round(startX + 16.0F), Math.round(headerY), 0xFFF8FAFC);
      float sepY = headerY + 13.0F;
      GlassRenderer.drawSeparator((float)(n + 4), sepY, (float)(n + this.width - 8), sepY, 1.0F, UiTheme.GLASS_DIVIDER);
   }

   public List getList() {
      return this.list;
   }

   @Override
   public int getSize() {
      return this.getList().size();
   }
}
