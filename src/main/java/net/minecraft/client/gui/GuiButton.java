package net.minecraft.client.gui;

import java.awt.Color;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.ClientManagers;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
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
   /** Owned hover/press envelopes for the glass rendering (per button: no shared-key collisions). */
   private float glassHoverAnim = 0.0F;
   private float glassPressAnim = 0.0F;

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
    * Draws this button as a ClickGUI glass control instead of the flat client-coloured plate.
    *
    * <p>What it draws, and why every piece comes from the ClickGUI's own pipeline:</p>
    * <ul>
    *   <li>{@code GlassRenderer.drawInteractiveSurface} paints the shared glass <em>material</em>
    *       (the frame's blurred backdrop capture + the level's tint mix), then the control's state
    *       layer, the overhead sheen, the cursor-following light and the press detent - the exact call
    *       the ClickGUI uses for its buttons, pills, tabs and segmented controls. So a menu button and a
    *       ClickGUI button are literally the same object, not two look-alikes.</li>
    *   <li>The accent comes from {@link UiTheme#accentColor()}, i.e. the client colour, so hover glow,
    *       rim and label all follow whatever the user set - the old flat fill did this too, but now it is
    *       the accent <em>inside</em> the glass rather than an opaque blue slab over it.</li>
    *   <li>Motion uses {@link UiTheme#smoothState(float, float)} / {@link UiTheme#smoothPress(float,
    *       boolean)}: the one hover curve and press detent every other control obeys, so the menus settle
    *       at the same rhythm as the ClickGUI instead of the old 100 ms scale pop.</li>
    *   <li>The label is drawn with the client's Inter face (the bitmap font was the last stock-looking
    *       thing in these menus) and scaled down when a stock label is wider than the button, so no label
    *       is ever truncated.</li>
    * </ul>
    *
    * <p>Screens that contain these buttons must advance {@link UiAnimation#beginFrame()} once per frame
    * (the ClickGUI does it in its own {@code drawScreen}); the menus call it at the top of their
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
         ? Math.min(h * 0.5F, UiTheme.CARD_RADIUS)
         : 0.0F;

      if (this.enabled && hovT > 0.02F) {
         // The accent halo the ClickGUI casts under a hovered pill or card.
         GlassRenderer.drawSoftShadow(x, y, w, h, radius, 8.0F + 2.0F * hovT, 1.0F, UiTheme.withAlpha(accent, Math.round(70.0F * hovT)));
      }

      int fill = this.enabled ? UiTheme.glassControlFillArgb(hovT, 0.0F, accent) : UiTheme.glassDisabledFillArgb();
      int bottomFill = this.enabled ? UiTheme.lerpArgb(fill, 0xFF0A0C12, 0.20F) : fill;
      int border = this.enabled
         ? UiTheme.lerpArgb(0x1AFFFFFF, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), hovT * 0.7F)
         : UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F);
      GlassRenderer.drawInteractiveSurface(
         x,
         y,
         w,
         h,
         radius,
         fill,
         bottomFill,
         border,
         UiTheme.BORDER_THICKNESS,
         UiTheme.glassTopHighlightAlpha(Math.max(0.30F, hovT)),
         hovT,
         prsT,
         this.enabled,
         (float)mouseX,
         (float)mouseY,
         accent
      );

      this.mouseDragged(mc, mouseX, mouseY);

      // Label: the client's Inter face, centred in the box exactly as the bitmap-font call it replaces.
      // WHY it is scaled instead of trimmed: the stock labels are authored for an 8 px bitmap font, so
      // "Can't download in single player" would lose its meaning at full size inside a 98 px button.
      // Scaling about the box centre keeps the original glyphs (no ellipsis, no clipping) and keeps one
      // type size across a menu - a shrunken label reads as intentional, a truncated one reads as a bug.
      //
      // WHY the '§' branch is gone: labels such as "Difficulty: §cHard", "Remove invalid: §cFalse" or
      // "§cFernan's Altshop" used to be bounced back to the bitmap FontRenderer (the SDF atlas has no
      // section-sign glyph), which is precisely why a few buttons on an otherwise Inter-only screen still
      // looked pixelated. drawStringFormatted() parses the codes and paints every run with Inter, so the
      // face is uniform and the colours the strings asked for survive.
      InterFontUtil font = FontManager.body();
      String label = this.displayString == null ? "" : this.displayString;
      int fg = this.enabled
         ? UiTheme.lerpArgb(UiTheme.primaryTextColor(), 0xFFFFFFFF, UiAnimation.clamp01(Math.max(hovT, prsT)))
         : UiTheme.disabledTextColor();
      if (font == null) {
         // Only reachable before FontManager.initFonts() has run (e.g. a screen built during startup).
         drawCenteredString(mc.fontRendererObj, label, this.xPosition + this.width / 2, this.yPosition + (this.height - 8) / 2, fg);
         return;
      }
      float textW = font.getStringWidthFormatted(label);
      // Padding is a share of the height so a 20 px row and a 26 px row keep the same optical inset.
      float availableW = Math.max(24.0F, w - Math.max(12.0F, h * 0.7F));
      float labelScale = textW > availableW ? availableW / textW : 1.0F;
      if (labelScale < 1.0F) {
         float cx = x + w * 0.5F;
         float cy = y + h * 0.5F + prsT * 0.4F;
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
