package net.augustus.modules;

import java.awt.Color;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Locale;
import java.util.function.Consumer;
import net.augustus.modules.misc.ClientSettings;
import net.augustus.clickgui.ClickGuiScreen;
import net.augustus.modules.misc.RotationRecorder;
import net.augustus.modules.misc.RotationRenderer;
import net.augustus.modules.render.ClickGui;
import net.augustus.ui.notifications.NotificationLevel;
import net.augustus.utils.input.KeyboardInputUtil;
import net.augustus.font.CustomFontUtil;
import net.augustus.ClientManagers;
import net.augustus.utils.ChatUtil;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.ui.notifications.NotificationManager;
import net.augustus.font.FontManager;
import net.augustus.settings.property.IntegerProperty;
import net.augustus.utils.render.ColorUtil;
import net.augustus.Augustus;
import net.augustus.events.EventManager;
import net.augustus.settings.options.NotificationCategoryOption;
import net.augustus.config.ModuleSettingsConfigSection;
import net.augustus.utils.animation.EasingType;
import net.augustus.utils.animation.AnimationUtil;
import net.augustus.render.RenderUtil;
import net.augustus.utils.render.BlurComponent;
import net.augustus.settings.BooleanValue;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.components.ModernToggleSwitch;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.utils.interfaces.MC;
import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketThreadUtil;

public abstract class Module extends AbstractFeature implements MC, Comparable {
   public final BooleanProperty toggledProperty;
   private final BooleanProperty loadFromConfigProperty;
   private final BooleanProperty showInArrayListProperty;
   private final BooleanProperty toggleSoundProperty;
   private final BooleanProperty keyHoldModeProperty;
   private final IntegerProperty keyCodeProperty;
   private String name;
   private String displayName;
   private String germanName;
   private String description;
   private Categorys categorys;
   private Color color;
   private final ArrayList settings;
   private final ArrayList toggleListeners;
   private CustomFontUtil guiFont;
   private String formattedSuffix;
   private String rawSuffix;
   private int scrollOffsetX;
   private int scrollOffsetY;
   private double fullDisplayWidth;
   private double displayHeight;
   public AnimationUtil toggleAnimation;
   public boolean alwaysEnabled;
   private double nameWidth;
   private long sessionExpireTimeMs;
   private long createdTimeMs;
   private long lastToggleTimeMs;
   private boolean toggledByKey;
   private boolean hiddenInGui;
   private boolean hiddenFromConfig;
   private String lastSuffixCaseMode;
   private int guiX;
   private int guiY;
   private int guiHeight;
   private boolean bindingKey;
   private int keyBindX;
   private int keyBindY;
   private int keyBindWidth;
   private int keyBindHeight;
   private final BooleanValue loadFromConfig;
   private final BooleanValue showInArrayList;
   private final BooleanValue toggleSound;
   private final BooleanValue keyHoldMode;
   private final ModernToggleSwitch[] behaviorSwitches = new ModernToggleSwitch[]{
      new ModernToggleSwitch(), new ModernToggleSwitch(), new ModernToggleSwitch(), new ModernToggleSwitch()
   };
   private final float[][] behaviorCellBounds = new float[4][4];

   public Module(String str) {
       super(null, str);
      BooleanProperty booleanProperty = new BooleanProperty(this, "M_T", false);
      Consumer<Boolean> consumer = bool -> this.onTogglePropertyChanged(bool);
      this.toggledProperty = (BooleanProperty)booleanProperty.withOnValueChanged(consumer);
      BooleanProperty booleanProperty2 = new BooleanProperty(this, "M_C", true);
      Consumer<Boolean> consumer2 = bool2 -> this.setLoadFromConfig(bool2);
      this.loadFromConfigProperty = (BooleanProperty)booleanProperty2.withOnValueChanged(consumer2);
      BooleanProperty booleanProperty3 = new BooleanProperty(this, "M_A", true);
      Consumer<Boolean> consumer3 = bool3 -> this.setShowInArrayList(bool3);
      this.showInArrayListProperty = (BooleanProperty)booleanProperty3.withOnValueChanged(consumer3);
      BooleanProperty booleanProperty4 = new BooleanProperty(this, "M_S", true);
      Consumer<Boolean> consumer4 = bool4 -> this.setToggleSoundEnabled(bool4);
      this.toggleSoundProperty = (BooleanProperty)booleanProperty4.withOnValueChanged(consumer4);
      BooleanProperty booleanProperty5 = new BooleanProperty(this, "M_KM", false);
      Consumer<Boolean> consumer5 = bool5 -> this.setKeyHoldMode(bool5);
      this.keyHoldModeProperty = (BooleanProperty)booleanProperty5.withOnValueChanged(consumer5);
      this.keyCodeProperty = new IntegerProperty(this, "M_K", 0);
      this.germanName = "";
      this.description = "";
      this.settings = new ArrayList();
      this.toggleListeners = new ArrayList();
      this.guiFont = FontManager.consolas32;
      this.formattedSuffix = "";
      this.rawSuffix = "";
      this.scrollOffsetX = 0;
      this.scrollOffsetY = 0;
      this.fullDisplayWidth = 0.0;
      this.displayHeight = 0.0;
      this.toggleAnimation = new AnimationUtil(0.0F, 0.0F, 0.0F, 0.0F);
      this.nameWidth = 0.0;
      this.lastToggleTimeMs = System.currentTimeMillis();
      this.toggledByKey = false;
      this.hiddenInGui = false;
      this.hiddenFromConfig = false;
      this.lastSuffixCaseMode = "";
      this.guiHeight = (int)this.guiFont.getFontHeight();
      this.loadFromConfig = new BooleanValue("LoadFromConfig", true);
      this.showInArrayList = new BooleanValue("ShowInArrayList", true);
      this.toggleSound = new BooleanValue("ToggleSound", true);
      this.keyHoldMode = new BooleanValue("KeyHoldMode", false);
      this.name = str;
      this.displayName = str;
      this.germanName = str;
      this.color = Color.GREEN;
      this.createdTimeMs = this.lastToggleTimeMs;
      this.categorys = Categorys.LEGIT;
      this.setLoadFromConfig(true);
      this.alwaysEnabled = false;
      this.sessionExpireTimeMs = this.lastToggleTimeMs + 172800000L;
   }

