package net.augustus.ui;

import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiTheme;
import net.minecraft.client.gui.GuiPageButtonList;
import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.MathHelper;

public class GuiPasswordField extends Gui {
   private static final int SELECTION_ALPHA = 110;
   private boolean isFocused;
   private boolean canLoseFocus;
   private boolean visible;
   private boolean enableBackgroundDrawing;
   private final int height;
   private boolean isEnabled;
   private int disabledColor;
   public int yPosition;
   private int lineScrollOffset;
   private final FontRenderer fontRenderer;
   private final int id;
   private int selectionEnd;
   private int enabledColor;
   private int cursorCounter;
   private String rawPasswordText;
   private Predicate validator;
   private int maxStringLength;
   private GuiPageButtonList.GuiResponder guiResponder;
   private final int width;
   private int cursorPosition;
   public int xPosition;
   private String text = "";
   private InterFontUtil customFont;
   private float wellHoverAnim = 0.0F;
   private float wellFocusAnim = 0.0F;

   /**
    * The client's Inter face, matching {@code GuiTextField}: password dots and caret placement must use
    * the same metrics the glyphs are painted with.
    */
   private static InterFontUtil passwordFont() {
      return FontManager.body();
   }

   public void setCustomFont(InterFontUtil font) {
      this.customFont = font;
   }

   public InterFontUtil getCustomFont() {
      return this.customFont;
   }

   private InterFontUtil resolveFont() {
      return this.customFont != null ? this.customFont : passwordFont();
   }

   private int horizontalPadding() {
      return this.customFont != null ? 8 : 4;
   }

   public String getText() {
      return this.text;
   }

   public void setGuiResponder(GuiPageButtonList.GuiResponder guiResponder2) {
      this.guiResponder = guiResponder2;
   }

   public void setCursorPositionEnd() {
      this.setCursorPosition(this.text.length());
   }

   public void setFocused(boolean focused) {
      if (focused && !this.isFocused) {
         this.cursorCounter = 0;
      }

      this.isFocused = focused;
   }

   public void setCursorPosition(int n) {
      this.cursorPosition = n;
      int length = this.text.length();
      this.cursorPosition = MathHelper.clamp_int(this.cursorPosition, 0, length);
      this.setSelectionPos(this.cursorPosition);
   }

   public String getSelectedText() {
      int n = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
      int n2 = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
      return this.text.substring(n, n2);
   }

   public int getNthWordFromPos(int n, int n2, boolean flag) {
      int n3 = n2;
      boolean flag2 = n < 0;
      int n4 = Math.abs(n);

      for (int i = 0; i < n4; i++) {
         if (!flag2) {
            int length = this.text.length();
            n3 = this.text.indexOf(32, n3);
            if (n3 == -1) {
               n3 = length;
            } else {
               while (flag && n3 < length && this.text.charAt(n3) == ' ') {
                  n3++;
               }
            }
         } else {
            while (flag && n3 > 0 && this.text.charAt(n3 - 1) == ' ') {
               n3--;
            }

            while (n3 > 0 && this.text.charAt(n3 - 1) != ' ') {
               n3--;
            }
         }
      }

      return n3;
   }

   public void writeText(String str) {
      String str2 = "";
      String str3 = ChatAllowedCharacters.filterAllowedCharacters(str);
      int n = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
      int n2 = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
      int n3 = this.maxStringLength - this.text.length() - (n - n2);
      int n4 = 0;
      if (this.text.length() > 0) {
         str2 = str2 + this.text.substring(0, n);
      }

      if (n3 < str3.length()) {
         str2 = str2 + str3.substring(0, n3);
         n4 = n3;
      } else {
         str2 = str2 + str3;
         n4 = str3.length();
      }

      if (this.text.length() > 0 && n2 < this.text.length()) {
         str2 = str2 + this.text.substring(n2);
      }

      if (this.validator.apply(str2)) {
         this.text = str2;
         this.rawPasswordText = str2;
         this.moveCursorBy(n - this.selectionEnd + n4);
         if (this.guiResponder != null) {
            this.guiResponder.setEntryValue(this.id, this.text);
         }
      }
   }

