package net.augustus.ui;

import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.UiAnimation;
import net.augustus.utils.render.BlurShadowManager;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

public class AugustusGuiScreen extends GuiScreen {
   private final List passwordFields;
   private boolean renderDefaultBackground;
   private final List textFields;
   private GuiScreen parentScreen;
   private int nextControlId;
   private final Map buttonClickHandlers = new HashMap();

   public static void disableScissor() {
      GL11.glDisable(3089);
   }

   public void navigateBack() {
      this.mc.displayGuiScreen(this.getParentScreen());
   }

   public void drawCenteredString(String str, float f, float f2) {
      this.drawCenteredString(str, f, f2, (byte)-1);
   }

   public void disableDefaultBackground() {
      this.renderDefaultBackground = false;
   }

   @Override
   public void onResize(Minecraft mc2, int n, int n2) {
      super.onResize(mc2, n, n2);
   }

   private void onBackButtonClicked(GuiButton button) {
      this.navigateBack();
   }

   public AugustusGuiScreen() {
      this.textFields = new ArrayList();
      this.passwordFields = new ArrayList();
      this.nextControlId = 0;
      this.renderDefaultBackground = true;
   }

   public AugustusGuiScreen(GuiScreen screen) {
      super(screen);
      this.textFields = new ArrayList();
      this.passwordFields = new ArrayList();
      this.nextControlId = 0;
      this.renderDefaultBackground = true;
   }

   public GuiButton addButton(int n, int n2, int n3, int n4, String str, GuiButtonClickHandler guiButtonClickHandler) {
      int nextControlId2 = this.nextControlId;
      GuiButton button = new GuiButton(nextControlId2, n, n2, n3, n4, str);
      this.buttonList.add(button);
      this.buttonClickHandlers.put(nextControlId2, guiButtonClickHandler);
      this.nextControlId++;
      return button;
   }

   public void initContent() {
   }

   public int getCenterY() {
      return this.height / 2;
   }

   public static void enableScissor(int n, int n2, int n3, int n4) {
      int n5 = n3 - n;
      int n6 = n4 - n2;
      ScaledResolution sr = new ScaledResolution(Minecraft.getMinecraft());
      int scaleFactor = sr.getScaleFactor();
      GL11.glScissor(n * scaleFactor, (sr.getScaledHeight() - n4) * scaleFactor, n5 * scaleFactor, n6 * scaleFactor);
      GL11.glEnable(3089);
   }

   public int getCenterX() {
      return this.width / 2;
   }

   public void drawTooltip(List list, int n, int n2) {
      this.drawHoveringText(list, n + 5, n2 + 5);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (keyCode == 1 && this.getParentScreen() != null) {
         this.navigateBack();
      } else {
         super.keyTyped(typedChar, keyCode);

         for (GuiTextField guiTextField : (Iterable<GuiTextField>)(this.textFields)) {
            guiTextField.textboxKeyTyped(typedChar, keyCode);
         }

         for (GuiPasswordField guiPasswordField : (Iterable<GuiPasswordField>)(this.passwordFields)) {
            guiPasswordField.textboxKeyTyped(typedChar, keyCode);
         }
      }
   }

   /**
    * Text of the redesigned menus (main menu corners, tab title, status line, credits, changelog, account
    * and proxy forms) is painted with the client's Inter face rather than the 8 px bitmap font, which is
    * what made a screen that already used Inter for its buttons and cards still look half-stock.
    *
    * <p>Width and painting must come from the same face: {@link #getStringWidth(String)} is the Inter width
    * now, so the right-aligned corner texts and the centred tab titles stay exactly centred. The bitmap
    * path survives only as a fallback for the frames before {@link FontManager#initFonts()} has completed.
    * The old 3/5x {@code glScaled} of the bitmap glyphs (the giant "Augustus" on the main menu) is now the
    * same face drawn at scale * size, so a scaled title is sharp instead of blocky.</p>
    */
   private static InterFontUtil uiFont() {
      return FontManager.body();
   }

   /** Line box of {@link #uiFont()}; the stock 9 px bitmap height before Inter exists. */
   public float getFontHeight() {
      return FontManager.bodyHeight();
   }

