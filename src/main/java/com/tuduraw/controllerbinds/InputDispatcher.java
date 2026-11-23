package com.tuduraw.controllerbinds;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import com.tuduraw.controllerbinds.action.ActionRegistry;
import com.tuduraw.controllerbinds.action.BindableAction;
import com.tuduraw.controllerbinds.action.KeyMappingAction;
import com.tuduraw.controllerbinds.action.MouseAction;
import com.tuduraw.controllerbinds.config.ControllerBindsConfig;
import com.tuduraw.controllerbinds.input.ControllerInput;
import com.tuduraw.controllerbinds.input.ControllerManager;
import com.tuduraw.controllerbinds.mixin.KeyMappingAccessor;
import com.tuduraw.controllerbinds.mixin.KeyboardHandlerInvoker;
import com.tuduraw.controllerbinds.mixin.MouseHandlerAccessor;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Set;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.client.Screenshot;
import net.minecraft.client.ToggleKeyMapping;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonInfo;
import net.minecraft.util.Util;
import org.jspecify.annotations.Nullable;
import org.lwjgl.glfw.GLFW;

/**
 * 毎フレーム、コントローラーの状態を割り当てに従って操作へ反映する。
 * <ul>
 *   <li>キーコンフィグ項目: その項目の押下状態とクリック回数を直接操作する（キー入力は経由しない）。
 *       そのため、キーボード側で同じキーを共有している他の項目は反応しない。</li>
 *   <li>画面（GUI）表示中: 画面はキーイベントしか受け付けないため、その項目に現在割り当てられている
 *       キーボードのキーとして画面に通知する（インベントリを閉じる等）。</li>
 *   <li>マウス操作: 実際のマウス入力と同じ処理経路に渡す。</li>
 * </ul>
 */
public final class InputDispatcher {
    private static final long REPEAT_DELAY_MS = 500L;
    private static final long REPEAT_INTERVAL_MS = 33L;
    private static final long WHEEL_REPEAT_DELAY_MS = 300L;
    private static final long WHEEL_REPEAT_INTERVAL_MS = 100L;

    /** コントローラーで押下中の（トグル式でない）キー項目。KeyMapping.isDown() に OR で合成される。 */
    private static final Set<KeyMapping> HELD = Collections.newSetFromMap(new IdentityHashMap<>());
    private static final Map<String, ActionState> STATES = new HashMap<>();

    private static long lastFrameNanos = -1L;

    private InputDispatcher() {
    }

    private static final class ActionState {
        boolean pressed;
        /** 押したときに画面が開いていたか。 */
        boolean pressedInScreen;
        /** 押している間に画面の開閉が起きた。離されるまで何もしない。 */
        boolean waitRelease;
        long nextRepeat;
    }

    public static boolean isHeld(KeyMapping mapping) {
        return !HELD.isEmpty() && HELD.contains(mapping);
    }