   public boolean textboxKeyTyped(char ch, int n) {
      if (!this.isFocused) {
         return false;
      } else if (GuiScreen.isKeyComboCtrlA(n)) {
         this.setCursorPositionEnd();
         this.setSelectionPos((byte)0);
         return true;
      } else if (GuiScreen.isKeyComboCtrlC(n)) {
         GuiScreen.setClipboardString(this.getSelectedText());
         return true;
      } else if (GuiScreen.isKeyComboCtrlV(n)) {
         if (this.isEnabled) {
            this.writeText(GuiScreen.getClipboardString());
         }

         return true;
      } else if (GuiScreen.isKeyComboCtrlX(n)) {
         GuiScreen.setClipboardString(this.getSelectedText());
         if (this.isEnabled) {
            this.writeText("");
         }

         return true;
      } else {
         switch (n) {
            case 14:
               if (GuiScreen.isCtrlKeyDown()) {
                  if (this.isEnabled) {
                     this.deleteWords((byte)-1);
                  }
               } else if (this.isEnabled) {
                  this.deleteFromCursor((byte)-1);
               }

               return true;
            case 199:
               if (GuiScreen.isShiftKeyDown()) {
                  this.setSelectionPos((byte)0);
               } else {
                  this.setCursorPositionZero();
               }

               return true;
            case 203:
               if (GuiScreen.isShiftKeyDown()) {
                  if (GuiScreen.isCtrlKeyDown()) {
                     this.setSelectionPos(this.getNthWordFromPos((byte)-1, this.getSelectionEnd()));
                  } else {
                     this.setSelectionPos(this.getSelectionEnd() - 1);
                  }
               } else if (GuiScreen.isCtrlKeyDown()) {
                  this.setCursorPosition(this.getNthWordFromCursor((byte)-1));
               } else {
                  this.moveCursorBy((byte)-1);
               }

               return true;
            case 205:
               if (GuiScreen.isShiftKeyDown()) {
                  if (GuiScreen.isCtrlKeyDown()) {
                     this.setSelectionPos(this.getNthWordFromPos((byte)1, this.getSelectionEnd()));
                  } else {
                     this.setSelectionPos(this.getSelectionEnd() + 1);
                  }
               } else if (GuiScreen.isCtrlKeyDown()) {
                  this.setCursorPosition(this.getNthWordFromCursor((byte)1));
               } else {
                  this.moveCursorBy((byte)1);
               }

               return true;
            case 207:
               if (GuiScreen.isShiftKeyDown()) {
                  this.setSelectionPos(this.text.length());
               } else {
                  this.setCursorPositionEnd();
               }

               return true;
            case 211:
               if (GuiScreen.isCtrlKeyDown()) {
                  if (this.isEnabled) {
                     this.deleteWords((byte)1);
                  }
               } else if (this.isEnabled) {
                  this.deleteFromCursor((byte)1);
               }

               return true;
            default:
               if (ChatAllowedCharacters.isAllowedCharacter(ch)) {
                  if (this.isEnabled) {
                     this.writeText(Character.toString(ch));
                  }

                  return true;
               } else {
                  return false;
               }
         }
      }
   }

   public boolean isFocused() {
      return this.isFocused;
   }

   public void deleteFromCursor(int n) {
      if (this.text.length() != 0) {
         if (this.selectionEnd != this.cursorPosition) {
            this.writeText("");
         } else {
            boolean flag = n < 0;
            int n2 = flag ? this.cursorPosition + n : this.cursorPosition;
            int n3 = flag ? this.cursorPosition : this.cursorPosition + n;
            String str2 = "";
            if (n2 >= 0) {
               str2 = this.text.substring(0, n2);
            }

            if (n3 < this.text.length()) {
               str2 = str2 + this.text.substring(n3);
            }

            if (this.validator.apply(str2)) {
               this.text = str2;
               this.rawPasswordText = str2;
               if (flag) {
                  this.moveCursorBy(n);
               }

               if (this.guiResponder != null) {
                  this.guiResponder.setEntryValue(this.id, this.text);
               }
            }
         }
      }
   }

