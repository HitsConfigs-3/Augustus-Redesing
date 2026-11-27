package net.minecraft.client.gui;

import com.google.common.base.Predicate;
import com.google.common.base.Predicates;
import java.util.function.Consumer;
import net.augustus.font.FontManager;
import net.augustus.ui.modern.font.InterFontUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.util.ChatAllowedCharacters;
import net.minecraft.util.MathHelper;

public class GuiTextField extends Gui {
   private final int id;
   private final FontRenderer fontRendererInstance;
   public int xPosition;
   public int yPosition;
   private final int width;
   private final int height;
   private String text = "";
   private int maxStringLength = 32;
   private int cursorCounter;
   private boolean enableBackgroundDrawing = true;
   private boolean canLoseFocus = true;
   private boolean isFocused;
   private boolean isEnabled = true;
   private int lineScrollOffset;
   private int cursorPosition;
   private int selectionEnd;
   private int textColor = 14737632;
   private int disabledTextColour = 7368816;
   private boolean visible = true;
   private GuiPageButtonList.GuiResponder guiResponder;
   private Predicate validator = Predicates.alwaysTrue();
   private Consumer textChangeListener;

   public int getMaxStringLength() {
      return this.maxStringLength;
   }

   /**
    * The face every text field is drawn with: the client's Inter, so an input no longer drops back to the
    * 8 px bitmap font while every surrounding control uses Inter (the last stock-looking piece of the
    * redesigned menus).
    *
    * <p>All caret, scroll-offset and text-width maths below goes through this same face - a field cannot
    * be drawn with Inter and measured with the bitmap font, or the caret drifts away from the glyphs.</p>
    */
   private static InterFontUtil textFieldFont() {
      return FontManager.body();
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
         this.moveCursorBy(n - this.selectionEnd + n4);
         if (this.guiResponder != null) {
            this.guiResponder.setEntryValue(this.id, this.text);
         }
      }
   }

   public int getWidth() {
      return this.getEnableBackgroundDrawing() ? this.width - 8 : this.width;
   }

   public String getText() {
      return this.text;
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

   public void setTextChangeListener(Consumer consumer) {
      this.textChangeListener = consumer;
   }

   public void setCursorPositionEnd() {
      this.setCursorPosition(this.text.length());
   }

   public void updateCursorCounter() {
      this.cursorCounter++;
   }

   public String getSelectedText() {
      int n = this.cursorPosition < this.selectionEnd ? this.cursorPosition : this.selectionEnd;
      int n2 = this.cursorPosition < this.selectionEnd ? this.selectionEnd : this.cursorPosition;
      return this.text.substring(n, n2);
   }

   public void setMaxStringLength(int n) {
      this.maxStringLength = n;
      if (this.text.length() > n) {
         this.text = this.text.substring(0, n);
      }
   }

   public void setTextColor(int n) {
      this.textColor = n;
   }

   public GuiTextField(int n, FontRenderer fontRenderer, int n2, int n3, int n4, int n5) {
      this.id = n;
      this.fontRendererInstance = fontRenderer;
      this.xPosition = n2;
      this.yPosition = n3;
      this.width = n4;
      this.height = n5;
      GuiScreen.NO_TOGGLE_SCREENS.add(Minecraft.getMinecraft().currentScreen);
   }

   public boolean isFocused() {
      return this.isFocused;
   }

   public void setCanLoseFocus(boolean flag) {
      this.canLoseFocus = flag;
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) {
      boolean flag = mouseX >= this.xPosition && mouseX < this.xPosition + this.width && mouseY >= this.yPosition && mouseY < this.yPosition + this.height;
      if (this.canLoseFocus) {
         this.setFocused(flag);
      }

      if (this.isFocused && flag && mouseButton == 0) {
         int n = mouseX - this.xPosition;
         if (this.enableBackgroundDrawing) {
            n -= 4;
         }

         InterFontUtil font = textFieldFont();
         if (font == null) {
            String str = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), this.getWidth());
            this.setCursorPosition(this.fontRendererInstance.trimStringToWidth(str, n).length() + this.lineScrollOffset);
         } else {
            String str = font.trimToWidth(this.text.substring(this.lineScrollOffset), (float)this.getWidth());
            this.setCursorPosition(font.trimToWidth(str, (float)n).length() + this.lineScrollOffset);
         }
      }
   }

   public void setText(String str) {
      if (this.validator.apply(str)) {
         if (this.textChangeListener != null) {
            this.textChangeListener.accept(str);
         }

         if (str.length() > this.maxStringLength) {
            this.text = str.substring(0, this.maxStringLength);
         } else {
            this.text = str;
         }

         this.setCursorPositionEnd();
      }
   }

   public void setCursorPositionZero() {
      this.setCursorPosition(0);
   }

   public int getSelectionEnd() {
      return this.selectionEnd;
   }

   public void drawTextBox() {
      this.drawTextBoxWithShadowOption(true);
   }

   public void setFocused(boolean flag) {
      if (flag && !this.isFocused) {
         this.cursorCounter = 0;
      }

      this.isFocused = flag;
   }

   public void setSelectionPos(int n) {
      int length = this.text.length();
      if (n > length) {
         n = length;
      }

      if (n < 0) {
         n = 0;
      }

      this.selectionEnd = n;
      if (this.fontRendererInstance != null) {
         if (this.lineScrollOffset > length) {
            this.lineScrollOffset = length;
         }

         int width2 = this.getWidth();
         InterFontUtil font = textFieldFont();
         if (font == null) {
            int n2 = this.fontRendererInstance.trimStringToWidth(this.text.substring(this.lineScrollOffset), width2).length() + this.lineScrollOffset;
            if (n == this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - this.fontRendererInstance.trimStringToWidth(this.text, width2, true).length();
            }

            if (n > n2) {
               this.lineScrollOffset += n - n2;
            } else if (n <= this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - (this.lineScrollOffset - n);
            }
         } else {
            int n2 = font.trimToWidth(this.text.substring(this.lineScrollOffset), (float)width2).length() + this.lineScrollOffset;
            if (n == this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - font.trimToWidthReverse(this.text, (float)width2).length();
            }

            if (n > n2) {
               this.lineScrollOffset += n - n2;
            } else if (n <= this.lineScrollOffset) {
               this.lineScrollOffset = this.lineScrollOffset - (this.lineScrollOffset - n);
            }
         }

         this.lineScrollOffset = MathHelper.clamp_int(this.lineScrollOffset, 0, length);
      }
   }

   public int getNthWordFromCursor(int n) {
      return this.getNthWordFromPos(n, this.getCursorPosition());
   }

   public void deleteFromCursor(int n) {
      if (this.text.length() != 0) {
         if (this.selectionEnd != this.cursorPosition) {
            this.writeText("");
         } else {
            boolean flag = n < 0;
            int n2 = flag ? this.cursorPosition + n : this.cursorPosition;
            int n3 = flag ? this.cursorPosition : this.cursorPosition + n;
            String str = "";
            if (n2 >= 0) {
               str = this.text.substring(0, n2);
            }

            if (n3 < this.text.length()) {
               str = str + this.text.substring(n3);
            }

            if (this.validator.apply(str)) {
               this.text = str;
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

   public void setEnableBackgroundDrawing(boolean flag) {
      this.enableBackgroundDrawing = flag;
   }

   public boolean textboxKeyTyped(char ch, int n) {
      if (!this.isFocused) {
         return false;
      } else if (GuiScreen.isKeyComboCtrlA(n)) {
         this.setCursorPositionEnd();
         this.setSelectionPos(0);
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
                     this.deleteWords(-1);
                  }
               } else if (this.isEnabled) {
                  this.deleteFromCursor(-1);
               }

               return true;
            case 199:
               if (GuiScreen.isShiftKeyDown()) {
                  this.setSelectionPos(0);
               } else {
                  this.setCursorPositionZero();
               }

               return true;
            case 203:
               if (GuiScreen.isShiftKeyDown()) {
                  if (GuiScreen.isCtrlKeyDown()) {
                     this.setSelectionPos(this.getNthWordFromPos(-1, this.getSelectionEnd()));
                  } else {
                     this.setSelectionPos(this.getSelectionEnd() - 1);
                  }
               } else if (GuiScreen.isCtrlKeyDown()) {
                  this.setCursorPosition(this.getNthWordFromCursor(-1));
               } else {
                  this.moveCursorBy(-1);
               }

               return true;
            case 205:
               if (GuiScreen.isShiftKeyDown()) {
                  if (GuiScreen.isCtrlKeyDown()) {
                     this.setSelectionPos(this.getNthWordFromPos(1, this.getSelectionEnd()));
                  } else {
                     this.setSelectionPos(this.getSelectionEnd() + 1);
                  }
               } else if (GuiScreen.isCtrlKeyDown()) {
                  this.setCursorPosition(this.getNthWordFromCursor(1));
               } else {
                  this.moveCursorBy(1);
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
                     this.deleteWords(1);
                  }
               } else if (this.isEnabled) {
                  this.deleteFromCursor(1);
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

   public boolean getEnableBackgroundDrawing() {
      return this.enableBackgroundDrawing;
   }

   public void setVisible(boolean flag) {
      this.visible = flag;
   }

   public void setValidator(Predicate predicate) {
      this.validator = predicate;
   }

   public int getCursorPosition() {
      return this.cursorPosition;
   }

   public void setGuiResponder(GuiPageButtonList.GuiResponder guiResponder2) {
      this.guiResponder = guiResponder2;
   }

   public void setEnabled(boolean flag) {
      this.isEnabled = flag;
   }

   public void drawTextBoxWithShadowOption(boolean flag) {
      if (!this.getVisible()) {
         return;
      }
      InterFontUtil font = textFieldFont();
      if (font == null) {
         this.drawTextBoxFallback(flag);
         return;
      }
      if (this.getEnableBackgroundDrawing()) {
         drawRect(this.xPosition - 1, this.yPosition - 1, this.xPosition + this.width + 1, this.yPosition + this.height + 1, -6250336);
         drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, -16777216);
      }

      int textArgb = this.isEnabled ? this.textColor : this.disabledTextColour;
      int caretPos = this.cursorPosition - this.lineScrollOffset;
      int selectionPos = this.selectionEnd - this.lineScrollOffset;
      int scrollStart = Math.min(this.lineScrollOffset, this.text.length());
      String visibleText = font.trimToWidth(this.text.substring(scrollStart), (float)this.getWidth());
      boolean caretInside = caretPos >= 0 && caretPos <= visibleText.length();
      boolean blinkVisible = this.isFocused && this.cursorCounter / 6 % 2 == 0 && caretInside;
      float textX = this.enableBackgroundDrawing ? (float)(this.xPosition + 4) : (float)this.xPosition;
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
            if (flag) {
               font.drawStringWithShadow(head, (double)penX, (double)textY, textArgb);
            } else {
               font.drawString(head, (double)penX, (double)textY, textArgb);
            }
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
         String tail = visibleText.substring(caretPos);
         if (flag) {
            font.drawStringWithShadow(tail, (double)penX, (double)textY, textArgb);
         } else {
            font.drawString(tail, (double)penX, (double)textY, textArgb);
         }
      }

      int caretHeight = (int)Math.ceil((double)fontHeight);
      if (blinkVisible) {
         if (caretAtEnd) {
            drawRect((int)caretX, (int)textY - 1, (int)caretX + 1, (int)textY + 1 + caretHeight, -3092272);
         } else if (flag) {
            font.drawStringWithShadow("_", (double)caretX, (double)textY, textArgb);
         } else {
            font.drawString("_", (double)caretX, (double)textY, textArgb);
         }
      }

      if (selectionPos != caretPos) {
         String selectionHead = visibleText.substring(0, Math.max(0, Math.min(selectionPos, visibleText.length())));
         float selectionX = textX + font.getStringWidth(selectionHead);
         this.drawCursorVertical((int)caretX, (int)textY - 1, (int)selectionX - 1, (int)textY + 1 + caretHeight);
      }
   }

   /**
    * Last-resort painting for the (practically unreachable) window where {@code FontManager.initFonts()}
    * has not run yet: the stock bitmap path, minus the caret precision the Inter path provides.
    */
   private void drawTextBoxFallback(boolean flag) {
      if (this.getEnableBackgroundDrawing()) {
         drawRect(this.xPosition - 1, this.yPosition - 1, this.xPosition + this.width + 1, this.yPosition + this.height + 1, -6250336);
         drawRect(this.xPosition, this.yPosition, this.xPosition + this.width, this.yPosition + this.height, -16777216);
      }

      int n = this.isEnabled ? this.textColor : this.disabledTextColour;
      int n2 = this.enableBackgroundDrawing ? this.xPosition + 4 : this.xPosition;
      int n3 = this.enableBackgroundDrawing ? this.yPosition + (this.height - 8) / 2 : this.yPosition;
      String str = this.fontRendererInstance.trimStringToWidth(this.text.substring(Math.min(this.lineScrollOffset, this.text.length())), this.getWidth());
      if (!str.isEmpty()) {
         if (flag) {
            this.fontRendererInstance.drawStringWithShadow(str, (float)n2, (float)n3, n);
         } else {
            this.fontRendererInstance.drawString(str, (float)n2, (float)n3, n);
         }
      }
   }

   public void setDisabledTextColour(int n) {
      this.disabledTextColour = n;
   }

   public void setCursorPosition(int n) {
      this.cursorPosition = n;
      int length = this.text.length();
      this.cursorPosition = MathHelper.clamp_int(this.cursorPosition, 0, length);
      this.setSelectionPos(this.cursorPosition);
   }

   public int getNthWordFromPosWS(int n, int n2, boolean flag) {
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

      Tessellator instance = Tessellator.getInstance();
      WorldRenderer worldRenderer = instance.getWorldRenderer();
      GlStateManager.color(0.0F, 0.0F, 255.0F, 255.0F);
      GlStateManager.disableTexture2D();
      GlStateManager.enableColorLogic();
      GlStateManager.colorLogicOp(5387);
      worldRenderer.begin(7, DefaultVertexFormats.POSITION);
      worldRenderer.pos((double)n, (double)n4, 0.0).endVertex();
      worldRenderer.pos((double)n3, (double)n4, 0.0).endVertex();
      worldRenderer.pos((double)n3, (double)n2, 0.0).endVertex();
      worldRenderer.pos((double)n, (double)n2, 0.0).endVertex();
      instance.draw();
      GlStateManager.disableColorLogic();
      GlStateManager.enableTexture2D();
   }

   public void moveCursorBy(int n) {
      this.setCursorPosition(this.selectionEnd + n);
   }

   public boolean getVisible() {
      return this.visible;
   }

   public int getId() {
      return this.id;
   }

   public int getHeight() {
      return this.height;
   }

   public int getNthWordFromPos(int n, int n2) {
      return this.getNthWordFromPosWS(n, n2, true);
   }
}
