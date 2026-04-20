package haruhikage.command;

import carpet.commands.CarpetAbstractCommand;
import carpet.utils.Messenger;
import haruhikage.mixins.ServerChunkCacheAccessor;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;

import java.lang.reflect.Field;

public class SearchCommand extends CarpetAbstractCommand {

    protected World world;

    @Override
    public String getName() {
        return "search";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "search";
    }

    @Override
    public void run(MinecraftServer server, CommandSource source, String[] args) throws CommandException {

        world = source.getSourceWorld();

        if (args[0].equals("hash")) {
            int hash = (int) getPrivateMethods(world, "n");
            Messenger.m(source, "d Current hashSize: " + hash);
        } else {
            Messenger.m(source, "d Clustering");
            try {
                search(source, parseChunkPosition(args[0], source.getSourceBlockPos().getX()), parseChunkPosition(args[1], source.getSourceBlockPos().getZ()));
            } catch (NoSuchFieldException | IllegalAccessException e) {
                throw new RuntimeException(e);
            }
        }
    }

    // (ChunkProviderServer) world.getChunkProvider().
    protected void search(CommandSource sender, int chunkX, int chunkZ) throws NoSuchFieldException, IllegalAccessException {
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = (Long2ObjectOpenHashMap<WorldChunk>) ((ServerChunkCacheAccessor) world.getChunkSource()).getChunks();
        loadedChunks.trim();
        Object[] chunks = getValues(loadedChunks);
        int mask = getMask(loadedChunks);
        for (int i = 0; i < chunks.length; i++) {
            WorldChunk chunk = (WorldChunk) chunks[i];
            if(chunk == null)
                continue;
            if (chunk.chunkX != chunkX || chunk.chunkZ != chunkZ)
                continue;
            sender.sendMessage(new LiteralText(formatChunk(chunk,i, mask)));
            break;
        }
    }

    private Object getPrivateMethods(World world, String name) {
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = (Long2ObjectOpenHashMap<WorldChunk>) ((ServerChunkCacheAccessor) world.getChunkSource()).getChunks();
        try {
            Field f = loadedChunks.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(loadedChunks);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    public static int getMask(Long2ObjectOpenHashMap hashMap) throws NoSuchFieldException, IllegalAccessException {
        Field mask = Long2ObjectOpenHashMap.class.getDeclaredField("mask");
        mask.setAccessible(true);
        return (int) mask.get(hashMap);
    }

    public static Object[] getValues(Long2ObjectOpenHashMap hashMap) throws NoSuchFieldException, IllegalAccessException {
        Field value = Long2ObjectOpenHashMap.class.getDeclaredField("value");
        value.setAccessible(true);
        return (Object[]) value.get(hashMap);
    }

    public String formatChunk(WorldChunk chunk, int pos, int mask){
        if (chunk == null) {
            return String.format("- %d: null", pos);

        }

        return String.format("- %d: %s(%d, %d) %d",
                pos, getChunkDescriber(chunk), chunk.chunkX, chunk.chunkZ,
                getKeyClass(chunk, mask));
    }

    public String getChunkDescriber(WorldChunk chunk){
        int x = chunk.chunkX, z = chunk.chunkZ;
        long hash = ChunkPos.toLong(x, z);
        String describer = "";
        if(world.isSpawnChunk(x, z)){
            describer +="S ";
        }
        if(((hash ^ (hash >>> 16)) & 0xFFFF) == 0){
            describer +="0 ";
        }
        return describer;
    }

    public static long getKeyClass(WorldChunk chunk, int mask){
        return HashCommon.mix(ChunkPos.toLong(chunk.chunkX, chunk.chunkZ)) & mask;
    }
}