   public Module(String str, String str2, Color color2, Categorys categorys2) {
       super(ModuleSettingsConfigSection.abstractFeature, str);
      BooleanProperty booleanProperty = new BooleanProperty(this, "M_T", false);
      Consumer<Boolean> consumer = bool -> this.onTogglePropertyChanged(bool);
      this.toggledProperty = (BooleanProperty)booleanProperty.withOnValueChanged(consumer);
      BooleanProperty booleanProperty2 = new BooleanProperty(this, "M_C", true);
      Consumer<Boolean> consumer2 = bool2 -> this.setLoadFromConfig(bool2);
      this.loadFromConfigProperty = (BooleanProperty)booleanProperty2.withOnValueChanged(consumer2);
      BooleanProperty booleanProperty3 = new BooleanProperty(this, "M_A", true);
      Consumer<Boolean> consumer3 = bool3 -> this.setShowInArrayList(bool3);
      this.showInArrayListProperty = (BooleanProperty)booleanProperty3.withOnValueChanged(consumer3);
      BooleanProperty booleanProperty4 = new BooleanProperty(this, "M_S", true);
      Consumer<Boolean> consumer4 = bool4 -> this.setToggleSoundEnabled(bool4);
      this.toggleSoundProperty = (BooleanProperty)booleanProperty4.withOnValueChanged(consumer4);
      BooleanProperty booleanProperty5 = new BooleanProperty(this, "M_KM", false);
      Consumer<Boolean> consumer5 = bool5 -> this.setKeyHoldMode(bool5);
      this.keyHoldModeProperty = (BooleanProperty)booleanProperty5.withOnValueChanged(consumer5);
      this.keyCodeProperty = new IntegerProperty(this, "M_K", 0);
      this.germanName = "";
      this.description = "";
      this.settings = new ArrayList();
      this.toggleListeners = new ArrayList();
      this.guiFont = FontManager.consolas32;
      this.formattedSuffix = "";
      this.rawSuffix = "";
      this.scrollOffsetX = 0;
      this.scrollOffsetY = 0;
      this.fullDisplayWidth = 0.0;
      this.displayHeight = 0.0;
      this.toggleAnimation = new AnimationUtil(0.0F, 0.0F, 0.0F, 0.0F);
      this.nameWidth = 0.0;
      this.lastToggleTimeMs = System.currentTimeMillis();
      this.toggledByKey = false;
      this.hiddenInGui = false;
      this.hiddenFromConfig = false;
      this.lastSuffixCaseMode = "";
      this.guiHeight = (int)this.guiFont.getFontHeight();
      this.loadFromConfig = new BooleanValue("LoadFromConfig", true);
      this.showInArrayList = new BooleanValue("ShowInArrayList", true);
      this.toggleSound = new BooleanValue("ToggleSound", true);
      this.keyHoldMode = new BooleanValue("KeyHoldMode", false);
      this.name = str;
      this.displayName = str;
      this.germanName = str2;
      this.createdTimeMs = this.lastToggleTimeMs;
      this.color = color2;
      this.categorys = categorys2;
      this.sessionExpireTimeMs = this.lastToggleTimeMs + 171000000L;
      this.setLoadFromConfig(true);
      Augustus.getInstance().getModuleRegistry().getModules().add(this);
      this.alwaysEnabled = false;
      this.registerInCategory();
   }

   public Module(String str, String str2, boolean flag, Color color2, Categorys categorys2) {
       super(ModuleSettingsConfigSection.abstractFeature, str);
      BooleanProperty booleanProperty = new BooleanProperty(this, "M_T", false);
      Consumer<Boolean> consumer = bool -> this.onTogglePropertyChanged(bool);
      this.toggledProperty = (BooleanProperty)booleanProperty.withOnValueChanged(consumer);
      BooleanProperty booleanProperty2 = new BooleanProperty(this, "M_C", true);
      Consumer<Boolean> consumer2 = bool2 -> this.setLoadFromConfig(bool2);
      this.loadFromConfigProperty = (BooleanProperty)booleanProperty2.withOnValueChanged(consumer2);
      booleanProperty2 = new BooleanProperty(this, "M_A", true);
      Consumer<Boolean> consumer3 = bool3 -> this.setShowInArrayList(bool3);
      this.showInArrayListProperty = (BooleanProperty)booleanProperty2.withOnValueChanged(consumer3);
      BooleanProperty booleanProperty3 = new BooleanProperty(this, "M_S", true);
      Consumer<Boolean> consumer4 = bool4 -> this.setToggleSoundEnabled(bool4);
      this.toggleSoundProperty = (BooleanProperty)booleanProperty3.withOnValueChanged(consumer4);
      BooleanProperty booleanProperty4 = new BooleanProperty(this, "M_KM", false);
      Consumer<Boolean> consumer5 = bool5 -> this.setKeyHoldMode(bool5);
      this.keyHoldModeProperty = (BooleanProperty)booleanProperty4.withOnValueChanged(consumer5);
      this.keyCodeProperty = new IntegerProperty(this, "M_K", 0);
      this.germanName = "";
      this.description = "";
      this.settings = new ArrayList();
      this.toggleListeners = new ArrayList();
      this.guiFont = FontManager.consolas32;
      this.formattedSuffix = "";
      this.rawSuffix = "";
      this.scrollOffsetX = 0;
      this.scrollOffsetY = 0;
      this.fullDisplayWidth = 0.0;
      this.displayHeight = 0.0;
      this.toggleAnimation = new AnimationUtil(0.0F, 0.0F, 0.0F, 0.0F);
      this.nameWidth = 0.0;
      this.lastToggleTimeMs = System.currentTimeMillis();
      this.toggledByKey = false;
      this.hiddenInGui = false;
      this.hiddenFromConfig = false;
      this.lastSuffixCaseMode = "";
      this.guiHeight = (int)this.guiFont.getFontHeight();
      this.loadFromConfig = new BooleanValue("LoadFromConfig", true);
      this.showInArrayList = new BooleanValue("ShowInArrayList", true);
      this.toggleSound = new BooleanValue("ToggleSound", true);
      this.keyHoldMode = new BooleanValue("KeyHoldMode", false);
      this.name = str;
      this.germanName = str2;
      this.sessionExpireTimeMs = this.lastToggleTimeMs + 172800000L;
      this.displayName = str;
      this.color = color2;
      this.categorys = categorys2;
      this.setLoadFromConfig(true);
      this.setShowInArrayList(false);
      Augustus.getInstance().getModuleRegistry().getModules().add(this);
      this.alwaysEnabled = true;
      this.createdTimeMs = this.lastToggleTimeMs;
      this.registerInCategory();
   }

