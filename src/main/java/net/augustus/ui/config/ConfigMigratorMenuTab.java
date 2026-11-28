package net.augustus.ui.config;

import java.awt.Color;
import java.awt.Desktop;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import javax.swing.JFileChooser;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.augustus.Augustus;
import net.augustus.config.LegacyConfigPort;
import net.augustus.ui.AugustusGuiScreen;
import net.augustus.ui.TabbedMenuScreen;
import net.augustus.ui.menu.ClientMenuTab;
import net.augustus.ui.modern.components.GlassMenuButton;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.LucideIcon;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.widgets.ScrollBar;
import net.augustus.utils.audio.ClientSounds;
import net.augustus.utils.file.ClientPaths;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;

/**
 * Paid/Legacy Augustus -> OpenAugustus b4.23.3 config migration tab powered by {@link LegacyConfigPort}.
 * Lives alongside Account Manager and Proxy Manager inside {@link TabbedMenuScreen}.
 */
public class ConfigMigratorMenuTab extends ClientMenuTab {
   private static final class SourceEntry {
      final String label;
      final byte[] rawBytes;
      final File sourceFile;

      SourceEntry(String label, byte[] rawBytes, File sourceFile) {
         this.label = label;
         this.rawBytes = rawBytes;
         this.sourceFile = sourceFile;
      }
   }

   private final List<SourceEntry> queue = new ArrayList<>();
   private final ScrollBar logScrollBar = new ScrollBar(14);
   private int selectedIndex = -1;
   private LegacyConfigPort.MigrationResult previewResult;
   private String previewError;

   private boolean encryptOutput = true;
   private boolean editableOutput = true;
   private boolean overwriteExisting = true;
   private boolean dialogOpen = false;
   private CompletableFuture<Void> activeBatchTask;

   private GuiTextField outputNameField;
   private GuiTextField authorField;
   private String lastTypedName = "";
   private String lastTypedAuthor = "";

   private float leftX;
   private float leftW;
   private float rightX;
   private float rightW;
   private float cardPad;
   private float rowGap;
   private float colGap;
   private float labelH;

   private float formY;
   private float nameLabelY;
   private float nameFieldY;
   private float authorLabelY;
   private float authorFieldY;
   private float flagsRowY;
   private float sourceRowY;
   private float localRowY;
   private float dividerY;
   private float actionRowY;
   private float formH;

   private float listX;
   private float listW;
   private float listTop;
   private float listHeadingY;
   private float queueBarY;
   private float queueBarH;
   private float statsY;
   private float rowsTop;
   private float rowsBottom;

   private float prevChipX;
   private float prevChipY;
   private float nextChipX;
   private float nextChipY;
   private float clearChipX;
   private float clearChipY;
   private float chipSize;

   public ConfigMigratorMenuTab() {
      super("Config Migrator");
      this.setStatus("Ready", new Color(74, 222, 128));
   }

   @Override
   public LucideIcon getTabIcon() {
      return LucideIcon.SLIDERS_HORIZONTAL;
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
      this.leftW = clamp(Math.round(pageW * 0.455F), 156.0F, 375.0F);
      if (this.leftW > pageW - 156.0F) {
         this.leftW = Math.max(145.0F, pageW - 156.0F);
      }
      this.rightX = this.leftX + this.leftW + gap;
      this.rightW = Math.max(140.0F, pageX + pageW - this.rightX);

      this.formY = top;
      this.nameLabelY = this.formY + this.cardPad;
      this.nameFieldY = this.nameLabelY + this.labelH + 4.0F;
      this.authorLabelY = this.nameFieldY + row + this.rowGap + 2.0F;
      this.authorFieldY = this.authorLabelY + this.labelH + 4.0F;
      this.flagsRowY = this.authorFieldY + row + this.rowGap + 2.0F;
      this.sourceRowY = this.flagsRowY + row + this.rowGap;
      this.localRowY = this.sourceRowY + row + this.rowGap;
      this.dividerY = this.localRowY + row + this.rowGap + 2.0F;
      this.actionRowY = this.dividerY + this.rowGap + 2.0F;
      this.formH = this.actionRowY + row + this.cardPad - this.formY;

      this.listX = this.rightX;
      this.listW = this.rightW;
      this.listTop = top;
      this.listHeadingY = top + this.cardPad;
      this.queueBarY = this.listHeadingY + this.labelH + 6.0F;
      this.queueBarH = 22.0F;
      this.statsY = this.queueBarY + this.queueBarH + 8.0F;
      this.rowsTop = this.statsY + 34.0F;
      this.rowsBottom = TabbedMenuScreen.bodyBottom();

      this.chipSize = 16.0F;
      float barRight = this.listX + this.listW - this.cardPad;
      this.clearChipX = barRight - this.chipSize;
      this.nextChipX = this.clearChipX - this.chipSize - 4.0F;
      this.prevChipX = this.nextChipX - this.chipSize - 3.0F;
      float chipY = this.queueBarY + (this.queueBarH - this.chipSize) * 0.5F;
      this.prevChipY = chipY;
      this.nextChipY = chipY;
      this.clearChipY = chipY;
   }

