package net.augustus.ui.proxy;

import java.awt.Color;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.augustus.Augustus;
import net.augustus.proxy.ProxyProtocolType;
import net.augustus.render.RenderUtil;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.ui.GuiButtonClickHandler;
import net.augustus.ui.GuiPasswordField;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.account.AccountAuthenticator;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.components.GlassMenuButton;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;
import net.augustus.utils.TuplePair;
import net.augustus.utils.network.ProxyEntry;
import net.augustus.utils.render.ColorUtil;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/**
 * Proxy Manager view: unified left frosted glass form card with recessed wells and icon buttons,
 * paired with a full-height right frosted glass proxy list container.
 */
public class ProxyManagerScreen extends ClientMenuTab {
   private boolean reinitPending;
   private CompletableFuture activePingTask;
   private static ProxyEntry pendingProxy = null;
   public static final ScrollBar scrollBar;
   public static final CopyOnWriteArrayList proxies = new CopyOnWriteArrayList();
   private boolean mcServerCheckEnabled;
   private GuiTextField usernameField;
   private GuiPasswordField passwordField;
   private ProxyEntry selectedProxy;
   private ProxyEntry previousProxy;
   public static final ConcurrentHashMap proxyListEntries = new ConcurrentHashMap();
   private GuiTextField addressField;
   private ProxyProtocolType selectedProtocolType = ProxyProtocolType.SOCKS5;

