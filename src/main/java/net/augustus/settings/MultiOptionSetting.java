package net.augustus.settings;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import net.augustus.clickgui.MainClickGuiWindow;
import net.augustus.render.RenderUtil;
import net.augustus.font.CustomFontUtil;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.settings.property.BooleanProperty;
import net.augustus.ui.modern.components.ModernDropdown;
import net.augustus.ui.modern.components.ModernSettingsPanel;
import net.augustus.ui.modern.popup.DropdownFloatingPopup;
import net.augustus.ui.modern.popup.PopupHost;
import net.augustus.ui.modern.render.UiTheme;

public class MultiOptionSetting extends Setting {
   private final List optionProperties;
   private final ConcurrentHashMap optionWidthCache;
   private static final int SEPARATOR_WIDTH;
   private int renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
   private final SelectableOption[] options;
   private int dragToggleState;
   private int labelWidth;
   private final ModernDropdown dropdown = new ModernDropdown();
   private float lastDropdownX;
   private float lastDropdownBottomY;
   private final DropdownFloatingPopup.Source dropdownSource = new DropdownFloatingPopup.Source() {
      private List<SelectableOption> getVisibleOptions() {
         List<SelectableOption> visible = new ArrayList<>();
         if (MultiOptionSetting.this.options != null) {
            for (SelectableOption opt : MultiOptionSetting.this.options) {
               if (opt != null && opt.isVisible() && opt.getName() != null && !opt.getName().isEmpty()) {
                  visible.add(opt);
               }
            }
         }
         return visible;
      }

      @Override
      public List<String> getOptions() {
         List<SelectableOption> visible = this.getVisibleOptions();
         List<String> names = new ArrayList<>(visible.size());
         for (SelectableOption opt : visible) {
            names.add(opt.getName());
         }
         return names;
      }

      @Override
      public boolean isSelected(int index) {
         List<SelectableOption> visible = this.getVisibleOptions();
         return index >= 0 && index < visible.size() && visible.get(index).isSelected();
      }

      @Override
      public boolean select(int index) {
         List<SelectableOption> visible = this.getVisibleOptions();
         if (index >= 0 && index < visible.size()) {
            SelectableOption opt = visible.get(index);
            opt.setSelected(!opt.isSelected());
            MultiOptionSetting.this.notifyValueChanged();
            MultiOptionSetting.this.notifyUserChanged();
         }
         return false;
      }

      @Override
      public boolean isMultiSelect() {
         return true;
      }
   };

