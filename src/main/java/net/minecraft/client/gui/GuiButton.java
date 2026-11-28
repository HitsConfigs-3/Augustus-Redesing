package net.minecraft.client.gui;

import java.awt.Color;
import java.util.Locale;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.ClientManagers;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.audio.SoundHandler;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

public class GuiButton extends Gui {
   public static final ResourceLocation buttonTextures;
   public int width;
   public int height;
   public int xPosition;
   public int yPosition;
   public String displayString;
   public int id;
   public boolean enabled;
   public boolean visible;
   public boolean hovered;
   public boolean forceVanillaStyle;
   private LucideIcon buttonIcon;
   private boolean primaryAccent;
   /** Owned hover/press envelopes for the glass rendering (per button: no shared-key collisions). */
   private float glassHoverAnim = 0.0F;
   private float glassPressAnim = 0.0F;

   public GuiButton withIcon(LucideIcon icon) {
      this.buttonIcon = icon;
      return this;
   }

   public GuiButton withPrimaryAccent(boolean primary) {
      this.primaryAccent = primary;
      return this;
   }

   public int darkenColor(int n, double d) {
      return RenderUtil.adjustColorBrightness(n, d);
   }

   public int getButtonWidth() {
      return this.width;
   }

   public void drawButton(Minecraft mc, int n, int n2) {
      if (this.visible) {
         if (!((Module)ClientManagers.moduleManager.invisMode).isToggled() && !this.forceVanillaStyle) {
            // Pointer state for this frame. The hover/press ENVELOPES are smooth floats owned by the
            // glass renderer below, so there is no 100 ms keyframe pop to start here any more.
            this.hovered = n >= this.xPosition && n2 >= this.yPosition && n < this.xPosition + this.width && n2 < this.yPosition + this.height;

            this.drawGlassButton(mc, n, n2);
         } else {
            FontRenderer fontRendererObj2 = mc.fontRendererObj;
            mc.getTextureManager().bindTexture(buttonTextures);
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.hovered = n >= this.xPosition && n2 >= this.yPosition && n < this.xPosition + this.width && n2 < this.yPosition + this.height;
            int hoverState = this.getHoverState(this.hovered);
            GlStateManager.enableBlend();
            GlStateManager.tryBlendFuncSeparate(770, 771, 1, 0);
            GlStateManager.blendFunc(770, 771);
            this.drawTexturedModalRect(this.xPosition, this.yPosition, 0, 46 + hoverState * 20, this.width / 2, this.height);
            this.drawTexturedModalRect(this.xPosition + this.width / 2, this.yPosition, 200 - this.width / 2, 46 + hoverState * 20, this.width / 2, this.height);
            this.mouseDragged(mc, n, n2);
            int n4 = 14737632;
            if (!this.enabled) {
               n4 = 10526880;
            } else if (this.hovered) {
               n4 = 16777120;
            }

            drawCenteredString(fontRendererObj2, this.displayString, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, n4);
         }
      }
   }

   public GuiButton(int n, int n2, int n3, String str) {
      this(n, n2, n3, 200, 20, str, false);
   }

   public void drawButtonForegroundLayer(int n, int n2) {
   }