   private float leftX;
   private float leftW;
   private float rightX;
   private float rightW;
   private float cardPad;
   private float rowGap;
   private float colGap;
   private float labelH;
   private float formY;
   private float formH;
   private float addressLabelY;
   private float addressFieldY;
   private float userLabelY;
   private float userFieldY;
   private float switchRowY;
   private float connectButtonY;
   private float toolsRow1Y;
   private float toolsRow2Y;
   private float dividerY;
   private float dangerRowY;
   private float listX;
   private float listW;
   private float listTop;
   private float listHeadingY;
   private float rowsTop;
   private float rowsBottom;

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.SHIELD;
   }

   private boolean hasPasswordRow() {
      return this.selectedProtocolType == ProxyProtocolType.SOCKS5;
   }

   private void layoutCards() {
      float pageX = TabbedMenuScreen.contentX();
      float pageW = TabbedMenuScreen.contentW();
      float gap = TabbedMenuScreen.gap();
      float row = TabbedMenuScreen.rowH();
      float top = TabbedMenuScreen.bodyTop();

      this.cardPad = clamp(Math.round(row * 0.48F), 10.0F, 13.0F);
      this.rowGap = clamp(Math.round(row * 0.25F), 5.0F, 7.0F);
      this.colGap = clamp(Math.round(gap * 0.65F), 6.0F, 8.0F);
      this.labelH = menuCaptionHeight();

      this.leftX = pageX;
      this.leftW = clamp(Math.round(pageW * 0.455F), 150.0F, 375.0F);
      if (this.leftW > pageW - 148.0F) {
         this.leftW = Math.max(140.0F, pageW - 148.0F);
      }
      this.rightX = this.leftX + this.leftW + gap;
      this.rightW = Math.max(130.0F, TabbedMenuScreen.contentX() + pageW - this.rightX);

      // Unified Left Form Card
      this.formY = top;
      this.addressLabelY = this.formY + this.cardPad;
      this.addressFieldY = this.addressLabelY + this.labelH + 4.0F;
      this.userLabelY = this.addressFieldY + row + this.rowGap + 2.0F;
      this.userFieldY = this.userLabelY + this.labelH + 4.0F;
      this.switchRowY = this.userFieldY + row + this.rowGap + 2.0F;
      this.connectButtonY = this.switchRowY + row + this.rowGap;
      this.toolsRow1Y = this.connectButtonY + row + this.rowGap;
      this.toolsRow2Y = this.toolsRow1Y + row + this.rowGap;
      this.dividerY = this.toolsRow2Y + row + this.rowGap + 2.0F;
      this.dangerRowY = this.dividerY + this.rowGap + 2.0F;
      this.formH = this.dangerRowY + row + this.cardPad - this.formY;

      // Right List Container
      this.listX = this.rightX;
      this.listW = this.rightW;
      this.listTop = top;
      this.listHeadingY = top + this.cardPad;
      this.rowsTop = this.listHeadingY + this.labelH + 8.0F;
      this.rowsBottom = TabbedMenuScreen.bodyBottom();
   }

   @Override
   public void drawChromeBehind() {
      this.layoutCards();
      this.menuGlassPanel(this.leftX, this.formY, this.leftW, this.formH, 0.0F, 1.0F);

      float innerX = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;
      float columnW = (innerW - this.colGap) * 0.5F;

      menuSectionLabel("PROXY ADDRESS", innerX, this.addressLabelY, 1.0F);
      menuSectionLabel("USERNAME", innerX, this.userLabelY, 1.0F);
      if (this.hasPasswordRow()) {
         menuSectionLabel("PASSWORD", innerX + columnW + this.colGap, this.userLabelY, 1.0F);
      }

      GlassRenderer.drawSeparator(
         innerX,
         this.dividerY,
         innerX + innerW,
         this.dividerY,
         UiTheme.SEPARATOR_THICKNESS,
         UiTheme.GLASS_DIVIDER
      );

      // Right full-height glass container
      this.menuGlassPanel(this.listX, this.listTop, this.listW, Math.max(0.0F, this.rowsBottom - this.listTop), 0.0F, 1.0F);

      menuSectionLabel("PROXIES", this.listX + this.cardPad, this.listHeadingY, 1.0F);
      String count = String.valueOf(proxies.size());
      InterFontUtil capFace = menuCaptionFont();
      if (capFace != null) {
         float cw = capFace.getStringWidth(count);
         capFace.drawStringWithShadow(count, (double)(this.listX + this.listW - this.cardPad - cw),
               (double)this.listHeadingY, UiTheme.scaleAlpha(TEXT_SUB, 0.80F));
      }

      if (proxies.isEmpty()) {
         this.menuEmptyState(
            this.listX,
            this.rowsTop,
            this.listW,
            Math.max(0.0F, this.rowsBottom - this.rowsTop - 8.0F),
            LucideIcon.GLOBE,
            "No proxies yet",
            "Import a .txt list, or type host:port above and press Connect.",
            false
         );
      }
   }

   @Override
   public void initContent() {
      this.reinitPending = false;
      super.initContent();
      this.layoutCards();

      float row = TabbedMenuScreen.rowH();
      float innerX = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;
      float columnW = (innerW - this.colGap) * 0.5F;

      this.addressField = this.addTextField(Math.round(innerX), Math.round(this.addressFieldY),
            Math.round(innerW), Math.round(row));
      this.addressField.setCustomFont(menuBodyFont());

      float userW = this.hasPasswordRow() ? columnW : innerW;
      this.usernameField = this.addTextField(Math.round(innerX), Math.round(this.userFieldY),
            Math.round(userW), Math.round(row));
      this.usernameField.setCustomFont(menuBodyFont());

      if (this.hasPasswordRow()) {
         this.passwordField = this.addPasswordField(Math.round(innerX + columnW + this.colGap),
               Math.round(this.userFieldY), Math.round(columnW), Math.round(row));
         this.passwordField.setCustomFont(menuBodyFont());
         this.passwordField.setVisible(true);
      } else if (this.passwordField != null) {
         this.passwordField.setVisible(false);
      }

      this.populateFieldsFromPendingProxy();

      String protoLabel = "Type: " + this.selectedProtocolType.getDisplayName();
      this.addGlassButton(Math.round(innerX), Math.round(this.switchRowY), Math.round(columnW), Math.round(row),
            protoLabel, button -> this.onToggleProtocolTypeClick(button))
            .withTrailingIcon(LucideIcon.CHEVRON_DOWN);

      String checkLabel = "MC Check: " + (this.mcServerCheckEnabled ? "§aTrue" : "§cFalse");
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.switchRowY),
            Math.round(columnW), Math.round(row), checkLabel, button7 -> {
               this.mcServerCheckEnabled = !this.mcServerCheckEnabled;
               button7.displayString = "MC Check: " + (this.mcServerCheckEnabled ? "§aTrue" : "§cFalse");
               if (button7 instanceof GlassMenuButton) {
                  ((GlassMenuButton)button7).setToggleState(this.mcServerCheckEnabled);
               }
            })
            .withTrailingIcon(LucideIcon.CHEVRON_DOWN);

      this.addGlassButton(Math.round(innerX), Math.round(this.connectButtonY), Math.round(columnW),
            Math.round(row), "Connect", button2 -> this.connectToProxy(this.addressField.getText(), false))
            .withIcon(LucideIcon.LINK)
            .withVariant(GlassMenuButton.Variant.PRIMARY);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.connectButtonY),
            Math.round(columnW), Math.round(row), "Clear", button3 -> this.onClearFieldsClick(button3))
            .withIcon(LucideIcon.TRASH_2);

      this.addGlassButton(Math.round(innerX), Math.round(this.toolsRow1Y), Math.round(columnW), Math.round(row),
            "Disconnect", button4 -> this.onDisconnectClick(button4))
            .withIcon(LucideIcon.CLOSE);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.toolsRow1Y),
            Math.round(columnW), Math.round(row), "Clipboard Login", button5 -> this.connectFromClipboard())
            .withIcon(LucideIcon.CLIPBOARD);

      this.addGlassButton(Math.round(innerX), Math.round(this.toolsRow2Y), Math.round(innerW), Math.round(row),
            "Import Proxies", button6 -> this.onImportProxiesClick(button6))
            .withIcon(LucideIcon.UPLOAD);

      this.addGlassButton(Math.round(innerX), Math.round(this.dangerRowY), Math.round(innerW), Math.round(row),
            "Delete selected Proxy", button8 -> this.onDeleteSelectedProxyClick(button8))
            .withIcon(LucideIcon.TRASH_2)
            .withVariant(GlassMenuButton.Variant.DANGER);

      scrollBar.setBounds(
         Math.round(this.listX + this.listW - 10.0F),
         Math.round(this.rowsTop),
         Math.round(this.listX + this.listW - 5.0F),
         Math.round(this.rowsBottom - 10.0F)
      );
      scrollBar.setItemCount(proxies.size());
   }

   @Override
   public void drawContent(int n, int n2, float f) {
      this.layoutCards();
      float row = TabbedMenuScreen.rowH();
      int hintColor = UiTheme.scaleAlpha(TEXT_SUB, 0.62F);

      if (this.addressField != null && this.addressField.getText().isEmpty() && !this.addressField.isFocused()) {
         this.drawFieldPlaceholder("IP-Adress:Port", this.addressField.xPosition, this.addressField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
      }

      if (this.usernameField != null && this.usernameField.getText().isEmpty() && !this.usernameField.isFocused()) {
         this.drawFieldPlaceholder("Username", this.usernameField.xPosition, this.usernameField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
      }

      if (this.passwordField != null && this.passwordField.getText().isEmpty() && !this.passwordField.isFocused()
            && this.selectedProtocolType != ProxyProtocolType.SOCKS4) {
         this.drawFieldPlaceholder("Password", this.passwordField.xPosition, this.passwordField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
      }

      this.drawProxyRows(n, n2);
      scrollBar.updateAndDraw();

      if (this.activePingTask != null && this.activePingTask.isDone()) {
         try {
            TuplePair tuplePair = (TuplePair)this.activePingTask.get();
            String first = (String)tuplePair.getFirst();
            Color green = Color.green;
            Color color = Color.red.darker();
            double d = Math.min(1.0, (double)((Integer)tuplePair.getSecond()).intValue() / 1000.0);
            this.setStatus(first, new Color(ColorUtil.blendColors(green, color, d)));
            this.activePingTask = null;
            ProxyEntry proxy = Augustus.getInstance().getProxy();
            if (proxy != null && getPendingProxy() != null) {
               if (proxy.matches(getPendingProxy())) {
                  this.addressField.setText(proxy.getHost() + ":" + proxy.getPort());
                  this.usernameField.setText(proxy.getUsername());
                  if (this.passwordField != null) {
                     this.passwordField.setText(proxy.getPassword());
                  }
               }
            }
         } catch (ExecutionException | InterruptedException e) {
            throw new RuntimeException(e);
         }
      }
   }

   private void drawProxyRows(int mouseX, int mouseY) {
      if (proxies.isEmpty()) {
         return;
      }
      float rowH = 34.0F;
      float pitch = 38.0F;
      float innerPad = 10.0F;
      float barReserve = scrollBar.fitsWithoutScrolling() ? innerPad : 16.0F;
      float rowX = this.listX + innerPad;
      float rowW = Math.max(40.0F, this.listW - innerPad - barReserve);
      int clampedScrollOffset = (int)scrollBar.getClampedScrollOffset();

      AugustusGuiScreen.enableScissor(
         Math.round(this.listX + 2.0F),
         Math.round(this.rowsTop),
         Math.round(this.listX + this.listW - 2.0F),
         Math.round(this.rowsBottom - 8.0F)
      );
      for (int pass = 0; pass < 2; pass++) {
         float y = this.rowsTop + (float)clampedScrollOffset;
         for (ProxyEntry proxyEntry : (Iterable<ProxyEntry>)(proxies)) {
            ProxyListEntry proxyListEntry = (ProxyListEntry)proxyListEntries.get(proxyEntry);
            if (proxyListEntry == null || proxyListEntry.isFavorite() != (pass == 0)) {
               continue;
            }
            proxyListEntry.drawEntry(mouseX, mouseY, Math.round(rowX), Math.round(y), Math.round(rowW),
                  Math.round(rowH), proxyEntry == this.selectedProxy, this);
            y += pitch;
         }
      }
      AugustusGuiScreen.disableScissor();
   }

   // ---------------------------------------------------------------------------------------------
   // Behaviour - unchanged
   // ---------------------------------------------------------------------------------------------

   public static synchronized void setPendingProxy(ProxyEntry proxyEntry) {
      pendingProxy = proxyEntry;
   }

   public static synchronized ProxyEntry getPendingProxy() {
      return pendingProxy;
   }

   public static Integer parsePortOrDefault(String str) {
      try {
         return Integer.parseInt(str);
      } catch (Exception e) {
         return -1;
      }
   }

   static {
      scrollBar = new ScrollBar(38);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      boolean passFocused = this.passwordField != null && this.passwordField.isFocused();
      if (this.selectedProxy != null && (keyCode == 211
            || keyCode == 14 && !this.addressField.isFocused() && !this.usernameField.isFocused() && !passFocused)) {
         proxies.remove(this.selectedProxy);
         proxyListEntries.remove(this.selectedProxy);
         scrollBar.setItemCount(proxies.size());
         this.selectedProxy = null;
      }
   }

   public void connectToProxy(String str, boolean flag) {
      if (str.isEmpty()) {
         this.setStatus("IP is empty!", Color.red.darker());
      } else {
         String[] parts = str.split(":");
         if (parts.length >= 2 && parts.length <= 4) {
            String[] strings2 = new String[4];
            System.arraycopy(parts, 0, strings2, 0, parts.length);
            if (!flag) {
               if (!this.usernameField.getText().isEmpty()) {
                  strings2[2] = this.usernameField.getText();
               }

               if (this.passwordField != null && !this.passwordField.getText().isEmpty()) {
                  strings2[3] = this.passwordField.getText();
               }
            }

            int n = parsePortOrDefault(strings2[1]);
            if (n == -1) {
               this.setStatus("Port is not numeric!", Color.red.darker());
            } else {
               String str4 = strings2[0];
               String str5 = strings2[2] == null ? "" : strings2[2];
               String str6 = strings2[3] == null ? "" : strings2[3];
               ProxyProtocolType selectedProtocolType2 = this.selectedProtocolType;
               pendingProxy = new ProxyEntry(str4, n, str5, str6, selectedProtocolType2);
               if (pendingProxy.getSocketAddress() == null) {
                  this.setStatus("No valid proxy format!", Color.red.darker());
                  pendingProxy = null;
               } else if (this.activePingTask == null) {
                  this.setStatus("Pinging...", Color.yellow);
                  this.previousProxy = Augustus.getInstance().getProxy();
                  this.activePingTask = CompletableFuture.supplyAsync(() -> {
                     boolean mcServerCheckEnabled2 = this.mcServerCheckEnabled;
                     ProxyEntry previousProxy2 = this.previousProxy;
                     return ProxyChecker.testAndApplyProxy(pendingProxy, previousProxy2, mcServerCheckEnabled2);
                  });
               }
            }
         } else {
            this.setStatus("Wrong proxy format! [Ip:Port] / [Ip:Port:Username] / [Ip:Port:Username:Password]", Color.red.darker());
         }
      }
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      scrollBar.handleMouseDrag((double)mouseY);
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      scrollBar.handleMouseWheel();
   }

   public ProxyManagerScreen() {
      super("Proxy Manager");
      this.mcServerCheckEnabled = false;
      this.selectedProxy = null;
      this.previousProxy = null;
      this.activePingTask = null;
      this.reinitPending = false;
      this.setStatus("Waiting...", Color.green);
   }

   private void importProxiesAsync() {
      new Thread(() -> this.showImportProxiesDialog()).start();
   }

   private void showImportProxiesDialog() {
      JFileChooser jFileChooser = new JFileChooser();
      jFileChooser.setDialogTitle("Select Proxy File");
      jFileChooser.setFileFilter(new FileNameExtensionFilter("Text Files", "txt"));
      if (jFileChooser.showOpenDialog(null) == 0) {
         File selectedFile = jFileChooser.getSelectedFile();
         try (BufferedReader reader = new BufferedReader(new FileReader(selectedFile))) {
            String str;
            while ((str = reader.readLine()) != null) {
               try {
                  if (!str.trim().isEmpty()) {
                     ProxyProtocolType selectedProtocolType2 = this.selectedProtocolType;
                     String str2 = str.trim();
                     if (str2.toLowerCase().startsWith("socks4://")) {
                        selectedProtocolType2 = ProxyProtocolType.SOCKS4;
                        str2 = str2.substring(9);
                     } else if (str2.toLowerCase().startsWith("socks5://")) {
                        selectedProtocolType2 = ProxyProtocolType.SOCKS5;
                        str2 = str2.substring(9);
                     }

                     String[] parts = str2.split(":");
                     if (parts.length >= 2) {
                        String str3 = parts[0];
                        int n2 = parsePortOrDefault(parts[1]);
                        if (n2 != -1) {
                           String str4 = parts.length > 2 ? parts[2] : "";
                           String str5 = parts.length > 3 ? parts[3] : "";
                           ProxyEntry proxyEntry = new ProxyEntry(str3, n2, str4, str5, selectedProtocolType2);
                           if (!proxies.contains(proxyEntry)) {
                              proxies.add(proxyEntry);
                              proxyListEntries.put(proxyEntry, new ProxyListEntry(proxyEntry));
                           }
                        }
                     }
                  }
               } catch (Exception e) {
                  System.err.println("Failed to parse proxy line: " + str);
               }
            }
            scrollBar.setItemCount(proxies.size());
         } catch (Exception e2) {
            e2.printStackTrace();
         }
      }
   }

   private void onImportProxiesClick(GuiButton button) {
      this.importProxiesAsync();
   }

   private void onDisconnectClick(GuiButton button) {
      this.disconnectProxy();
   }

   private void disconnectProxy() {
      Augustus.getInstance().setProxy((ProxyEntry)null);
      this.setStatus("Waiting...", Color.green);
   }

   private void onClearFieldsClick(GuiButton button) {
      this.clearInputFields();
   }

   private void clearInputFields() {
      this.addressField.setText("");
      if (this.passwordField != null) {
         this.passwordField.setText("");
      }
      this.usernameField.setText("");
      pendingProxy = null;
   }

   private void connectFromClipboard() {
      this.connectToProxy(GuiScreen.getClipboardString().replace(" ", ""), true);
   }

   private void onDeleteSelectedProxyClick(GuiButton button) {
      if (this.selectedProxy != null) {
         proxies.remove(this.selectedProxy);
         proxyListEntries.remove(this.selectedProxy);
         scrollBar.setItemCount(proxies.size());
         this.selectedProxy = null;
         AccountAuthenticator.playSuccessSound();
      } else {
         AccountAuthenticator.playFailureSound();
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (this.reinitPending) {
         this.initGui();
      }
      this.layoutCards();

      for (ProxyEntry proxyEntry : (Iterable<ProxyEntry>)(proxies)) {
         ProxyListEntry proxyListEntry = (ProxyListEntry)proxyListEntries.get(proxyEntry);
         if (proxyListEntry == null) {
            continue;
         }
         proxyListEntry.mouseClicked(mouseX, mouseY, mouseButton, this);
         if ((mouseButton == 1 || mouseButton == 0)
               && RenderUtil.isHovered((float)proxyListEntry.getRenderX(), (float)proxyListEntry.getRenderY(),
                     (float)proxyListEntry.getRenderWidth(), (float)proxyListEntry.getRenderHeight(), mouseX, mouseY)) {
            this.selectedProxy = proxyEntry;
         }
      }

      scrollBar.handleMouseClick((double)mouseX, (double)mouseY);
   }

   private void onToggleProtocolTypeClick(GuiButton button) {
      switch (this.selectedProtocolType) {
         case SOCKS4:
            this.selectedProtocolType = ProxyProtocolType.SOCKS5;
            break;
         case SOCKS5:
            this.selectedProtocolType = ProxyProtocolType.SOCKS4;
      }

      this.reinitPending = true;
      button.displayString = "Type: " + this.selectedProtocolType.getDisplayName();
   }

   private void populateFieldsFromPendingProxy() {
      if (pendingProxy != null) {
         this.addressField.setText(pendingProxy.getHost() + ":" + pendingProxy.getPort());
         this.usernameField.setText(pendingProxy.getUsername());
         if (this.passwordField != null) {
            this.passwordField.setText(pendingProxy.getPassword());
         }
      }
   }
}
