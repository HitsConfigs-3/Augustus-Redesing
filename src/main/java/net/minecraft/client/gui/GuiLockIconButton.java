package net.minecraft.client.gui;

import net.augustus.ClientManagers;
import net.augustus.modules.Module;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.input.Mouse;

public class GuiLockIconButton extends GuiButton {
   private boolean locked = false;
   private float lockHoverAnim = 0.0F;
   private float lockPressAnim = 0.0F;

   public void setLocked(boolean flag) {
      this.locked = flag;
   }

   public boolean isLocked() {
      return this.locked;
   }

   @Override
   public void drawButton(Minecraft mc, int n, int n2) {
      if (this.visible) {
         this.hovered = n >= this.xPosition && n2 >= this.yPosition && n < this.xPosition + this.width && n2 < this.yPosition + this.height;
         if (!((Module)ClientManagers.moduleManager.invisMode).isToggled() && !this.forceVanillaStyle) {
            this.lockHoverAnim = UiTheme.smoothState(this.lockHoverAnim, this.enabled && this.hovered ? 1.0F : 0.0F);
            this.lockPressAnim = UiTheme.smoothPress(this.lockPressAnim, this.enabled && this.hovered && Mouse.isButtonDown(0));
            float radius = ClientManagers.moduleManager.clientSettings.roundedButtons.getValue()
               ? MenuScreenSkin.optionRadius((float)this.width, (float)this.height)
               : 0.0F;
            MenuScreenSkin.drawOptionSurface(
               (float)this.xPosition,
               (float)this.yPosition,
               (float)this.width,
               (float)this.height,
               radius,
               this.lockHoverAnim,
               this.lockPressAnim,
               this.enabled,
               (float)n,
               (float)n2,
               UiTheme.accentColor()
            );
            LucideIcon icon = this.locked ? LucideIcon.LOCK : LucideIcon.CHECK;
            int iconColor = this.locked ? 0xFFF59E0B : (this.enabled ? MenuScreenSkin.LABEL_COLOR : UiTheme.disabledTextColor());
            icon.drawCentered((float)this.xPosition + (float)this.width * 0.5F, (float)this.yPosition + (float)this.height * 0.5F, 11.0F, iconColor);
            return;
         }
         mc.getTextureManager().bindTexture(GuiButton.buttonTextures);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         GuiLockIconButton.Icon icon;
         if (this.locked) {
            if (!this.enabled) {
               icon = GuiLockIconButton.Icon.LOCKED_DISABLED;
            } else if (this.hovered) {
               icon = GuiLockIconButton.Icon.LOCKED_HOVER;
            } else {
               icon = GuiLockIconButton.Icon.LOCKED;
            }
         } else if (!this.enabled) {
            icon = GuiLockIconButton.Icon.UNLOCKED_DISABLED;
         } else if (this.hovered) {
            icon = GuiLockIconButton.Icon.UNLOCKED_HOVER;
         } else {
            icon = GuiLockIconButton.Icon.UNLOCKED;
         }

         this.drawTexturedModalRect(this.xPosition, this.yPosition, icon.getIconX(), icon.getIconY(), this.width, this.height);
      }
   }

   public GuiLockIconButton(int n, int n2, int n3) {
      super(n, n2, n3, 20, 20, "");
   }


   public enum Icon {
      LOCKED(0, 146),
      LOCKED_HOVER(0, 166),
      LOCKED_DISABLED(0, 186),
      UNLOCKED(20, 146),
      UNLOCKED_HOVER(20, 166),
      UNLOCKED_DISABLED(20, 186);

   private final int iconX;
      private final int iconY;
   public int getIconX() {
         return this.iconX;
      }

      private Icon(int n, int n2) {
         this.iconX = n;
         this.iconY = n2;
      }

      public int getIconY() {
         return this.iconY;
      }
   }
}