   public boolean isVisible() {
      return this.visible;
   }

   public void setCanLoseFocus(boolean flag) {
      this.canLoseFocus = flag;
   }

   public void setEnableBackgroundDrawing(boolean flag) {
      this.enableBackgroundDrawing = flag;
   }

   public int getId() {
      return this.id;
   }

   public int getCursorPosition() {
      return this.cursorPosition;
   }

   public void setSelectionPos(int selectionPos) {
      int length = this.text.length();
      if (selectionPos > length) {
         selectionPos = length;
      }

      if (selectionPos < 0) {
         selectionPos = 0;
      }

      this.selectionEnd = selectionPos;
      if (this.fontRenderer != null) {
         if (this.lineScrollOffset > length) {
            this.lineScrollOffset = length;
         }

         int width2 = this.getWidth();
         InterFontUtil font = this.resolveFont();
         if (font == null) {
            int n2 = this.fontRenderer.trimStringToWidth(this.text.substring(this.lineScrollOffset), width2).length() + this.lineScrollOffset;
            if (selectionPos == this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - this.fontRenderer.trimStringToWidth(this.text, width2, true).length();
            }

            if (selectionPos > n2) {
               this.lineScrollOffset += selectionPos - n2;
            } else if (selectionPos <= this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - (this.lineScrollOffset - selectionPos);
            }
         } else {
            int n2 = font.trimToWidth(this.text.substring(this.lineScrollOffset), (float)width2).length() + this.lineScrollOffset;
            if (selectionPos == this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - font.trimToWidthReverse(this.text, (float)width2).length();
            }

            if (selectionPos > n2) {
               this.lineScrollOffset += selectionPos - n2;
            } else if (selectionPos <= this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - (this.lineScrollOffset - selectionPos);
            }
         }

         this.lineScrollOffset = MathHelper.clamp_int(this.lineScrollOffset, 0, length);
      }
   }

   public void setMaxStringLength(int n) {
      this.maxStringLength = n;
      if (this.text.length() > n) {
         this.text = this.text.substring(0, n);
         this.rawPasswordText = this.rawPasswordText.substring(0, n);
      }
   }

   public void setCursorPositionZero() {
      this.setCursorPosition((byte)0);
   }

   public void deleteWords(int n) {
      if (this.text.length() != 0) {
         if (this.selectionEnd != this.cursorPosition) {
            this.writeText("");
         } else {
            this.deleteFromCursor(this.getNthWordFromCursor(n) - this.cursorPosition);
         }
      }
   }