   /**
    * Draws this button with the redesigned menu's option material instead of a flat client-coloured plate.
    *
    * <p>What it draws, and why every piece is the menu's own:</p>
    * <ul>
    *   <li>{@link MenuScreenSkin#drawOptionSurface} paints the shared glass <em>material</em> (the ClickGUI's
    *       per-frame backdrop capture, clipped to the silhouette), then the menu's dark veil, then the
    *       cursor-following light, then <b>one</b> accent ring - the same three passes, in the same order,
    *       that {@code HomeMainMenuScreen.drawPillBox} makes for a home-screen pill.</li>
    *   <li>Both inner-line weights of the underlying primitive ({@code topHighlightAlpha},
    *       {@code innerBottomAlpha}) are passed as {@code 0}, so this surface cannot carry the lit top edge
    *       or the inner bottom glow that used to hug the top and bottom of every option. That call is the
    *       whole reason this no longer goes through {@code GlassRenderer.drawInteractiveSurface}, which
    *       hard-codes {@code innerBottomAlpha = 0.06F} and therefore could never drop the bottom line.</li>
    *   <li>The accent comes from {@link UiTheme#accentColor()}, i.e. the client colour, and reaches only the
    *       ring and the cursor light.</li>
    *   <li>Motion uses {@link UiTheme#smoothState(float, float)} / {@link UiTheme#smoothPress(float,
    *       boolean)}: the one hover curve and press detent every other control obeys, so the menus settle
    *       at the same rhythm as the ClickGUI instead of the old 100 ms scale pop.</li>
    *   <li>The label is drawn with the client's Inter face (the bitmap font was the last stock-looking
    *       thing in these menus) and scaled down when a stock label is wider than the button, so no label
    *       is ever truncated. It rests on the menu's label colour and <b>does not react to hover</b>: the
    *       ring and the light are the button's only answers to the pointer.</li>
    * </ul>
    *
    * <p>Screens that contain these buttons must advance {@link UiAnimation#beginFrame()} once per frame
    * (the ClickGUI does it in its own {@code drawScreen}, and {@code GuiScreen.drawBackground} now does it
    * for every stock screen that draws the menu backdrop - {@code GuiScreen.drawMenuScreenBackground}, which
     * {@link GuiScreen#usesMenuBackdrop()} gates, so a screen outside that list and without a menu ancestor
     * still has no clock and its glass samples a capture that never invalidates; the menus call it at the top of their
    * {@code drawScreen} for exactly that reason.</p>
    */
   private void drawGlassButton(Minecraft mc, int mouseX, int mouseY) {
      float x = (float)this.xPosition;
      float y = (float)this.yPosition;
      float w = (float)this.width;
      float h = (float)this.height;
      int accent = UiTheme.accentColor();

      this.glassHoverAnim = UiTheme.smoothState(this.glassHoverAnim, this.enabled && this.hovered ? 1.0F : 0.0F);
      this.glassPressAnim = UiTheme.smoothPress(this.glassPressAnim, this.enabled && this.hovered && Mouse.isButtonDown(0));
      float hovT = this.glassHoverAnim;
      float prsT = this.glassPressAnim;
      // The ClickGUI's corner family (cards 11, controls 9), capped at half the height so a stock 20 px
      // row reads as a pill rather than as a rounded rectangle. "RoundedButtons off" still means square;
      // RoundFactor no longer shapes these buttons - the shape is a design-system decision now, and the
      // old 1..20 px knob produced corners that belonged to neither the cards nor the pills.
      float radius = ClientManagers.moduleManager.clientSettings.roundedButtons.getValue()
         ? MenuScreenSkin.optionRadius(w, h)
         : 0.0F;

      // The option surface, in three passes and three only: the shared glass material inside the silhouette,
      // the menu veil plus the cursor light (both inner-line weights at zero - no catchlight, no bottom glow),
      // and one accent hairline around the same outline. No hover halo is drawn: the menu's own pill has none,
      // and the ring plus the light are the option's whole answer to the pointer.
      MenuScreenSkin.drawOptionSurface(
         x,
         y,
         w,
         h,
         radius,
         hovT,
         prsT,
         this.enabled,
         (float)mouseX,
         (float)mouseY,
         accent
      );

      if (this.primaryAccent && this.enabled) {
         GlassRenderer.drawRoundedGradientBoxHorizontal(
            x + 1.0F,
            y + 1.0F,
            w - 2.0F,
            h - 2.0F,
            Math.max(0.0F, radius - 1.0F),
            UiTheme.withAlpha(accent, Math.round(52.0F + 28.0F * hovT)),
            UiTheme.withAlpha(accent, Math.round(16.0F + 18.0F * hovT)),
            1.0F
         );
         GlassRenderer.drawRoundedOutline(x, y, w, h, radius, 1.0F, UiTheme.withAlpha(accent, Math.round(160.0F + 75.0F * hovT)));
      }

      this.mouseDragged(mc, mouseX, mouseY);

      InterFontUtil font = FontManager.uiBody();
      String label = this.displayString == null ? "" : this.displayString;
      int fg = this.enabled ? (this.primaryAccent ? 0xFFF8FAFC : MenuScreenSkin.LABEL_COLOR) : UiTheme.disabledTextColor();
      if (font == null) {
         drawCenteredString(mc.fontRendererObj, label, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, fg);
         return;
      }

      LucideIcon icon = this.resolveEffectiveIcon(label);
      boolean showIcon = icon != null && icon.isAvailable() && (w >= 48.0F || label.isEmpty());
      float iconSize = 10.5F;
      float iconGap = 5.0F;
      float textW = font.getStringWidthFormatted(label);
      float padSide = Math.max(8.0F, h * 0.45F);
      float contentW = textW + (showIcon ? (label.isEmpty() ? iconSize : iconSize + iconGap) : 0.0F);
      float availableW = Math.max(20.0F, w - padSide * 2.0F);
      float labelScale = contentW > availableW ? availableW / contentW : 1.0F;
      float textY = y + (h - font.getFontHeight()) * 0.5F + prsT * 0.4F;
      float cx = x + w * 0.5F;
      float cy = y + h * 0.5F + prsT * 0.4F;

      if (showIcon) {
         float scaledW = contentW * labelScale;
         float startX = cx - scaledW * 0.5F;
         int iconColor = this.enabled
            ? (this.primaryAccent ? UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.45F) : UiTheme.lerpArgb(0xFFB8B0D0, 0xFFF3E8FF, hovT))
            : UiTheme.disabledTextColor();
         float iconY = y + (h - iconSize) * 0.5F + prsT * 0.4F;
         icon.draw(startX, iconY, iconSize, iconColor, false);

         if (!label.isEmpty()) {
            float textStartX = startX + iconSize * labelScale + iconGap * labelScale;
            if (labelScale < 1.0F) {
               GL11.glPushMatrix();
               GL11.glTranslatef(textStartX, cy, 0.0F);
               GL11.glScalef(labelScale, labelScale, 1.0F);
               GL11.glTranslatef(-textStartX, -cy, 0.0F);
               font.drawStringFormatted(label, (double)textStartX, (double)textY, fg, false);
               GL11.glPopMatrix();
            } else {
               font.drawStringFormatted(label, (double)textStartX, (double)textY, fg, false);
            }
         }
         return;
      }

