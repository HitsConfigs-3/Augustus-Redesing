package net.minecraft.client.gui;

import com.google.common.base.Splitter;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import java.awt.Toolkit;
import java.awt.datatransfer.ClipboardOwner;
import java.awt.datatransfer.DataFlavor;
import java.awt.datatransfer.StringSelection;
import java.awt.datatransfer.Transferable;
import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.events.EventRenderGuiBackground;
import net.augustus.events.EventInventoryClick;
import net.augustus.utils.shader.BackgroundShader;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ClientManagers;
import net.augustus.utils.input.MouseInputUtil;
import net.augustus.events.listeners.RenderGuiBackgroundListener;
import net.augustus.events.listeners.InventoryClickListener;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.render.MenuScreenSkin;
import net.augustus.ui.modern.render.UiAnimation;
import net.minecraft.client.multiplayer.GuiConnecting;
import net.augustus.Augustus;
import net.augustus.utils.render.GlStateSaver;
import net.augustus.events.EventManager;
import net.augustus.utils.input.ToggleUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import net.minecraft.entity.EntityList;
import net.minecraft.event.ClickEvent;
import net.minecraft.event.HoverEvent;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTException;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.stats.Achievement;
import net.minecraft.stats.StatBase;
import net.minecraft.stats.StatList;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

public abstract class GuiScreen extends Gui implements GuiYesNoCallback {
   private final GlStateSaver glStateSaver = GlStateSaver.create();
   public static final Set NO_TOGGLE_SCREENS = new HashSet();
   private static final Logger LOGGER = LogManager.getLogger();
   private static final Set PROTOCOLS = Sets.newHashSet(new String[]{"http", "https"});
   private static final Splitter NEWLINE_SPLITTER = Splitter.on('\n');
   public Minecraft mc;
   public RenderItem itemRender;
   public int width;
   public int height;
   public List buttonList = Lists.newArrayList();
   public List labelList = Lists.newArrayList();
   public boolean allowUserInput;
   public FontRenderer fontRendererObj;
   private GuiButton selectedButton;
   private int eventButton;
   private long lastMouseEvent;
   private int touchValue;
   private URI clickedLinkURI;
   private boolean allowNativeMouseClick = false;
   public final GuiScreen parentScreen;
   private boolean leftMouseHeld = false;
   private boolean leftMouseSyntheticDown = false;
   private boolean rightMouseHeld = false;
   private boolean rightMouseSyntheticDown = false;

   public GuiScreen(GuiScreen screen) {
      this.parentScreen = screen;
   }

   public void renderToolTip(ItemStack stack, int n, int n2) {
      if (!ClientManagers.moduleManager.antiStrike.shouldHideItemNames()) {
         List tooltip = stack.getTooltip(this.mc.thePlayer, this.mc.gameSettings.advancedItemTooltips);

         for (int i = 0; i < tooltip.size(); i++) {
            if (i == 0) {
               tooltip.set(i, stack.getRarity().rarityColor + (String)tooltip.get(i));
            } else {
               tooltip.set(i, EnumChatFormatting.GRAY + (String)tooltip.get(i));
            }
         }

         this.drawHoveringText(tooltip, n, n2);
      }
   }

   public void actionPerformed(GuiButton button) throws IOException {
   }

   public void drawDefaultBackground() {
      this.drawWorldBackground(0);
   }

   public void confirmClicked(boolean flag, int n) {
      if (n == 31102009) {
         if (flag) {
            this.openWebLink(this.clickedLinkURI);
         }

         this.clickedLinkURI = null;
         this.mc.displayGuiScreen(this);
      }
   }

   public void drawScreen(int mouseX, int mouseY, float partialTicks) {
      for (int i = 0; i < this.buttonList.size(); i++) {
         ((GuiButton)this.buttonList.get(i)).drawButton(this.mc, mouseX, mouseY);
      }

      for (int j = 0; j < this.labelList.size(); j++) {
         ((GuiLabel)this.labelList.get(j)).drawLabel(this.mc, mouseX, mouseY);
      }
   }

