package com.tuduraw.controllerbinds.mixin;

import com.tuduraw.controllerbinds.InputDispatcher;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * コントローラーで押されている操作は、キーボードの状態に関わらず「押下中」として扱う。
 * キーボード側の状態とは OR で合成するため、どちらか一方を離しても他方の押下は維持される。
 */
@Mixin(KeyMapping.class)
public abstract class KeyMappingMixin {
    @Inject(method = "isDown", at = @At("RETURN"), cancellable = true)
    private void controllerbinds$isDown(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ() && InputDispatcher.isHeld((KeyMapping) (Object) this)) {
            cir.setReturnValue(true);
        }
    }
}