   @Override
   public void initContent() {
      super.initContent();
      this.layoutCards();

      float row = TabbedMenuScreen.rowH();
      float innerX = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;
      float columnW = (innerW - this.colGap) * 0.5F;

      this.outputNameField = this.addTextField(Math.round(innerX), Math.round(this.nameFieldY), Math.round(innerW), Math.round(row));
      this.outputNameField.setCustomFont(menuBodyFont());
      this.outputNameField.setMaxStringLength(96);
      if (!this.lastTypedName.isEmpty()) {
         this.outputNameField.setText(this.lastTypedName);
      }

      this.authorField = this.addTextField(Math.round(innerX), Math.round(this.authorFieldY), Math.round(innerW), Math.round(row));
      this.authorField.setCustomFont(menuBodyFont());
      this.authorField.setMaxStringLength(64);
      if (!this.lastTypedAuthor.isEmpty()) {
         this.authorField.setText(this.lastTypedAuthor);
      }

      String fmtLabel = this.encryptOutput ? "AES-256" : "Plain JSON";
      this.addGlassButton(Math.round(innerX), Math.round(this.flagsRowY), Math.round(columnW), Math.round(row),
            fmtLabel, b -> {
               this.encryptOutput = !this.encryptOutput;
               b.displayString = this.encryptOutput ? "AES-256" : "Plain JSON";
               if (b instanceof GlassMenuButton) {
                  ((GlassMenuButton)b).setToggleState(this.encryptOutput);
               }
               this.refreshPreview();
            })
            .withIcon(LucideIcon.LOCK)
            .withVariant(GlassMenuButton.Variant.TOGGLE)
            .withToggleState(this.encryptOutput);

      String editLabel = "Editable: " + (this.editableOutput ? "§aTrue" : "§cFalse");
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.flagsRowY), Math.round(columnW), Math.round(row),
            editLabel, b -> {
               this.editableOutput = !this.editableOutput;
               b.displayString = "Editable: " + (this.editableOutput ? "§aTrue" : "§cFalse");
               if (b instanceof GlassMenuButton) {
                  ((GlassMenuButton)b).setToggleState(this.editableOutput);
               }
               this.refreshPreview();
            })
            .withVariant(GlassMenuButton.Variant.TOGGLE)
            .withToggleState(this.editableOutput);

      this.addGlassButton(Math.round(innerX), Math.round(this.sourceRowY), Math.round(columnW), Math.round(row),
            "Select .json", b -> this.openFilePickerAsync())
            .withIcon(LucideIcon.UPLOAD);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.sourceRowY), Math.round(columnW), Math.round(row),
            "Clipboard", b -> this.loadFromClipboard())
            .withIcon(LucideIcon.CLIPBOARD);

      this.addGlassButton(Math.round(innerX), Math.round(this.localRowY), Math.round(columnW), Math.round(row),
            "Scan configs/", b -> this.scanLocalConfigs())
            .withIcon(LucideIcon.REFRESH_CW);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.localRowY), Math.round(columnW), Math.round(row),
            "Open Folder", b -> this.openConfigsFolder())
            .withIcon(LucideIcon.FOLDER);

      this.addGlassButton(Math.round(innerX), Math.round(this.actionRowY), Math.round(columnW), Math.round(row),
            "Migrate & Load", b -> this.migrateCurrentAndLoad())
            .withIcon(LucideIcon.SPARKLES)
            .withVariant(GlassMenuButton.Variant.PRIMARY);
      this.addGlassButton(Math.round(innerX + columnW + this.colGap), Math.round(this.actionRowY), Math.round(columnW), Math.round(row),
            "Migrate All", b -> this.migrateAllQueued())
            .withIcon(LucideIcon.DOWNLOAD);

      this.updateLogScrollBar();
   }

   @Override
   public void drawChromeBehind() {
      this.layoutCards();
      this.menuGlassPanel(this.leftX, this.formY, this.leftW, this.formH, 0.0F, 1.0F);

      float innerX = this.leftX + this.cardPad;
      float innerW = this.leftW - this.cardPad * 2.0F;

      menuSectionLabel("OUTPUT CONFIG NAME (OPTIONAL)", innerX, this.nameLabelY, 1.0F);
      menuSectionLabel("AUTHOR OVERRIDE (OPTIONAL)", innerX, this.authorLabelY, 1.0F);

      GlassRenderer.drawSeparator(
         innerX,
         this.dividerY,
         innerX + innerW,
         this.dividerY,
         UiTheme.SEPARATOR_THICKNESS,
         UiTheme.GLASS_DIVIDER
      );

      this.menuGlassPanel(this.listX, this.listTop, this.listW, Math.max(0.0F, this.rowsBottom - this.listTop), 0.0F, 1.0F);

      menuSectionLabel("AUGUSTUS CONFIG PORT (B4.X -> B4.23.3)", this.listX + this.cardPad, this.listHeadingY, 1.0F);
      String qCount = this.queue.isEmpty() ? "0 queued" : (this.selectedIndex + 1) + " / " + this.queue.size();
      InterFontUtil capFace = menuCaptionFont();
      if (capFace != null) {
         float cw = capFace.getStringWidth(qCount);
         capFace.drawStringWithShadow(qCount, (double)(this.listX + this.listW - this.cardPad - cw),
               (double)this.listHeadingY, UiTheme.scaleAlpha(TEXT_SUB, 0.85F));
      }

      if (this.queue.isEmpty()) {
         this.menuEmptyState(
            this.listX,
            this.rowsTop - 16.0F,
            this.listW,
            Math.max(0.0F, this.rowsBottom - this.rowsTop),
            LucideIcon.FILE_TEXT,
            "No config loaded",
            "Choose .json file(s), paste clipboard JSON, or scan /configs to migrate old paid configs.",
            false
         );
      }
   }

   @Override
   public void drawContent(int mouseX, int mouseY, float partialTicks) {
      this.layoutCards();
      float row = TabbedMenuScreen.rowH();
      int hintColor = UiTheme.scaleAlpha(TEXT_SUB, 0.62F);

      if (this.outputNameField != null) {
         this.lastTypedName = this.outputNameField.getText();
         if (this.outputNameField.getText().isEmpty() && !this.outputNameField.isFocused()) {
            this.drawFieldPlaceholder("Keep source name / filename", this.outputNameField.xPosition, this.outputNameField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
         }
      }
      if (this.authorField != null) {
         this.lastTypedAuthor = this.authorField.getText();
         if (this.authorField.getText().isEmpty() && !this.authorField.isFocused()) {
            this.drawFieldPlaceholder("Preserve header or No_Author", this.authorField.xPosition, this.authorField.yPosition,
               Math.round(row), hintColor, menuBodyFont(), 8.0F);
         }
      }

      float innerX = this.listX + this.cardPad;
      float innerW = this.listW - this.cardPad * 2.0F;
      this.menuRow(innerX, this.queueBarY, innerW, this.queueBarH, 0.0F, 1.0F);

      if (!this.queue.isEmpty() && this.selectedIndex >= 0 && this.selectedIndex < this.queue.size()) {
         SourceEntry entry = this.queue.get(this.selectedIndex);
         float labelMaxW = Math.max(40.0F, this.prevChipX - (innerX + 24.0F) - 6.0F);
         LucideIcon.FILE_TEXT.draw(innerX + 7.0F, this.queueBarY + (this.queueBarH - 10.5F) * 0.5F, 10.5F, UiTheme.accentColor(), false);
         menuText(menuFit(menuBodyFont(), entry.label, labelMaxW), innerX + 21.0F,
            this.queueBarY + (this.queueBarH - menuTextHeight()) * 0.5F, 0xFFF8FAFC);

         this.drawMiniButton("mig.prev", LucideIcon.ARROW_LEFT, this.prevChipX, this.prevChipY, this.chipSize, mouseX, mouseY);
         this.drawMiniButton("mig.next", LucideIcon.ARROW_RIGHT, this.nextChipX, this.nextChipY, this.chipSize, mouseX, mouseY);
         this.drawMiniButton("mig.clear", LucideIcon.TRASH_2, this.clearChipX, this.clearChipY, this.chipSize, mouseX, mouseY);
      } else {
         menuSmallText("Queue empty — load a legacy config on the left", innerX + 8.0F,
            this.queueBarY + (this.queueBarH - menuSmallHeight()) * 0.5F, TEXT_SUB);
      }

      this.drawPreviewAndLogs(innerX, innerW);
      this.logScrollBar.updateAndDraw();
   }

   private void drawMiniButton(String id, LucideIcon icon, float x, float y, float size, int mouseX, int mouseY) {
      boolean hovered = menuHovered(x, y, size, size, mouseX, mouseY);
      float hover = this.menuHover(id, hovered);
      this.menuSurface(x, y, size, size, 4.0F, hover, 0.0F, 1.0F);
      icon.drawCentered(x + size * 0.5F, y + size * 0.5F, 9.0F, hovered ? 0xFFF8FAFC : UiTheme.accentSoftGlow());
   }

   private void drawPreviewAndLogs(float innerX, float innerW) {
      if (this.queue.isEmpty()) {
         return;
      }

      if (this.previewError != null) {
         this.menuEmptyState(
            this.listX,
            this.rowsTop - 16.0F,
            this.listW,
            Math.max(0.0F, this.rowsBottom - this.rowsTop),
            LucideIcon.ALERT_TRIANGLE,
            "Cannot parse config",
            this.previewError,
            false
         );
         return;
      }

      if (this.previewResult == null) {
         return;
      }

      LegacyConfigPort.MigrationResult res = this.previewResult;
      float bx = innerX;
      int badgeAccent = UiTheme.withAlpha(UiTheme.accentColor(), 0xBA);
      int badgeNeutral = UiTheme.withAlpha(UiTheme.menuPillFillBottomArgb(), 0xD8);

      bx += menuBadge(res.migratedModules + " modules", bx, this.statsY, badgeAccent, 0xFFFFFFFF) + 4.0F;
      bx += menuBadge(res.directSettingsCopied + " direct", bx, this.statsY, badgeNeutral, 0xFFE2E8F0) + 4.0F;
      bx += menuBadge(res.renamedSettingsCopied + " ported", bx, this.statsY, badgeNeutral, 0xFFE2E8F0) + 4.0F;
      if (bx + 70.0F < innerX + innerW) {
         bx += menuBadge(res.wasEncryptedInput ? "IN: AE53" : "IN: " + res.detectedEncoding.toUpperCase(Locale.ROOT),
            bx, this.statsY, badgeNeutral, 0xFFCBD5E1) + 4.0F;
      }
      if (bx + 65.0F < innerX + innerW) {
         menuBadge(res.droppedModules.size() + " dropped", bx, this.statsY, badgeNeutral, 0xFF94A3B8);
      }

      String sub = "Target: " + res.outputName + ".json  |  Author: " + res.author + "  |  +" + res.newDefaultModules.size() + " new defaults";
      menuSmallText(menuFit(menuSmallFont(), sub, innerW), innerX, this.statsY + 17.0F, TEXT_SUB);

      List<String> lines = this.buildLogLines(res);
      float viewTop = this.rowsTop;
      float viewBottom = this.rowsBottom - 10.0F;
      if (viewBottom <= viewTop + 10.0F) {
         return;
      }

      float barReserve = this.logScrollBar.fitsWithoutScrolling() ? 0.0F : 12.0F;
      float rowW = Math.max(40.0F, innerW - barReserve);
      int scrollOffset = (int)this.logScrollBar.getClampedScrollOffset();

      AugustusGuiScreen.enableScissor(
         Math.round(this.listX + 2.0F),
         Math.round(viewTop),
         Math.round(this.listX + this.listW - 2.0F),
         Math.round(viewBottom)
      );

      float y = viewTop + (float)scrollOffset;
      float linePitch = 14.0F;
      for (int i = 0; i < lines.size(); i++) {
         if (y + linePitch >= viewTop - 6.0F && y <= viewBottom + 6.0F) {
            String line = lines.get(i);
            boolean isHeader = line.startsWith("[");
            int color = isHeader ? UiTheme.accentSoftGlow() : 0xFFE2E8F0;
            menuSmallText(menuFit(menuSmallFont(), line, rowW), innerX, y + 2.0F, color);
         }
         y += linePitch;
      }

      this.disableScissor();
   }

   private List<String> buildLogLines(LegacyConfigPort.MigrationResult res) {
      List<String> lines = new ArrayList<>();
      if (!res.renamedModules.isEmpty()) {
         lines.add("[Renamed Modules] " + String.join(", ", res.renamedModules));
      }
      if (!res.droppedModules.isEmpty()) {
         lines.add("[Removed Obsolete Modules] " + String.join(", ", res.droppedModules));
      }
      if (res.notes.isEmpty()) {
         lines.add("[Schema] All matching settings copied directly onto OpenAugustus b4.23.3.");
      } else {
         lines.add("[Transformations: " + res.notes.size() + "]");
         for (int i = 0; i < res.notes.size(); i++) {
            lines.add(" - " + res.notes.get(i));
         }
      }
      return lines;
   }

   private void updateLogScrollBar() {
      this.layoutCards();
      this.logScrollBar.setBounds(
         Math.round(this.listX + this.listW - 10.0F),
         Math.round(this.rowsTop),
         Math.round(this.listX + this.listW - 5.0F),
         Math.round(this.rowsBottom - 10.0F)
      );
      int count = this.previewResult != null ? this.buildLogLines(this.previewResult).size() : 0;
      this.logScrollBar.setItemCount(count);
   }

   private LegacyConfigPort.MigrationOptions buildOptions(String fallbackName) {
      LegacyConfigPort.MigrationOptions opts = new LegacyConfigPort.MigrationOptions();
      opts.configNameOverride = this.outputNameField != null ? this.outputNameField.getText() : this.lastTypedName;
      opts.authorOverride = this.authorField != null ? this.authorField.getText() : this.lastTypedAuthor;
      opts.editable = this.editableOutput;
      opts.encryptOutput = this.encryptOutput;
      opts.fallbackName = fallbackName;
      return opts;
   }

   private void refreshPreview() {
      if (this.selectedIndex < 0 || this.selectedIndex >= this.queue.size()) {
         this.previewResult = null;
         this.previewError = null;
         this.updateLogScrollBar();
         return;
      }
      SourceEntry entry = this.queue.get(this.selectedIndex);
      try {
         String base = LegacyConfigPort.stripJsonExtension(entry.label);
         this.previewResult = LegacyConfigPort.migrateBytes(entry.rawBytes, this.buildOptions(base));
         this.previewError = null;
         this.setStatus("Ready: " + this.previewResult.outputName, new Color(74, 222, 128));
      } catch (Exception e) {
         this.previewResult = null;
         this.previewError = e.getMessage() != null ? e.getMessage() : e.getClass().getSimpleName();
         this.setStatus("Parse error", Color.red.darker());
      }
      this.logScrollBar.scrollOffset = 0.0;
      this.updateLogScrollBar();
   }

   private void openFilePickerAsync() {
      if (this.dialogOpen) {
         return;
      }
      this.dialogOpen = true;
      Thread t = new Thread(() -> {
         try {
            JFileChooser chooser = new JFileChooser();
            chooser.setDialogTitle("Select Legacy Augustus Config JSON(s)");
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileFilter(new FileNameExtensionFilter("Augustus Config JSON (*.json)", "json"));
            File configsDir = getConfigsDirectory();
            if (configsDir.isDirectory()) {
               chooser.setCurrentDirectory(configsDir);
            }
            if (chooser.showOpenDialog(null) == JFileChooser.APPROVE_OPTION) {
               File[] selected = chooser.getSelectedFiles();
               if (selected == null || selected.length == 0) {
                  File single = chooser.getSelectedFile();
                  if (single != null) {
                     selected = new File[]{single};
                  }
               }
               if (selected != null && selected.length > 0) {
                  List<SourceEntry> loaded = new ArrayList<>();
                  for (File f : selected) {
                     if (f != null && f.isFile()) {
                        byte[] bytes = Files.readAllBytes(f.toPath());
                        loaded.add(new SourceEntry(f.getName(), bytes, f));
                     }
                  }
                  if (!loaded.isEmpty()) {
                     this.enqueueEntries(loaded, false);
                  }
               }
            }
         } catch (Exception e) {
            this.setStatus("Failed reading file: " + e.getMessage(), Color.red.darker());
         } finally {
            this.dialogOpen = false;
         }
      }, "OpenAugustus-ConfigPicker");
      t.setDaemon(true);
      t.start();
   }

   private void loadFromClipboard() {
      String clip = GuiScreen.getClipboardString();
      if (clip == null || clip.trim().isEmpty()) {
         this.setStatus("Clipboard is empty", Color.yellow);
         return;
      }
      byte[] bytes = clip.getBytes(java.nio.charset.StandardCharsets.UTF_8);
      SourceEntry entry = new SourceEntry("clipboard.json", bytes, null);
      this.enqueueEntries(Arrays.asList(entry), false);
      ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
   }

   private void scanLocalConfigs() {
      File dir = getConfigsDirectory();
      if (!dir.isDirectory()) {
         this.setStatus("configs/ folder not found", Color.yellow);
         return;
      }
      File[] files = dir.listFiles((d, name) -> name.toLowerCase(Locale.ROOT).endsWith(".json"));
      if (files == null || files.length == 0) {
         this.setStatus("No .json files in configs/", Color.yellow);
         return;
      }
      Arrays.sort(files, (a, b) -> a.getName().compareToIgnoreCase(b.getName()));
      List<SourceEntry> found = new ArrayList<>();
      for (File f : files) {
         try {
            found.add(new SourceEntry(f.getName(), Files.readAllBytes(f.toPath()), f));
         } catch (Exception ignored) {
         }
      }
      this.enqueueEntries(found, true);
   }

   private void enqueueEntries(List<SourceEntry> entries, boolean replaceQueue) {
      synchronized (this.queue) {
         if (replaceQueue) {
            this.queue.clear();
         }
         int startIdx = this.queue.size();
         this.queue.addAll(entries);
         if (!this.queue.isEmpty()) {
            this.selectedIndex = replaceQueue ? 0 : startIdx;
         }
      }
      this.refreshPreview();
   }

   private void migrateCurrentAndLoad() {
      if (this.queue.isEmpty() || this.selectedIndex < 0 || this.selectedIndex >= this.queue.size()) {
         this.setStatus("Load a config first", Color.yellow);
         return;
      }
      this.refreshPreview();
      if (this.previewResult == null) {
         this.setStatus("Cannot migrate: invalid config", Color.red.darker());
         return;
      }
      try {
         File outFile = resolveOutputFile(this.previewResult.outputName, this.overwriteExisting);
         Files.createDirectories(outFile.getParentFile().toPath());
         Files.write(outFile.toPath(), this.previewResult.outputBytes);
         String savedName = LegacyConfigPort.stripJsonExtension(outFile.getName());
         Augustus.getInstance().getConfigManager().moduleConfigStorage.applyConfigJson(
            this.previewResult.migratedDoc, savedName, true
         );
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
         this.setStatus("Migrated & loaded: " + savedName, new Color(74, 222, 128));
      } catch (Exception e) {
         this.setStatus("Save failed: " + e.getMessage(), Color.red.darker());
      }
   }

   private void migrateAllQueued() {
      if (this.queue.isEmpty()) {
         this.setStatus("Queue is empty", Color.yellow);
         return;
      }
      if (this.activeBatchTask != null && !this.activeBatchTask.isDone()) {
         return;
      }
      this.setStatus("Migrating " + this.queue.size() + " config(s)...", Color.yellow);
      final List<SourceEntry> snapshot = new ArrayList<>(this.queue);
      final boolean singleItem = snapshot.size() == 1;
      this.activeBatchTask = CompletableFuture.runAsync(() -> {
         int ok = 0;
         int fail = 0;
         File lastOut = null;
         for (SourceEntry entry : snapshot) {
            try {
               String base = LegacyConfigPort.stripJsonExtension(entry.label);
               LegacyConfigPort.MigrationOptions opts = this.buildOptions(base);
               if (!singleItem) {
                  opts.configNameOverride = null;
               }
               LegacyConfigPort.MigrationResult res = LegacyConfigPort.migrateBytes(entry.rawBytes, opts);
               File outFile = resolveOutputFile(res.outputName, this.overwriteExisting);
               Files.createDirectories(outFile.getParentFile().toPath());
               Files.write(outFile.toPath(), res.outputBytes);
               lastOut = outFile;
               ok++;
            } catch (Exception ignored) {
               fail++;
            }
         }
         ClientSounds.playSound(ClientSounds.AUGUSTUS_TEXTURE2);
         if (fail == 0) {
            String suffix = singleItem && lastOut != null ? " -> " + lastOut.getName() : "";
            this.setStatus("Migrated " + ok + " config(s)" + suffix, new Color(74, 222, 128));
         } else {
            this.setStatus("Done: " + ok + " ok, " + fail + " failed", Color.yellow);
         }
      });
   }

   private void openConfigsFolder() {
      try {
         File dir = getConfigsDirectory();
         if (!dir.exists()) {
            Files.createDirectories(dir.toPath());
         }
         if (Desktop.isDesktopSupported()) {
            Desktop.getDesktop().open(dir);
         }
      } catch (Exception e) {
         this.setStatus("Cannot open folder: " + e.getMessage(), Color.red.darker());
      }
   }

   private static File getConfigsDirectory() {
      return new File(ClientPaths.CLIENT_DIR + File.separator + "configs");
   }

   private static File resolveOutputFile(String baseName, boolean overwrite) {
      File dir = getConfigsDirectory();
      String clean = sanitizeFileName(baseName);
      File target = new File(dir, clean + ".json");
      if (overwrite || !target.exists()) {
         return target;
      }
      File suffixed = new File(dir, clean + "_migrated.json");
      if (!suffixed.exists()) {
         return suffixed;
      }
      int idx = 2;
      while (true) {
         File numbered = new File(dir, clean + "_migrated_" + idx + ".json");
         if (!numbered.exists()) {
            return numbered;
         }
         idx++;
      }
   }

   private static String sanitizeFileName(String name) {
      if (name == null || name.trim().isEmpty()) {
         return "migrated";
      }
      String cleaned = name.trim().replaceAll("[\\\\/:*?\"<>|]", "_");
      return cleaned.isEmpty() ? "migrated" : cleaned;
   }

   @Override
   public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
      super.mouseClicked(mouseX, mouseY, mouseButton);
      if (mouseButton == 0 && !this.queue.isEmpty()) {
         if (menuHovered(this.prevChipX, this.prevChipY, this.chipSize, this.chipSize, mouseX, mouseY)) {
            this.selectedIndex = (this.selectedIndex - 1 + this.queue.size()) % this.queue.size();
            this.refreshPreview();
            return;
         }
         if (menuHovered(this.nextChipX, this.nextChipY, this.chipSize, this.chipSize, mouseX, mouseY)) {
            this.selectedIndex = (this.selectedIndex + 1) % this.queue.size();
            this.refreshPreview();
            return;
         }
         if (menuHovered(this.clearChipX, this.clearChipY, this.chipSize, this.chipSize, mouseX, mouseY)) {
            this.queue.clear();
            this.selectedIndex = -1;
            this.previewResult = null;
            this.previewError = null;
            this.updateLogScrollBar();
            this.setStatus("Queue cleared", new Color(74, 222, 128));
            return;
         }
      }
      this.logScrollBar.handleMouseClick((double)mouseX, (double)mouseY);
   }

   @Override
   public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
      super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
      this.logScrollBar.handleMouseDrag((double)mouseY);
   }

   @Override
   public void handleMouseInput() throws IOException {
      super.handleMouseInput();
      this.logScrollBar.handleMouseWheel();
   }

   @Override
   public void keyTyped(char typedChar, int keyCode) throws IOException {
      String beforeName = this.outputNameField != null ? this.outputNameField.getText() : "";
      String beforeAuthor = this.authorField != null ? this.authorField.getText() : "";
      super.keyTyped(typedChar, keyCode);
      String afterName = this.outputNameField != null ? this.outputNameField.getText() : "";
      String afterAuthor = this.authorField != null ? this.authorField.getText() : "";
      if (!beforeName.equals(afterName) || !beforeAuthor.equals(afterAuthor)) {
         this.lastTypedName = afterName;
         this.lastTypedAuthor = afterAuthor;
         this.refreshPreview();
      }
   }
}
