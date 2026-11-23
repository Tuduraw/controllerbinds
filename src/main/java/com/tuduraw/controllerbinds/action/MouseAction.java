package com.tuduraw.controllerbinds.action;

import net.minecraft.network.chat.Component;

/**
 * キーコンフィグに存在しないマウス操作の代替。
 * ゲーム中は視点操作、画面（GUI）表示中はカーソル操作として、実際のマウスと同じ経路で処理する。
 */
public enum MouseAction implements BindableAction {
    MOVE_UP("move_up"),
    MOVE_DOWN("move_down"),
    MOVE_LEFT("move_left"),
    MOVE_RIGHT("move_right"),
    BUTTON_LEFT("button_left"),
    BUTTON_RIGHT("button_right"),
    BUTTON_MIDDLE("button_middle"),
    WHEEL_UP("wheel_up"),
    WHEEL_DOWN("wheel_down"),
    /** Esc キー。ポーズメニューを開く／画面を閉じる。キーコンフィグに項目がないため用意する。 */
    ESCAPE("escape");

    public static final String PREFIX = "mouse:";
    private static final Component CATEGORY = Component.translatable("controllerbinds.category.mouse");

    private final String key;

    MouseAction(String key) {
        this.key = key;
    }

    @Override
    public String id() {
        return PREFIX + key;
    }

    @Override
    public Component displayName() {
        return Component.translatable("controllerbinds.action." + key);
    }

    @Override
    public Component category() {
        return CATEGORY;
    }

    public boolean isMovement() {
        return this == MOVE_UP || this == MOVE_DOWN || this == MOVE_LEFT || this == MOVE_RIGHT;
    }
}
