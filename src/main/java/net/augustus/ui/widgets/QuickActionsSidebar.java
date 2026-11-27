package net.augustus.ui.widgets;

import net.augustus.Augustus;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.menu.ContextMenuItem;
import net.augustus.ui.menu.QuickMenuBuilder;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.sidebar.HeaderSidebarItem;
import net.augustus.utils.animation.AnimationUtil;
import net.augustus.utils.animation.EasingType;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.input.Mouse;

public final class QuickActionsSidebar implements MC {
   private ScrollBar scrollBar;
   private final List menuItems;
   private static final int BACKGROUND_COLOR = -939524096;
   private static final int HORIZONTAL_PADDING = 20;
   private boolean expanded;
   public boolean visible;
   private static final int ITEM_MARGIN = 2;
   private AnimationUtil slideAnimation;
   private int sidebarWidth;

   public QuickActionsSidebar(List list) {
      this.menuItems = list;
   }

   /**
    * The quick-actions list is part of the ClickGUI, so it speaks the ClickGUI's face: Inter. Every line
    * check below (widths, scroll pitch, hover bands) goes through the same face, or the list would be laid
    * out for glyphs it does not draw.
    */
   private static InterFontUtil font() {
      return FontManager.body();
   }

   private float itemHeight() {
      return FontManager.bodyLineHeight();
   }

   private int textWidth(String str) {
      InterFontUtil font = font();
      return font == null ? mc.fontRendererObj.getStringWidth(str) : Math.round(font.getStringWidth(str));
   }

   public void initSidebar() {
      ScaledResolution sr = new ScaledResolution(mc);
      this.scrollBar = new ScrollBar(Math.round(this.itemHeight()));
      this.scrollBar.setBounds(sr.getScaledWidth() - 4, (byte)0, sr.getScaledWidth() - 2, sr.getScaledHeight());
      List list = this.collectDisplayLines();
      this.scrollBar.setItemCount(list.size());
      this.sidebarWidth = 0;

      for (String str : (Iterable<String>)(list)) {
         this.sidebarWidth = 20 + Math.max(this.sidebarWidth - 20, this.textWidth(str));
      }

      this.sidebarWidth += 4;
      this.slideAnimation = new AnimationUtil((float)sr.getScaledWidth(), 0.0F, (float)(sr.getScaledWidth() - this.sidebarWidth), 0.0F);
   }

   public boolean handleMouseWheel() {
      boolean flag = this.visible && Mouse.getX() > new ScaledResolution(mc).getScaledWidth() - this.sidebarWidth;
      if (flag) {
         this.scrollBar.handleMouseWheel();
      }

      return flag;
   }

   private void updateSlideState(int n) {
      int scaledWidth = new ScaledResolution(mc).getScaledWidth();
      this.slideAnimation.setBounds((float)scaledWidth, 0.0F, (float)(scaledWidth - this.sidebarWidth), 0.0F);
      if (!((float)n > Math.max(this.slideAnimation.updateX(), (float)(scaledWidth - this.sidebarWidth))) && n <= scaledWidth - 40) {
         if (this.expanded) {
            this.slideAnimation.start(200, EasingType.easeOutQuad, 0, false);
         }

         this.expanded = false;
      } else {
         if (!this.expanded) {
            this.slideAnimation.start(200, EasingType.easeInQuad, 1, false);
         }

         this.expanded = true;
      }

      this.visible = this.slideAnimation.getEasedProgress() > 0.0;
   }

   public static QuickMenuBuilder builder() {
      return new QuickMenuBuilder();
   }

   private ContextMenuItem findItemByText(String str) {

      for (ContextMenuItem contextMenuItem : (Iterable<ContextMenuItem>)(this.menuItems)) {
         String displayText = contextMenuItem.getDisplayText();
         if (displayText != null && displayText.equals(str)) {
            return contextMenuItem;
         }

         Set displayOptions = contextMenuItem.getDisplayOptions();
         if (displayOptions != null && displayOptions.contains(str)) {
            return contextMenuItem;
         }
      }

      return null;
   }

