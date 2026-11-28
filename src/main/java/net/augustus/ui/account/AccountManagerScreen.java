package net.augustus.ui.account;

import com.google.common.base.Charsets;
import com.google.common.collect.Lists;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufInputStream;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.base64.Base64;
import java.awt.Color;
import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ThreadLocalRandom;
import net.augustus.Augustus;
import net.augustus.alt.AltUsernameGenerator;
import net.augustus.config.ClientSettingsConfigFile;
import net.augustus.render.RenderUtil;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.settings.property.StringProperty;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.ui.GuiButtonClickHandler;
import net.augustus.ui.GuiPasswordField;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.components.GlassMenuButton;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;
import net.augustus.utils.TuplePair;
import net.augustus.utils.file.ClientFileUtil;
import net.augustus.utils.file.ClientPaths;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.TextureUtil;
import net.minecraft.nbt.CompressedStreamTools;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.ResourceLocation;
import org.apache.commons.lang3.Validate;

/**
 * Account Manager view: Augustus-styled frosted glass workspace with 3 left-column form/action cards,
 * full-height right-column saved accounts glass container, crisp smaller Inter typography, Tabler icons,
 * and cursor-tracking hover glow.
 */
public class AccountManagerScreen extends ClientMenuTab {
   public static AccountManagerScreen instance;
   public static List randomNames = new ArrayList();
   private final List savedServers;
   public static final CopyOnWriteArrayList accounts = new CopyOnWriteArrayList();
   public final BooleanProperty removeInvalidAccounts;
   private CompletableFuture activeLoginTask;
   private final StringProperty apiKey;
   private GuiTextField emailOrTokenField;
   private AccountEntry selectedAccount;
   public static final ScrollBar scrollBar;
   private GuiPasswordField passwordField;