      if (labelScale < 1.0F) {
         GL11.glPushMatrix();
         GL11.glTranslatef(cx, cy, 0.0F);
         GL11.glScalef(labelScale, labelScale, 1.0F);
         GL11.glTranslatef(-cx, -cy, 0.0F);
         font.drawCenteredStringFormattedInBox(label, (double)x, (double)(y + prsT * 0.4F), (double)w, (double)h, fg);
         GL11.glPopMatrix();
      } else {
         font.drawCenteredStringFormattedInBox(label, (double)x, (double)(y + prsT * 0.4F), (double)w, (double)h, fg);
      }
   }

   private LucideIcon resolveEffectiveIcon(String label) {
      if (this.buttonIcon != null) {
         return this.buttonIcon;
      }
      if (this instanceof GuiOptionSlider || this instanceof GuiSlider || label == null || label.isEmpty()) {
         return null;
      }
      String lower = label.toLowerCase(Locale.ROOT);
      if (lower.equals("done") || lower.equals("apply") || lower.contains("save and quit")) {
         return lower.contains("quit") ? LucideIcon.LOGOUT : LucideIcon.CHECK;
      }
      if (lower.equals("cancel") || lower.equals("back")) {
         return LucideIcon.ARROW_LEFT;
      }
      if (lower.contains("back to game") || lower.contains("play selected")) {
         return LucideIcon.PLAYER_PLAY;
      }
      if (lower.contains("disconnect")) {
         return LucideIcon.LOGOUT;
      }
      if (lower.contains("options") || lower.contains("other")) {
         return LucideIcon.SETTINGS;
      }
      if (lower.contains("video")) {
         return LucideIcon.DEVICE_DESKTOP;
      }
      if (lower.contains("shader")) {
         return LucideIcon.SPARKLES;
      }
      if (lower.contains("quality")) {
         return LucideIcon.SLIDERS_HORIZONTAL;
      }
      if (lower.contains("detail")) {
         return LucideIcon.LIST;
      }
      if (lower.contains("performance")) {
         return LucideIcon.ZAP;
      }
      if (lower.contains("animation")) {
         return LucideIcon.WIND;
      }
      if (lower.contains("control")) {
         return LucideIcon.KEYBOARD;
      }
      if (lower.contains("language") || lower.contains("unicode")) {
         return LucideIcon.GLOBE;
      }
      if (lower.contains("sound") || lower.contains("music") || lower.contains("lan")) {
         return LucideIcon.BROADCAST;
      }
      if (lower.contains("chat")) {
         return LucideIcon.USERS_GROUP;
      }
      if (lower.contains("resource pack")) {
         return lower.contains("folder") ? LucideIcon.FOLDER : LucideIcon.BOXES;
      }
      if (lower.contains("skin")) {
         return LucideIcon.SHIRT;
      }
      if (lower.contains("super secret")) {
         return LucideIcon.EYE;
      }
      if (lower.contains("achievement")) {
         return LucideIcon.SPARKLES;
      }
      if (lower.contains("statistic")) {
         return LucideIcon.ACTIVITY;
      }
      if (lower.contains("direct connect")) {
         return LucideIcon.LINK;
      }
      if (lower.contains("join server")) {
         return LucideIcon.LOGIN;
      }
      if (lower.contains("create new") || lower.contains("add server")) {
         return LucideIcon.PLUS;
      }
      if (lower.contains("rename") || lower.equals("edit")) {
         return LucideIcon.FILE_TEXT;
      }
      if (lower.equals("delete")) {
         return LucideIcon.TRASH_2;
      }
      if (lower.contains("re-create") || lower.equals("refresh")) {
         return LucideIcon.REFRESH_CW;
      }
      if (lower.contains("mark banned")) {
         return LucideIcon.ALERT_TRIANGLE;
      }
      return null;
   }

   static {
      String str = "textures/gui/widgets.png";
      buttonTextures = new ResourceLocation(str);
   }

   public boolean isMouseOver() {
      return this.hovered;
   }

   public GuiButton(int n, int n2, int n3, int n4, int n5, String str, boolean flag) {
      this.width = 200;
      this.height = 20;
      this.enabled = true;
      this.visible = true;
      this.id = n;
      this.xPosition = n2;
      this.yPosition = n3;
      this.width = n4;
      this.height = n5;
      this.displayString = str;
      this.forceVanillaStyle = flag;
   }

   public void playPressSound(SoundHandler soundHandler) {
      ClientSettings.playButtonClickSound();
   }

   public int getHeight() {
      return this.height;
   }

   public GuiButton(int n, int n2, int n3, int n4, int n5, String str) {
      this(n, n2, n3, n4, n5, str, false);
   }

   public void mouseDragged(Minecraft mc, int n, int n2) {
   }

   public void setHeight(int n) {
      this.height = n;
   }

   public boolean mousePressed(Minecraft mc, int n, int n2) {
      return this.enabled && this.visible && n >= this.xPosition && n2 >= this.yPosition && n < this.xPosition + this.width && n2 < this.yPosition + this.height;
   }

   public void setWidth(int n) {
      this.width = n;
   }

   public void mouseReleased(int n, int n2) {
   }

   public int getHoverState(boolean flag) {
      byte b = 1;
      if (!this.enabled) {
         b = 0;
      } else if (flag) {
         b = 2;
      }

      return b;
   }

}