   public void mouseClickMove(int n, int n2) {
      double d = (double)n2;
      this.scrollBar.handleMouseDrag(d);
   }

   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) {
      if (!this.visible) {
         return false;
      } else {
         double d = (double)mouseX;
         double d3 = (double)mouseY;
         this.scrollBar.handleMouseClick(d, d3);
         ScaledResolution sr = new ScaledResolution(mc);
         int clampedScrollOffset = (int)this.scrollBar.getClampedScrollOffset();

         for (String str : (Iterable<String>)(this.collectDisplayLines())) {
            boolean flag = mouseX >= sr.getScaledWidth() - this.sidebarWidth - 2
               && mouseX < sr.getScaledWidth() - 4
               && mouseY >= clampedScrollOffset - 1
               && (float)mouseY <= (float)clampedScrollOffset + this.itemHeight() - 1.0F;
            if (flag) {
               ContextMenuItem contextMenuItem = this.findItemByText(str);
               if (contextMenuItem != null) {
                  ClientSettings.playButtonClickSound();
                  contextMenuItem.onClick(str);
                  return true;
               }
            }

            clampedScrollOffset = Math.round((float)clampedScrollOffset + this.itemHeight());
         }

         return false;
      }
   }

   public void drawSidebar(int n, int n2, float f) {
      this.updateSlideState(n);
      int n3 = (int)this.slideAnimation.updateX();
      ScaledResolution sr = new ScaledResolution(mc);
      InterFontUtil font = font();
      float lineHeight = this.itemHeight();
      if (!this.visible) {
         // Tab handle shown while the list is collapsed: "->" (the '§l' run is applied by the face itself).
         String handle = "§l->";
         if (font == null) {
            mc.fontRendererObj
               .drawStringWithShadow(
                  handle, (float)(n3 - mc.fontRendererObj.getStringWidth(handle)), (float)sr.getScaledHeight() / 2.0F - (float)mc.fontRendererObj.FONT_HEIGHT / 2.0F, -1
               );
         } else {
            font.drawStringWithShadow(
               handle,
               (double)((float)n3 - font.getStringWidth(handle)),
               (double)((float)sr.getScaledHeight() / 2.0F - font.getFontHeight() / 2.0F),
               -1
            );
         }
      } else {
         int n4 = sr.getScaledWidth() - n3;
         RenderUtil.drawRect(n3, 0, sr.getScaledWidth(), sr.getScaledHeight(), -939524096);
         int clampedScrollOffset = (int)this.scrollBar.getClampedScrollOffset();

         for (String str : (Iterable<String>)(this.collectDisplayLines())) {
            ContextMenuItem contextMenuItem = this.findItemByText(str);
            boolean flag = n >= sr.getScaledWidth() - this.sidebarWidth - 2
               && n < sr.getScaledWidth() - 4
               && n2 >= clampedScrollOffset - 1
               && (float)n2 <= (float)clampedScrollOffset + lineHeight - 1.0F;
            int n5 = flag ? -4144960 : -1;
            if (font == null) {
               if (contextMenuItem != null && contextMenuItem.isCentered()) {
                  int n6 = n3 + this.sidebarWidth / 2 - mc.fontRendererObj.getStringWidth(str) / 2 - 2;
                  mc.fontRendererObj.drawStringWithShadow(str, (float)n6, (float)clampedScrollOffset, contextMenuItem instanceof HeaderSidebarItem ? Augustus.getInstance().getClientColor() : n5);
               } else {
                  mc.fontRendererObj.drawStringWithShadow(str, (float)(n3 + 2), (float)clampedScrollOffset, n5);
               }
            } else if (contextMenuItem != null && contextMenuItem.isCentered()) {
               font.drawStringWithShadow(
                  str,
                  (double)((float)n3 + (float)this.sidebarWidth / 2.0F - font.getStringWidth(str) / 2.0F - 2.0F),
                  (double)((float)clampedScrollOffset),
                  contextMenuItem instanceof HeaderSidebarItem ? Augustus.getInstance().getClientColor() : n5
               );
            } else {
               font.drawStringWithShadow(str, (double)((float)(n3 + 2)), (double)((float)clampedScrollOffset), n5);
            }

            clampedScrollOffset = Math.round((float)clampedScrollOffset + lineHeight);
         }

         if (n4 >= this.sidebarWidth - 2) {
            this.scrollBar.updateAndDraw();
         }
      }
   }

   private List collectDisplayLines() {
      LinkedList list = new LinkedList();

      for (ContextMenuItem contextMenuItem : (Iterable<ContextMenuItem>)(this.menuItems)) {
         Set displayOptions = contextMenuItem.getDisplayOptions();
         if (displayOptions != null) {
            list.addAll(displayOptions);
         } else {
            list.add(contextMenuItem.getDisplayText());
         }
      }

      return list;
   }

}