   private float leftX;
   private float leftW;
   private float rightX;
   private float rightW;
   private float cardPad;
   private float rowGap;
   private float colGap;
   private float labelH;
   private float signInY;
   private float signInH;
   private float emailLabelY;
   private float emailFieldY;
   private float passwordLabelY;
   private float passwordFieldY;
   private float signInButtonY;
   private float methodsY;
   private float methodsH;
   private float methodsRow1Y;
   private float methodsRow2Y;
   private float manageY;
   private float manageH;
   private float manageRow1Y;
   private float manageRow2Y;
   private float detailY;
   private float detailH;
   private float listX;
   private float listW;
   private float listTop;
   private float listHeadingY;
   private float rowsTop;
   private float rowsBottom;
   private float loginX;
   private float loginW;
   private float generateX;
   private float generateW;

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.USER;
   }

   private void layoutCards() {
      float pageX = TabbedMenuScreen.contentX();
      float pageW = TabbedMenuScreen.contentW();
      float gap = TabbedMenuScreen.gap();
      float row = TabbedMenuScreen.rowH();
      float top = TabbedMenuScreen.bodyTop();

      this.cardPad = clamp(Math.round(row * 0.45F), 9.0F, 12.0F);
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

      // Card 1 - Sign in form
      this.signInY = top;
      this.emailLabelY = this.signInY + this.cardPad;
      this.emailFieldY = this.emailLabelY + this.labelH + 4.0F;
      this.passwordLabelY = this.emailFieldY + row + this.rowGap + 1.0F;
      this.passwordFieldY = this.passwordLabelY + this.labelH + 4.0F;
      this.signInButtonY = this.passwordFieldY + row + this.rowGap + 2.0F;
      this.signInH = this.signInButtonY + row + this.cardPad - this.signInY;

      float innerW = this.leftW - this.cardPad * 2.0F;
      this.loginW = Math.round((innerW - this.colGap) * 0.50F);
      this.loginX = this.leftX + this.cardPad;
      this.generateX = this.loginX + this.loginW + this.colGap;
      this.generateW = Math.max(40.0F, innerW - this.loginW - this.colGap);

      // Card 2 - Alternative sign-in methods
      this.methodsY = this.signInY + this.signInH + gap;
      this.methodsRow1Y = this.methodsY + this.cardPad;
      this.methodsRow2Y = this.methodsRow1Y + row + this.rowGap;
      this.methodsH = this.methodsRow2Y + row + this.cardPad - this.methodsY;

      // Card 3 - Housekeeping & Altshop
      this.manageY = this.methodsY + this.methodsH + gap;
      this.manageRow1Y = this.manageY + this.cardPad;
      this.manageRow2Y = this.manageRow1Y + row + this.rowGap;
      this.manageH = this.manageRow2Y + row + this.cardPad - this.manageY;

      // Card 4 - Selected account details when vertical space permits
      float linePitch = Math.max(menuSmallHeight() + 2.5F, 11.5F);
      float detailNeeded = this.cardPad * 2.0F + 6.0F * linePitch + 24.0F;
      float remaining = TabbedMenuScreen.bodyBottom() - (this.manageY + this.manageH + gap);
      this.detailY = this.manageY + this.manageH + gap;
      this.detailH = remaining < 78.0F ? 0.0F : Math.min(remaining, detailNeeded);

      // Right Panel - Full-height frosted glass container
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
      this.menuGlassPanel(this.leftX, this.signInY, this.leftW, this.signInH, 0.0F, 1.0F);
      this.menuGlassPanel(this.leftX, this.methodsY, this.leftW, this.methodsH, 0.0F, 1.0F);
      this.menuGlassPanel(this.leftX, this.manageY, this.leftW, this.manageH, 0.0F, 1.0F);

      if (this.detailH > 0.0F && this.selectedAccount != null) {
         this.menuGlassPanel(this.leftX, this.detailY, this.leftW, this.detailH, 0.0F, 1.0F);
      }

      menuSectionLabel("EMAIL OR TOKEN", this.leftX + this.cardPad, this.emailLabelY, 1.0F);
      menuSectionLabel("PASSWORD", this.leftX + this.cardPad, this.passwordLabelY, 1.0F);

      // Right full-height glass panel
      this.menuGlassPanel(this.listX, this.listTop, this.listW, Math.max(0.0F, this.rowsBottom - this.listTop), 0.0F, 1.0F);

      menuSectionLabel("ACCOUNTS", this.listX + this.cardPad, this.listHeadingY, 1.0F);
      String count = String.valueOf(accounts.size());
      InterFontUtil capFace = menuCaptionFont();
      if (capFace != null) {
         float cw = capFace.getStringWidth(count);
         capFace.drawStringWithShadow(count, (double)(this.listX + this.listW - this.cardPad - cw),
               (double)this.listHeadingY, UiTheme.scaleAlpha(TEXT_SUB, 0.80F));
      }

      if (accounts.isEmpty()) {
         this.menuEmptyState(
            this.listX,
            this.rowsTop,
            this.listW,
            Math.max(0.0F, this.rowsBottom - this.rowsTop - 8.0F),
            LucideIcon.USERS,
            "No accounts yet",
            "Sign in with the form on the left, saved accounts stay here.",
            false
         );
      }
   }

   @Override
   public void initContent() {
      super.initContent();
      this.loadSavedServerList();
      HashMap map = new HashMap();

      for (ServerData serverData : (Iterable<ServerData>)(this.savedServers)) {
         ResourceLocation location = new ResourceLocation("servers/" + serverData.serverIP + "/icon");
         map.put(serverData.serverIP, this.loadServerIconTexture(serverData, location));
      }

      if (accounts.isEmpty()) {
         Augustus.getInstance().getConfigManager().accountConfig.load();
      }

      for (AccountEntry accountEntry : (Iterable<AccountEntry>)(accounts)) {
         accountEntry.refreshServerIcons(map);
      }

      this.setStatus("Waiting...", Color.GREEN);
      this.layoutCards();

      float row = TabbedMenuScreen.rowH();
      float innerX = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;
      float columnW = (innerW - this.colGap) * 0.5F;

      this.emailOrTokenField = this.addTextField(Math.round(innerX), Math.round(this.emailFieldY),
            Math.round(innerW), Math.round(row));
      this.emailOrTokenField.setCustomFont(menuBodyFont());
      this.passwordField = this.addPasswordField(Math.round(innerX), Math.round(this.passwordFieldY),
            Math.round(innerW), Math.round(row));
      this.passwordField.setCustomFont(menuBodyFont());

      this.addGlassButton(Math.round(this.loginX), Math.round(this.signInButtonY), Math.round(this.loginW),
            Math.round(row), "Login", button -> this.onLoginButtonClick(button))
            .withIcon(LucideIcon.LOGIN)
            .withVariant(GlassMenuButton.Variant.PRIMARY);
      this.addGlassButton(Math.round(this.generateX), Math.round(this.signInButtonY), Math.round(this.generateW),
            Math.round(row), "Generate", button -> this.onGenerateAlteningClick(button))
            .withIcon(LucideIcon.REFRESH_CW);

      GuiButtonClickHandler reconnectClick = button -> {
         String[] strings = new String[]{"igd", ""};
         String str4 = "<?xml version=\"1.0\" encoding=\"utf-8\"?><s:Envelope xmlns:s=\"http://schemas.xmlsoap.org/soap/envelope/\" s:encodingStyle=\"http://schemas.xmlsoap.org/soap/encoding/\"><s:Body><u:ForceTermination xmlns:u=\"urn:schemas-upnp-org:service:WANIPConnection:1\" /></s:Body></s:Envelope>";

         for (String str5 : strings) {
            try {
               String str6 = "http://fritz.box:49000/" + str5 + "upnp/control/WANIPConn1";
               HttpURLConnection conn = (HttpURLConnection)new URL(str6).openConnection();
               conn.setRequestMethod("POST");
               conn.setRequestProperty("Content-Type", "text/xml; charset=\"utf-8\"");
               conn.setRequestProperty("Connection", "close");
               conn.setRequestProperty("Content-Length", String.valueOf(str4.length()));
               conn.setRequestProperty("HOST", "fritz.box:49000");
               conn.setRequestProperty("SOAPACTION", "\"urn:schemas-upnp-org:service:WANIPConnection:1#ForceTermination\"");
               conn.setDoOutput(true);

               try (OutputStream outputStream = conn.getOutputStream()) {
                  outputStream.write(str4.getBytes("UTF-8"));
               }

               int responseCode = conn.getResponseCode();
               System.out.println("Response Code: " + responseCode);
            } catch (Exception e) {
               System.err.println("Fehler: " + e.getMessage());
            }
         }
      };

      this.addGlassButton(Math.round(innerX), Math.round(this.methodsRow1Y), Math.round(columnW), Math.round(row),
            "Reconnect", reconnectClick).withIcon(LucideIcon.REFRESH_CW);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.methodsRow1Y),
            Math.round(columnW), Math.round(row), "Clipboard login", button4 -> this.onClipboardLoginClick(button4))
            .withIcon(LucideIcon.CLIPBOARD);
      this.addGlassButton(Math.round(innerX), Math.round(this.methodsRow2Y), Math.round(columnW), Math.round(row),
            "Device code login", button5 -> this.onDeviceCodeLoginClick(button5))
            .withIcon(LucideIcon.DEVICE_DESKTOP);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.methodsRow2Y),
            Math.round(columnW), Math.round(row), "External browser login", button6 -> this.onExternalBrowserLoginClick(button6))
            .withIcon(LucideIcon.GLOBE);

      this.addGlassButton(Math.round(innerX), Math.round(this.manageRow1Y), Math.round(columnW), Math.round(row),
            "Delete selected Alt", button7 -> this.onDeleteSelectedClick(button7))
            .withIcon(LucideIcon.TRASH_2)
            .withVariant(GlassMenuButton.Variant.DANGER);

      boolean remInv = Boolean.TRUE.equals(this.removeInvalidAccounts.getValue());
      String toggleLabel = "Remove invalid: " + (remInv ? "§aTrue" : "§cFalse");
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.manageRow1Y),
            Math.round(columnW), Math.round(row), toggleLabel, button8 -> this.onToggleRemoveInvalidClick(button8))
            .withIcon(LucideIcon.ALERT_TRIANGLE)
            .withToggleState(remInv);

      this.addGlassButton(Math.round(innerX), Math.round(this.manageRow2Y), Math.round(columnW), Math.round(row),
            "Link Premium IRC", button9 -> this.onLinkSelectedPremiumClick(button9))
            .withIcon(LucideIcon.LINK)
            .withVariant(GlassMenuButton.Variant.PRIMARY);

      GuiButtonClickHandler altshopClick = button10 -> {
         try {
            Desktop.getDesktop().browse(new URI("https://alts.fernan.club/"));
         } catch (URISyntaxException | IOException e2) {
            e2.printStackTrace();
         }
      };
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.manageRow2Y), Math.round(columnW), Math.round(row),
            "Fernan's Altshop", altshopClick).withIcon(LucideIcon.SHOPPING_CART);

      scrollBar.setBounds(
         Math.round(this.listX + this.listW - 10.0F),
         Math.round(this.rowsTop),
         Math.round(this.listX + this.listW - 5.0F),
         Math.round(this.rowsBottom - 10.0F)
      );
      scrollBar.setItemCount(accounts.size());
   }

   @Override
   public void drawContent(int n, int n2, float f) {
      super.drawContent(n, n2, f);
      this.layoutCards();
      float row = TabbedMenuScreen.rowH();
      int hintColor = UiTheme.scaleAlpha(TEXT_SUB, 0.62F);

      if (this.emailOrTokenField != null && this.emailOrTokenField.getText().isEmpty()
            && !this.emailOrTokenField.isFocused()) {
         this.drawFieldPlaceholder("Email / Email:Password", this.emailOrTokenField.xPosition,
               this.emailOrTokenField.yPosition, Math.round(row), hintColor, menuBodyFont(), 8.0F);
      }

      if (this.passwordField != null && this.passwordField.getText().isEmpty() && !this.passwordField.isFocused()) {
         this.drawFieldPlaceholder("Password", this.passwordField.xPosition, this.passwordField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
      }

      this.drawAccountRows(n, n2);
      this.drawSelectedAccountDetails();
      scrollBar.updateAndDraw();
   }

   private void drawAccountRows(int mouseX, int mouseY) {
      if (accounts.isEmpty()) {
         return;
      }
      float rowH = 38.0F;
      float pitch = 42.0F;
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
         for (AccountEntry accountEntry : (Iterable<AccountEntry>)(accounts)) {
            if (accountEntry.isMarked() != (pass == 0)) {
               continue;
            }
            accountEntry.drawEntry(Math.round(rowX), Math.round(y), Math.round(rowW), Math.round(rowH),
                  mouseX, mouseY, accountEntry == this.selectedAccount, this);
            y += pitch;
         }
      }
      AugustusGuiScreen.disableScissor();
   }

   private void drawSelectedAccountDetails() {
      if (this.detailH <= 0.0F || this.selectedAccount == null) {
         return;
      }
      InterFontUtil face = menuSmallFont();
      if (face == null) {
         return;
      }
      float paragraph = Math.max(face.getFontHeight() + 2.5F, 11.5F);
      float x = this.leftX + this.cardPad;
      float right = this.leftX + this.leftW - this.cardPad;
      float y = this.detailY + this.cardPad;

      menuSectionLabel("ACCOUNT DETAILS", x, y, 1.0F);
      y += menuCaptionHeight() + 5.0F;
      this.menuDetailLine("§7Name: §f" + this.selectedAccount.getUsername(), x, y, right);
      y += paragraph;
      this.menuDetailLine("§7Login type: §f" + this.selectedAccount.getLoginType().getDisplayName(), x, y, right);
      y += paragraph;
      String state = this.selectedAccount.isWorking() ? "§aFunctional" : "§cInvalid";
      this.menuDetailLine("§7Account state: §f" + state, x, y, right);
      y += paragraph;
      DateFormat dateInstance = DateFormat.getDateInstance(2);
      DateFormat timeInstance = DateFormat.getTimeInstance(3);
      String str6 = dateInstance.format(this.selectedAccount.getLastLoginTime());
      String str7 = timeInstance.format(this.selectedAccount.getLastLoginTime());
      this.menuDetailLine("§7Last login: §f" + str6 + " " + str7, x, y, right);
      y += paragraph;
      this.menuDetailLine("§7Playtime: §f" + this.selectedAccount.getPlayTimeMillis() / 1000L / 60L + " min", x, y, right);
      y += paragraph + 2.0F;

      int banned = this.countLoadedServerIcons(this.selectedAccount.getBannedServerIcons().values());
      if (banned > 0 && y + paragraph < this.detailY + this.detailH - 8.0F) {
         this.menuDetailLine("§7Banned on servers:", x, y, right);
         y += paragraph;
         this.drawServerIconRow(this.selectedAccount.getBannedServerIcons().values(), Math.round(x), Math.round(right),
               Math.round(y), 10);
         y += 14.0F;
      }

      int unbanned = this.countLoadedServerIcons(this.selectedAccount.getUnbannedServerIcons().values());
      if (unbanned > 0 && y + paragraph < this.detailY + this.detailH - 8.0F) {
         this.menuDetailLine("§7Unbanned on servers:", x, y, right);
         y += paragraph;
         this.drawServerIconRow(this.selectedAccount.getUnbannedServerIcons().values(), Math.round(x), Math.round(right),
               Math.round(y), 10);
      }
   }

   private void menuDetailLine(String text, float x, float y, float right) {
      InterFontUtil face = menuSmallFont();
      if (face == null) {
         return;
      }
      String line = text;
      float available = right - x;
      if (face.getStringWidthFormatted(line) > available) {
         String plain = InterFontUtil.stripFormatting(line);
         line = face.trimToWidth(plain, Math.max(0.0F, available - face.getStringWidth("..."))) + "...";
      }
      face.drawStringFormatted(line, (double)x, (double)y, Color.white.getRGB(), true);
   }

   private void drawServerIconRow(Collection items, int startX, int right, int y, int iconSize) {
      int cursorX = startX;
      for (TuplePair tuplePair : (Iterable<TuplePair>)(items)) {
         if (tuplePair.getSecond() != null && cursorX + iconSize <= right) {
            GlStateManager.color(1.0F, 1.0F, 1.0F);
            this.drawDetailServerIcon(cursorX, y, (ResourceLocation)tuplePair.getFirst(), iconSize);
            cursorX += iconSize + 3;
         }
      }
   }

   private void drawDetailServerIcon(int n, int n2, ResourceLocation location, int size) {
      this.mc.getTextureManager().bindTexture(location);
      GlStateManager.enableBlend();
      Gui.drawModalRectWithCustomSizedTexture(n, n2, 0.0F, 0.0F, size, size, (float)size, (float)size);
      GlStateManager.disableBlend();
   }

   public void loadSavedServerList() {
      try {
         this.savedServers.clear();
         NBTTagCompound tag = CompressedStreamTools.read(new File(this.mc.mcDataDir, "servers.dat"));
         if (tag == null) {
            return;
         }
         NBTTagList tagList = tag.getTagList("servers", 10);
         for (int i = 0; i < tagList.tagCount(); i++) {
            this.savedServers.add(ServerData.getServerDataFromNBTCompound(tagList.getCompoundTagAt(i)));
         }
      } catch (Exception e) {
      }
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      this.layoutCards();

      for (AccountEntry accountEntry : (Iterable<AccountEntry>)(accounts)) {
         accountEntry.mouseClicked(mouseX, mouseY, mouseButton);
         if ((mouseButton == 1 || mouseButton == 0)
               && RenderUtil.isHovered((float)accountEntry.getRenderX(), (float)accountEntry.getRenderY(),
                     (float)accountEntry.getRenderWidth(), (float)accountEntry.getRenderHeight(), mouseX, mouseY)) {
            this.selectedAccount = accountEntry;
         }
      }

      scrollBar.handleMouseClick((double)mouseX, (double)mouseY);
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      super.keyTyped(typedChar, keyCode);
      if ((keyCode == 211 || keyCode == 14) && this.selectedAccount != null && !this.emailOrTokenField.isFocused()
            && !this.passwordField.isFocused()) {
         accounts.remove(this.selectedAccount);
         scrollBar.setItemCount(accounts.size());
         this.selectedAccount = null;
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

   public AccountManagerScreen() {
      super("Account Manager");
      this.activeLoginTask = null;
      this.savedServers = Lists.newArrayList();
      this.apiKey = new StringProperty(ClientSettingsConfigFile.abstractFeature, "API-Key", "");
      this.removeInvalidAccounts = new BooleanProperty(ClientSettingsConfigFile.abstractFeature,
            "RemoveInvalidAccounts", false);
      instance = this;
      this.loadRandomNames();
   }

   static {
      scrollBar = new ScrollBar(42);
   }

   // ---------------------------------------------------------------------------------------------
   // Behaviour - unchanged
   // ---------------------------------------------------------------------------------------------

   private void loginFromClipboard() {
      if (this.activeLoginTask == null || this.activeLoginTask.isDone()) {
         String clipboardString = GuiScreen.getClipboardString();
         String[] parts = clipboardString.split(":");
         if (parts.length == 1 && parts[0].contains("@alt")) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginClipboardAlteningToken(parts)));
         } else if (clipboardString.startsWith("eyJra") && clipboardString.length() > 16) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginClipboardAccessToken(clipboardString)));
         } else if (parts.length == 1) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginClipboardEasyMc(clipboardString)));
         } else if (parts.length == 2) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> {
               AccountAuthenticator.loginMicrosoftCredentials(parts[0], parts[1]);
            }));
         }
      }
   }

   private void loadRandomNames() {
      randomNames.clear();
      String str = Augustus.getInstance().getName().toLowerCase() + "/names";
      ClientFileUtil.extractResource("assets/augustus/RandomNames.txt", str, false);

      try {
         BufferedReader reader = new BufferedReader(new FileReader(ClientPaths.CLIENT_DIR + "/names/RandomNames.txt"));
         String str3;
         while ((str3 = reader.readLine()) != null) {
            if (!str3.equals("")) {
               randomNames.add(str3.trim());
            }
         }
         reader.close();
      } catch (IOException e) {
      }

      Collections.shuffle(randomNames);
   }

   private static void loginAlteningApiKey(String str) {
      AccountAuthenticator.generateAndLoginTheAltening(str);
   }

   private void loginInputCredentials() {
      AccountAuthenticator.loginMicrosoftCredentials(this.emailOrTokenField.getText(), this.passwordField.getText());
   }

   private void performFieldLogin() {
      if (this.activeLoginTask == null || this.activeLoginTask.isDone()) {
         if (this.emailOrTokenField.getText().isEmpty() && this.passwordField.getText().isEmpty()) {
            CompletableFuture completableFuture = CompletableFuture.runAsync(() -> {
               String str;
               if (ThreadLocalRandom.current().nextBoolean()) {
                  str = SyllableUsernameGenerator.generateUsername();
               } else {
                  str = AltUsernameGenerator.generateUsername(16);
               }
               AccountAuthenticator.loginCracked(str, false);
            });
            this.setActiveLoginTask(completableFuture);
         } else if (!this.emailOrTokenField.getText().isEmpty() && !this.passwordField.getText().isEmpty()) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> this.loginInputCredentials()));
         } else if (!this.emailOrTokenField.getText().isEmpty() && this.passwordField.getText().isEmpty()) {
            String str2 = this.emailOrTokenField.getText().trim();
            if (str2.contains("@alt")) {
               this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginInputAlteningToken(str2)));
            } else if (str2.contains(":") && str2.contains("@")) {
               String[] parts = str2.split(":");
               this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginSplitCredentials(parts)));
            } else if (str2.startsWith("eyJra") && str2.length() > 16) {
               this.setActiveLoginTask(CompletableFuture.runAsync(() -> {
                  AccountAuthenticator.loginWithAccessToken(str2);
               }));
            } else {
               this.setActiveLoginTask(CompletableFuture.runAsync(() -> {
                  AccountAuthenticator.loginCracked(str2, true);
               }));
            }
         }
      }
   }

   private void onDeleteSelectedClick(GuiButton button) {
      if (this.selectedAccount != null) {
         accounts.remove(this.selectedAccount);
         scrollBar.setItemCount(accounts.size());
         this.selectedAccount = null;
         AccountAuthenticator.playSuccessSound();
      } else {
         AccountAuthenticator.playFailureSound();
      }
   }

   private void onLinkSelectedPremiumClick(GuiButton button) {
      if (this.selectedAccount != null && net.augustus.irc.IrcManager.isMicrosoftType(this.selectedAccount.getLoginType())) {
         net.augustus.irc.IrcManager.getInstance().linkAccountEntry(this.selectedAccount);
         this.setStatus("Vinculando " + this.selectedAccount.getUsername() + " al IRC...", Color.GREEN);
         AccountAuthenticator.playSuccessSound();
      } else {
         net.augustus.irc.IrcManager.getInstance().startInteractiveMicrosoftLink();
         this.setStatus("Abriendo login Microsoft para IRC...", Color.ORANGE);
      }
   }

   private static void loginClipboardEasyMc(String str) {
      AccountAuthenticator.loginEasyMc(str);
   }

   private void onExternalBrowserLoginClick(GuiButton button) {
      this.startExternalBrowserLogin();
   }

   public void setActiveLoginTask(CompletableFuture completableFuture) {
      this.activeLoginTask = completableFuture;
   }

   private TuplePair loadServerIconTexture(ServerData serverData, ResourceLocation location) {
      DynamicTexture dynamicTexture = null;
      if (serverData.getBase64EncodedIconData() == null) {
         this.mc.getTextureManager().deleteTexture(location);
         dynamicTexture = null;
      } else {
         ByteBuf buf = Unpooled.copiedBuffer(serverData.getBase64EncodedIconData(), Charsets.UTF_8);
         ByteBuf buf2 = Base64.decode(buf);

         BufferedImage image;
         try {
            image = TextureUtil.readBufferedImage(new ByteBufInputStream(buf2));
            Validate.validState(image.getWidth() == 64, "Must be 64 pixels wide", new Object[0]);
            Validate.validState(image.getHeight() == 64, "Must be 64 pixels high", new Object[0]);
         } catch (Throwable e) {
            serverData.setBase64EncodedIconData((String)null);
            return new TuplePair(location, null);
         } finally {
            buf.release();
            buf2.release();
         }

         dynamicTexture = new DynamicTexture(image.getWidth(), image.getHeight());
         this.mc.getTextureManager().loadTexture(location, dynamicTexture);

         try {
            image.getRGB(0, 0, image.getWidth(), image.getHeight(), dynamicTexture.getTextureData(), 0,
                  image.getWidth());
         } catch (Throwable e2) {
         }

         dynamicTexture.updateDynamicTexture();
      }

      return new TuplePair(location, dynamicTexture);
   }

   private static void loginClipboardAccessToken(String str) {
      AccountAuthenticator.loginWithAccessToken(str);
   }

   private static void loginSplitCredentials(String[] strings) {
      AccountAuthenticator.loginMicrosoftCredentials(strings[0], strings[1]);
   }

   public void startExternalBrowserLogin() {
      CompletableFuture.runAsync(() -> AccountAuthenticator.loginExternalBrowser());
   }

   private static void loginClipboardAlteningToken(String[] strings) {
      AccountAuthenticator.loginTheAlteningToken(strings[0]);
   }

   public CompletableFuture getActiveLoginTask() {
      return this.activeLoginTask;
   }

   private void onClipboardLoginClick(GuiButton button) {
      this.loginFromClipboard();
   }

   private void onLoginButtonClick(GuiButton button) {
      this.performFieldLogin();
   }

   private void onGenerateAlteningClick(GuiButton button) {
      this.generateAlteningAccount();
   }

   private void onToggleRemoveInvalidClick(GuiButton button) {
      boolean next = !Boolean.TRUE.equals(this.removeInvalidAccounts.getValue());
      this.removeInvalidAccounts.setValue(Boolean.valueOf(next));
      button.displayString = "Remove invalid: " + (next ? "§aTrue" : "§cFalse");
      if (button instanceof GlassMenuButton) {
         ((GlassMenuButton)button).setToggleState(next);
      }
   }

   private void onDeviceCodeLoginClick(GuiButton button) {
      this.startDeviceCodeLogin();
   }

   public void startDeviceCodeLogin() {
      CompletableFuture.runAsync(() -> AccountAuthenticator.loginDeviceCode());
   }

   private void generateAlteningAccount() {
      String clipboardString = getClipboardString();
      if (clipboardString.contains("api-")) {
         this.apiKey.setValue(clipboardString);
      }

      if (this.activeLoginTask == null || this.activeLoginTask.isDone()) {
         String value = (String)this.apiKey.getValue();
         if (value != null && !value.isEmpty()) {
            this.setActiveLoginTask(CompletableFuture.runAsync(() -> loginAlteningApiKey(value)));
         }
      }
   }

   private int countLoadedServerIcons(Collection items) {
      int n = 0;
      for (TuplePair tuplePair : (Iterable<TuplePair>)(items)) {
         if (tuplePair.getSecond() != null) {
            n++;
         }
      }
      return n;
   }

   private static void loginInputAlteningToken(String str) {
      AccountAuthenticator.loginTheAlteningToken(str);
   }
}
