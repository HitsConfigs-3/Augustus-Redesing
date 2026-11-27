package net.augustus.clickgui;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.augustus.Augustus;
import net.augustus.ClientManagers;
import net.augustus.config.VisualsConfigFile;
import net.augustus.font.FontManager;
import net.augustus.modules.Module;
import net.augustus.modules.ModuleManager;
import net.augustus.settings.DoubleValue;
import net.augustus.ui.modern.font.InterFontUtil;
import net.augustus.ui.modern.render.GlassRenderer;
import net.augustus.ui.modern.render.UiTheme;
import net.augustus.ui.modern.render.UiAnimation;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;

/**
 * Full-screen HUD drag editor: renders live draggable glass handles
 * over active HUD widgets so the player can reposition them visually.
 *
 * <p>Each widget rectangle updates the module's 0..100 position DoubleValues.
 * Right-click on a widget toggles it. Press Esc to return to the ClickGUI.</p>
 */
public class HudEditorScreen extends GuiScreen {

    private final List<HudWidget> widgets = new ArrayList<HudWidget>();
    private HudWidget dragging = null;
    private int dragOffsetX, dragOffsetY;

    // --- Widget descriptor ---------------------------------------------------
    private static final class HudWidget {
        final String label;
        final Module module;
        final DoubleValue posXVal;
        final DoubleValue posYVal;
        final int defaultW;
        final int defaultH;
        float hoverAnim;

        HudWidget(String label, Module module, DoubleValue posXVal, DoubleValue posYVal, int defaultW, int defaultH) {
            this.label = label;
            this.module = module;
            this.posXVal = posXVal;
            this.posYVal = posYVal;
            this.defaultW = defaultW;
            this.defaultH = defaultH;
            this.hoverAnim = 0.0F;
        }

        int screenX(int fbW) {
            return (int) (posXVal.getValue() / 100.0 * (fbW - defaultW));
        }

        int screenY(int fbH) {
            return (int) (posYVal.getValue() / 100.0 * (fbH - defaultH));
        }

        void setFromScreen(int sx, int sy, int fbW, int fbH) {
            double px = defaultW >= fbW ? 0.0 : ((double) sx / (double) (fbW - defaultW)) * 100.0;
            double py = defaultH >= fbH ? 0.0 : ((double) sy / (double) (fbH - defaultH)) * 100.0;
            posXVal.setValue(Math.max(0.0, Math.min(100.0, px)));
            posYVal.setValue(Math.max(0.0, Math.min(100.0, py)));
        }
    }

    // --- Lifecycle ------------------------------------------------------------

    @Override
    public void initGui() {
        super.initGui();
        this.allowUserInput = true;
        rebuildWidgets();
    }

    private void rebuildWidgets() {
        widgets.clear();
        ModuleManager mm = ClientManagers.moduleManager;
        if (mm == null) return;

        // HUD watermark
        addWidget("HUD", mm.hud, mm.hud.xPos, mm.hud.yPos, 120, 24);
        // ArrayList
        addWidget("ArrayList", mm.arrayList, mm.arrayList.xPos, mm.arrayList.yPos, 100, 120);
        // TargetHud (only when not entity-tracking)
        addWidget("TargetHud", mm.targetHud, mm.targetHud.xCoord, mm.targetHud.yCoord, 160, 60);
        // KeyStrokes
        addWidget("KeyStrokes", mm.keyStrokes, mm.keyStrokes.x, mm.keyStrokes.y, 58, 58);
        // PotionHUD
        addWidget("PotionHUD", mm.potionHUD, mm.potionHUD.xCoord, mm.potionHUD.yCoord, 120, 60);
        // Coordinates
        addWidget("Coordinates", mm.coordinates, mm.coordinates.posX, mm.coordinates.posY, 140, 18);
        // ArmorStatus
        addWidget("ArmorStatus", mm.armorStatus, mm.armorStatus.posX, mm.armorStatus.posY, 60, 70);
        // Scoreboard
        addWidget("Scoreboard", mm.scoreboard, mm.scoreboard.positionX, mm.scoreboard.positionY, 120, 100);
        // HealthDisplay
        addWidget("Health", mm.healthDisplay, mm.healthDisplay.posX, mm.healthDisplay.posY, 60, 16);
    }

    private void addWidget(String label, Module mod, DoubleValue px, DoubleValue py, int w, int h) {
        if (mod != null && px != null && py != null) {
            widgets.add(new HudWidget(label, mod, px, py, w, h));
        }
    }

    /**
     * Labels of the HUD editor (widget cards and the "Back to ClickGUI" pill) use the same Inter body face
     * as the ClickGUI that opened them; the pill is measured with this face too, so its width still hugs
     * the text.
     */
    private static InterFontUtil labelFont() {
        return FontManager.body();
    }

    private int backPillLabelWidth() {
        InterFontUtil font = labelFont();
        return font == null ? this.fontRendererObj.getStringWidth("Back to ClickGUI") : Math.round(font.getStringWidth("Back to ClickGUI"));
    }

    // --- Drawing -------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        UiAnimation.beginFrame();
        // dim background slightly
        drawRect(0, 0, this.width, this.height, 0x44000000);

        ScaledResolution sr = new ScaledResolution(this.mc);
        int fbW = sr.getScaledWidth();
        int fbH = sr.getScaledHeight();

