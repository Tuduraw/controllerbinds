package com.tuduraw.controllerbinds.action;

import net.minecraft.client.KeyMapping;
import net.minecraft.network.chat.Component;

/** キーコンフィグの項目（バニラ・Mod 共通）。キーではなく項目そのものに割り当てる。 */
public record KeyMappingAction(KeyMapping mapping) implements BindableAction {
    public static final String PREFIX = "key:";

    @Override
    public String id() {
        return PREFIX + mapping.getName();
    }

    @Override
    public Component displayName() {
        return Component.translatable(mapping.getName());
    }

    @Override
    public Component category() {
        return mapping.getCategory().label();
    }
}
