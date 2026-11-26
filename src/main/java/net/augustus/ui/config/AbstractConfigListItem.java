package net.augustus.ui.config;

import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.clickgui.MainClickGuiWindow;
import net.augustus.font.CustomFontUtil;
import net.augustus.render.RenderUtil;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.TimeHelper;
import java.io.IOException;

public abstract class AbstractConfigListItem {
   private final String author;
   private final TimeHelper loadCooldownTimer;
   private int x;
   private int tooltipX;
   private int cachedNameWidth;
   private String updatedTime;
   private final TimeHelper hoverTimer;
   private final TimeHelper doubleClickTimer;
   private int lastMouseX;
   private int tooltipY;
   private int width;
   private CustomFontUtil fontRenderer;
   private int lastMouseY;
   private final boolean editable;
   private boolean rendered;
   private boolean hovered;
   private int y;
   private String updatedDate;
   private String configName;
   private int height;

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY this row type needs its own fade: the config row's card behind it is drawn by
    * {@code ConfigSystemPanel}, but the row's own name label is painted here, so without this it would hang
    * at full brightness over a fading card for the whole ~290 ms close. Only alpha is scaled - the cached
    * name width and the hit-test rectangle are untouched.
    *
    * <p>Wrapped exactly once, at the draw; the value is never stored in a field, so it cannot leak a stale
    * envelope into a later frame or another screen.
    */
   private static int faded(int argb) {
      // Settled overlay (everything except the ~288 ms open/close transition): skip the arithmetic.
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public AbstractConfigListItem(String configName, String author, String updatedDate, String updatedTime, boolean editable) {
      this.configName = configName;
      this.author = author;
      this.updatedDate = updatedDate;
      this.updatedTime = updatedTime;
      this.editable = editable;
      this.doubleClickTimer = new TimeHelper();
      this.loadCooldownTimer = new TimeHelper();
      this.hoverTimer = new TimeHelper();
      this.cachedNameWidth = -1;
   }

   public void renderTooltip() {
      if (this.rendered && this.updatedDate != null && this.updatedTime != null && !this.updatedDate.isEmpty() && !this.updatedTime.isEmpty()) {
         boolean isHovered = RenderUtil.isHovered(
            (float)(this.x - 5), (float)this.y, this.fontRenderer.getStringWidth(this.configName) + 10.0F, (float)(this.height + 2), this.lastMouseX, this.lastMouseY
         );
         if (isHovered) {
            if (!this.hovered) {
               this.hoverTimer.reset();
               this.tooltipX = -1337;
               this.tooltipY = -1337;
            }

            if (this.hoverTimer.reached(500L)) {
               if (this.tooltipX == -1337 && this.tooltipY == -1337) {
                  this.tooltipX = this.lastMouseX;
                  this.tooltipY = this.lastMouseY;
               }

               String text = "Updated: " + this.updatedDate + " " + this.updatedTime + " by " + this.author;
               MainClickGuiWindow.drawTooltip(this.fontRenderer, text, this.tooltipX, this.tooltipY);
            }
         }

         this.hovered = isHovered;
      }
   }

   public CustomFontUtil getFontRenderer() {
      return this.fontRenderer;
   }

   public abstract void loadConfig();

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.getFontRenderer() != null) {
         if (RenderUtil.isHovered((float)(this.getX() - 5), (float)this.getY(), (float)((int)(this.getFontRenderer().getStringWidth(this.getConfigName()) + 10.0F)), (float)(this.getHeight() + 2), mouseX, mouseY)) {
            if (!this.doubleClickTimer.reached(500L)) {
               if (this.loadCooldownTimer.reached(1000L)) {
                  this.loadCooldownTimer.reset();
                  this.loadConfig();
               }
            }
            this.doubleClickTimer.reset();
         }
      }
   }

   public int getY() {
      return this.y;
   }

   public int getX() {
      return this.x;
   }

   public boolean isAuthor(String name) {
      return this.author.equals(name);
   }

   public String getConfigName() {
      return this.configName;
   }

   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
   }

   public int getWidth() {
      return this.width;
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
   }

   public int getHeight() {
      return this.height;
   }

   public boolean isEditable() {
      return this.editable;
   }

   public void keyTyped(char typedChar, int keyCode) throws IOException {
   }

   public void renderItem(int x, int y, int width, int height, int mouseX, int mouseY, CustomFontUtil fontRenderer, boolean selected) {
      this.x = x;
      this.y = y;
      this.lastMouseX = mouseX;
      this.lastMouseY = mouseY;
      this.width = width;
      this.height = height;
      this.fontRenderer = fontRenderer;
      if (this.cachedNameWidth == -1) {
         this.cachedNameWidth = (int)fontRenderer.getStringWidth(this.configName);
      }

      boolean isHovered = RenderUtil.isHovered((float)(x - 5), (float)y, (float)(this.cachedNameWidth + 10), (float)(height + 2), mouseX, mouseY);
      int color = selected || isHovered ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor();
      fontRenderer.drawString(this.configName, (double)x, (double)y, faded(color));
      this.rendered = true;
   }
}
