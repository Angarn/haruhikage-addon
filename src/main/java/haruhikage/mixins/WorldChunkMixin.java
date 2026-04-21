package haruhikage.mixins;

import carpet.CarpetServer;
import carpet.utils.Messenger;
import haruhikage.HaruhikageAddonServer;
import haruhikage.HaruhikageAddonSettings;
import net.minecraft.world.World;
import org.objectweb.asm.Opcodes;
import net.minecraft.world.chunk.ChunkGenerator;
import net.minecraft.world.chunk.WorldChunk;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;


@Mixin(WorldChunk.class)
public abstract class WorldChunkMixin {

    @Unique
    private int cX;

    @Unique
    private int cZ;

    @Shadow
    private boolean terrainPopulated;

    @Shadow public abstract World getWorld();

    @Shadow @Final public int chunkZ;

    @Shadow @Final public int chunkX;

    @Inject(method = "unload", at = @At(value = "HEAD"))
    private void unloadChunkUnloadTimer(CallbackInfo ci) {
        if (HaruhikageAddonSettings.logCertainTickPhases
            && this.chunkX == HaruhikageAddonSettings.unloadChunkX
            && this.chunkZ == HaruhikageAddonSettings.unloadChunkZ)
        {
            System.out.println("server up for " + getWorld().getServer().getTicks() + "gt");
            System.out.println("gametime: " + getWorld().getTime() % 2147483647L + "gt");
        }
    }

    @Redirect(method = "populate(Lnet/minecraft/world/chunk/ChunkGenerator;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkGenerator;populateChunk(II)V"))
    private void populateChunk (ChunkGenerator generator, int x, int z) {
        if (!HaruhikageAddonSettings.disableTerrainPopulation) {
            this.cX = x;
            this.cZ = z;
            generator.populateChunk(x, z);
        }
    }

    @Redirect(method = "populateLight", at = @At(value = "FIELD", target = "Lnet/minecraft/world/chunk/WorldChunk;terrainPopulated:Z", opcode = Opcodes.PUTFIELD))
    private void redirectPopulation (WorldChunk chunk, boolean bool) {
        if (HaruhikageAddonSettings.disableTerrainPopulation) {
            this.terrainPopulated = false;
        } else {
            this.terrainPopulated = bool;
        }
    }

    @Inject(method = "populate(Lnet/minecraft/world/chunk/ChunkGenerator;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkGenerator;populateChunk(II)V", shift = At.Shift.BEFORE))
    private void populationLoggeStart(CallbackInfo ci) {
        if (HaruhikageAddonSettings.logChunkPopulation && !HaruhikageAddonSettings.disableTerrainPopulation) {
            Messenger.print_server_message(CarpetServer.minecraftServer, "Started populating a chunk...");
        }
    }

    @Inject(method = "populate(Lnet/minecraft/world/chunk/ChunkGenerator;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/chunk/ChunkGenerator;populateChunk(II)V", shift = At.Shift.AFTER))
    private void populationLoggerEnd(CallbackInfo ci) {
        if (HaruhikageAddonSettings.logChunkPopulation && !HaruhikageAddonSettings.disableTerrainPopulation) {
            Messenger.print_server_message(CarpetServer.minecraftServer, String.format("Finished populating chunk %d %d!", cX, cZ));
        }
    }
}
