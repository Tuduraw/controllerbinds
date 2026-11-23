package com.tuduraw.controllerbinds.input;

import net.minecraft.network.chat.Component;
import org.jspecify.annotations.Nullable;

/**
 * コントローラー上の1つの入力。GLFW のジョイスティック API の生の番号で識別するため、
 * ゲームパッド以外（フライトスティック、ハンドル等）でも同じ扱いになる。
 * 軸は正方向・負方向を別々の入力として扱う。
 */
public record ControllerInput(Kind kind, int index) {

    public enum Kind {
        BUTTON("button"),
        AXIS_POSITIVE("axis+"),
        AXIS_NEGATIVE("axis-");

        final String prefix;

        Kind(String prefix) {
            this.prefix = prefix;
        }
    }

    public static ControllerInput button(int index) {
        return new ControllerInput(Kind.BUTTON, index);
    }

    public static ControllerInput axis(int index, boolean positive) {
        return new ControllerInput(positive ? Kind.AXIS_POSITIVE : Kind.AXIS_NEGATIVE, index);
    }

    public String serialize() {
        return kind.prefix + ":" + index;
    }

    @Nullable
    public static ControllerInput parse(@Nullable String text) {
        if (text == null) {
            return null;
        }
        int sep = text.indexOf(':');
        if (sep <= 0) {
            return null;
        }
        String prefix = text.substring(0, sep);
        int index;
        try {
            index = Integer.parseInt(text.substring(sep + 1));
        } catch (NumberFormatException e) {
            return null;
        }
        if (index < 0) {
            return null;
        }
        for (Kind kind : Kind.values()) {
            if (kind.prefix.equals(prefix)) {
                return new ControllerInput(kind, index);
            }
        }
        return null;
    }

    /** 表示上の番号は 1 始まり（OS のコントローラー設定画面と揃える）。 */
    public Component displayName() {
        return switch (kind) {
            case BUTTON -> Component.translatable("controllerbinds.input.button", index + 1);
            case AXIS_POSITIVE -> Component.translatable("controllerbinds.input.axis_positive", index + 1);
            case AXIS_NEGATIVE -> Component.translatable("controllerbinds.input.axis_negative", index + 1);
        };
    }
}