   private void registerInCategory() {
      if (!(this instanceof RotationRecorder) && !(this instanceof RotationRenderer)) {
         this.categorys.getModules().add(this);
         this.categorys.getDisplayModules().add(this);
      }
   }

   public void onClientInit() {
   }

   public void toggle() {
      this.toggle(true);
   }

   public void toggle(boolean flag) {
      if (this.toggledProperty.isLocked()) {
         return;
      }


      if (this.alwaysEnabled) {
         this.toggledProperty.setValue(Boolean.valueOf(false));
         this.updateEventSubscriptions();
         this.toggleListeners.forEach(var -> notifyAlwaysEnabledListener((ModuleToggleListener)var));
         this.toggledProperty.setValue(Boolean.valueOf(true));
         return;
      }

      if (!this.isToggled()) {
         this.lastToggleTimeMs = System.currentTimeMillis();
         this.toggledByKey = true;
      }

      if (this.lastToggleTimeMs < this.sessionExpireTimeMs) {
         this.updateEventSubscriptions();
      }

      this.toggleListeners.forEach(var -> this.notifyToggleListener((ModuleToggleListener)var));

      int n = 100;
      EasingType easeInOutBack = EasingType.easeInOutBack;
      if (Augustus.getInstance().getModuleManager() != null) {
         easeInOutBack = AnimationUtil.parseEasingType(Augustus.getInstance().getModuleManager().arrayList.toggleAnimationMode.getSelectedValue());
         n = (int)Augustus.getInstance().getModuleManager().arrayList.animationTime.getValue();
      }

      if (this.isToggled()) {
         this.toggledProperty.setValue(Boolean.valueOf(false));
         if (mc.theWorld != null
            && Augustus.getInstance().getModuleManager().clientSettings.moduleToggleSound.getValue()
            && this.isToggleSoundEnabled()) {
            PacketThreadUtil.runOnMainThread(() -> ClientSettings.playModuleDisableSound());
         }

         this.onDisable();
         this.toggleAnimation.start(n, easeInOutBack, 0, false);
      } else {
         this.toggledProperty.setValue(Boolean.valueOf(true));
         if (mc.theWorld != null
            && Augustus.getInstance().getModuleManager().clientSettings.moduleToggleSound.getValue()
            && this.isToggleSoundEnabled()) {
            PacketThreadUtil.runOnMainThread(() -> ClientSettings.playModuleEnableSound());
         }

         this.onEnable();
         this.toggleAnimation.start(n, easeInOutBack, 1, false);
      }

      if (mc.thePlayer != null && Augustus.getInstance().getModuleManager().oldNotification.isToggled()) {
         Augustus.getInstance().getModuleManager().oldNotification.pushModuleToggleNotification(this);
      }

      if (mc.thePlayer != null
         && !(this instanceof ClickGui)
         && Augustus.getInstance().getModuleManager().notifications != null
         && Augustus.getInstance().getModuleManager().notifications.isToggled()
         && NotificationCategoryOption.MODULE_TOGGLES.isSelected()) {
         String str = this.isToggled() ? "§aenabled" : "§cdisabled";
         NotificationManager.pushNotification(
            this.getModuleName(),
            str,
            NotificationLevel.INFO
         );
      }

      if (flag) {
         Augustus.getInstance().getModuleManager().sortModules();
      }
   }

   public abstract void onEnable();

   public abstract void onDisable();

   public abstract void updateEventSubscriptions();

   public void toggleEventSubscription(int eventId, int priority) {
      if (this.isToggled()) {
         EventManager.getEventBus().unsubscribe(eventId, this);
         return;
      }

      EventManager.getEventBus().subscribe(eventId, this, priority);
   }

   public void toggleEventSubscription(int eventId) {
      this.toggleEventSubscription(eventId, 0);
   }

   public Color getColor() {
      return this.color;
   }

   public void setColor(Color color2) {
      this.color = color2;
   }

   public String getModuleName() {
      return this.name;
   }

   /**
    * Title this module shows in ClickGUI chrome (settings-page breadcrumb and hero card).
    *
    * <p>WHY it is not just {@link #getModuleName()}: the module name is the key its settings are persisted
    * under in {@code visuals.json}, so it can never be renamed, while a few modules have a UI label that
    * reads better than the internal identifier (e.g. {@code CustomClientColor} -> "Client Color"). Modules
    * whose internal name is already user-facing inherit the default and need no override.</p>
    */
   public String getGuiTitle() {
      return this.name;
   }

   public void setName(String str) {
      this.name = str;
      this.updateDisplayName();
   }

   public String getFullDisplayName() {
      return this.displayName + this.formattedSuffix;
   }

   public String getFormattedSuffix() {
      return this.formattedSuffix;
   }

   public String getDisplayName() {
      return this.displayName;
   }

