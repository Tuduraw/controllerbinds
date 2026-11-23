package com.tuduraw.controllerbinds.mixin;

import net.minecraft.client.KeyboardHandler;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(KeyboardHandler.class)
public interface KeyboardHandlerInvoker {
    @Invoker("handleDebugKeys")
    boolean controllerbinds$handleDebugKeys(KeyEvent event);

    @Invoker("showDebugChat")
    void controllerbinds$showDebugChat(Component message);
}
