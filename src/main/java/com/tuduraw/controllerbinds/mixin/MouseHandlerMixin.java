package com.tuduraw.controllerbinds.mixin;

import com.tuduraw.controllerbinds.InputDispatcher;
import net.minecraft.client.MouseHandler;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** 毎フレーム呼ばれるマウス移動処理の直前でコントローラーを読み取り、同じフレームの視点移動に反映させる。 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerMixin {
    @Inject(method = "handleAccumulatedMovement", at = @At("HEAD"))
    private void controllerbinds$beforeMovement(CallbackInfo ci) {
        InputDispatcher.onFrame();
    }
}
