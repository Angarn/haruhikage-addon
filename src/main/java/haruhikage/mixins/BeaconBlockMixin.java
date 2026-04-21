package haruhikage.mixins;

import haruhikage.HaruhikageAddonSettings;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import com.llamalad7.mixinextras.sugar.Share;

@Mixin(targets = "net/minecraft/block/entity/BeaconBlockEntity$1")
public abstract class BeaconBlockMixin {

    @Inject(method = "run()V", at = @At("HEAD"))
    private void onStart(CallbackInfo ci, @Share("startTime") long startTime) {
        if(HaruhikageAddonSettings.logAsyncTimes) {
            System.out.println("Starting Async Thread...");
            startTime = System.nanoTime();
        }
    }

    @Inject(method = "run()V", at = @At("TAIL"))
    private void onEnd(CallbackInfo ci, @Share("startTime") long startTime) {
        if (HaruhikageAddonSettings.logAsyncTimes) {
            System.out.println("Async thread exiting. Alive for " + (System.nanoTime() - startTime) + "ns. Global timer: " + System.nanoTime());
        }
    }

}
