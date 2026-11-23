package com.tuduraw.controllerbinds.action;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;

/**
 * 割り当て可能な操作の一覧。キーコンフィグの全項目（キーコンフィグ画面と同じ順）と、マウス操作の代替。
 * Mod のキー項目はゲーム起動時に登録が終わっているため、最初の参照時に一度だけ組み立てる。
 */
public final class ActionRegistry {
    private static List<BindableAction> actions = List.of();
    private static int builtFor = -1;

    private ActionRegistry() {
    }

    public static List<BindableAction> all() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.options == null) {
            return List.of();
        }
        KeyMapping[] mappings = mc.options.keyMappings;
        if (builtFor != mappings.length) {
            KeyMapping[] sorted = mappings.clone();
            Arrays.sort(sorted);
            List<BindableAction> list = new ArrayList<>(sorted.length + MouseAction.values().length);
            for (KeyMapping mapping : sorted) {
                list.add(new KeyMappingAction(mapping));
            }
            Collections.addAll(list, MouseAction.values());
            actions = List.copyOf(list);
            builtFor = mappings.length;
        }
        return actions;
    }
}