   public static boolean isKeyComboCtrlC(int n) {
      return n == 46 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   /**
    * Background for a screen drawn <em>over a loaded world</em>.
    *
    * <p>The dimmed world gradient is the right answer for the screens that live inside a game - the
    * inventory, a chest, the chat, a book - and it is untouched for them. The pause menu and every menu
    * screen opened from it are the exception: they are in-game screens that belong to the menu, and the user
    * asked for the pause menu to show the same backdrop as the main menu, so they draw the menu backdrop
    * while they are open.</p>
    *
    * <p>The {@code RenderGuiBackground} event is still posted first and exactly once, for every screen: the
    * Custom GUI BG Color module claims screens through it and paints its own colour or blur, and when it
    * claims one, its choice wins over both the gradient and the menu backdrop - a per-screen background
    * setting the user made must not be silently overridden by this pass.</p>
    */
   public void drawWorldBackground(int n) {
      if (this.mc.theWorld != null) {
         if (!RenderGuiBackgroundListener.postRenderGuiBackground(this).isCancelled()) {
            this.ensureFrameClock();
            // Always show the real Minecraft world, lighting, and camera when in-game (Escape menu & in-game submenus).
            if (this instanceof GuiIngameMenu) {
               this.drawGradientRect(0, 0, this.width, this.height, UiTheme.withAlpha(UiTheme.menuScrimTopArgb(), 0x38), UiTheme.withAlpha(UiTheme.menuScrimBottomArgb(), 0x54));
            } else {
               this.drawGradientRect(0, 0, this.width, this.height, UiTheme.withAlpha(UiTheme.menuScrimTopArgb(), 0x56), UiTheme.withAlpha(UiTheme.menuScrimBottomArgb(), 0x74));
            }
         }
      } else {
         this.drawBackground(n);
      }
   }

   public boolean handleComponentClick(IChatComponent component) {
      if (component == null) {
         return false;
      } else {
         ClickEvent chatClickEvent = component.getChatStyle().getChatClickEvent();
         if (isShiftKeyDown()) {
            if (component.getChatStyle().getInsertion() != null) {
               this.setText(component.getChatStyle().getInsertion(), false);
            }
         } else if (chatClickEvent != null) {
            if (chatClickEvent.getAction() == ClickEvent.Action.OPEN_URL) {
               if (!this.mc.gameSettings.chatLinks) {
                  return false;
               }

               try {
                  URI uri = new URI(chatClickEvent.getValue());
                  String scheme = uri.getScheme();
                  if (scheme == null) {
                     throw new URISyntaxException(chatClickEvent.getValue(), "Missing protocol");
                  }

                  if (!PROTOCOLS.contains(scheme.toLowerCase())) {
                     throw new URISyntaxException(chatClickEvent.getValue(), "Unsupported protocol: " + scheme.toLowerCase());
                  }

                  if (this.mc.gameSettings.chatLinksPrompt) {
                     this.clickedLinkURI = uri;
                     this.mc.displayGuiScreen((GuiScreen)new GuiConfirmOpenLink(this, chatClickEvent.getValue(), 31102009, false));
                  } else {
                     this.openWebLink(uri);
                  }
               } catch (URISyntaxException e) {
                  LOGGER.error("Can't open url for " + chatClickEvent, e);
               }
            } else if (chatClickEvent.getAction() == ClickEvent.Action.OPEN_FILE) {
               this.openWebLink(new File(chatClickEvent.getValue()).toURI());
            } else if (chatClickEvent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
               this.setText(chatClickEvent.getValue(), true);
            } else if (chatClickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
               this.sendChatMessage(chatClickEvent.getValue(), false);
            } else {
               LOGGER.error("Don't know how to handle " + chatClickEvent);
            }

            return true;
         }

         return false;
      }
   }

   public static boolean isKeyComboCtrlV(int n) {
      return n == 47 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public void drawCreativeTabHoveringText(String str, int n, int n2) {
      this.drawHoveringText(Arrays.asList(str), n, n2);
   }

   private void simulateMouseClick(int n) throws IOException {
      if (!ClientManagers.moduleManager.invManager.isToggled() || !ClientManagers.moduleManager.invManager.isBlockingGuiInput() || this.allowNativeMouseClick) {
         int n2 = Mouse.getEventX() * this.width / this.mc.displayWidth;
         int n3 = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
         if (!this.mc.gameSettings.touchscreen || this.touchValue++ <= 0) {
            this.eventButton = n;
            this.lastMouseEvent = Minecraft.getSystemTime();
            this.mouseClicked(n2, n3, this.eventButton);
         }
      }
   }

   public void onGuiClosed() {
   }

   public static String getClipboardString() {
      try {
         Transferable contents = Toolkit.getDefaultToolkit().getSystemClipboard().getContents(null);
         if (contents != null && contents.isDataFlavorSupported(DataFlavor.stringFlavor)) {
            return (String)contents.getTransferData(DataFlavor.stringFlavor);
         }
      } catch (Exception e) {
      }

      return "";
   }

   private void simulateMouseRelease(int n) {
      if (!ClientManagers.moduleManager.invManager.isToggled() || !ClientManagers.moduleManager.invManager.isBlockingGuiInput() || this.allowNativeMouseClick) {
         int n2 = Mouse.getEventX() * this.width / this.mc.displayWidth;
         int n3 = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
         if (!this.mc.gameSettings.touchscreen || --this.touchValue <= 0) {
            this.eventButton = -1;
            this.mouseReleased(n2, n3, n);
         }
      }
   }

   public boolean isAllowNativeMouseClick() {
      return this.allowNativeMouseClick;
   }

   public void setWorldAndResolution(Minecraft mc2, int width2, int height2) {
      this.mc = mc2;
      this.itemRender = mc2.getRenderItem();
      this.fontRendererObj = mc2.fontRendererObj;
      this.width = width2;
      this.height = height2;
      this.buttonList.clear();
      this.initGui();
   }

   public static boolean isShiftKeyDown() {
      return Keyboard.isKeyDown(42) || Keyboard.isKeyDown(54);
   }

   public void updateScreen() {
   }

   public void sendChatMessage(String str, boolean flag) {
      if (flag) {
         this.mc.ingameGUI.getChatGUI().addToSentMessages(str);
      }

      this.mc.thePlayer.sendChatMessage(str);
   }

   public static boolean isKeyComboCtrlA(int n) {
      return n == 30 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public GuiScreen() {
      this.parentScreen = null;
   }

   public void handleComponentHover(IChatComponent component, int n, int n2) {
      if (component != null && component.getChatStyle().getChatHoverEvent() != null) {
         HoverEvent chatHoverEvent = component.getChatStyle().getChatHoverEvent();
         if (chatHoverEvent.getAction() == net.minecraft.event.HoverEvent.Action.SHOW_ITEM) {
            ItemStack stack = null;

            try {
               NBTTagCompound tagFromJson = JsonToNBT.getTagFromJson(chatHoverEvent.getValue().getUnformattedText());
               if (tagFromJson instanceof NBTTagCompound) {
                  stack = ItemStack.loadItemStackFromNBT(tagFromJson);
               }
            } catch (NBTException e) {
            }

            if (stack != null) {
               this.renderToolTip(stack, n, n2);
            } else {
               this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Item!", n, n2);
            }
         } else if (chatHoverEvent.getAction() == net.minecraft.event.HoverEvent.Action.SHOW_ENTITY) {
            if (this.mc.gameSettings.advancedItemTooltips) {
               try {
                  NBTTagCompound tagFromJson2 = JsonToNBT.getTagFromJson(chatHoverEvent.getValue().getUnformattedText());
                  if (tagFromJson2 instanceof NBTTagCompound) {
                     ArrayList list = Lists.newArrayList();
                     NBTTagCompound tag = tagFromJson2;
                     list.add(tag.getString("name"));
                     if (tag.hasKey("type", 8)) {
                        String string = tag.getString("type");
                        list.add("Type: " + string + " (" + EntityList.getIDFromString(string) + ")");
                     }

                     list.add(tag.getString("id"));
                     this.drawHoveringText(list, n, n2);
                  } else {
                     this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Entity!", n, n2);
                  }
               } catch (NBTException e2) {
                  this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid Entity!", n, n2);
               }
            }
         } else if (chatHoverEvent.getAction() == net.minecraft.event.HoverEvent.Action.SHOW_TEXT) {
            this.drawHoveringText(NEWLINE_SPLITTER.splitToList(chatHoverEvent.getValue().getFormattedText()), n, n2);
         } else if (chatHoverEvent.getAction() == net.minecraft.event.HoverEvent.Action.SHOW_ACHIEVEMENT) {
            StatBase oneShotStat = StatList.getOneShotStat(chatHoverEvent.getValue().getUnformattedText());
            if (oneShotStat != null) {
               IChatComponent statName = oneShotStat.getStatName();
               ChatComponentTranslation chatComponentTranslation = new ChatComponentTranslation(
                  "stats.tooltip.type." + (oneShotStat.isAchievement() ? "achievement" : "statistic"), new Object[0]
               );
               chatComponentTranslation.getChatStyle().setItalic(Boolean.TRUE);
               String str = oneShotStat instanceof Achievement ? ((Achievement)oneShotStat).getDescription() : null;
               ArrayList list2 = Lists.newArrayList(new String[]{statName.getFormattedText(), chatComponentTranslation.getFormattedText()});
               if (str != null) {
                  list2.addAll(this.fontRendererObj.listFormattedStringToWidth(str, 150));
               }

               this.drawHoveringText(list2, n, n2);
            } else {
               this.drawCreativeTabHoveringText(EnumChatFormatting.RED + "Invalid statistic/achievement!", n, n2);
            }
         }

         GlStateManager.disableLighting();
      }
   }

   public void setEventButton(int n) {
      this.eventButton = n;
   }

   public static boolean isKeyComboCtrlX(int n) {
      return n == 45 && isCtrlKeyDown() && !isShiftKeyDown() && !isAltKeyDown();
   }

   public boolean doesGuiPauseGame() {
      return true;
   }

   public void drawHoveringText(List list, int n, int n2) {
      if (!list.isEmpty()) {
         GlStateManager.disableRescaleNormal();
         RenderHelper.disableStandardItemLighting();
         GlStateManager.disableLighting();
         GlStateManager.disableDepth();
         int n3 = 0;

         for (String str : (Iterable<String>)(list)) {
            int stringWidth = this.fontRendererObj.getStringWidth(str);
            if (stringWidth > n3) {
               n3 = stringWidth;
            }
         }

         int n4 = n + 12;
         int n5 = n2 - 12;
         int n6 = 8;
         if (list.size() > 1) {
            n6 += 2 + (list.size() - 1) * 10;
         }

         if (n4 + n3 > this.width) {
            n4 -= 28 + n3;
         }

         if (n5 + n6 + 6 > this.height) {
            n5 = this.height - n6 - 6;
         }

         this.zLevel = 300.0F;
         this.itemRender.zLevel = 300.0F;
         int n7 = -267386864;
         this.drawGradientRect(n4 - 3, n5 - 4, n4 + n3 + 3, n5 - 3, n7, n7);
         this.drawGradientRect(n4 - 3, n5 + n6 + 3, n4 + n3 + 3, n5 + n6 + 4, n7, n7);
         this.drawGradientRect(n4 - 3, n5 - 3, n4 + n3 + 3, n5 + n6 + 3, n7, n7);
         this.drawGradientRect(n4 - 4, n5 - 3, n4 - 3, n5 + n6 + 3, n7, n7);
         this.drawGradientRect(n4 + n3 + 3, n5 - 3, n4 + n3 + 4, n5 + n6 + 3, n7, n7);
         int n8 = 1347420415;
         int n9 = (n8 & 16711422) >> 1 | n8 & 0xFF000000;
         this.drawGradientRect(n4 - 3, n5 - 3 + 1, n4 - 3 + 1, n5 + n6 + 3 - 1, n8, n9);
         this.drawGradientRect(n4 + n3 + 2, n5 - 3 + 1, n4 + n3 + 3, n5 + n6 + 3 - 1, n8, n9);
         this.drawGradientRect(n4 - 3, n5 - 3, n4 + n3 + 3, n5 - 3 + 1, n8, n8);
         this.drawGradientRect(n4 - 3, n5 + n6 + 2, n4 + n3 + 3, n5 + n6 + 3, n9, n9);

         for (int i = 0; i < list.size(); i++) {
            String str2 = (String)list.get(i);
            this.fontRendererObj.drawStringWithShadow(str2, (float)n4, (float)n5, -1);
            if (i == 0) {
               n5 += 2;
            }

            n5 += 10;
         }

         this.zLevel = 0.0F;
         this.itemRender.zLevel = 0.0F;
         GlStateManager.enableLighting();
         GlStateManager.enableDepth();
         RenderHelper.enableStandardItemLighting();
         GlStateManager.enableRescaleNormal();
      }
   }

   /**
    * Draws the screen's background.
    *
    * <p>A screen reached from the redesigned main menu - every stock options page, the world and server
    * selectors, the resource-pack and language pages, the pause menu and anything opened from any of them -
    * now draws the <em>menu's own</em> baked blurred backdrop (same frames, same zoom, same pan: literally the
    * same {@code MenuBackdrop} the home screen plays), so a menu screen is indistinguishable from the menu it
    * was opened from. Everything else keeps the previous path: the client's blue shader or the vanilla dirt
    * tile.</p>
    *
    * <p>Screens inside a world (inventory, chest, chat, book, anvil, the HUD) never reach this method with a
    * loaded world - {@link #drawWorldBackground(int)} keeps the world gradient for them - and they are
    * deliberately left alone. The one in-game screen family that does get the backdrop is the menu itself:
    * see {@link #drawWorldBackground(int)}.</p>
    */
   public void drawBackground(int n) {
      net.augustus.utils.shader.BackgroundShaderManager bsm = Augustus.getInstance().getBackgroundShaderManager();
      ClientSettings augustusServer = Augustus.getInstance().getModuleManager() != null
         ? Augustus.getInstance().getModuleManager().clientSettings : null;
      boolean customEnabled = augustusServer == null || augustusServer.customBackground.getValue();

      if (!customEnabled) {
         this.glStateSaver.setCull(false);
         this.glStateSaver.setTexture2D(true);
         this.glStateSaver.setBlend(false);
         GlStateManager.disableLighting();
         GlStateManager.disableFog();
         Tessellator instance = Tessellator.getInstance();
         WorldRenderer worldRenderer = instance.getWorldRenderer();
         this.mc.getTextureManager().bindTexture(optionsBackground);
         GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
         worldRenderer.begin(7, DefaultVertexFormats.POSITION_TEX_COLOR);
         worldRenderer.pos(0.0, (double)this.height, 0.0).tex(0.0, (double)((float)this.height / 32.0F + (float)n)).color(64, 64, 64, 255).endVertex();
         worldRenderer.pos((double)this.width, (double)this.height, 0.0)
            .tex((double)((float)this.width / 32.0F), (double)((float)this.height / 32.0F + (float)n))
            .color(64, 64, 64, 255)
            .endVertex();
         worldRenderer.pos((double)this.width, 0.0, 0.0).tex((double)((float)this.width / 32.0F), (double)n).color(64, 64, 64, 255).endVertex();
         worldRenderer.pos(0.0, 0.0, 0.0).tex(0.0, (double)n).color(64, 64, 64, 255).endVertex();
         instance.draw();
         this.glStateSaver.restoreCull();
         this.glStateSaver.restoreTexture2D();
         this.glStateSaver.restoreBlend();
         return;
      }

      if (bsm != null && bsm.isShaderBackdropActive()) {
         this.glStateSaver.setCull(false);
         this.glStateSaver.setTexture2D(true);
         this.glStateSaver.setBlend(false);
         bsm.getCurrentShader().render(1800.0F);
         this.glStateSaver.restoreCull();
         this.glStateSaver.restoreTexture2D();
         this.glStateSaver.restoreBlend();
         return;
      }

      if (this.usesMenuBackdrop()) {
         this.drawMenuScreenBackground();
         return;
      }

      this.glStateSaver.setCull(false);
      this.glStateSaver.setTexture2D(true);
      this.glStateSaver.setBlend(false);
      if (bsm != null && bsm.getCurrentShader() != null) {
         bsm.getCurrentShader().render(1800.0F);
      } else {
         this.drawGradientRect(0, 0, this.width, this.height, 0xFF22304C, 0xFF0B1020);
      }

      this.glStateSaver.restoreCull();
      this.glStateSaver.restoreTexture2D();
      this.glStateSaver.restoreBlend();
   }

   /**
    * The menu backdrop, drawn with the same GL-state bracket the shader path uses.
    *
    * <p>WHY the gradient fallback: {@code MenuBackdrop} is a set of baked textures resolved from the resource
    * pack. If they cannot be loaded the screen must not be left transparent onto whatever was in the
    * framebuffer - the home screen falls back the same way.</p>
    */
   private void drawMenuScreenBackground() {
      this.glStateSaver.setCull(false);
      this.glStateSaver.setTexture2D(true);
      this.glStateSaver.setBlend(false);

      this.ensureFrameClock();
      float screenWidth = (float)this.width;
      float screenHeight = (float)this.height;
      MenuScreenSkin.drawBackdrop(screenWidth, screenHeight);
      if (!MenuScreenSkin.isBackdropAvailable()) {
         this.drawGradientRect(0, 0, this.width, this.height, 0xFF22304C, 0xFF0B1020);
      }

      this.glStateSaver.restoreCull();
      this.glStateSaver.restoreTexture2D();
      this.glStateSaver.restoreBlend();
   }

   /**
    * Whether this screen belongs to the redesigned menu, i.e. whether it draws the menu backdrop.
    *
    * <p>Two ways to belong, and the walk is why the answer covers "any screen opened from the main menu"
    * without a per-screen edit: the screen is one of the stock menu screens listed in
    * {@link #isMenuBackdropScreen()}, or one of its ancestors in the {@code parentScreen} chain is. A
    * confirmation dialog, a "disconnect" page or a server-edit form therefore inherits the backdrop from the
    * screen that opened it instead of each of them having to be enumerated.</p>
    *
    * <p>The walk is depth-capped: {@code parentScreen} is a plain field that a screen may set to itself
    * (several vanilla screens do, to mean "reopen me"), and an uncapped walk would spin.</p>
    */
   private boolean usesMenuBackdrop() {
      GuiScreen screen = this;
      for (int depth = 0; screen != null && depth < 32; depth++) {
         if (screen.isMenuBackdropScreen()) {
            return true;
         }

         screen = screen.parentScreen;
      }

      return false;
   }

   /**
    * The stock screens that belong to the menu rather than to the game, by type.
    *
    * <p>Deliberately a list of the stock screens the redesign asked for - options and all its pages, the world
    * and server selectors, the world-creation pages, the resource-pack and language pages, the pause menu.</p>
    *
    * <p>WHAT IS NOT HERE, on purpose: the client's own redesigned screens ({@link AugustusGuiScreen} and its
    * tabbed pages) - they belong to the menu's owner, not to this pass, and they paint their own composition;
    * the in-game screens (inventory, chest, beacon, chest-of-commands, sign, chat, book,
    * anvil/enchanting/merchant, the HUD); the transitional loading screens ({@code GuiScreenWorking},
    * {@code GuiDownloadTerrain}); the error pages; and the demo intro. The user's rule for this round was
    * explicit: if a screen is not clearly a stock menu screen, leave it exactly as it was and report it.</p>
    */
   private boolean isMenuBackdropScreen() {
      return this instanceof GuiIngameMenu
         || this instanceof GuiOptions
         || this instanceof GuiVideoSettings
         || this instanceof GuiScreenOptionsSounds
         || this instanceof ScreenChatOptions
         || this instanceof GuiControls
         || this instanceof GuiCustomizeSkin
         || this instanceof GuiLanguage
         || this instanceof GuiScreenResourcePacks
         || this instanceof GuiSelectWorld
         || this instanceof GuiMultiplayer
         || this instanceof GuiCreateWorld
         || this instanceof GuiCreateFlatWorld
         || this instanceof GuiFlatPresets
         || this instanceof GuiScreenCustomizePresets
         || this instanceof GuiScreenAddServer
         || this instanceof GuiScreenServerList
         || this instanceof GuiRenameWorld
         || this instanceof GuiShareToLan
         || this instanceof GuiDisconnected
         || this instanceof GuiConnecting
         || this instanceof GuiMainMenu;
   }

   /**
    * Advances the shared UI clock once per frame for the screens that never did.
    *
    * <p>WHY it is needed here now: the menu backdrop's sequence and the glass material the options are made of
    * both read {@link UiAnimation#dt()} and {@link UiAnimation#frameId()}, and neither advances unless
    * somebody calls {@code beginFrame()} once per frame. Without it a stock screen froze the backdrop's clock
    * and - worse - left the glass backdrop capture's frame id unchanged, so the blur every option samples was
    * never refreshed.</p>
    *
    * <p>WHY the guard: {@code beginFrame()} latches a delta-time from the wall clock, so a second call inside
    * the same frame measures ~0 s and would freeze every animation for the rest of that frame. The screens
    * that already call it at the top of their {@code drawScreen} - the pause menu, the stock main menu and
    * every {@link AugustusGuiScreen} - are therefore skipped, and this method keeps exactly one clock per
    * frame per screen.</p>
    */
   private void ensureFrameClock() {
      if (!(this instanceof GuiIngameMenu) && !(this instanceof GuiMainMenu) && !(this instanceof AugustusGuiScreen)) {
         UiAnimation.beginFrame();
      }
   }

   public void handleKeyboardInput() throws IOException {
      char eventCharacter = Keyboard.getEventCharacter();
      int eventKey = Keyboard.getEventKey();
      boolean flag = !NO_TOGGLE_SCREENS.contains(this)
         && !(this instanceof GuiMainMenu)
         && !(this instanceof GuiMultiplayer)
         && !(this instanceof GuiScreenServerList)
         && !(this instanceof GuiSelectWorld)
         && !(this instanceof TabbedMenuScreen)
         && !(this instanceof ClientMenuTab);
      ToggleUtil.onKeyboardInput(!flag);
      if (Keyboard.getEventKeyState() || eventKey == 0 && Character.isDefined(eventCharacter)) {
         this.keyTyped(eventCharacter, eventKey);
      }

      this.mc.dispatchKeypresses();
   }

   public void sendChatMessage(String str) {
      this.sendChatMessage(str, true);
   }

   public static void setClipboardString(String str) {
      if (!StringUtils.isEmpty(str)) {
         try {
            StringSelection stringSelection = new StringSelection(str);
            Toolkit.getDefaultToolkit().getSystemClipboard().setContents(stringSelection, (ClipboardOwner)null);
         } catch (Exception e) {
         }
      }
   }

   public int getEventButton() {
      return this.eventButton;
   }

   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
   }

   public void keyTyped(char typedChar, int keyCode) throws IOException {
      if (keyCode == 1) {
         GuiScreen parentScreen2 = this.parentScreen;
         this.mc.displayGuiScreen(parentScreen2);
         if (this.mc.currentScreen == null) {
            this.mc.setIngameFocus();
         }
      }
   }

   public void setText(String str, boolean flag) {
   }

   public static boolean isCtrlKeyDown() {
      return Minecraft.isRunningOnMac ? Keyboard.isKeyDown(219) || Keyboard.isKeyDown(220) : Keyboard.isKeyDown(29) || Keyboard.isKeyDown(157);
   }

   public void handleInput() throws IOException {
      if (Mouse.isCreated()
         && (
            !ClientManagers.moduleManager.guiMove.isToggled()
               || !ClientManagers.moduleManager.guiMove.canRotateInGui()
               || !MouseInputUtil.isButtonDown(0)
               || !MouseInputUtil.isButtonDown(1)
               || !ClientManagers.moduleManager.guiMove.isRotateOnlyWhileClicking()
         )) {
         EventInventoryClick event = InventoryClickListener.onInventoryClick();
         EventManager.getEventBus().post(51, event);
         if (event.isOverrideMouseInput()) {
            if (this.leftMouseSyntheticDown) {
               this.simulateMouseRelease(0);
               this.leftMouseSyntheticDown = false;
            }

            if (this.rightMouseSyntheticDown) {
               this.simulateMouseRelease(1);
               this.rightMouseSyntheticDown = false;
            }
         }

         for (int i = 0; i < event.getLeftClicks(); i++) {
            this.simulateMouseClick(0);
            this.simulateMouseRelease(0);
         }

         for (int j = 0; j < event.getRightClicks(); j++) {
            this.simulateMouseClick(1);
            this.simulateMouseRelease(1);
         }

         while (Mouse.next()) {
            int eventButton2 = Mouse.getEventButton();
            boolean eventButtonState = Mouse.getEventButtonState();
            if (eventButton2 != 0 && eventButton2 != 1) {
               boolean flag = !NO_TOGGLE_SCREENS.contains(this)
                  && !(this instanceof GuiMainMenu)
                  && !(this instanceof GuiMultiplayer)
                  && !(this instanceof GuiScreenServerList)
                  && !(this instanceof GuiSelectWorld)
                  && !(this instanceof TabbedMenuScreen)
                  && !(this instanceof ClientMenuTab);
               ToggleUtil.onMouseInput(!flag);
            }

            if (event.isOverrideMouseInput()) {
               if (eventButton2 == 0) {
                  this.leftMouseHeld = eventButtonState;
               } else if (eventButton2 == 1) {
                  this.rightMouseHeld = eventButtonState;
               }
            } else {
               boolean flag2 = false;
               if (eventButton2 == 0 && this.leftMouseSyntheticDown != this.leftMouseHeld) {
                  this.leftMouseHeld = this.leftMouseSyntheticDown;
                  flag2 = true;
               }

               if (eventButton2 == 1 && this.rightMouseSyntheticDown != this.rightMouseHeld) {
                  this.rightMouseHeld = this.rightMouseSyntheticDown;
                  flag2 = true;
               }

               if (!flag2) {
                  this.handleMouseInput();
               }
            }
         }
      }

      if (Keyboard.isCreated()) {
         while (Keyboard.next()) {
            this.handleKeyboardInput();
         }
      }
   }

   public void initGui() {
   }

   public void onResize(Minecraft mc2, int n, int n2) {
      this.setWorldAndResolution(mc2, n, n2);
   }

   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      if (mouseButton == 0) {
         for (int i = 0; i < this.buttonList.size(); i++) {
            GuiButton button = (GuiButton)this.buttonList.get(i);
            if (button.mousePressed(this.mc, mouseX, mouseY)) {
               this.selectedButton = button;
               button.playPressSound(this.mc.getSoundHandler());
               this.actionPerformed(button);
            }
         }
      }
   }

   public static boolean isAltKeyDown() {
      return Keyboard.isKeyDown(56) || Keyboard.isKeyDown(184);
   }

   public void handleMouseInput() throws IOException {
      if ((!ClientManagers.moduleManager.invManager.isToggled() || !ClientManagers.moduleManager.invManager.isBlockingGuiInput())
            && (!ClientManagers.moduleManager.refill.isToggled() || !ClientManagers.moduleManager.refill.shouldBlockInventoryActions())
         || this.allowNativeMouseClick) {
         int n = Mouse.getEventX() * this.width / this.mc.displayWidth;
         int n2 = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
         int eventButton2 = Mouse.getEventButton();
         if (Mouse.getEventButtonState()) {
            if (eventButton2 == 0) {
               this.leftMouseHeld = true;
               this.leftMouseSyntheticDown = true;
            }

            if (eventButton2 == 1) {
               this.rightMouseHeld = true;
               this.rightMouseSyntheticDown = true;
            }

            this.allowNativeMouseClick = true;
            ClientManagers.moduleManager.inventoryClicker.onPreClick();
            if (this.mc.gameSettings.touchscreen && this.touchValue++ > 0) {
               return;
            }

            this.eventButton = eventButton2;
            this.lastMouseEvent = Minecraft.getSystemTime();
            this.mouseClicked(n, n2, this.eventButton);
         } else if (eventButton2 != -1) {
            if (eventButton2 == 0) {
               this.leftMouseHeld = false;
               this.leftMouseSyntheticDown = false;
            }

            if (eventButton2 == 1) {
               this.rightMouseHeld = false;
               this.rightMouseSyntheticDown = false;
            }

            ClientManagers.moduleManager.inventoryClicker.onPostClick();
            if (this.mc.gameSettings.touchscreen && --this.touchValue > 0) {
               return;
            }

            this.eventButton = -1;
            this.mouseReleased(n, n2, eventButton2);
            this.allowNativeMouseClick = false;
         } else if (this.eventButton != -1 && this.lastMouseEvent > 0L) {
            long l = Minecraft.getSystemTime() - this.lastMouseEvent;
            this.mouseClickMove(n, n2, this.eventButton, l);
         }
      }
   }

   public void mouseReleased(int mouseX, int mouseY, int state) {
      if (this.selectedButton != null && state == 0) {
         this.selectedButton.mouseReleased(mouseX, mouseY);
         this.selectedButton = null;
      }
   }

   public void openWebLink(URI uri) {
      try {
         Class clazz = Class.forName("java.awt.Desktop");
         Object obj = clazz.getMethod("getDesktop").invoke(null);
         clazz.getMethod("browse", URI.class).invoke(obj, uri);
      } catch (Throwable e) {
         LOGGER.error("Couldn't open link", e);
      }
   }

}