        for (HudWidget w : widgets) {
            int wx = w.screenX(fbW);
            int wy = w.screenY(fbH);
            int ww = w.defaultW;
            int wh = w.defaultH;

            boolean hovered = mouseX >= wx && mouseX <= wx + ww && mouseY >= wy && mouseY <= wy + wh;
            boolean active = w.module.isToggled();
            w.hoverAnim = UiAnimation.smooth(w.hoverAnim, hovered ? 1.0F : 0.0F, 14.0F);

            int fillColor;
            int borderColor;
            if (active) {
                fillColor = UiTheme.withAlpha(UiTheme.accentColor(), (int) (40 + 30 * w.hoverAnim));
                borderColor = UiTheme.withAlpha(UiTheme.accentColor(), (int) (140 + 80 * w.hoverAnim));
            } else {
                fillColor = UiTheme.withAlpha(0xFFFFFFFF, (int) (15 + 20 * w.hoverAnim));
                borderColor = UiTheme.withAlpha(0xFFFFFFFF, (int) (50 + 50 * w.hoverAnim));
            }

            // Glass card
            GlassRenderer.drawRoundedRect((float) wx, (float) wy, (float) ww, (float) wh, 4.0F, fillColor);
            GlassRenderer.drawRoundedOutline((float) wx, (float) wy, (float) ww, (float) wh, 4.0F, 1.0F, borderColor);

            // Label
            int textCol = active ? UiTheme.TEXT_PRIMARY : UiTheme.secondaryTextColor();
            InterFontUtil font = labelFont();
            if (font == null) {
                drawCenteredString(this.fontRendererObj, w.label, wx + ww / 2, wy + (wh - 8) / 2, textCol);
            } else {
                font.drawCenteredStringInBox(w.label, wx, wy, ww, wh, textCol);
            }

            // Active indicator dot
            if (active) {
                int dotColor = 0xFF22C55E;
                GlassRenderer.drawRoundedRect((float) (wx + ww - 10), (float) (wy + 4), 6.0F, 6.0F, 3.0F, dotColor);
            }
        }

        // Bottom pill: "Back to ClickGUI"
        String backText = "Back to ClickGUI";
        int tw = this.backPillLabelWidth();
        int pillW = tw + 24;
        int pillH = 20;
        int pillX = (this.width - pillW) / 2;
        int pillY = this.height - 36;
        GlassRenderer.drawRoundedRect((float) pillX, (float) pillY, (float) pillW, (float) pillH, 10.0F, UiTheme.glassCardFillArgb(0.0F, 0.0F, UiTheme.accentColor()));
        GlassRenderer.drawRoundedOutline((float) pillX, (float) pillY, (float) pillW, (float) pillH, 10.0F, 1.0F, UiTheme.GLASS_BORDER);
        InterFontUtil pillFont = labelFont();
        if (pillFont == null) {
            drawCenteredString(this.fontRendererObj, backText, pillX + pillW / 2, pillY + 6, UiTheme.TEXT_PRIMARY);
        } else {
            pillFont.drawCenteredStringInBox(backText, pillX, pillY, pillW, pillH, UiTheme.TEXT_PRIMARY);
        }

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    // --- Input ---------------------------------------------------------------

    @Override
    public void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        ScaledResolution sr = new ScaledResolution(this.mc);
        int fbW = sr.getScaledWidth();
        int fbH = sr.getScaledHeight();

        // Check "Back to ClickGUI" pill
        String backText = "Back to ClickGUI";
        int tw = this.backPillLabelWidth();
        int pillW = tw + 24;
        int pillH = 20;
        int pillX = (this.width - pillW) / 2;
        int pillY = this.height - 36;
        if (mouseButton == 0 && mouseX >= pillX && mouseX <= pillX + pillW && mouseY >= pillY && mouseY <= pillY + pillH) {
            this.mc.displayGuiScreen(Augustus.getInstance().getClickGui());
            return;
        }

        // Find widget under cursor (reverse order = topmost first)
        for (int i = widgets.size() - 1; i >= 0; i--) {
            HudWidget w = widgets.get(i);
            int wx = w.screenX(fbW);
            int wy = w.screenY(fbH);
            if (mouseX >= wx && mouseX <= wx + w.defaultW && mouseY >= wy && mouseY <= wy + w.defaultH) {
                if (mouseButton == 0) {
                    // Left-click = drag
                    dragging = w;
                    dragOffsetX = mouseX - wx;
                    dragOffsetY = mouseY - wy;
                } else if (mouseButton == 1) {
                    // Right-click = toggle module
                    w.module.toggle();
                }
                return;
            }
        }

        super.mouseClicked(mouseX, mouseY, mouseButton);
    }

    @Override
    public void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (dragging != null && clickedMouseButton == 0) {
            ScaledResolution sr = new ScaledResolution(this.mc);
            int fbW = sr.getScaledWidth();
            int fbH = sr.getScaledHeight();
            int nx = mouseX - dragOffsetX;
            int ny = mouseY - dragOffsetY;
            // Clamp within screen
            nx = Math.max(0, Math.min(fbW - dragging.defaultW, nx));
            ny = Math.max(0, Math.min(fbH - dragging.defaultH, ny));
            dragging.setFromScreen(nx, ny, fbW, fbH);
        }
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }

    @Override
    public void mouseReleased(int mouseX, int mouseY, int state) {
        if (dragging != null && state == 0) {
            dragging = null;
            VisualsConfigFile.saveNow();
        }
        super.mouseReleased(mouseX, mouseY, state);
    }

    @Override
    public void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == 1) { // Escape
            this.mc.displayGuiScreen(Augustus.getInstance().getClickGui());
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }

    @Override
    public void onGuiClosed() {
        VisualsConfigFile.saveNow();
        super.onGuiClosed();
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