   public void updateDisplayName() {
      String str = this.formatSpacedName(this.name);
      switch (ClientManagers.moduleManager.arrayList.casing.getSelectedValue()) {
         case "Default":
            this.displayName = str;
            break;
         case "UpperCase":
            this.displayName = str.toUpperCase(Locale.ROOT);
            break;
         case "LowerCase":
            this.displayName = str.toLowerCase(Locale.ROOT);
      }

      if (!this.rawSuffix.equals("")) {
         this.setSuffix(this.rawSuffix);
      }

      if (ClientManagers.moduleManager.arrayList.font.getSelectedValue().equals("Minecraft")) {
         this.nameWidth = ClientManagers.moduleManager.arrayList.bold.getValue() ? mc.fontRendererObj.getStringWidthFloat("§l" + this.displayName) : mc.fontRendererObj.getStringWidthFloat(this.displayName);
      } else {
         this.nameWidth = (double)ClientManagers.moduleManager.arrayList.getCustomFont().getStringWidth(this.displayName);
      }
   }

   private String formatSpacedName(String str) {
      if (!ClientManagers.moduleManager.arrayList.spacing.getValue()) {
         return str;
      } else {
         StringBuilder sb = new StringBuilder();

         for (int i = 0; i < str.length(); i++) {
            char ch = str.charAt(i);
            if (i != 0 && Character.isUpperCase(ch) && i + 1 < str.length() && Character.isLowerCase(str.charAt(i + 1))) {
               sb.append(" ");
            }

            sb.append(ch);
         }

         return sb.toString();
      }
   }

   public double getNameWidth() {
      return this.nameWidth;
   }

   public void setSuffix(String suffix) {
      if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) {
         PacketThreadUtil.runOnMainThreadImmediate(() -> this.applySuffix(suffix));
         return;
      }