    public static void onFrame() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return;
        }
        long nowNanos = System.nanoTime();
        double dt = lastFrameNanos < 0 ? 0.0 : Math.min(0.1, (nowNanos - lastFrameNanos) / 1.0e9);
        lastFrameNanos = nowNanos;

        ControllerBindsConfig config = ControllerBindsClient.config();
        ControllerManager manager = ControllerBindsClient.controller();
        manager.poll(config.selectedDevice);

        ControllerBindsConfig.Profile profile = manager.hasDevice() ? config.getProfile(manager.guid()) : null;
        boolean suppressed = profile == null
                || !manager.isCalibrated()
                || manager.isCapturing()
                || !mc.isWindowActive()
                || mc.getOverlay() != null;

        long now = Util.getMillis();
        double moveX = 0.0;
        double moveY = 0.0;

        for (BindableAction action : ActionRegistry.all()) {
            ActionState state = STATES.get(action.id());
            float value = 0f;
            if (!suppressed) {
                ControllerInput input = ControllerInput.parse(profile.bindings.get(action.id()));
                if (input != null) {
                    value = manager.value(input);
                }
            }
            if (state == null) {
                if (value <= 0f) {
                    continue;
                }
                state = new ActionState();
                STATES.put(action.id(), state);
            }

            if (action instanceof MouseAction mouse && mouse.isMovement()) {
                double v = applyDeadzone(value, profile == null ? 0f : profile.deadzone);
                switch (mouse) {
                    case MOVE_UP -> moveY -= v;
                    case MOVE_DOWN -> moveY += v;
                    case MOVE_LEFT -> moveX -= v;
                    case MOVE_RIGHT -> moveX += v;
                    default -> {
                    }
                }
                continue;
            }

            boolean active = profile != null && value >= profile.pressThreshold;
            boolean inScreen = mc.screen != null;
            if (active) {
                if (!state.pressed) {
                    state.pressed = true;
                    state.pressedInScreen = inScreen;
                    state.waitRelease = false;
                    state.nextRepeat = now + (action == MouseAction.WHEEL_UP || action == MouseAction.WHEEL_DOWN
                            ? WHEEL_REPEAT_DELAY_MS : REPEAT_DELAY_MS);
                    press(mc, action, inScreen);
                } else if (!state.waitRelease) {
                    if (state.pressedInScreen != inScreen && action instanceof KeyMappingAction) {
                        // 画面の開閉をまたいだ場合は、キーボードと同様にいったん離した扱いにする
                        release(mc, action, state.pressedInScreen);
                        state.waitRelease = true;
                    } else if (now >= state.nextRepeat) {
                        repeat(mc, action, inScreen);
                        state.nextRepeat = now + (action == MouseAction.WHEEL_UP || action == MouseAction.WHEEL_DOWN
                                ? WHEEL_REPEAT_INTERVAL_MS : REPEAT_INTERVAL_MS);
                    }
                }
            } else if (state.pressed) {
                state.pressed = false;
                if (!state.waitRelease) {
                    release(mc, action, state.pressedInScreen);
                }
                state.waitRelease = false;
            }
        }

        if (!suppressed && (moveX != 0.0 || moveY != 0.0) && dt > 0.0) {
            moveMouse(mc, moveX * profile.mouseSpeed * dt, moveY * profile.mouseSpeed * dt);
        }
    }

    /** すべての押下状態を解除する（設定画面での割り当て変更時など）。 */
    public static void releaseAll() {
        Minecraft mc = Minecraft.getInstance();
        for (BindableAction action : ActionRegistry.all()) {
            ActionState state = STATES.get(action.id());
            if (state != null && state.pressed && !state.waitRelease) {
                release(mc, action, state.pressedInScreen);
            }
        }
        STATES.clear();
        HELD.clear();
    }

    private static double applyDeadzone(float value, float deadzone) {
        if (value <= deadzone) {
            return 0.0;
        }
        return Math.min(1.0, (value - deadzone) / (1.0 - deadzone));
    }

    // ------------------------------------------------------------------ dispatch

    private static void press(Minecraft mc, BindableAction action, boolean inScreen) {
        if (action instanceof KeyMappingAction(KeyMapping mapping)) {
            pressKeyMapping(mc, mapping, inScreen);
        } else if (action instanceof MouseAction mouse) {
            pressMouse(mc, mouse);
        }
    }

    private static void repeat(Minecraft mc, BindableAction action, boolean inScreen) {
        if (action instanceof KeyMappingAction(KeyMapping mapping)) {
            InputConstants.Key key = boundKey(mapping);
            // キーボードのキーリピートと同じ振る舞い。マウスボタンに割り当てられた項目はリピートしない
            if (key.getType() == InputConstants.Type.MOUSE || isSpecial(mc.options, mapping)) {
                return;
            }
            if (inScreen) {
                sendKeyToScreen(mc, key, true);
            } else {
                click(mapping);
            }
        } else if (action == MouseAction.WHEEL_UP || action == MouseAction.WHEEL_DOWN) {
            pressMouse(mc, (MouseAction) action);
        }
    }

    private static void release(Minecraft mc, BindableAction action, boolean inScreen) {
        if (action instanceof KeyMappingAction(KeyMapping mapping)) {
            if (inScreen) {
                if (!isSpecial(mc.options, mapping)) {
                    sendKeyToScreen(mc, boundKey(mapping), false);
                }
            } else if (mapping instanceof ToggleKeyMapping) {
                mapping.setDown(false);
            } else {
                HELD.remove(mapping);
            }
        } else if (action instanceof MouseAction mouse) {
            releaseMouse(mc, mouse);
        }
    }

    private static void pressKeyMapping(Minecraft mc, KeyMapping mapping, boolean inScreen) {
        Options options = mc.options;
        // バニラで KeyEvent を直接判定している項目は、その処理を直接呼ぶ
        if (mapping == options.keyFullscreen) {
            Window window = mc.getWindow();
            window.toggleFullScreen();
            options.fullscreen().set(window.isFullscreen());
            options.save();
            return;
        }
        if (mapping == options.keyScreenshot) {
            Screenshot.grab(mc.gameDirectory, mc.getMainRenderTarget(),
                    message -> mc.execute(() -> ((KeyboardHandlerInvoker) mc.keyboardHandler).controllerbinds$showDebugChat(message)));
            return;
        }
        if (inScreen) {
            sendKeyToScreen(mc, boundKey(mapping), true);
            return;
        }
        if (mapping == options.keyToggleGui) {
            options.hideGui = !options.hideGui;
        } else if (mapping == options.keyToggleSpectatorShaderEffects) {
            mc.gameRenderer.togglePostEffect();
        } else if (mapping == options.keyDebugOverlay) {
            mc.debugEntries.toggleDebugOverlay();
        } else if (isDebugSubKey(options, mapping) && options.keyDebugModifier.isDown()) {
            KeyEvent event = keyEventFor(boundKey(mapping));
            if (event != null) {
                ((KeyboardHandlerInvoker) mc.keyboardHandler).controllerbinds$handleDebugKeys(event);
            }
        }

        if (mapping instanceof ToggleKeyMapping) {
            // トグル式（スニーク・ダッシュ）は押した／離したの通知だけ行い、切り替えは本体に任せる
            mapping.setDown(true);
        } else {
            HELD.add(mapping);
        }
        click(mapping);
    }

    private static void click(KeyMapping mapping) {
        KeyMappingAccessor accessor = (KeyMappingAccessor) mapping;
        accessor.controllerbinds$setClickCount(accessor.controllerbinds$getClickCount() + 1);
    }

    private static boolean isSpecial(Options options, KeyMapping mapping) {
        return mapping == options.keyFullscreen || mapping == options.keyScreenshot;
    }

    private static boolean isDebugSubKey(Options options, KeyMapping mapping) {
        if (mapping == options.keyDebugModifier || mapping == options.keyDebugOverlay || mapping == options.keyDebugCrash) {
            return false;
        }
        for (KeyMapping debugKey : options.debugKeys) {
            if (debugKey == mapping) {
                return true;
            }
        }
        return false;
    }

    private static InputConstants.Key boundKey(KeyMapping mapping) {
        return ((KeyMappingAccessor) mapping).controllerbinds$getBoundKey();
    }

    @Nullable
    private static KeyEvent keyEventFor(InputConstants.Key key) {
        if (key.equals(InputConstants.UNKNOWN)) {
            return null;
        }
        if (key.getType() == InputConstants.Type.KEYSYM) {
            return new KeyEvent(key.getValue(), GLFW.glfwGetKeyScancode(key.getValue()), 0);
        }
        if (key.getType() == InputConstants.Type.SCANCODE) {
            return new KeyEvent(InputConstants.UNKNOWN.getValue(), key.getValue(), 0);
        }
        return null;
    }

    private static void sendKeyToScreen(Minecraft mc, InputConstants.Key key, boolean pressed) {
        Screen screen = mc.screen;
        KeyEvent event = keyEventFor(key);
        if (screen == null || event == null) {
            return;
        }
        if (pressed) {
            screen.afterKeyboardAction();
            screen.keyPressed(event);
        } else {
            screen.keyReleased(event);
        }
    }

    // ------------------------------------------------------------------ mouse

    private static void pressMouse(Minecraft mc, MouseAction action) {
        long handle = mc.getWindow().handle();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        switch (action) {
            case BUTTON_LEFT -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_PRESS);
            case BUTTON_RIGHT -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0), GLFW.GLFW_PRESS);
            case BUTTON_MIDDLE -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0), GLFW.GLFW_PRESS);
            case WHEEL_UP -> mouse.controllerbinds$onScroll(handle, 0.0, 1.0);
            case WHEEL_DOWN -> mouse.controllerbinds$onScroll(handle, 0.0, -1.0);
            case ESCAPE -> {
                Screen screen = mc.screen;
                if (screen != null) {
                    KeyEvent event = new KeyEvent(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0);
                    screen.afterKeyboardAction();
                    screen.keyPressed(event);
                } else {
                    mc.pauseGame(false);
                }
            }
            default -> {
            }
        }
    }

    private static void releaseMouse(Minecraft mc, MouseAction action) {
        long handle = mc.getWindow().handle();
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        switch (action) {
            case BUTTON_LEFT -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_LEFT, 0), GLFW.GLFW_RELEASE);
            case BUTTON_RIGHT -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_RIGHT, 0), GLFW.GLFW_RELEASE);
            case BUTTON_MIDDLE -> mouse.controllerbinds$onButton(handle, new MouseButtonInfo(GLFW.GLFW_MOUSE_BUTTON_MIDDLE, 0), GLFW.GLFW_RELEASE);
            case ESCAPE -> {
                Screen screen = mc.screen;
                if (screen != null) {
                    screen.keyReleased(new KeyEvent(GLFW.GLFW_KEY_ESCAPE, GLFW.glfwGetKeyScancode(GLFW.GLFW_KEY_ESCAPE), 0));
                }
            }
            default -> {
            }
        }
    }

    private static void moveMouse(Minecraft mc, double dx, double dy) {
        MouseHandlerAccessor mouse = (MouseHandlerAccessor) mc.mouseHandler;
        if (mc.mouseHandler.isMouseGrabbed()) {
            // ゲーム中: マウスを動かしたのと同じく視点移動量として加算（感度・反転設定も本体の設定が適用される）
            mouse.controllerbinds$setAccumulatedDX(mouse.controllerbinds$getAccumulatedDX() + dx);
            mouse.controllerbinds$setAccumulatedDY(mouse.controllerbinds$getAccumulatedDY() + dy);
        } else if (mc.screen != null) {
            // 画面表示中: OS のカーソルを動かし、移動イベントとして通知する
            Window window = mc.getWindow();
            double x = Math.max(0.0, Math.min(window.getScreenWidth() - 1, mc.mouseHandler.xpos() + dx));
            double y = Math.max(0.0, Math.min(window.getScreenHeight() - 1, mc.mouseHandler.ypos() + dy));
            GLFW.glfwSetCursorPos(window.handle(), x, y);
            mouse.controllerbinds$onMove(window.handle(), x, y);
        }
    }
}
