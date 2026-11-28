package net.minecraft.client.gui;

import net.minecraft.util.MovingObjectPosition;
import baritone.api.BaritoneAPI;
import baritone.api.IBaritone;
import baritone.api.event.events.TabCompleteEvent;
import com.google.common.collect.Lists;
import java.io.IOException;
import java.util.List;
import net.augustus.modules.Module;
import net.augustus.modules.render.Chat;
import net.augustus.Augustus;
import net.minecraft.network.play.client.C14PacketTabComplete;
import net.minecraft.util.BlockPos;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public class GuiChat extends GuiScreen {
   private static final Logger logger = LogManager.getLogger();
   private String historyBuffer = "";
   private int sentHistoryCursor = -1;
   private boolean playerNamesFound;
   private boolean waitingOnAutocomplete;
   private int autocompleteIndex;
   private List foundPlayerNames = Lists.newArrayList();
   public GuiTextField inputField;
   private String defaultInputFieldText = "";

   public void autocompletePlayerNames() {
      if (this.playerNamesFound) {
         this.inputField.deleteFromCursor(this.inputField.getNthWordFromPosWS(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
         if (this.autocompleteIndex >= this.foundPlayerNames.size()) {
            this.autocompleteIndex = 0;
         }
      } else {
         int nthWordFromPosWS = this.inputField.getNthWordFromPosWS(-1, this.inputField.getCursorPosition(), false);
         this.foundPlayerNames.clear();
         this.autocompleteIndex = 0;
         String str = this.inputField.getText().substring(nthWordFromPosWS).toLowerCase();
         this.sendAutocompleteRequest(this.inputField.getText().substring(0, this.inputField.getCursorPosition()), str);
         if (this.foundPlayerNames.isEmpty()) {
            return;
         }

         this.playerNamesFound = true;
         this.inputField.deleteFromCursor(nthWordFromPosWS - this.inputField.getCursorPosition());
      }

      if (this.foundPlayerNames.size() > 1) {
         StringBuilder sb = new StringBuilder();

         for (String str3 : (Iterable<String>)(this.foundPlayerNames)) {
            if (sb.length() > 0) {
               sb.append(", ");
            }

            sb.append(str3);
         }

         this.mc.ingameGUI.getChatGUI().printChatMessageWithOptionalDeletion(new ChatComponentText(sb.toString()), 1);
      }

      if (this.autocompleteIndex >= this.foundPlayerNames.size()) {
         this.autocompleteIndex = 0;
      }

      if (!this.foundPlayerNames.isEmpty()) {
         this.inputField.writeText((String)this.foundPlayerNames.get(this.autocompleteIndex++));
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0 && this.handleIrcBarClick(mouseX, mouseY)) {
         return;
      }

      if (mouseButton == 0) {
         if (this.handleComponentClick(this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY()))) {
            return;
         }
      }

      this.inputField.mouseClicked(mouseX, mouseY, mouseButton);
      super.mouseClicked(mouseX, mouseY, mouseButton);
   }

   @Override
   public void onGuiClosed() {
      Keyboard.enableRepeatEvents(false);
      this.mc.ingameGUI.getChatGUI().resetScroll();
   }

   private void sendAutocompleteRequest(String str, String str2) {
      if (((Module)Augustus.getInstance().getModuleManager().baritone).isToggled()) {
         IBaritone primaryBaritone = BaritoneAPI.getProvider().getPrimaryBaritone();
         TabCompleteEvent tabCompleteEvent = new TabCompleteEvent(str);
         primaryBaritone.getGameEventHandler().onPreTabComplete(tabCompleteEvent);
         if (tabCompleteEvent.isCancelled()) {
            return;
         }

         if (tabCompleteEvent.completions != null) {
            this.waitingOnAutocomplete = true;
            this.onAutocompleteResponse(tabCompleteEvent.completions);
            return;
         }
      }

      if (str.length() >= 1) {
         BlockPos pos = null;
         if (this.mc.objectMouseOver != null && this.mc.objectMouseOver.typeOfHit == MovingObjectPosition.MovingObjectType.BLOCK) {
            pos = this.mc.objectMouseOver.getBlockPos();
         }

         this.mc.thePlayer.sendQueue.addToSendQueue(new C14PacketTabComplete(str, pos));
         this.waitingOnAutocomplete = true;
      }
   }

   public void onAutocompleteResponse(String[] strings) {
      if (this.waitingOnAutocomplete) {
         this.playerNamesFound = false;
         this.foundPlayerNames.clear();

         for (String str : strings) {
            if (str.length() > 0) {
               this.foundPlayerNames.add(str);
            }
         }

         String str2 = this.inputField.getText().substring(this.inputField.getNthWordFromPosWS(-1, this.inputField.getCursorPosition(), false));
         String commonPrefix = StringUtils.getCommonPrefix(strings);
         if (commonPrefix.length() > 0 && !str2.equalsIgnoreCase(commonPrefix)) {
            this.inputField
               .deleteFromCursor(this.inputField.getNthWordFromPosWS(-1, this.inputField.getCursorPosition(), false) - this.inputField.getCursorPosition());
            this.inputField.writeText(commonPrefix);
         } else if (this.foundPlayerNames.size() > 0) {
            this.playerNamesFound = true;
            this.autocompletePlayerNames();
         }
      }
   }

   @Override
   public void initGui() {
      Keyboard.enableRepeatEvents(true);
      this.sentHistoryCursor = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
      this.inputField = new GuiTextField(0, this.fontRendererObj, 4, this.height - 12, this.width - 4, 12);
      this.inputField.setUseMinecraftFont(true);
      this.inputField.setMaxStringLength(100);
      this.inputField.setEnableBackgroundDrawing(false);
      this.inputField.setFocused(true);
      this.inputField.setText(this.defaultInputFieldText);
      this.inputField.setCanLoseFocus(false);
   }

   @Override
   public void updateScreen() {
      this.inputField.updateCursorCounter();
   }

   @Override
   public void setText(String str, boolean flag) {
      if (flag) {
         this.inputField.setText(str);
      } else {
         this.inputField.writeText(str);
      }
   }

   @Override
   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      net.augustus.irc.IrcManager irc = net.augustus.irc.IrcManager.getInstance();
      boolean ircEnabled = irc.getConfig().isIrcEnabled();
      boolean exclusiveIrc = ircEnabled && irc.getConfig().isExclusiveChatMode();

      drawRect(2, this.height - 14, this.width - 2, this.height - 2, -2147483648);
      if (exclusiveIrc) {
         int accent = net.augustus.ui.modern.render.UiTheme.accentColor();
         net.augustus.ui.modern.render.GlassRenderer.drawRoundedOutline(
            2.0F, (float)(this.height - 14), (float)(this.width - 4), 12.0F, 3.0F, 1.0F,
            net.augustus.ui.modern.render.UiTheme.withAlpha(accent, 210)
         );
      }

      Chat chat = Augustus.getInstance().getModuleManager().chat;
      this.inputField.drawTextBoxWithShadowOption(!chat.isToggled() || chat.fontShadow.getValue());
      if (exclusiveIrc && this.inputField.getText().isEmpty()) {
         String hint = "Escribe en el chat exclusivo de Augustus IRC...";
         this.fontRendererObj.drawStringWithShadow(hint, 6.0F, (float)(this.height - 12), 0x88CBD5E1);
      }

      if (ircEnabled) {
         this.drawIrcChatModeBar(mouseX, mouseY, exclusiveIrc);
      }

      IChatComponent chatComponent = this.mc.ingameGUI.getChatGUI().getChatComponent(Mouse.getX(), Mouse.getY());
      if (chatComponent != null && chatComponent.getChatStyle().getChatHoverEvent() != null) {
         this.handleComponentHover(chatComponent, mouseX, mouseY);
      }

      super.drawScreen(mouseX, mouseY, partialTicks);
   }

   private void drawIrcChatModeBar(int mouseX, int mouseY, boolean exclusiveIrc) {
      net.augustus.irc.IrcManager irc = net.augustus.irc.IrcManager.getInstance();
      net.augustus.ui.modern.font.InterFontUtil face = net.augustus.font.FontManager.uiSmall();
      int accent = net.augustus.ui.modern.render.UiTheme.accentColor();
      float barY = (float)(this.height - 31);
      float barH = 14.0F;

      String globalLabel = "Global";
      float globalW = (face != null ? face.getStringWidth(globalLabel) : (float)this.fontRendererObj.getStringWidth(globalLabel)) + 14.0F;
      float globalX = 2.0F;
      drawChatModePill(globalX, barY, globalW, barH, globalLabel, !exclusiveIrc, mouseX, mouseY, accent, face, this.fontRendererObj);

      String ircLabel = "Augustus IRC (" + irc.getOnlineUserCount() + ")";
      float ircW = (face != null ? face.getStringWidth(ircLabel) : (float)this.fontRendererObj.getStringWidth(ircLabel)) + 16.0F;
      float ircX = globalX + globalW + 4.0F;
      drawChatModePill(ircX, barY, ircW, barH, ircLabel, exclusiveIrc, mouseX, mouseY, accent, face, this.fontRendererObj);

      if (exclusiveIrc) {
         java.util.List<net.augustus.irc.IrcUserRecord> users = irc.getOnlineUsersSorted();
         StringBuilder namesOnly = new StringBuilder();
         for (int i = 0; i < users.size(); i++) {
            if (i > 0) {
               namesOnly.append("  ·  ");
            }
            namesOnly.append(users.get(i).getDisplayOriginalNick());
         }
         String listStr = namesOnly.toString();
         float listX = ircX + ircW + 6.0F;
         float maxListW = (float)(this.width - 4) - listX;
         if (maxListW > 48.0F && !listStr.isEmpty()) {
            String trimmed = face != null
               ? net.augustus.font.FontManager.uiFit(face, listStr, maxListW - 14.0F)
               : this.fontRendererObj.trimStringToWidth(listStr, (int)(maxListW - 14.0F));
            float pillW = (face != null ? face.getStringWidth(trimmed) : (float)this.fontRendererObj.getStringWidth(trimmed)) + 14.0F;
            net.augustus.ui.modern.render.GlassRenderer.drawRoundedBox(
               listX, barY, Math.min(maxListW, pillW), barH, 4.0F, 0xB80E1019,
               net.augustus.ui.modern.render.UiTheme.withAlpha(accent, 110), 0.75F
            );
            if (face != null) {
               face.drawStringWithShadow(trimmed, (double)(listX + 7.0F), (double)(barY + (barH - face.getFontHeight()) * 0.5F), 0xFFE2E8F0);
            } else {
               this.fontRendererObj.drawStringWithShadow(trimmed, listX + 7.0F, barY + 3.0F, 0xFFE2E8F0);
            }
         }
      }
   }

   private static void drawChatModePill(
      float x, float y, float w, float h, String text, boolean active, int mouseX, int mouseY,
      int accent, net.augustus.ui.modern.font.InterFontUtil face, FontRenderer fallbackFont
   ) {
      boolean hovered = (float)mouseX >= x && (float)mouseX <= x + w && (float)mouseY >= y && (float)mouseY <= y + h;
      int fill = active
         ? net.augustus.ui.modern.render.UiTheme.lerpArgb(0xD6121521, accent, 0.28F)
         : (hovered ? 0xCC181B2A : 0xB80E1019);
      int border = active
         ? net.augustus.ui.modern.render.UiTheme.withAlpha(accent, 235)
         : (hovered ? 0x66FFFFFF : 0x33FFFFFF);
      net.augustus.ui.modern.render.GlassRenderer.drawRoundedBox(x, y, w, h, 4.0F, fill, border, 0.9F);
      int textColor = active ? 0xFFFFFFFF : 0xFFB8C2D4;
      if (face != null) {
         face.drawCenteredStringInBox(text, (double)x, (double)y, (double)w, (double)h, textColor);
      } else {
         fallbackFont.drawStringWithShadow(text, x + (w - fallbackFont.getStringWidth(text)) * 0.5F, y + 3.0F, textColor);
      }
   }

   private boolean handleIrcBarClick(int mouseX, int mouseY) {
      net.augustus.irc.IrcManager irc = net.augustus.irc.IrcManager.getInstance();
      if (!irc.getConfig().isIrcEnabled()) {
         return false;
      }
      net.augustus.ui.modern.font.InterFontUtil face = net.augustus.font.FontManager.uiSmall();
      float barY = (float)(this.height - 31);
      float barH = 14.0F;
      float globalW = (face != null ? face.getStringWidth("Global") : (float)this.fontRendererObj.getStringWidth("Global")) + 14.0F;
      float globalX = 2.0F;
      if ((float)mouseX >= globalX && (float)mouseX <= globalX + globalW && (float)mouseY >= barY && (float)mouseY <= barY + barH) {
         irc.getConfig().setExclusiveChatMode(false);
         return true;
      }

      String ircLabel = "Augustus IRC (" + irc.getOnlineUserCount() + ")";
      float ircW = (face != null ? face.getStringWidth(ircLabel) : (float)this.fontRendererObj.getStringWidth(ircLabel)) + 16.0F;
      float ircX = globalX + globalW + 4.0F;
      if ((float)mouseX >= ircX && (float)mouseX <= ircX + ircW && (float)mouseY >= barY && (float)mouseY <= barY + barH) {
         irc.getConfig().setExclusiveChatMode(true);
         return true;
      }
      return false;
   }

   @Override
   public boolean doesGuiPauseGame() {
      return false;
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      int eventDWheel = Mouse.getEventDWheel();
      if (eventDWheel != 0) {
         if (eventDWheel > 1) {
            eventDWheel = 1;
         }

         if (eventDWheel < -1) {
            eventDWheel = -1;
         }

         if (!isShiftKeyDown()) {
            eventDWheel *= 7;
         }

         this.mc.ingameGUI.getChatGUI().scroll(eventDWheel);
      }
   }

   public GuiChat(String str) {
      this.defaultInputFieldText = str;
   }

   public void getSentHistory(int n) {
      int n2 = this.sentHistoryCursor + n;
      int size = this.mc.ingameGUI.getChatGUI().getSentMessages().size();
      n2 = MathHelper.clamp_int(n2, 0, size);
      if (n2 != this.sentHistoryCursor) {
         if (n2 == size) {
            this.sentHistoryCursor = size;
            this.inputField.setText(this.historyBuffer);
         } else {
            if (this.sentHistoryCursor == size) {
               this.historyBuffer = this.inputField.getText();
            }

            this.inputField.setText((String)this.mc.ingameGUI.getChatGUI().getSentMessages().get(n2));
            this.sentHistoryCursor = n2;
         }
      }
   }

   public GuiChat() {
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      this.waitingOnAutocomplete = false;
      if (keyCode == 15) {
         this.autocompletePlayerNames();
      } else {
         this.playerNamesFound = false;
      }

      if (keyCode == 1) {
         GuiScreen screen = (GuiScreen)null;
         this.mc.displayGuiScreen(screen);
      } else if (keyCode == 28 || keyCode == 156) {
         String str = this.inputField.getText().trim();
         if (str.length() > 0) {
            net.augustus.irc.IrcManager irc = net.augustus.irc.IrcManager.getInstance();
            String prefix = Augustus.getInstance().getModuleManager().clientSettings.commandPrefixes.getSelectedValue();
            if (irc.getConfig().isIrcEnabled() && irc.getConfig().isExclusiveChatMode() && !str.startsWith(prefix)) {
               this.mc.ingameGUI.getChatGUI().addToSentMessages(str);
               irc.sendIrcChat(str);
            } else if (irc.getConfig().isIrcEnabled() && str.startsWith("#") && !"#".equals(prefix)) {
               this.mc.ingameGUI.getChatGUI().addToSentMessages(str);
               String body = str.substring(1).trim();
               if (!body.isEmpty()) {
                  irc.sendIrcChat(body);
               }
            } else {
               this.sendChatMessage(str);
            }
         }

         GuiScreen screen2 = (GuiScreen)null;
         this.mc.displayGuiScreen(screen2);
      } else if (keyCode == 200) {
         this.getSentHistory(-1);
      } else if (keyCode == 208) {
         this.getSentHistory(1);
      } else if (keyCode == 201) {
         this.mc.ingameGUI.getChatGUI().scroll(this.mc.ingameGUI.getChatGUI().getLineCount() - 1);
      } else if (keyCode == 209) {
         this.mc.ingameGUI.getChatGUI().scroll(-this.mc.ingameGUI.getChatGUI().getLineCount() + 1);
      } else {
         this.inputField.textboxKeyTyped(typedChar, keyCode);
      }
   }

}
