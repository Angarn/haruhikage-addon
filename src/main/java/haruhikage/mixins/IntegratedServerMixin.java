package haruhikage.mixins;

import com.mojang.authlib.GameProfileRepository;
import com.mojang.authlib.minecraft.MinecraftSessionService;
import com.mojang.authlib.yggdrasil.YggdrasilAuthenticationService;
import net.minecraft.server.GameProfileCache;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.integrated.IntegratedServer;
import net.minecraft.util.datafix.DataFixerUpper;
import org.apache.logging.log4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.File;
import java.net.Proxy;

@Mixin(IntegratedServer.class)
public abstract class IntegratedServerMixin extends MinecraftServer {

    public IntegratedServerMixin(File gameDir, Proxy proxy, DataFixerUpper dfu, YggdrasilAuthenticationService authService, MinecraftSessionService sessionService, GameProfileRepository profileRepository, GameProfileCache profileCache) {
        super(gameDir, proxy, dfu, authService, sessionService, profileRepository, profileCache);
    }

    @Inject(method = "tick()V", at = @At(value = "INVOKE", target = "Lnet/minecraft/server/integrated/IntegratedServer;saveWorlds(Z)V", shift = At.Shift.BEFORE))
    private void logEscGametime(CallbackInfo ci) {
        System.out.println("server up for " + getTicks() + "gt");
        System.out.println("gametime: " + worlds[0].getTime() % 2147483647L + "gt");
    }


}