   @Override
   public boolean mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      PopupHost host = MainClickGuiWindow.activePopupHost();
      MainClickGuiWindow mw = MainClickGuiWindow.activeMainWindow();
      float ox = mw != null ? (float)mw.getSettingsOffsetX() : 0.0F;
      float oy = mw != null ? (float)mw.getSettingsOffsetY() : 0.0F;
      return this.dropdown.handleClick(mouseX, mouseY, mouseButton, host, this.dropdownSource, this.lastDropdownX + ox, this.lastDropdownBottomY + oy);
   }

   @Override
   public void renderTooltip() {
      this.drawDescriptionTooltip(this.labelWidth);
   }

   public SelectableOption[] getOptions() {
      return this.options;
   }

   @Override
   public Setting withDisplayName(String str) {
      return this.named(str);
   }

   static {
      String str = ", ";
      SEPARATOR_WIDTH = (int)FontManager.consolas30.getStringWidth(str);
   }

   @Override
   public void resetToDefault() {

      for (BooleanProperty booleanProperty : (Iterable<BooleanProperty>)(this.optionProperties)) {
         booleanProperty.resetToDefault();
      }
   }

   public SelectableOption findOptionByName(SelectableOption[] selectableOptions, String str) {
      return Arrays.stream(selectableOptions).filter(selectableOption -> selectableOption.getName().equals(str)).findFirst().orElse(null);
   }

   @Override
   public Setting withVisibility(SettingVisibilityCondition condition) {
      return this.visibleWhen(condition);
   }

   private Boolean readOptionEnabled(SelectableOption[] selectableOptions, SelectableOption selectableOption) {
      return this.findOptionByName(selectableOptions, selectableOption.getName()).isSelected();
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      if (this.dragToggleState != 0) {
         if (clickedMouseButton == 0 || clickedMouseButton == 1) {
            String str = this.getDisplayName() + ": ";
            float renderX2 = (float)this.renderX;
            int n = (int)(renderX2 + this.renderFont.getStringWidth(str));
            int renderY2 = this.renderY;

            for (int i = 0; i < this.options.length; i++) {
               SelectableOption selectableOption = this.options[i];
               if (selectableOption.isVisible()) {
                  String name = selectableOption.getName();
                  if (!name.isEmpty()) {
                     int stringWidth = (int)this.renderFont.getStringWidth(name);
                     renderX2 = (float)(n + stringWidth);
                     String str2;
                     if (i < this.options.length - 1) {
                        str2 = ", ";
                     } else {
                        str2 = "";
                     }

                     String str3 = str2;
                     if (renderX2 + this.renderFont.getStringWidth(str3) > (float)this.boundsWidth) {
                        renderY2 += (int)this.renderFont.getFontHeight() + 1;
                        renderX2 = (float)this.renderX;
                        n = (int)(renderX2 + this.renderFont.getStringWidth(str));
                     }

                     if (RenderUtil.isHovered((float)(n - 6), (float)renderY2, (float)(stringWidth + 12), (float)((int)(this.renderFont.getFontHeight() + 1.0F)), mouseX, mouseY)
                        && selectableOption.isSelected() == (this.dragToggleState == 1)) {
                        selectableOption.setSelected(!selectableOption.isSelected());
                        this.notifyValueChanged();
                        this.notifyUserChanged();
                        break;
                     }

                     n += (int)this.renderFont.getStringWidth(name);
                     if (i < this.options.length - 1) {
                        String str4 = ", ";
                        n += (int)this.renderFont.getStringWidth(str4);
                     }
                  }
               }
            }
         }
      }
   }

   private MultiOptionSetting(String str, Module module, SettingGroup doubleValue, SelectableOption[] selectableOptions) {
       super(module, doubleValue, str);
      this.optionProperties = new ArrayList();
      this.labelWidth = -1;
      this.optionWidthCache = new ConcurrentHashMap();
      this.dragToggleState = 0;
      this.renderedHeight = ModernSettingsPanel.MIN_CARD_HEIGHT;
      this.options = selectableOptions;

      for (SelectableOption selectableOption : this.options) {
         List optionProperties2 = this.optionProperties;
         BooleanProperty booleanProperty2 = (BooleanProperty)new BooleanProperty(this, selectableOption.getName(), selectableOption.isSelected()).withValueSupplier(() -> this.readOptionEnabled(selectableOptions, selectableOption));
         Consumer consumer = obj -> {
            SelectableOption selectableOption2 = this.findOptionByName(selectableOptions, selectableOption.getName());
            boolean flag = (Boolean)(obj);
            selectableOption2.setSelected(flag);
         };
         optionProperties2.add(booleanProperty2.withOnValueApplied(consumer));
      }
   }

   public MultiOptionSetting(Module module, String str, SelectableOption[] selectableOptions) {
       this(str, module, null, selectableOptions);
   }

   @Override
   public int getHeight() {
      return this.renderedHeight;
   }

   public MultiOptionSetting visibleWhen(SettingVisibilityCondition condition) {
      this.visibilityCondition = condition;
      return this;
   }

   @Override
   public void mouseReleased(int mouseX, int mouseY, int state) {
      this.dragToggleState = 0;
   }

   public MultiOptionSetting(Module module, String str, SelectableOption[] selectableOptions, SettingGroup doubleValue) {
       this(str, module, doubleValue, selectableOptions);
   }

   @Override
   public void render(int n, int n2, int n3, int n4, int n5, int n6, CustomFontUtil customFontUtil, boolean flag) {
      customFontUtil = FontManager.interMedium;
      super.render(n, n2, n3, n4, n5, n6, customFontUtil, flag);
      String label = this.getFormattedDisplayName();
      if (this.consumeLabelDirty(this.labelWidth == -1)) {
         this.labelWidth = (int)customFontUtil.getStringWidth(label);
      }

      float cardW = ModernSettingsPanel.resolveCardWidth(n3, n5);
      float dropW = Math.min(240.0F, Math.max(170.0F, cardW * 0.28F));
      PopupHost host = MainClickGuiWindow.activePopupHost();
      boolean isOpen = host != null && host.isOpenFor(this.dropdown);
      ModernSettingsPanel.CardMetrics m = ModernSettingsPanel.drawSettingCard(
         n3,
         n4,
         n5,
         label,
         this.getDescription(),
         this.shouldShowInlineDescription(),
         dropW,
         false,
         isOpen,
         flag,
         n,
         n2
      );
      this.renderedHeight = m.cardH;
      this.applyCardMetrics(m);

      float dropX = m.controlAreaRightX - dropW;
      float dropY = m.controlCenterY - 12.0F;
      this.lastDropdownX = dropX;
      this.lastDropdownBottomY = dropY + 24.0F;

      if (flag) {
         String summary = this.buildSelectionSummary();
         MainClickGuiWindow mw = MainClickGuiWindow.activeMainWindow();
         float ox = mw != null ? (float)mw.getSettingsOffsetX() : 0.0F;
         float oy = mw != null ? (float)mw.getSettingsOffsetY() : 0.0F;

         this.dropdown.render(
            dropX,
            dropY,
            dropW,
            24.0F,
            summary,
            n,
            n2,
            host,
            this.dropdownSource,
            dropX + ox,
            this.lastDropdownBottomY + oy
         );
      }
   }

   private String buildSelectionSummary() {
      List<String> selectedNames = new ArrayList<>();
      int visibleCount = 0;
      if (this.options != null) {
         for (SelectableOption opt : this.options) {
            if (opt != null && opt.isVisible() && opt.getName() != null && !opt.getName().isEmpty()) {
               visibleCount++;
               if (opt.isSelected()) {
                  selectedNames.add(opt.getName());
               }
            }
         }
      }
      if (selectedNames.isEmpty()) {
         return "None";
      }
      if (visibleCount > 2 && selectedNames.size() == visibleCount) {
         return "All (" + visibleCount + ")";
      }
      if (selectedNames.size() <= 2) {
         return String.join(", ", selectedNames);
      }
      return selectedNames.get(0) + ", " + selectedNames.get(1) + " (+" + (selectedNames.size() - 2) + ")";
   }

   public MultiOptionSetting named(String str) {
      this.setDisplayName(str);
      return this;
   }

}
