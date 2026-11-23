package com.tuduraw.controllerbinds.screen;

import com.tuduraw.controllerbinds.ControllerBindsClient;
import com.tuduraw.controllerbinds.InputDispatcher;
import com.tuduraw.controllerbinds.action.BindableAction;
import com.tuduraw.controllerbinds.config.ControllerBindsConfig;
import com.tuduraw.controllerbinds.input.ControllerManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.layouts.HeaderAndFooterLayout;
import net.minecraft.client.gui.layouts.LinearLayout;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/** コントローラー専用の割り当て画面。キーコンフィグ画面と同じ要領で、項目を選んでから入力を押して割り当てる。 */
public final class ControllerBindsScreen extends Screen {
    private static final Component TITLE = Component.translatable("controllerbinds.screen.title");

    @Nullable
    private final Screen parent;
    final HeaderAndFooterLayout layout = new HeaderAndFooterLayout(this, 46, HeaderAndFooterLayout.DEFAULT_HEADER_AND_FOOTER_HEIGHT);
    private ControllerBindsList list;
    private Button clearAllButton;

    /** 割り当て待ちの操作。 */
    @Nullable
    BindableAction selectedAction;

    public ControllerBindsScreen(@Nullable Screen parent) {
        super(TITLE);
        this.parent = parent;
    }

    ControllerBindsConfig config() {
        return ControllerBindsClient.config();
    }

    ControllerManager controller() {
        return ControllerBindsClient.controller();
    }

    /** 編集対象のプロファイル。接続中の機器のもの（なければ作る）。機器がなければ null。 */
    ControllerBindsConfig.@Nullable Profile profile(boolean create) {
        ControllerManager manager = controller();
        if (manager.hasDevice() && manager.guid() != null) {
            return create
                    ? config().getOrCreateProfile(manager.guid(), manager.name())
                    : config().getProfile(manager.guid());
        }
        return null;
    }

    @Override
    protected void init() {
        // 開いた時点で押下中の操作をすべて離す（画面上で誤作動しないように）
        InputDispatcher.releaseAll();
        // 割り当て画面を開いた機器を以後の優先機器とし、プロファイルを用意する
        if (controller().hasDevice() && controller().guid() != null) {
            config().selectedDevice = controller().guid();
            profile(true);
        }

        layout.addTitleHeader(TITLE, font);
        list = layout.addToContents(new ControllerBindsList(this, minecraft));

        clearAllButton = Button.builder(Component.translatable("controllerbinds.screen.clear_all"), b -> {
            ControllerBindsConfig.Profile profile = profile(false);
            if (profile == null || profile.bindings.isEmpty()) {
                return;
            }
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) {
                    InputDispatcher.releaseAll();
                    profile.bindings.clear();
                    config().save();
                }
                minecraft.setScreen(this);
            }, Component.translatable("controllerbinds.screen.clear_all.confirm"),
                    Component.translatable("controllerbinds.screen.clear_all.message")));
        }).build();
        LinearLayout footer = layout.addToFooter(LinearLayout.horizontal().spacing(8));
        footer.addChild(clearAllButton);
        footer.addChild(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).build());

        layout.visitWidgets(this::addRenderableWidget);
        repositionElements();
    }

    @Override
    protected void repositionElements() {
        layout.arrangeElements();
        if (list != null) {
            list.updateSize(width, layout);
        }
    }

    @Override
    public void tick() {
        super.tick();
        list.tickEntries();
        ControllerBindsConfig.Profile profile = profile(false);
        clearAllButton.active = profile != null && !profile.bindings.isEmpty();
    }

    // ------------------------------------------------------------------ binding

    void startListening(BindableAction action) {
        ControllerBindsConfig.Profile profile = profile(true);
        if (profile == null) {
            return;
        }
        selectedAction = action;
        controller().startCapture(input -> {
            InputDispatcher.releaseAll();
            profile.bindings.put(action.id(), input.serialize());
            selectedAction = null;
            config().save();
            list.refreshEntries();
        });
        list.refreshEntries();
    }

    void stopListening() {
        selectedAction = null;
        controller().cancelCapture();
        list.refreshEntries();
    }

    void clearBinding(BindableAction action) {
        ControllerBindsConfig.Profile profile = profile(false);
        if (profile != null && profile.bindings.remove(action.id()) != null) {
            InputDispatcher.releaseAll();
            config().save();
        }
        if (selectedAction == action) {
            stopListening();
        } else {
            list.refreshEntries();
        }
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (selectedAction != null) {
            BindableAction action = selectedAction;
            if (event.key() == GLFW.GLFW_KEY_DELETE || event.key() == GLFW.GLFW_KEY_BACKSPACE) {
                // Delete / Backspace: 割り当てを解除
                clearBinding(action);
            } else {
                // Esc などその他のキー: 割り当て待ちを取り消す
                stopListening();
            }
            return true;
        }
        return super.keyPressed(event);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (selectedAction != null) {
            stopListening();
            return true;
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        super.render(graphics, mouseX, mouseY, partialTick);
        Component hint = selectedAction != null
                ? Component.translatable("controllerbinds.screen.listening", selectedAction.displayName())
                : Component.translatable("controllerbinds.screen.hint");
        graphics.drawCenteredString(font, hint, width / 2, layout.getHeaderHeight() - 13,
                selectedAction != null ? 0xFFFFFF55 : 0xFFA0A0A0);
    }

    @Override
    public void removed() {
        controller().cancelCapture();
        config().save();
    }

    @Override
    public void onClose() {
        minecraft.setScreen(parent);
    }
}