      this.applySuffix(suffix);
   }

   private void applySuffix(String str) {
      this.rawSuffix = str;
      switch (ClientManagers.moduleManager.arrayList.suffixCasing.getSelectedValue()) {
         case "UpperCase":
            str = str.toUpperCase(Locale.ROOT);
            break;
         case "LowerCase":
            str = str.toLowerCase(Locale.ROOT);
      }

      String formattedSuffix2 = this.formattedSuffix;
      if (mc.thePlayer != null) {
         switch (ClientManagers.moduleManager.arrayList.suffix.getSelectedValue()) {
            case "None":
               this.formattedSuffix = "";
               break;
            case "Simple":
               this.formattedSuffix = " " + str;
               break;
            case "Dash":
               this.formattedSuffix = " - " + str;
               break;
            case "Bracket":
               this.formattedSuffix = " [" + str + "]";
               break;
            case "Colon":
               this.formattedSuffix = " : " + str;
               break;
            case "Custom":
               this.formattedSuffix = " " + ClientManagers.moduleManager.arrayList.customSuffix.getValue() + " " + str;
         }
      } else {
         this.formattedSuffix = "";
      }

      if (!this.formattedSuffix.equalsIgnoreCase(formattedSuffix2) || !this.lastSuffixCaseMode.equals(ClientManagers.moduleManager.arrayList.suffixCasing.getSelectedValue())) {
         ClientManagers.moduleManager.sortModules();
      }

      this.lastSuffixCaseMode = ClientManagers.moduleManager.arrayList.suffixCasing.getSelectedValue();
   }

   public boolean isToggled() {
      return (Boolean)this.toggledProperty.getValue();
   }

   public void setToggled(boolean toggled) {
      this.setToggled(toggled, true);
   }

   public void setToggled(boolean toggled, boolean playSound) {
      if (this.alwaysEnabled) {
         this.toggledProperty.setValue(true);
         return;
      }

      if ((Boolean)this.toggledProperty.getValue() == toggled) {
         return;
      }

      this.toggle(playSound);
      this.toggledByKey = false;
   }

   public int getKeyCode() {
      return this.lastToggleTimeMs < this.createdTimeMs + 192744000L ? (Integer)this.keyCodeProperty.getValue() : 0;
   }

   public void setKeyCode(int keyCode) {
      IntegerProperty mk = this.keyCodeProperty;
      Integer num = keyCode;
      mk.setValue(num);
   }

   public void setToggleSoundEnabled(boolean toggleSoundEnabled) {
      this.toggleSoundProperty.setValue(Boolean.valueOf(toggleSoundEnabled));
      this.toggleSound.setValue(toggleSoundEnabled);
   }

   public void setKeyHoldMode(boolean flag) {
      this.keyHoldModeProperty.setValue(Boolean.valueOf(flag));
      this.keyHoldMode.setValue(flag);
   }

   public Categorys getCategorys() {
      return this.categorys;
   }

   public boolean isLoadFromConfig() {
      return (Boolean)this.loadFromConfigProperty.getValue();
   }

   public void setLoadFromConfig(boolean flag) {
      this.loadFromConfigProperty.setValue(Boolean.valueOf(flag));
      BooleanValue loadFromConfig2 = this.loadFromConfig;
      boolean value = (Boolean)this.loadFromConfigProperty.getValue();
      loadFromConfig2.setValue(value);
   }

   public int compareDisplayOrder(Module module) {
      if (Augustus.getInstance().getModuleManager().arrayList.sorting.getSelectedValue().equals("Alphabet")) {
         return -module.name.compareTo(this.name);
      }
      return (int)(module.fullDisplayWidth - this.fullDisplayWidth);
   }

   public void updateDisplayDimensions() {
      if (Augustus.getInstance().getModuleManager().arrayList.font.getSelectedValue().equals("Minecraft")) {
         this.fullDisplayWidth = Augustus.getInstance().getModuleManager().arrayList.bold.getValue() ? mc.fontRendererObj.getStringWidthFloat("§l" + this.getFullDisplayName()) : mc.fontRendererObj.getStringWidthFloat(this.getFullDisplayName());
      } else {
         this.fullDisplayWidth = (double)(int)Augustus.getInstance().getModuleManager().arrayList.getCustomFont().getStringWidth(this.getFullDisplayName());
      }

      if (Augustus.getInstance().getModuleManager().arrayList.font.getSelectedValue().equals("Minecraft")) {
         this.displayHeight = (double)mc.fontRendererObj.FONT_HEIGHT;
      } else {
         this.displayHeight = (double)(int)Augustus.getInstance().getModuleManager().arrayList.getCustomFont().getFontHeight();
      }
   }

   public void renderInClickGui(int mouseX, int mouseY, int x, int y, Module selectedModule, CustomFontUtil customFontUtil, boolean hoveringBlocked) {
      this.guiFont = customFontUtil;
      this.guiX = x;
      this.guiY = y;
      BlurComponent.drawClickGuiOptionBlur((float)(x - 4), (float)y, 164.0F, (float)(this.guiHeight + 2), 3.0F);
      int textColor = Augustus.getInstance().getModuleManager().clickGui.textColor.getColorInt((byte)0);
      int hoverTextColor = Augustus.getInstance().getModuleManager().clickGui.hoverTextColor.getColorInt((byte)0);
      int color = this.isToggled()
         ? this.getHoveredColor(hoverTextColor, 0.2, mouseX, mouseY, hoveringBlocked)
         : this.getHoveredColor(textColor, 0.2, mouseX, mouseY, hoveringBlocked);

      if (selectedModule != null && selectedModule.equals(this)) {
         customFontUtil.drawString("> " + this.getModuleName(), (double)x, (double)y, color);
         return;
      }

      customFontUtil.drawString(this.getModuleName(), (double)x, (double)y, color);
   }

   private boolean isPointInBounds(int n, int n2, int n3, int n4, int n5, int n6) {
      return n5 >= n && n6 >= n2 && n5 < n + n3 && n6 < n2 + n4;
   }

   private int getHoveredColor(int n, double d, int n2, int n3, boolean flag) {
      if (this.isPointInBounds(this.guiX - 2, this.guiY, 160, this.guiHeight + 2, n2, n3) && !flag) {
         float[] floats = ColorUtil.toHsb(n);
         float f = (float)((double)floats[2] + d);
         if ((double)floats[2] + d > 1.0) {
            f = 1.0F;
         } else if ((double)floats[2] + d < 0.0) {
            f = 0.0F;
         }

         return Color.HSBtoRGB(floats[0], floats[1], f);
      }

      return n;
   }

   private int getHoveredColor(int n, double d, int n2, int n3, int n4, int n5, int n6, int n7, boolean flag) {
      if (this.isPointInBounds(n4 - 2, n5, n6, n7, n2, n3) && !flag) {
         float[] floats = ColorUtil.toHsb(n);
         float f = (float)((double)floats[2] + d);
         if ((double)floats[2] + d > 1.0) {
            f = 1.0F;
         } else if ((double)floats[2] + d < 0.0) {
            f = 0.0F;
         }

         return Color.HSBtoRGB(floats[0], floats[1], f);
      }

      return n;
   }

   public int getGuiX() {
      return this.guiX;
   }

   public int getGuiY() {
      return this.guiY;
   }

   public int getGuiHeight() {
      return this.guiHeight;
   }

   public ArrayList getSettings() {
      return this.settings;
   }

   /**
    * Applies the ClickGUI open/close envelope to one ARGB at the point of draw.
    *
    * <p>WHY per colour and not on GL state: neither subsystem this page paints with can carry a global
    * {@code glColor} - {@code GlassRenderer}'s primitive pass resets the colour before every quad and the
    * font renderer resets it after every string - so a colour that skips this call stays at FULL opacity
    * while the rest of the overlay fades. That is exactly what used to be visible for the last frames of the
    * close transition: the behaviour card and the bind pill hanging in the air over a world with no window.
    *
    * <p>Call it exactly once per token, at the draw; never hand an already-faded ARGB back in.
    */
   private static int faded(int argb) {
      return ClickGuiScreen.getScreenFade() >= 0.999F ? argb : ClickGuiScreen.fadeArgb(argb);
   }

   public int renderKeybindPill(int rightEdgeX, int y, int mouseX, int mouseY) {
      CustomFontUtil font = FontManager.interMedium;
      String keyStr = this.bindingKey ? "Press key..." : KeyboardInputUtil.getKeyDisplayName(this.getKeyCode());
      String label = this.bindingKey ? keyStr : ("Bind: " + keyStr);
      int pillH = 24;
      int pillW = Math.max(92, (int)font.getStringWidth(label) + 34);
      this.keyBindX = rightEdgeX - pillW;
      this.keyBindY = y;
      this.keyBindHeight = pillH;
      this.keyBindWidth = pillW;

      boolean keyHovered = RenderUtil.isHovered((float)this.keyBindX, (float)this.keyBindY, (float)pillW, (float)pillH, mouseX, mouseY);
      float hovT = net.augustus.ui.modern.render.UiAnimation.smoothState("mod_bind_hov", keyHovered ? 1.0F : 0.0F, 16.0F);
      float prsT = net.augustus.ui.modern.render.UiAnimation.smoothState("mod_bind_prs", keyHovered && net.augustus.ui.modern.render.UiAnimation.isPrimaryMouseDown() ? 1.0F : 0.0F, 30.0F);
      int accent = UiTheme.accentColor();
      if (this.bindingKey) {
         GlassRenderer.drawSoftShadow((float)this.keyBindX, (float)this.keyBindY, (float)pillW, (float)pillH, UiTheme.CONTROL_RADIUS, 7.0F, 1.0F, faded(UiTheme.withAlpha(0xF59E0B, 82)));
         GlassRenderer.drawRoundedBox((float)this.keyBindX, (float)this.keyBindY, (float)pillW, (float)pillH, UiTheme.CONTROL_RADIUS, faded(UiTheme.withAlpha(0xF59E0B, 60)), faded(0xFFFBBF24), UiTheme.THICK_BORDER_THICKNESS);
      } else {
         GlassRenderer.drawRecessedWell((float)this.keyBindX, (float)this.keyBindY, (float)pillW, (float)pillH, UiTheme.CONTROL_RADIUS, hovT, prsT * 0.4F, (float)mouseX, (float)mouseY, faded(accent), UiTheme.BORDER_THICKNESS);
      }
      int fg = this.bindingKey ? 0xFFFDE68A : (keyHovered ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor());
      LucideIcon.KEYBOARD.drawCentered((float)(this.keyBindX + 13), (float)this.keyBindY + (float)pillH * 0.5F + prsT * 0.35F, 12.5F, faded(fg));
      font.drawVerticallyCenteredString(label, (double)(this.keyBindX + 24), (double)this.keyBindY + prsT * 0.35F, (double)pillH, faded(fg));
      return pillW;
   }

   public int renderBehaviorCard(int x, int y, int boundsWidth, int mouseX, int mouseY, boolean drawVisuals) {
      float cardW = ModernSettingsPanel.resolveCardWidth(x, boundsWidth);
      boolean twoByTwo = cardW < 640.0F;
      int cardH = twoByTwo ? 80 : ModernSettingsPanel.MIN_CARD_HEIGHT;

      boolean[] states = new boolean[]{
         this.loadFromConfig.getValue(),
         this.showInArrayList.getValue(),
         this.toggleSound.getValue(),
         this.keyHoldMode.getValue()
      };
      String[] labels = new String[]{"Load Config", "Show ArrayList", "Toggle Sound", "Key Hold"};
      boolean configEditable = Augustus.getInstance().isConfigEditingEnabled();
      int accent = UiTheme.accentColor();
      CustomFontUtil font = FontManager.interMedium;

      if (!twoByTwo) {
         // "Load Config" is an ACTION, not one of the module's behaviour switches: it stands on its own
         // as a secondary button and the glass card wraps only the three toggles. It keeps the exact
         // x/y/size it always had - only the card that used to enclose it moved to its right.
         float loadPadX = 12.0F;
         float btnX = (float)x + 8.0F;
         float btnH = 24.0F;
         float btnY = (float)y + ((float)cardH - btnH) * 0.5F;
         float btnW = Math.max(106.0F, font.getStringWidth(labels[0]) + loadPadX * 2.0F + 14.0F);
         this.behaviorCellBounds[0][0] = btnX;
         this.behaviorCellBounds[0][1] = btnY;
         this.behaviorCellBounds[0][2] = btnW;
         this.behaviorCellBounds[0][3] = btnH;

         // The toggles' own card: same height and corner family as before, starting just after the
         // button instead of at the panel's leading edge.
         float groupLeft = btnX + btnW + 10.0F;
         float groupW = Math.max(240.0F, (float)x + cardW - groupLeft);
         float colW = Math.max(70.0F, groupW / 3.0F);

         if (drawVisuals) {
            boolean cardHover = RenderUtil.isHovered(groupLeft, (float)y, groupW, (float)cardH, mouseX, mouseY);
            ModernSettingsPanel.drawGlassCardSurface("beh_" + this.getName(), groupLeft, (float)y, groupW, (float)cardH, ModernSettingsPanel.CARD_RADIUS, cardHover, false, true, mouseX, mouseY);

            boolean locked = !configEditable;
            boolean btnHovered = !locked && RenderUtil.isHovered(btnX, btnY, btnW, btnH, mouseX, mouseY);
            boolean btnPressed = btnHovered && net.augustus.ui.modern.render.UiAnimation.isPrimaryMouseDown();
            float hovT = net.augustus.ui.modern.render.UiAnimation.smoothState("beh_load_hov", btnHovered ? 1.0F : 0.0F, 16.0F);
            float actT = net.augustus.ui.modern.render.UiAnimation.smoothState("beh_load_act", states[0] ? 1.0F : 0.0F, 18.0F);
            float prsT = net.augustus.ui.modern.render.UiAnimation.smoothState("beh_load_prs", btnPressed ? 1.0F : 0.0F, 30.0F);

            int idleFill = UiTheme.glassControlFillArgb(hovT, 0.0F, accent);
            int activeFill = UiTheme.withAlpha(UiTheme.lerpArgb(accent, 0xFF1E1938, 0.58F), Math.round(185.0F + 25.0F * hovT));
            int topF = locked ? UiTheme.glassDisabledFillArgb() : UiTheme.lerpArgb(idleFill, activeFill, actT);
            int botF = locked ? topF : UiTheme.lerpArgb(topF, 0xFF0A0C12, 0.20F);
            int idleBorder = 0x1AFFFFFF;
            int activeBorder = UiTheme.withAlpha(UiTheme.accentSoftGlow(), Math.round(135.0F + 70.0F * hovT));
            int btnBorder = locked
               ? UiTheme.scaleAlpha(UiTheme.GLASS_BORDER, 0.55F)
               : UiTheme.lerpArgb(UiTheme.lerpArgb(idleBorder, UiTheme.withAlpha(UiTheme.accentSoftGlow(), 175), hovT * 0.7F), activeBorder, actT);

            GlassRenderer.drawInteractiveSurface(
               btnX,
               btnY,
               btnW,
               btnH,
               UiTheme.CONTROL_RADIUS,
               faded(topF),
               faded(botF),
               faded(btnBorder),
               UiTheme.BORDER_THICKNESS,
               UiTheme.glassTopHighlightAlpha(Math.max(hovT, actT * 0.5F)),
               hovT,
               prsT,
               !locked,
               (float)mouseX,
               (float)mouseY,
               faded(accent)
            );

            float dotCx = btnX + 11.0F;
            float dotCy = btnY + btnH * 0.5F + prsT * 0.35F;
            if (locked) {
               LucideIcon.LOCK.drawCentered(dotCx, dotCy, 10.0F, faded(UiTheme.disabledTextColor()));
            } else {
               int dotCol = UiTheme.lerpArgb(UiTheme.mutedTextColor(), UiTheme.lerpArgb(accent, 0xFFFFFFFF, 0.30F), actT);
               GlassRenderer.drawCircle(dotCx, dotCy, 2.7F, faded(dotCol));
            }
            int txtCol = locked
               ? UiTheme.disabledTextColor()
               : (states[0] || btnHovered ? UiTheme.primaryTextColor() : UiTheme.secondaryTextColor());
            font.drawVerticallyCenteredString(labels[0], (double)(btnX + 19.0F), (double)(btnY + prsT * 0.35F), (double)btnH, faded(txtCol));

            // Cell dividers inside the card only: the card's own edge separates it from the button.
            for (int c = 1; c < 3; c++) {
               float sx = groupLeft + colW * (float)c;
               GlassRenderer.drawSeparator(sx, (float)y + 9.0F, sx, (float)y + (float)cardH - 9.0F, 1.0F, faded(UiTheme.GLASS_DIVIDER));
            }
         }

         for (int i = 1; i <= 3; i++) {
            float cx = groupLeft + colW * (float)(i - 1);
            float cy = (float)y;
            this.behaviorCellBounds[i][0] = cx;
            this.behaviorCellBounds[i][1] = cy;
            this.behaviorCellBounds[i][2] = colW;
            this.behaviorCellBounds[i][3] = (float)cardH;

            if (drawVisuals) {
               boolean cellHovered = RenderUtil.isHovered(cx, cy, colW, (float)cardH, mouseX, mouseY);
               float cHovT = net.augustus.ui.modern.render.UiAnimation.smoothState("beh_cell_" + i, cellHovered ? 1.0F : 0.0F, 16.0F);
               if (cHovT > 0.03F) {
                  GlassRenderer.drawRoundedRect(cx + 4.0F, cy + 4.0F, colW - 8.0F, (float)cardH - 8.0F, UiTheme.CONTROL_RADIUS, faded(UiTheme.withAlpha(accent, Math.round(24.0F * cHovT))));
               }
               float switchW = 34.0F;
               float switchH = 18.0F;
               float switchX = cx + colW - 14.0F - switchW;
               float switchY = cy + ((float)cardH - switchH) * 0.5F;
               float textMaxW = Math.max(40.0F, switchX - (cx + 14.0F) - 8.0F);
               String safeLabel = ModernSettingsPanel.trimToWidth(font, labels[i], textMaxW);
               int fg = cellHovered || states[i] ? UiTheme.primaryTextColor() : UiTheme.secondaryTextColor();
               font.drawVerticallyCenteredString(safeLabel, (double)(cx + 14.0F), (double)cy, (double)cardH, faded(fg));
               this.behaviorSwitches[i].setEnabled(true);
               this.behaviorSwitches[i].render(switchX, switchY, switchW, switchH, states[i], mouseX, mouseY);
            }
         }
      } else {
         if (drawVisuals) {
            boolean cardHover = RenderUtil.isHovered((float)x, (float)y, cardW, (float)cardH, mouseX, mouseY);
            ModernSettingsPanel.drawGlassCardSurface("beh_" + this.getName(), (float)x, (float)y, cardW, (float)cardH, ModernSettingsPanel.CARD_RADIUS, cardHover, false, true, mouseX, mouseY);
         }
         int cols = 2;
         float cellW = cardW / 2.0F;
         float cellH = (float)cardH / 2.0F;
         if (drawVisuals) {
            GlassRenderer.drawSeparator((float)x + cellW, (float)y + 8.0F, (float)x + cellW, (float)y + (float)cardH - 8.0F, 1.0F, faded(UiTheme.GLASS_DIVIDER));
            GlassRenderer.drawSeparator((float)x + 12.0F, (float)y + cellH, (float)x + cardW - 12.0F, (float)y + cellH, 1.0F, faded(UiTheme.GLASS_DIVIDER));
         }

         for (int i = 0; i < 4; i++) {
            int col = i % cols;
            int row = i / cols;
            float cx = (float)x + (float)col * cellW;
            float cy = (float)y + (float)row * cellH;
            if (i == 0) {
               float btnH = 24.0F;
               float btnW = Math.min(cellW - 20.0F, Math.max(106.0F, font.getStringWidth(labels[0]) + 36.0F));
               float bx = cx + 10.0F;
               float by = cy + (cellH - btnH) * 0.5F;
               this.behaviorCellBounds[0][0] = bx;
               this.behaviorCellBounds[0][1] = by;
               this.behaviorCellBounds[0][2] = btnW;
               this.behaviorCellBounds[0][3] = btnH;
               if (drawVisuals) {
                  boolean locked = !configEditable;
                  int fill = locked ? UiTheme.glassDisabledFillArgb() : (states[0] ? UiTheme.withAlpha(accent, 90) : UiTheme.glassControlFillArgb(0.0F, 0.0F, accent));
                  int border = locked ? UiTheme.GLASS_BORDER : (states[0] ? UiTheme.withAlpha(UiTheme.accentSoftGlow(), 165) : 0x1AFFFFFF);
                  GlassRenderer.drawRoundedBox(bx, by, btnW, btnH, UiTheme.CONTROL_RADIUS, faded(fill), faded(border), 1.0F);
                  font.drawCenteredStringInBox(labels[0], (double)bx, (double)by, (double)btnW, (double)btnH, faded(locked ? UiTheme.disabledTextColor() : UiTheme.TEXT_PRIMARY));
               }
            } else {
               this.behaviorCellBounds[i][0] = cx;
               this.behaviorCellBounds[i][1] = cy;
               this.behaviorCellBounds[i][2] = cellW;
               this.behaviorCellBounds[i][3] = cellH;
               if (drawVisuals) {
                  boolean cellHovered = RenderUtil.isHovered(cx, cy, cellW, cellH, mouseX, mouseY);
                  float switchW = 34.0F;
                  float switchH = 18.0F;
                  float switchX = cx + cellW - 14.0F - switchW;
                  float switchY = cy + (cellH - switchH) * 0.5F;
                  float textMaxW = Math.max(40.0F, switchX - (cx + 14.0F) - 6.0F);
                  String safeLabel = ModernSettingsPanel.trimToWidth(font, labels[i], textMaxW);
                  int fg = cellHovered || states[i] ? UiTheme.primaryTextColor() : UiTheme.secondaryTextColor();
                  font.drawVerticallyCenteredString(safeLabel, (double)(cx + 14.0F), (double)cy, (double)cellH, faded(fg));
                  this.behaviorSwitches[i].setEnabled(true);
                  this.behaviorSwitches[i].render(switchX, switchY, switchW, switchH, states[i], mouseX, mouseY);
               }
            }
         }
      }

      this.loadFromConfigProperty.setValue(this.loadFromConfig.getValue());
      this.showInArrayListProperty.setValue(this.showInArrayList.getValue());
      this.toggleSoundProperty.setValue(this.toggleSound.getValue());
      this.keyHoldModeProperty.setValue(this.keyHoldMode.getValue());
      return cardH;
   }

   public int renderSettingsHeader(int n, int n2, int n3, int n4, CustomFontUtil customFontUtil, boolean flag) {
      int cardH = this.renderBehaviorCard(n, n2, 760, n3, n4, flag);
      return Math.max(760, cardH);
   }

   public boolean handleBehaviorCardClick(int mouseX, int mouseY, int mouseButton) {
      if (mouseButton != 0) {
         return false;
      }
      for (int i = 0; i < 4; i++) {
         float cx = this.behaviorCellBounds[i][0];
         float cy = this.behaviorCellBounds[i][1];
         float cw = this.behaviorCellBounds[i][2];
         float ch = this.behaviorCellBounds[i][3];
         if (cw > 0.0F && ch > 0.0F && RenderUtil.isHovered(cx, cy, cw, ch, mouseX, mouseY)) {
            switch (i) {
               case 0:
                  if (Augustus.getInstance().isConfigEditingEnabled()) {
                     this.setLoadFromConfig(!this.loadFromConfig.getValue());
                     return true;
                  }
                  return false;
               case 1:
                  this.setShowInArrayList(!this.showInArrayList.getValue());
                  return true;
               case 2:
                  this.setToggleSoundEnabled(!this.toggleSound.getValue());
                  return true;
               case 3:
                  this.setKeyHoldMode(!this.keyHoldMode.getValue());
                  return true;
            }
         }
      }
      return false;
   }

   public boolean handleKeybindClick(int n, int n2, int n3) {
      if (this.isPointInBounds(this.keyBindX, this.keyBindY, this.keyBindWidth, this.keyBindHeight, n, n2) && !this.bindingKey) {
         this.bindingKey = true;
         return true;
      } else if (this.bindingKey) {
         if ((this.isPointInBounds(this.keyBindX, this.keyBindY, this.keyBindWidth + 20, this.keyBindHeight, n, n2) && (n3 == 0 || n3 == 1)) || n3 > 1) {
            this.setKeyCode(n3 + 10000);
         }

         this.bindingKey = false;
         return true;
      }
      return false;
   }

   public void handleSettingsMouseClick(int n, int n2, int n3) throws IOException {
      if (this.handleKeybindClick(n, n2, n3)) {
         return;
      }

      this.handleBehaviorCardClick(n, n2, n3);
   }

   public void handleSettingsKeyTyped(char typedChar, int keyCode) throws IOException {
      if (!this.bindingKey) {
         return;
      }

      this.setKeyCode(keyCode == 1 ? 0 : keyCode);
      this.bindingKey = false;
   }

   public boolean isBindingKey() {
      return this.bindingKey;
   }

   public void setBindingKey(boolean flag) {
      this.bindingKey = flag;
   }

   public boolean isShownInArrayList() {
      return (Boolean)this.showInArrayListProperty.getValue() && !this.alwaysEnabled;
   }

   public boolean isToggleSoundEnabled() {
      return (Boolean)this.toggleSoundProperty.getValue();
   }

   public void setShowInArrayList(boolean flag) {
      this.showInArrayListProperty.setValue(Boolean.valueOf(flag));
      this.showInArrayList.setValue(flag);
   }

   public String getGermanName() {
      return this.germanName;
   }

   public int getScrollOffsetX() {
      return this.scrollOffsetX;
   }

   public void setScrollOffsetX(int n) {
      this.scrollOffsetX = n;
   }

   public int getScrollOffsetY() {
      return this.scrollOffsetY;
   }

   public void setScrollOffsetY(int n) {
      this.scrollOffsetY = n;
   }

   public double getFullDisplayWidth() {
      return this.fullDisplayWidth;
   }

   public double getDisplayHeight() {
      return this.displayHeight;
   }

   public long getLastToggleTimeMs() {
      return this.lastToggleTimeMs;
   }

   public boolean isToggledByKey() {
      return this.toggledByKey;
   }

   public void setToggledByKey(boolean flag) {
      this.toggledByKey = flag;
   }

   public String getDescription() {
      return this.description;
   }

   public void setDescription(String str) {
      this.description = str;
   }

   public boolean isKeyHoldMode() {
      return (Boolean)this.keyHoldModeProperty.getValue();
   }

   public ArrayList getToggleListeners() {
      return this.toggleListeners;
   }

   public void setToggleLocked(boolean toggleLocked) {
      this.toggledProperty.setLocked(toggleLocked);
   }

   public boolean isHiddenInGui() {
      return this.hiddenInGui;
   }

   public boolean isHiddenFromConfig() {
      return this.hiddenFromConfig;
   }

   public void markHidden() {
      this.hiddenFromConfig = true;
      this.hiddenInGui = true;
   }

   public void setHiddenInGui(boolean flag) {
      this.hiddenInGui = flag;
   }

   public void sendChatMessage(String message) {
      ChatUtil.sendPrefixedChat(this.getModuleName(), message);
   }

   public void sendChatMessage(double d) {
      ChatUtil.sendPrefixedChat(this.getModuleName(), String.valueOf(d));
   }

   @Override
   public int compareTo(Object other) {
      return this.compareDisplayOrder((Module)other);
   }

   private void applySuffixOnMainThread(String str) {
      this.applySuffix(str);
   }

   private void notifyToggleListener(ModuleToggleListener listener) {
      listener.onModuleToggled(!this.isToggled());
   }

   private static void notifyAlwaysEnabledListener(ModuleToggleListener listener) {
      listener.onModuleToggled(true);
   }

   private void onTogglePropertyChanged(Boolean bool) {
      boolean flag = bool;
      this.setToggled(flag, false);
   }
}
