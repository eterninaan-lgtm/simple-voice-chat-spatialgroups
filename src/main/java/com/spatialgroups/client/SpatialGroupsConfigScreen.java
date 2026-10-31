package com.spatialgroups.client;

import com.spatialgroups.SpatialGroupsClientState;
import com.spatialgroups.SpatialGroupsCommonConfig;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.DoubleFunction;

/**
 * In-game configuration screen for Simple Voice Chat SpatialGroups.
 *
 * <p>The layout intentionally mirrors the flat, tabbed settings screens of the Simple Voice Chat
 * / Cloth Config style: a title, a row of tabs, a search box and one flat list of options. There
 * are no folders and no icons - all labels and buttons are plain text.</p>
 *
 * <p>The screen writes directly to the NeoForge {@link ModConfigSpec} common config. Values are
 * applied immediately in memory and saved to {@code config/spatialgroups/common.toml} when a
 * slider is released or the screen is closed.</p>
 */
public class SpatialGroupsConfigScreen extends Screen {

    private static final int TAB_HEIGHT = 20;
    private static final int TAB_WIDTH = 100;
    private static final int TAB_GAP = 4;
    private static final int SEARCH_HEIGHT = 20;
    private static final int ROW_HEIGHT = 24;
    private static final int CONTROL_HEIGHT = 20;
    private static final int CONTROL_WIDTH = 160;
    private static final int RESET_WIDTH = 60;
    private static final int BOTTOM_BUTTON_WIDTH = 120;

    private static final int COLOR_TEXT = 0xFFFFFF;

    private final Screen parent;

    private final List<OptionRow> rows = new ArrayList<>();
    private final List<Button> tabButtons = new ArrayList<>();

    private EditBox searchBox;

    private int selectedTab;
    private int left;
    private int right;
    private int titleY;
    private int rowsTop;
    private int controlX;
    private int resetX;

