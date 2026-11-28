package net.augustus.ui.widgets;

import net.augustus.Augustus;
import net.augustus.font.FontManager;
import net.augustus.render.RenderUtil;
import net.augustus.ui.menu.ContextMenuItem;
import net.augustus.ui.menu.QuickMenuBuilder;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
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
      return FontManager.uiSmall();
   }

   private float itemHeight() {
      return 14.0F;
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
         int clampedScrollOffset = Math.round(10.0F + (float)this.scrollBar.getClampedScrollOffset());

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
         float pillW = 18.0F;
         float pillH = 42.0F;
         float pillX = (float)n3 - pillW - 4.0F;
         float pillY = (float)sr.getScaledHeight() * 0.5F - pillH * 0.5F;
         boolean pillHovered = (float)n >= pillX && (float)n <= pillX + pillW && (float)n2 >= pillY && (float)n2 <= pillY + pillH;
         MenuScreenSkin.drawOptionSurface(
            pillX, pillY, pillW, pillH, 6.0F,
            pillHovered ? 1.0F : 0.0F, 0.0F, true,
            (float)n, (float)n2, UiTheme.accentColor()
         );
         LucideIcon.ARROW_LEFT.drawCentered(pillX + pillW * 0.5F, pillY + pillH * 0.5F, 10.5F, pillHovered ? 0xFFF8FAFC : UiTheme.withAlpha(UiTheme.accentSoftGlow(), 0xCC));
      } else {
         int n4 = sr.getScaledWidth() - n3;
         MenuScreenSkin.drawGlassContainer((float)(n3 - 4), 6.0F, (float)(n4), (float)(sr.getScaledHeight() - 12), 8.0F);
         int clampedScrollOffset = Math.round(10.0F + (float)this.scrollBar.getClampedScrollOffset());

         for (String str : (Iterable<String>)(this.collectDisplayLines())) {
            ContextMenuItem contextMenuItem = this.findItemByText(str);
            boolean flag = n >= sr.getScaledWidth() - this.sidebarWidth - 2
               && n < sr.getScaledWidth() - 6
               && n2 >= clampedScrollOffset - 1
               && (float)n2 <= (float)clampedScrollOffset + lineHeight - 1.0F;
            boolean isHeader = contextMenuItem instanceof HeaderSidebarItem;
            if (flag && !isHeader && !str.trim().isEmpty()) {
               GlassRenderer.drawRoundedBox((float)n3, (float)clampedScrollOffset - 1.0F, (float)(this.sidebarWidth - 4), lineHeight, 4.0F,
                  UiTheme.withAlpha(UiTheme.accentColor(), 64), 0, 0.0F);
            }
            int textColor = isHeader ? UiTheme.lerpArgb(UiTheme.accentColor(), 0xFFFFFFFF, 0.35F) : (flag ? 0xFFF8FAFC : 0xFFE2E8F0);
            if (font == null) {
               if (contextMenuItem != null && contextMenuItem.isCentered()) {
                  int n6 = n3 + this.sidebarWidth / 2 - mc.fontRendererObj.getStringWidth(str) / 2 - 2;
                  mc.fontRendererObj.drawStringWithShadow(str, (float)n6, (float)clampedScrollOffset, textColor);
               } else {
                  mc.fontRendererObj.drawStringWithShadow(str, (float)(n3 + 6), (float)clampedScrollOffset, textColor);
               }
            } else if (contextMenuItem != null && contextMenuItem.isCentered()) {
               font.drawStringWithShadow(
                  str,
                  (double)((float)n3 + (float)this.sidebarWidth / 2.0F - font.getStringWidth(str) / 2.0F - 2.0F),
                  (double)((float)clampedScrollOffset + 1.5F),
                  textColor
               );
            } else {
               font.drawStringWithShadow(str, (double)((float)(n3 + 6)), (double)((float)clampedScrollOffset + 1.5F), textColor);
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
