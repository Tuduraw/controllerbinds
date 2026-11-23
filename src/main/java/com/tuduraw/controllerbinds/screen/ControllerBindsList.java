package com.tuduraw.controllerbinds.screen;

import com.google.common.collect.ImmutableList;
import com.tuduraw.controllerbinds.action.ActionRegistry;
import com.tuduraw.controllerbinds.action.BindableAction;
import com.tuduraw.controllerbinds.config.ControllerBindsConfig;
import com.tuduraw.controllerbinds.input.ControllerInput;
import com.tuduraw.controllerbinds.input.ControllerManager;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.DoubleFunction;
import java.util.function.Function;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.ContainerObjectSelectionList;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.narration.NarratableEntry;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import org.jspecify.annotations.Nullable;

public final class ControllerBindsList extends ContainerObjectSelectionList<ControllerBindsList.Entry> {
    private static final int ROW_HEIGHT = 20;
    private static final int WIDGET_WIDTH = 150;

    final ControllerBindsScreen screen;

    public ControllerBindsList(ControllerBindsScreen screen, Minecraft minecraft) {
        super(minecraft, screen.width, screen.layout.getContentHeight(), screen.layout.getHeaderHeight(), ROW_HEIGHT);
        this.screen = screen;

        addEntry(new SectionEntry(Component.translatable("controllerbinds.section.device")));
        addEntry(new DeviceEntry());
        addEntry(new MonitorEntry());
        addEntry(new SliderEntry(Component.translatable("controllerbinds.option.deadzone"),
                p -> (double) p.deadzone / 0.9,
                (p, v) -> p.deadzone = (float) (v * 0.9),
                v -> Component.literal(Math.round(v * 90.0) + "%")));
        addEntry(new SliderEntry(Component.translatable("controllerbinds.option.press_threshold"),
                p -> (p.pressThreshold - 0.05) / 0.9,
                (p, v) -> p.pressThreshold = (float) (0.05 + v * 0.9),
                v -> Component.literal(Math.round((0.05 + v * 0.9) * 100.0) + "%")));
        addEntry(new SliderEntry(Component.translatable("controllerbinds.option.mouse_speed"),
                p -> (p.mouseSpeed - 50.0) / 4950.0,
                (p, v) -> p.mouseSpeed = (float) (50.0 + v * 4950.0),
                v -> Component.literal(Math.round(50.0 + v * 4950.0) + " px/s")));

        Component lastCategory = null;
        for (BindableAction action : ActionRegistry.all()) {
            Component category = action.category();
            if (!category.equals(lastCategory)) {
                lastCategory = category;
                addEntry(new SectionEntry(category));
            }
            addEntry(new BindEntry(action));
        }
    }

    @Override
    public int getRowWidth() {
        return 340;
    }

    public void refreshEntries() {
        children().forEach(Entry::refresh);
    }

    void tickEntries() {
        children().forEach(Entry::tick);
    }

    private ControllerBindsConfig.@Nullable Profile profile() {
        return screen.profile(false);
    }

    private int widgetX(int width) {
        return scrollBarX() - width - 10;
    }

    // ================================================================== entries

    public abstract static class Entry extends ContainerObjectSelectionList.Entry<Entry> {
        void refresh() {
        }

        void tick() {
        }
    }

    /** 見出し。 */
    final class SectionEntry extends Entry {
        private final Component label;

