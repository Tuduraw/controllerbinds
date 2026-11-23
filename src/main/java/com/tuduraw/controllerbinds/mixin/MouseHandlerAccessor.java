package com.tuduraw.controllerbinds.mixin;

import net.minecraft.client.MouseHandler;
import net.minecraft.client.input.MouseButtonInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MouseHandler.class)
public interface MouseHandlerAccessor {
    @Accessor("accumulatedDX")
    double controllerbinds$getAccumulatedDX();

    @Accessor("accumulatedDX")
    void controllerbinds$setAccumulatedDX(double value);

    @Accessor("accumulatedDY")
    double controllerbinds$getAccumulatedDY();

    @Accessor("accumulatedDY")
    void controllerbinds$setAccumulatedDY(double value);

    @Invoker("onButton")
    void controllerbinds$onButton(long window, MouseButtonInfo info, int action);

    @Invoker("onScroll")
    void controllerbinds$onScroll(long window, double xOffset, double yOffset);

    @Invoker("onMove")
    void controllerbinds$onMove(long window, double x, double y);
}