   public void drawCenteredString(String str, float f, float f2, int n) {
      InterFontUtil font = uiFont();
      if (font == null) {
         Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(str, f - (float)Minecraft.getMinecraft().fontRendererObj.getStringWidth(str) / 2.0F, f2, n);
         return;
      }
      font.drawStringWithShadow(str, (double)(f - font.getStringWidth(str) / 2.0F), (double)f2, n);
   }

   public void drawString(String str, float f, float f2, int n) {
      InterFontUtil font = uiFont();
      if (font == null) {
         Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(str, f, f2, n);
         return;
      }
      font.drawStringWithShadow(str, (double)f, (double)f2, n);
   }

   public void openScreen(GuiScreen screen) {
      Minecraft.getMinecraft().displayGuiScreen(screen);
   }

   public void drawString(String str, float f, float f2) {
      this.drawString(str, f, f2, (byte)-1);
   }

   public void setParentScreen(GuiScreen screen) {
      this.parentScreen = screen;
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);

      for (GuiTextField guiTextField : (Iterable<GuiTextField>)(this.textFields)) {
         guiTextField.mouseClicked(mouseX, mouseY, mouseButton);
      }

      for (GuiPasswordField guiPasswordField : (Iterable<GuiPasswordField>)(this.passwordFields)) {
         guiPasswordField.mouseClicked(mouseX, mouseY, mouseButton);
      }
   }

   public void drawCenteredScaledString(String str, float f, float f2, float f3) {
      this.drawCenteredScaledString(str, f, f2, f3, uiFont());
   }

   /**
    * Centre of a {@code f3}-scaled line, drawn with a caller-chosen face so brand titles can ask for Inter
    * Bold (the face the splash art uses for "Augustus") while section headings keep the body face.
    *
    * <p>The caller's coordinates are in pre-scale space because the bitmap version scaled the matrix; the
    * Inter version multiplies both the origin and the glyph size by {@code f3} instead, which keeps the
    * optical centre identical (the atlas' advance is linear in size) and stays sharp at 3x and 5x.</p>
    */
   public void drawCenteredScaledString(String str, float f, float f2, float f3, InterFontUtil font) {
      if (font == null) {
         this.drawCenteredScaledStringBitmap(str, f, f2, f3);
         return;
      }
      this.drawScaledString(str, f - font.getStringWidth(str) / 2.0F, f2, f3, -1, font);
   }

   public void drawScaledString(String str, float f, float f2, float f3, int n) {
      this.drawScaledString(str, f, f2, f3, n, uiFont());
   }

   public void drawScaledString(String str, float f, float f2, float f3, int n, InterFontUtil font) {
      if (font == null) {
         this.drawScaledStringBitmap(str, f, f2, f3, n);
         return;
      }
      font.drawStringAtSize(str, f * f3, f2 * f3, font.getEffectiveSize() * f3, n, true);
   }

   /**
    * The client name as a menu's brand title: Inter Bold (the face the splash art sets "Augustus" in),
    * horizontally centred on {@code f} and vertically centred on {@code f2} at {@code f3}x.
    *
    * <p>WHY the vertical offset is half the brand face's own height: the callers pass the screen's centre
    * line, and the old bitmap code subtracted half of the 9 px bitmap box. The Inter box is taller, so the
    * same formula with the face that is actually drawn (in pre-scale space - see
    * {@link #drawScaledString(String, float, float, float, int, InterFontUtil)}) is what keeps the title on
    * the centre line instead of hanging below it.</p>
    */
   public void drawBrandTitle(String str, float f, float f2, float f3) {
      InterFontUtil font = FontManager.interBold != null ? FontManager.interBold : FontManager.body();
      if (font == null) {
         this.drawCenteredScaledStringBitmap(str, f, f2 - 4.5F, f3);
         return;
      }
      this.drawScaledString(str, f - font.getStringWidth(str) / 2.0F, f2 - font.getFontHeight() / 2.0F, f3, -1, font);
   }

   private void drawCenteredScaledStringBitmap(String str, float f, float f2, float f3) {
      this.drawScaledStringBitmap(str, f - (float)Minecraft.getMinecraft().fontRendererObj.getStringWidth(str) / 2.0F, f2, f3, (byte)-1);
   }

   private void drawScaledStringBitmap(String str, float f, float f2, float f3, int n) {
      GL11.glPushMatrix();
      GL11.glScalef(f3, f3, f3);
      Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(str, f, f2, n);
      GL11.glPopMatrix();
   }

   public GuiScreen getParentScreen() {
      return this.parentScreen;
   }

   public void drawContent(int n, int n2, float f) {
   }

   @Override
   public void updateScreen() {
      super.updateScreen();

      for (GuiTextField guiTextField : (Iterable<GuiTextField>)(this.textFields)) {
         guiTextField.updateCursorCounter();
      }

      for (GuiPasswordField guiPasswordField : (Iterable<GuiPasswordField>)(this.passwordFields)) {
         guiPasswordField.updateCursorCounter();
      }
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      // Frame clock for the glass buttons this screen hosts (see GuiButton.drawGlassButton): exactly one
      // beginFrame per rendered frame, before any button draws, so hover/press smoothing is real-time.
      UiAnimation.beginFrame();
      if (this.renderDefaultBackground) {
         this.drawDefaultBackground();
         BlurShadowManager.flushRenderQueues(new ScaledResolution(this.mc), true);
      }

      for (GuiTextField guiTextField : (Iterable<GuiTextField>)(this.textFields)) {
         guiTextField.drawTextBox();
      }

      for (GuiPasswordField guiPasswordField : (Iterable<GuiPasswordField>)(this.passwordFields)) {
         guiPasswordField.drawTextBox();
      }

      this.drawContent(mouseX, mouseY, partialTicks);
      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   public GuiPasswordField addPasswordField(int n, int n2, int n3, int n4) {
      int nextControlId2 = this.nextControlId;
      GuiPasswordField guiPasswordField = new GuiPasswordField(nextControlId2, Minecraft.getMinecraft().fontRendererObj, n, n2, n3, n4);
      guiPasswordField.setMaxStringLength((short)1337);
      this.nextControlId++;
      this.passwordFields.add(guiPasswordField);
      return guiPasswordField;
   }

   @Override
   public final void initGui() {
      super.initGui();
      this.buttonClickHandlers.clear();
      this.buttonList.clear();
      this.textFields.clear();
      this.passwordFields.clear();
      if (this.getParentScreen() != null) {
         int n = this.height - 26;
         GuiButtonClickHandler guiButtonClickHandler = button -> this.onBackButtonClicked(button);
         this.addButton((byte)5, n, (byte)20, (byte)20, "<-", guiButtonClickHandler);
      }

      this.initContent();
   }

   public void drawScaledString(String str, float f, float f2, float f3) {
      this.drawScaledString(str, f, f2, f3, (byte)-1);
   }

   public int getStringWidth(String str) {
      InterFontUtil font = uiFont();
      if (font == null) {
         return Minecraft.getMinecraft().fontRendererObj.getStringWidth(str);
      }
      // Rounded for the right-aligned corner lines, which measure in ints; the painting stays float.
      return Math.round(font.getStringWidth(str));
   }

   /**
    * Hint text of an empty field, drawn at the same inset and vertical centre
    * {@code GuiTextField.drawTextBoxWithShadowOption} uses for typed text: the hint sits exactly where the
    * first character will appear, in the same Inter face, so a form is no longer a bitmap-font island on an
    * Inter screen.
    */
   public void drawFieldPlaceholder(String text, int fieldX, int fieldY, int fieldHeight, int color) {
      InterFontUtil font = uiFont();
      if (font == null) {
         Minecraft.getMinecraft().fontRendererObj.drawStringWithShadow(text, (float)(fieldX + 4), (float)(fieldY + (fieldHeight - 8) / 2), color);
         return;
      }
      font.drawStringWithShadow(
         text,
         (double)((float)fieldX + 4.0F),
         (double)((float)fieldY + ((float)fieldHeight - font.getFontHeight()) / 2.0F),
         color
      );
   }

   public GuiTextField addTextField(int n, int n2, int n3, int n4) {
      int nextControlId2 = this.nextControlId;
      GuiTextField guiTextField = new GuiTextField(nextControlId2, Minecraft.getMinecraft().fontRendererObj, n, n2, n3, n4);
      guiTextField.setMaxStringLength(1337);
      this.nextControlId++;
      this.textFields.add(guiTextField);
      return guiTextField;
   }

   @Override
   public void actionPerformed(GuiButton button) throws IOException {
      super.actionPerformed(button);

      try {
         ((GuiButtonClickHandler)this.buttonClickHandlers.get(button.id)).onClick(button);
      } catch (Exception e) {
      }
   }

}