        SectionEntry(Component label) {
            this.label = label.copy().withStyle(ChatFormatting.BOLD);
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawCenteredString(minecraft.font, label, ControllerBindsList.this.width / 2,
                    getContentBottom() - minecraft.font.lineHeight - 1, 0xFFFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    /** 使用する機器の切り替えと、中立位置の再記録。 */
    final class DeviceEntry extends Entry {
        private final Button deviceButton;
        private final Button calibrateButton;

        DeviceEntry() {
            deviceButton = Button.builder(Component.empty(), b -> {
                ControllerManager manager = screen.controller();
                screen.stopListening();
                manager.selectNext();
                if (manager.guid() != null) {
                    screen.config().selectedDevice = manager.guid();
                    screen.profile(true);
                }
                ControllerBindsList.this.refreshEntries();
            }).bounds(0, 0, 185, 20).build();
            calibrateButton = Button.builder(Component.translatable("controllerbinds.option.calibrate"),
                    b -> screen.controller().recalibrate())
                    .bounds(0, 0, 100, 20)
                    .tooltip(Tooltip.create(Component.translatable("controllerbinds.option.calibrate.tooltip")))
                    .build();
            refresh();
        }

        @Override
        void tick() {
            refresh();
        }

        @Override
        void refresh() {
            ControllerManager manager = screen.controller();
            if (manager.hasDevice()) {
                int[] pos = manager.position();
                deviceButton.setMessage(Component.literal(manager.name() + " (" + pos[0] + "/" + pos[1] + ")"));
                deviceButton.setTooltip(Tooltip.create(Component.literal("GUID: " + manager.guid())));
            } else {
                deviceButton.setMessage(Component.translatable("controllerbinds.device.none").withStyle(ChatFormatting.GRAY));
                deviceButton.setTooltip(null);
            }
            deviceButton.active = manager.hasDevice();
            calibrateButton.active = manager.hasDevice();
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int y = getContentY() - 2;
            int right = scrollBarX() - 10;
            calibrateButton.setPosition(right - calibrateButton.getWidth(), y);
            deviceButton.setPosition(calibrateButton.getX() - 5 - deviceButton.getWidth(), y);
            deviceButton.render(graphics, mouseX, mouseY, partialTick);
            calibrateButton.render(graphics, mouseX, mouseY, partialTick);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(deviceButton, calibrateButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(deviceButton, calibrateButton);
        }
    }

    /** 今押されている入力の表示。どの番号がどの物理ボタンかを確認するため。 */
    final class MonitorEntry extends Entry {
        private Component text = Component.empty();

        @Override
        void tick() {
            ControllerManager manager = screen.controller();
            if (!manager.hasDevice()) {
                text = Component.empty();
                return;
            }
            if (!manager.isCalibrated()) {
                text = Component.translatable("controllerbinds.monitor.calibrating").withStyle(ChatFormatting.GRAY);
                return;
            }
            List<ControllerInput> active = manager.activeInputs(0.5f);
            MutableComponent line = Component.translatable("controllerbinds.monitor.label").append(" ");
            if (active.isEmpty()) {
                line.append(Component.translatable("controllerbinds.monitor.none").withStyle(ChatFormatting.GRAY));
            } else {
                for (int i = 0; i < active.size(); i++) {
                    if (i > 0) {
                        line.append(", ");
                    }
                    line.append(active.get(i).displayName().copy().withStyle(ChatFormatting.YELLOW));
                }
            }
            text = line;
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            graphics.drawString(minecraft.font, text, getContentX(), getContentYMiddle() - minecraft.font.lineHeight / 2, 0xFFFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return List.of();
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return List.of();
        }
    }

    interface ProfileSetter {
        void set(ControllerBindsConfig.Profile profile, double value);
    }

    /** プロファイルの数値設定。 */
    final class SliderEntry extends Entry {
        private final Component name;
        private final Function<ControllerBindsConfig.Profile, Double> getter;
        private final Slider slider;

        SliderEntry(Component name, Function<ControllerBindsConfig.Profile, Double> getter, ProfileSetter setter,
                    DoubleFunction<Component> formatter) {
            this.name = name;
            this.getter = getter;
            this.slider = new Slider(formatter, value -> {
                ControllerBindsConfig.Profile profile = profile();
                if (profile != null) {
                    setter.set(profile, value);
                }
            });
            refresh();
        }

        @Override
        void refresh() {
            ControllerBindsConfig.Profile profile = profile();
            slider.active = profile != null;
            slider.setValueSilently(profile == null ? 0.0 : getter.apply(profile));
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            slider.setPosition(widgetX(slider.getWidth()), getContentY() - 2);
            slider.render(graphics, mouseX, mouseY, partialTick);
            graphics.drawString(minecraft.font, name, getContentX(), getContentYMiddle() - minecraft.font.lineHeight / 2, 0xFFFFFFFF);
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(slider);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(slider);
        }
    }

    static final class Slider extends AbstractSliderButton {
        private final DoubleFunction<Component> formatter;
        private final Consumer<Double> onChange;

        Slider(DoubleFunction<Component> formatter, Consumer<Double> onChange) {
            super(0, 0, WIDGET_WIDTH, 20, Component.empty(), 0.0);
            this.formatter = formatter;
            this.onChange = onChange;
            updateMessage();
        }

        void setValueSilently(double v) {
            this.value = Math.max(0.0, Math.min(1.0, v));
            updateMessage();
        }

        @Override
        protected void updateMessage() {
            setMessage(formatter.apply(value));
        }

        @Override
        protected void applyValue() {
            onChange.accept(value);
        }
    }

    /** 1つの操作への割り当て。 */
    final class BindEntry extends Entry {
        private final BindableAction action;
        private final Component name;
        private final Button changeButton;
        private final Button clearButton;
        private boolean duplicated;

        BindEntry(BindableAction action) {
            this.action = action;
            this.name = action.displayName();
            this.changeButton = Button.builder(Component.empty(), b -> screen.startListening(action))
                    .bounds(0, 0, 95, 20)
                    .build();
            this.clearButton = Button.builder(Component.translatable("controllerbinds.screen.clear"), b -> screen.clearBinding(action))
                    .bounds(0, 0, 50, 20)
                    .build();
            refresh();
        }

        @Override
        void refresh() {
            ControllerBindsConfig.Profile profile = profile();
            ControllerInput input = profile == null ? null : ControllerInput.parse(profile.bindings.get(action.id()));
            changeButton.active = profile != null;
            clearButton.active = input != null;
            duplicated = false;

            Component label = input == null
                    ? Component.translatable("controllerbinds.input.none").withStyle(ChatFormatting.DARK_GRAY)
                    : input.displayName();

            // 重複は許可する。分かるように印と一覧だけ表示する
            MutableComponent others = Component.empty();
            if (input != null) {
                String serialized = input.serialize();
                for (BindableAction other : ActionRegistry.all()) {
                    if (other != action && serialized.equals(profile.bindings.get(other.id()))) {
                        if (duplicated) {
                            others.append(", ");
                        }
                        duplicated = true;
                        others.append(other.displayName());
                    }
                }
            }
            if (duplicated) {
                label = Component.literal("[ ").append(label.copy().withStyle(ChatFormatting.WHITE)).append(" ]")
                        .withStyle(ChatFormatting.AQUA);
                changeButton.setTooltip(Tooltip.create(Component.translatable("controllerbinds.screen.shared", others)));
            } else {
                changeButton.setTooltip(null);
            }
            if (screen.selectedAction == action) {
                label = Component.literal("> ").append(label.copy().withStyle(ChatFormatting.WHITE, ChatFormatting.UNDERLINE))
                        .append(" <").withStyle(ChatFormatting.YELLOW);
            }
            changeButton.setMessage(label);
        }

        @Override
        public void renderContent(GuiGraphics graphics, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int y = getContentY() - 2;
            clearButton.setPosition(widgetX(clearButton.getWidth()), y);
            changeButton.setPosition(clearButton.getX() - 5 - changeButton.getWidth(), y);
            clearButton.render(graphics, mouseX, mouseY, partialTick);
            changeButton.render(graphics, mouseX, mouseY, partialTick);

            int maxNameWidth = changeButton.getX() - getContentX() - 6;
            Component shown = name;
            if (minecraft.font.width(name) > maxNameWidth) {
                shown = Component.literal(minecraft.font.plainSubstrByWidth(name.getString(), maxNameWidth - minecraft.font.width("..."))
                        + "...");
            }
            graphics.drawString(minecraft.font, shown, getContentX(), getContentYMiddle() - minecraft.font.lineHeight / 2, 0xFFFFFFFF);
            if (duplicated) {
                int x = changeButton.getX() - 6;
                graphics.fill(x, getContentY() - 1, x + 3, getContentBottom(), 0xFF55FFFF);
            }
        }

        @Override
        public List<? extends GuiEventListener> children() {
            return ImmutableList.of(changeButton, clearButton);
        }

        @Override
        public List<? extends NarratableEntry> narratables() {
            return ImmutableList.of(changeButton, clearButton);
        }
    }

}