    public SpatialGroupsConfigScreen(Screen parent) {
        super(Component.translatable("gui.spatialgroups.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();

        this.left = Math.max(20, this.width / 8);
        this.right = this.width - this.left;
        this.titleY = 12;

        int tabsY = this.titleY + 20;
        int searchY = tabsY + TAB_HEIGHT + 10;
        this.rowsTop = searchY + SEARCH_HEIGHT + 12;

        int controlWidth = Math.min(CONTROL_WIDTH, Math.max(100, (this.right - this.left) - 260));
        this.resetX = this.right - RESET_WIDTH;
        this.controlX = this.resetX - 8 - controlWidth;

        buildTabs(tabsY);
        buildSearchBox(searchY);
        buildOptions(controlWidth);
        buildBottomButtons();

        applyFilterAndLayout();
    }

    private void buildTabs(int y) {
        this.tabButtons.clear();
        int tabWidth = Math.min(TAB_WIDTH, Math.max(60, (this.right - this.left - TAB_GAP * 2) / 3));
        int x = this.left;
        for (int i = 0; i < 2; i++) {
            final int tab = i;
            Button button = Button.builder(tabTitle(i), b -> selectTab(tab))
                    .bounds(x, y, tabWidth, TAB_HEIGHT)
                    .build();
            this.tabButtons.add(button);
            addRenderableWidget(button);
            x += tabWidth + TAB_GAP;
        }
        updateTabTitles();
    }

    private Component tabTitle(int tab) {
        String key = tab == 1 ? "gui.spatialgroups.tab.distance" : "gui.spatialgroups.tab.general";
        Component title = Component.translatable(key);
        if (tab == this.selectedTab) {
            return title.copy().withStyle(ChatFormatting.YELLOW, ChatFormatting.UNDERLINE);
        }
        return title.copy().withStyle(ChatFormatting.GRAY);
    }

    private void updateTabTitles() {
        for (int i = 0; i < this.tabButtons.size(); i++) {
            this.tabButtons.get(i).setMessage(tabTitle(i));
        }
    }

    private void selectTab(int tab) {
        if (this.selectedTab == tab) {
            return;
        }
        this.selectedTab = tab;
        updateTabTitles();
        applyFilterAndLayout();
    }

    private void buildSearchBox(int y) {
        this.searchBox = new EditBox(this.font, this.left, y, this.right - this.left, SEARCH_HEIGHT,
                Component.translatable("gui.spatialgroups.search"));
        this.searchBox.setHint(Component.translatable("gui.spatialgroups.search.hint").withStyle(ChatFormatting.DARK_GRAY));
        this.searchBox.setResponder(value -> applyFilterAndLayout());
        addRenderableWidget(this.searchBox);
    }

    private void buildOptions(int controlWidth) {
        this.rows.clear();

        addBooleanRow(0, Component.translatable("spatialgroups.configuration.enabled"),
                SpatialGroupsCommonConfig.ENABLED, controlWidth);
        addBooleanRow(0, Component.translatable("spatialgroups.configuration.persistent_groups"),
                SpatialGroupsCommonConfig.PERSISTENT_GROUPS, controlWidth);
        addSliderRow(1, Component.translatable("spatialgroups.configuration.spatial_distance"),
                SpatialGroupsCommonConfig.SPATIAL_DISTANCE, 0.0D, 160.0D, 1.0D,
                SpatialGroupsConfigScreen::formatSpatialDistance,
                controlWidth);
        addSliderRow(1, Component.translatable("spatialgroups.configuration.fade_distance"),
                SpatialGroupsCommonConfig.FADE_DISTANCE, 1.0D, 96.0D, 1.0D,
                value -> Component.translatable("gui.spatialgroups.blocks", Math.round(value)),
                controlWidth);
        addSliderRow(1, Component.translatable("spatialgroups.configuration.minimum_gain"),
                SpatialGroupsCommonConfig.MINIMUM_GAIN, 0.0D, 1.0D, 0.05D,
                value -> Component.translatable("gui.spatialgroups.percent", Math.round(value * 100.0D)),
                controlWidth);
    }

    private void addBooleanRow(int tab, Component label, ModConfigSpec.BooleanValue configValue, int controlWidth) {
        Button control = Button.builder(booleanText(configValue.get()), b -> {
            boolean newValue = !configValue.get();
            configValue.set(newValue);
            configValue.save();
            b.setMessage(booleanText(newValue));
        }).bounds(this.controlX, 0, controlWidth, CONTROL_HEIGHT).build();

        Button reset = Button.builder(Component.translatable("gui.spatialgroups.reset"), b -> {
            configValue.set(Boolean.TRUE.equals(configValue.getDefault()));
            configValue.save();
            control.setMessage(booleanText(configValue.get()));
        }).bounds(this.resetX, 0, RESET_WIDTH, CONTROL_HEIGHT).build();

        this.rows.add(new OptionRow(tab, label, control, reset, configValue.getSpec(), configValue));
        addRenderableWidget(control);
        addRenderableWidget(reset);
    }

    private void addSliderRow(int tab, Component label, ModConfigSpec.DoubleValue configValue,
                              double min, double max, double step,
                              DoubleFunction<Component> formatter, int controlWidth) {
        ConfigSlider slider = new ConfigSlider(this.controlX, 0, controlWidth, CONTROL_HEIGHT,
                configValue, min, max, step, formatter);

        Button reset = Button.builder(Component.translatable("gui.spatialgroups.reset"), b -> {
            configValue.set(configValue.getDefault());
            configValue.save();
            slider.refreshFromConfig();
        }).bounds(this.resetX, 0, RESET_WIDTH, CONTROL_HEIGHT).build();

        this.rows.add(new OptionRow(tab, label, slider, reset, configValue.getSpec(), null));
        addRenderableWidget(slider);
        addRenderableWidget(reset);
    }

    /**
     * Formats the "0 = automatic" spatial distance. When the voice connection is already
     * established, the effective server distance is shown directly.
     */
    private static Component formatSpatialDistance(double value) {
        if (value < 0.5D) {
            double autoDistance = SpatialGroupsClientState.getVoiceChatDistance();
            if (autoDistance > 0.0D) {
                return Component.translatable("gui.spatialgroups.auto_distance", Math.round(autoDistance));
            }
            return Component.translatable("gui.spatialgroups.auto");
        }
        return Component.translatable("gui.spatialgroups.blocks", Math.round(value));
    }

    private Component booleanText(boolean value) {
        return Component.translatable(value ? "gui.spatialgroups.yes" : "gui.spatialgroups.no")
                .withStyle(value ? ChatFormatting.GREEN : ChatFormatting.RED);
    }

    private void buildBottomButtons() {
        int y = this.height - 28;
        int totalWidth = BOTTOM_BUTTON_WIDTH * 2 + 8;
        int x = (this.width - totalWidth) / 2;

        addRenderableWidget(Button.builder(Component.translatable("gui.spatialgroups.reset_all"), b -> resetAll())
                .bounds(x, y, BOTTOM_BUTTON_WIDTH, CONTROL_HEIGHT)
                .build());
        addRenderableWidget(Button.builder(Component.translatable("gui.spatialgroups.done"), b -> onClose())
                .bounds(x + BOTTOM_BUTTON_WIDTH + 8, y, BOTTOM_BUTTON_WIDTH, CONTROL_HEIGHT)
                .build());
    }

    private void resetAll() {
        SpatialGroupsCommonConfig.ENABLED.set(Boolean.TRUE.equals(SpatialGroupsCommonConfig.ENABLED.getDefault()));
        SpatialGroupsCommonConfig.SPATIAL_DISTANCE.set(SpatialGroupsCommonConfig.SPATIAL_DISTANCE.getDefault());
        SpatialGroupsCommonConfig.FADE_DISTANCE.set(SpatialGroupsCommonConfig.FADE_DISTANCE.getDefault());
        SpatialGroupsCommonConfig.MINIMUM_GAIN.set(SpatialGroupsCommonConfig.MINIMUM_GAIN.getDefault());
        SpatialGroupsCommonConfig.PERSISTENT_GROUPS.set(Boolean.TRUE.equals(SpatialGroupsCommonConfig.PERSISTENT_GROUPS.getDefault()));
        saveAll();

        for (OptionRow row : this.rows) {
            if (row.control instanceof ConfigSlider slider) {
                slider.refreshFromConfig();
            } else if (row.booleanValue != null) {
                row.control.setMessage(booleanText(row.booleanValue.get()));
            }
        }
    }

    private void applyFilterAndLayout() {
        String query = this.searchBox == null ? "" : this.searchBox.getValue().trim().toLowerCase(Locale.ROOT);
        int y = this.rowsTop;
        for (OptionRow row : this.rows) {
            boolean visible = row.tab == this.selectedTab
                    && (query.isEmpty() || row.label.getString().toLowerCase(Locale.ROOT).contains(query));
            row.control.visible = visible;
            row.resetButton.visible = visible;
            row.visible = visible;
            if (visible) {
                row.control.setX(this.controlX);
                row.resetButton.setX(this.resetX);
                row.control.setY(y);
                row.resetButton.setY(y);
                row.y = y;
                y += ROW_HEIGHT;
            }
        }
    }

    @Override
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.render(guiGraphics, mouseX, mouseY, partialTick);

        Component title = getTitle();
        guiGraphics.drawString(this.font, title, (this.width - this.font.width(title)) / 2, this.titleY, COLOR_TEXT, false);

        OptionRow hovered = null;
        for (OptionRow row : this.rows) {
            if (!row.visible) {
                continue;
            }
            guiGraphics.drawString(this.font, row.label, this.left, row.y + 6, COLOR_TEXT, false);
            if (hovered == null && isOverLabel(row, mouseX, mouseY)) {
                hovered = row;
            }
        }

        if (this.selectedTab == 1) {
            Component range = effectiveRangeText();
            guiGraphics.drawString(this.font, range, this.left, this.height - 46, 0xB0B0B0, false);
        }

        if (hovered != null) {
            Component tooltip = tooltipFor(hovered);
            if (tooltip != null) {
                // renderComponentTooltip wraps long text; the single line renderTooltip overload does not.
                guiGraphics.renderComponentTooltip(this.font, List.of(tooltip), mouseX, mouseY);
            }
        }
    }

    private boolean isOverLabel(OptionRow row, int mouseX, int mouseY) {
        return mouseX >= this.left && mouseX < this.controlX - 4
                && mouseY >= row.y && mouseY < row.y + CONTROL_HEIGHT;
    }

    /**
     * Uses the translated tooltip of the config value if the current language has one, and falls
     * back to the comment from the ModConfigSpec (which is always present).
     */
    private static Component tooltipFor(OptionRow row) {
        ModConfigSpec.ValueSpec spec = row.valueSpec;
        if (spec == null) {
            return null;
        }
        String translationKey = spec.getTranslationKey();
        if (translationKey != null && Language.getInstance().has(translationKey + ".tooltip")) {
            return Component.translatable(translationKey + ".tooltip");
        }
        String comment = spec.getComment();
        if (comment == null || comment.isBlank()) {
            return null;
        }
        return Component.literal(comment);
    }

    /**
     * @return a one line summary of the currently effective ranges (distance tab only)
     */
    private static Component effectiveRangeText() {
        double range = SpatialGroupsCommonConfig.SPATIAL_DISTANCE.get();
        if (range <= 0.0D) {
            range = SpatialGroupsClientState.getVoiceChatDistance();
        }
        if (range <= 0.0D) {
            return Component.translatable("gui.spatialgroups.range_unknown");
        }
        double fade = Math.max(0.0D, Math.min(SpatialGroupsCommonConfig.FADE_DISTANCE.get(), range));
        long start = Math.round(range - fade);
        long end = Math.round(range);
        return Component.translatable("gui.spatialgroups.range_info", start, start, end, end);
    }

    @Override
    public void onClose() {
        saveAll();
        if (this.minecraft != null) {
            this.minecraft.setScreen(this.parent);
        }
    }

    private void saveAll() {
        try {
            SpatialGroupsCommonConfig.SPEC.save();
        } catch (Throwable ignored) {
            // The config is not loaded (for example in a very early screen); nothing to save.
        }
    }

    private static final class OptionRow {
        private final int tab;
        private final Component label;
        private final AbstractWidget control;
        private final Button resetButton;
        private final ModConfigSpec.ValueSpec valueSpec;
        private final ModConfigSpec.BooleanValue booleanValue;
        private boolean visible;
        private int y;

        private OptionRow(int tab, Component label, AbstractWidget control, Button resetButton,
                          ModConfigSpec.ValueSpec valueSpec, ModConfigSpec.BooleanValue booleanValue) {
            this.tab = tab;
            this.label = label;
            this.control = control;
            this.resetButton = resetButton;
            this.valueSpec = valueSpec;
            this.booleanValue = booleanValue;
        }
    }

    /**
     * A vanilla style slider that edits a NeoForge double config value.
     */
    private static final class ConfigSlider extends AbstractSliderButton {

        private final ModConfigSpec.DoubleValue configValue;
        private final double min;
        private final double max;
        private final double step;
        private final DoubleFunction<Component> formatter;

        private ConfigSlider(int x, int y, int width, int height, ModConfigSpec.DoubleValue configValue,
                             double min, double max, double step, DoubleFunction<Component> formatter) {
            super(x, y, width, height, Component.empty(), normalized(configValue.get(), min, max));
            this.configValue = configValue;
            this.min = min;
            this.max = max;
            this.step = step;
            this.formatter = formatter;
            updateMessage();
        }

        private static double normalized(double value, double min, double max) {
            if (max <= min) {
                return 0.0D;
            }
            return Math.max(0.0D, Math.min(1.0D, (value - min) / (max - min)));
        }

        private double actualValue() {
            double raw = this.min + this.value * (this.max - this.min);
            if (this.step > 0.0D) {
                raw = this.min + Math.round((raw - this.min) / this.step) * this.step;
            }
            return Math.max(this.min, Math.min(this.max, raw));
        }

        @Override
        protected void updateMessage() {
            setMessage(this.formatter.apply(actualValue()));
        }

        @Override
        protected void applyValue() {
            // Update in memory on every movement, but only write the config file on release.
            this.configValue.set(actualValue());
            updateMessage();
        }

        @Override
        public void onRelease(double mouseX, double mouseY) {
            super.onRelease(mouseX, mouseY);
            this.configValue.save();
        }

        private void refreshFromConfig() {
            this.value = normalized(this.configValue.get(), this.min, this.max);
            updateMessage();
        }
    }
}