   public void drawTextBox() {
      this.text = "";

      for (int i = 0; i < this.rawPasswordText.length(); i++) {
         this.text = this.text + "*";
      }

      InterFontUtil font = this.resolveFont();
      if (this.isVisible()) {
         if (font == null) {
            this.drawTextBoxFallback();
         } else {
            if (this.isEnableBackgroundDrawing()) {
               float mx = TabbedMenuScreen.mouseX();
               float my = TabbedMenuScreen.mouseY();
               boolean hovered = this.isEnabled
                  && mx >= (float)this.xPosition
                  && mx <= (float)(this.xPosition + this.width)
                  && my >= (float)this.yPosition
                  && my <= (float)(this.yPosition + this.height);
               this.wellHoverAnim = UiTheme.smoothState(this.wellHoverAnim, hovered ? 1.0F : 0.0F);
               this.wellFocusAnim = UiTheme.smoothState(this.wellFocusAnim, this.isFocused ? 1.0F : 0.0F);
               MenuScreenSkin.drawWellSurface(
                  (float)this.xPosition,
                  (float)this.yPosition,
                  (float)this.width,
                  (float)this.height,
                  MenuScreenSkin.optionRadius((float)this.width, (float)this.height),
                  this.wellHoverAnim,
                  this.wellFocusAnim,
                  mx,
                  my,
                  UiTheme.accentColor()
               );
            }

            int color = this.isEnabled ? this.enabledColor : this.disabledColor;
            int caretPos = this.cursorPosition - this.lineScrollOffset;
            int selectionPos = this.selectionEnd - this.lineScrollOffset;
            int scrollStart = Math.min(this.lineScrollOffset, this.text.length());
            String visibleText = font.trimToWidth(this.text.substring(scrollStart), (float)this.getWidth());
            boolean caretInside = caretPos >= 0 && caretPos <= visibleText.length();
            boolean blinkVisible = this.isFocused && this.cursorCounter / 6 % 2 == 0 && caretInside;
            float textX = this.enableBackgroundDrawing ? (float)(this.xPosition + this.horizontalPadding()) : (float)this.xPosition;
            float fontHeight = font.getFontHeight();
            float textY = this.enableBackgroundDrawing
               ? (float)this.yPosition + ((float)this.height - fontHeight) * 0.5F
               : (float)this.yPosition;
            float penX = textX;
            if (selectionPos > visibleText.length()) {
               selectionPos = visibleText.length();
            }

            if (!visibleText.isEmpty()) {
               int headEnd = caretInside ? Math.max(0, Math.min(caretPos, visibleText.length())) : visibleText.length();
               String head = visibleText.substring(0, headEnd);
               if (!head.isEmpty()) {
                  font.drawStringWithShadow(head, (double)penX, (double)textY, color);
                  penX += font.getStringWidth(head);
               }
            }

            boolean caretAtEnd = this.cursorPosition < this.text.length() || this.text.length() >= this.getMaxStringLength();
            float caretX = penX;
            if (!caretInside) {
               caretX = caretPos > 0 ? textX + (float)this.width : textX;
            } else if (caretAtEnd) {
               caretX = penX - 1.0F;
               penX -= 1.0F;
            }

            if (!visibleText.isEmpty() && caretInside && caretPos < visibleText.length()) {
               font.drawStringWithShadow(visibleText.substring(caretPos), (double)penX, (double)textY, color);
            }

            int caretHeight = (int)Math.ceil((double)fontHeight);
            if (blinkVisible) {
               int caretArgb = UiTheme.withAlpha(UiTheme.accentColor(), 255);
               if (caretAtEnd) {
                  Gui.drawRect((int)caretX, (int)textY - 1, (int)caretX + 1, (int)textY + 1 + caretHeight, caretArgb);
               } else {
                  font.drawStringWithShadow("_", (double)caretX, (double)textY, caretArgb);
               }
            }

            if (selectionPos != caretPos) {
               String selectionHead = visibleText.substring(0, Math.max(0, Math.min(selectionPos, visibleText.length())));
               float selectionX = textX + font.getStringWidth(selectionHead);
               this.drawCursorVertical((int)caretX, (int)textY - 1, (int)selectionX - 1, (int)textY + 1 + caretHeight);
            }
         }
      }

      this.text = this.rawPasswordText;
   }

   /**
    * Stock bitmap path, kept only for the window where {@code FontManager.initFonts()} has not run yet, so
    * a password field can never paint nothing at all.
    */
   private void drawTextBoxFallback() {
      if (this.isEnableBackgroundDrawing()) {
         drawRect(this.xPosition - 1, this.yPosition - 1, this.xPosition + this.width + 1, this.yPosition + this.height + 1, -6250336);
         drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, -16777216);
      }

      int color = this.isEnabled ? this.enabledColor : this.disabledColor;
      int textX = this.enableBackgroundDrawing ? this.xPosition + 4 : this.xPosition;
      int textY = this.enableBackgroundDrawing ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
      String visibleText = this.fontRenderer.trimStringToWidth(
         this.text.substring(Math.min(this.lineScrollOffset, this.text.length())), this.getWidth());
      if (!visibleText.isEmpty()) {
         this.fontRenderer.drawStringWithShadow(visibleText, (float)textX, (float)textY, color);
      }
   }

   public int getNthWordFromPos(int n, int n2) {
      return this.getNthWordFromPos(n, n2, true);
   }

   public int getSelectionEnd() {
      return this.selectionEnd;
   }

   public void setDisabledColor(int n) {
      this.disabledColor = n;
   }

   public void setVisible(boolean flag) {
      this.visible = flag;
   }

   public void setText(String str) {
      if (this.validator.apply(str)) {
         if (str.length() > this.maxStringLength) {
            this.text = str.substring(0, this.maxStringLength);
            this.rawPasswordText = str.substring(0, this.maxStringLength);
         } else {
            this.text = str;
            this.rawPasswordText = str;
         }

         this.setCursorPositionEnd();
      }
   }

   public void moveCursorBy(int n) {
      this.setCursorPosition(this.selectionEnd + n);
   }

   public void setValidator(Predicate predicate) {
      this.validator = predicate;
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      boolean flag = mouseX >= this.xPosition && mouseX < this.xPosition + this.width && mouseY >= this.yPosition && mouseY < this.yPosition + this.height;
      if (this.canLoseFocus) {
         this.setFocused(flag);
      }

      if (this.isFocused && flag && mouseButton == 0) {
         int n = mouseX - this.xPosition;
         if (this.enableBackgroundDrawing) {
            n -= this.horizontalPadding();
         }

         InterFontUtil clickFont = this.resolveFont();
         if (clickFont == null) {
            String str2 = this.fontRenderer.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            this.setCursorPosition(this.fontRenderer.trimStringToWidth(str2, n).length() + this.lineScrollOffset);
         } else {
            String str2 = clickFont.trimToWidth(this.text.substring(this.lineScrollOffset), (float)this.getWidth());
            this.setCursorPosition(clickFont.trimToWidth(str2, (float)n).length() + this.lineScrollOffset);
         }
      }
   }

   public GuiPasswordField(int n, FontRenderer fontRenderer2, int n2, int n3, int n4, int n5) {
      this.rawPasswordText = "";
      this.maxStringLength = 32;
      this.enableBackgroundDrawing = true;
      this.canLoseFocus = true;
      this.isEnabled = true;
      this.enabledColor = 14737632;
      this.disabledColor = 7368816;
      this.visible = true;
      this.validator = Predicates.alwaysTrue();
      this.id = n;
      this.fontRenderer = fontRenderer2;
      this.xPosition = n2;
      this.yPosition = n3;
      this.width = n4;
      this.height = n5;
   }

   public int getNthWordFromCursor(int n) {
      return this.getNthWordFromPos(n, this.getCursorPosition());
   }

   public void setIsEnabled(boolean flag) {
      this.isEnabled = flag;
   }

   public int getWidth() {
      return this.isEnableBackgroundDrawing() ? this.width - this.horizontalPadding() * 2 : this.width;
   }

   public void updateCursorCounter() {
      this.cursorCounter++;
   }

   public boolean isEnableBackgroundDrawing() {
      return this.enableBackgroundDrawing;
   }

   public int getMaxStringLength() {
      return this.maxStringLength;
   }

   private void drawCursorVertical(int n, int n2, int n3, int n4) {
      if (n < n3) {
         int n5 = n;
         n = n3;
         n3 = n5;
      }

      if (n2 < n4) {
         int n6 = n2;
         n2 = n4;
         n4 = n6;
      }

      if (n3 > this.xPosition + this.width) {
         n3 = this.xPosition + this.width;
      }

      if (n > this.xPosition + this.width) {
         n = this.xPosition + this.width;
      }

      drawRect(n, n2, n3, n4, UiTheme.withAlpha(UiTheme.accentColor(), SELECTION_ALPHA));
   }

   public void setEnabledColor(int n) {
      this.enabledColor = n;
   }
}
