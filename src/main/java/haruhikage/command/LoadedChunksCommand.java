package haruhikage.command;

import carpet.commands.CarpetAbstractCommand;
import carpet.utils.Messenger;
import haruhikage.HaruhikageAddonSettings;
import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.server.world.chunk.ServerChunkCache;
import net.minecraft.text.LiteralText;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.world.World;
import net.minecraft.world.chunk.WorldChunk;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.reflect.Field;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.stream.Collectors;

public class LoadedChunksCommand extends CarpetAbstractCommand {

    @Override
    public String getName() {
        return "loadedChunks";
    }

    @Override
    public String getUsage(CommandSource sender) {
        return "loadedChunks <size | search | remove | add | inspect | dump>";
    }

    @Override
    public void run(MinecraftServer server, CommandSource sender, String[] args) throws CommandException {
        if (!CarpetAbstractCommand.canUseCommand(sender, HaruhikageAddonSettings.loadedChunksCommand)) {
            Messenger.m(sender, "r Command not active! Enable it with /carpet loadedChunksCommand true");
            return;
        }
        if (args.length == 0) {
            throw new IncorrectUsageException(getUsage(sender));
        }

        World world = sender.getSourceWorld();
        try {
            switch (args[0]) {
                case "size":
                    size(sender);
                    break;
                case "search": {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("loadedChunks search <chunkX> <chunkZ>");
                    }
                    int chunkX = parseChunkPosition(args[1], sender.getSourceBlockPos().getX());
                    int chunkZ = parseChunkPosition(args[2], sender.getSourceBlockPos().getZ());
                    search(sender, chunkX, chunkZ);
                    break;
                }
                case "remove": {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("loadedChunks remove <chunkX> <chunkZ>");
                    }
                    int chunkX = parseChunkPosition(args[1], sender.getSourceBlockPos().getX());
                    int chunkZ = parseChunkPosition(args[2], sender.getSourceBlockPos().getZ());
                    remove(sender, chunkX, chunkZ);
                    break;
                }
                case "add": {
                    if (args.length != 3) {
                        throw new IncorrectUsageException("loadedChunks add <chunkX> <chunkZ>");
                    }
                    int chunkX = parseChunkPosition(args[1], sender.getSourceBlockPos().getX());
                    int chunkZ = parseChunkPosition(args[2], sender.getSourceBlockPos().getZ());
                    add(sender, chunkX, chunkZ);
                    break;
                }
                case "inspect":
                    inspect(sender, args);
                    break;
                case "dump":
                    String fileName = "loadedchunks-" + new SimpleDateFormat("yyyy-MM-dd-HH-mm-ss-SSSS").format(new Date()) + ".csv";
                    try (PrintWriter pw = new PrintWriter(new BufferedWriter(new FileWriter(fileName)))) {
                        pw.println("index,key,x,z,hash");
                        long[] keys = (long[]) getPrivateField(world, "key");
                        Object[] values = (Object[]) getPrivateField(world, "value");
                        int hashSize = (int) getPrivateField(world, "n");
                        for (int i = 0, n = hashSize; i <= n; i++) {
                            long key = keys[i];
                            WorldChunk val = (WorldChunk) values[i];
                            if (val == null) {
                                pw.println(i + ",,,,");
                            } else {
                                pw.printf("%d,%d,%d,%d,%d\n", i, key, val.chunkX, val.chunkZ, HashCommon.mix(key) & (n - 1));
                            }
                        }
                        pw.flush();
                    }
                    AbstractCommand.sendSuccess(sender, this, "Written to %s", fileName);
                    break;
                default:
                    throw new IncorrectUsageException(getUsage(sender));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
            throw new CommandException(exception.getMessage());
        }

    }

    private Object getPrivateField(World world, String name) {
        ServerChunkCache provider = (ServerChunkCache) world.getChunkSource();
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = (Long2ObjectOpenHashMap<WorldChunk>) provider.chunks;
        try {
            Field f = loadedChunks.getClass().getDeclaredField(name);
            f.setAccessible(true);
            return f.get(loadedChunks);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return null;
    }

    protected Long2ObjectOpenHashMap<WorldChunk> getLoadedChunks(CommandSource sender) {
        World world = sender.getSourceWorld();
        ServerChunkCache provider = (ServerChunkCache) world.getChunkSource();
        return (Long2ObjectOpenHashMap<WorldChunk>) provider.chunks;
    }

    protected void size(CommandSource sender) throws NoSuchFieldException, IllegalAccessException {
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = this.getLoadedChunks(sender);
        int maxField = getMaxField(loadedChunks);
        int capacity = getMask(loadedChunks) + 1;
        Messenger.m(sender,
            String.format("w HashMap size is %d / %d, (%.2f). %d downsize, %d upsize",
                loadedChunks.size(), capacity, getFillLevel(loadedChunks), maxField / 4, maxField)
        );
    }

    protected void inspect(CommandSource sender, String[] args) throws CommandException, NoSuchFieldException, IllegalAccessException {
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = this.getLoadedChunks(sender);
        Object[] chunks = getValues(loadedChunks);
        int mask = getMask(loadedChunks);
        int start = 0, end = chunks.length;
        Optional<Long> keyClass = Optional.empty();
        for (int i = 1; i < args.length; i++) {
            try {
                switch (args[i]) {
                    case "from":
                        start = Integer.parseInt(args[++i]);
                        break;
                    case "to":
                        end = Integer.parseInt(args[++i]);
                        break;
                    case "class":
                        keyClass = Optional.of(Long.valueOf(args[++i]));
                        break;
                    default:
                        throw new IncorrectUsageException("loadedChunks inspect [from <start>] [to <end>] [class <keyClass>]");
                }
            } catch (IndexOutOfBoundsException | NumberFormatException e) {
                throw new IncorrectUsageException("loadedChunks inspect [from <start>] [to <end>] [class <keyClass>]");
            }
        }
        List<String> inspections = new ArrayList<>();
        String last = "";
        int lastN = 0;
        for (int i = start; (i & mask) != (end & mask); i++) {
            WorldChunk chunk = (WorldChunk) chunks[i & mask];
            if (keyClass.isPresent()) {
                if (chunk == null) {
                    if (!"null".equals(last)) {
                        if (lastN > 0) {
                            inspections.add(String.format("... %d %s", lastN, last));
                        }
                        last = "null";
                        lastN = 0;
                    }
                    lastN++;
                    continue;
                }
                if (getKeyClass(chunk, mask) != keyClass.get()) {
                    if (!"chunks".equals(last)) {
                        if (lastN > 0) {
                            inspections.add(String.format("... %d %s", lastN, last));
                        }
                        last = "chunks";
                        lastN = 0;
                    }
                    lastN++;
                    continue;
                }
            }
            if (!last.isEmpty()) {
                if (lastN > 0) {
                    inspections.add(String.format("... %d %s", lastN, last));
                }
                last = "";
                lastN = 0;
            }
            inspections.add(formatChunk(chunk, i & mask, mask));

        }
        if (lastN > 0) {
            inspections.add(String.format("... %d %s", lastN, last));
        }
        String result = inspections.stream().collect(Collectors.joining(", ", "[", "]"));
        sender.sendMessage(new LiteralText(result));
    }

    protected void search(CommandSource sender, int chunkX, int chunkZ) throws NoSuchFieldException, IllegalAccessException {
        World world = sender.getSourceWorld();
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = (Long2ObjectOpenHashMap<WorldChunk>) ((ServerChunkCache) world.getChunkSource()).chunks;
        Object[] chunks = getValues(loadedChunks);
        int mask = getMask(loadedChunks);
        for (int i = 0; i < chunks.length; i++) {
            WorldChunk chunk = (WorldChunk) chunks[i];
            if (chunk == null) {
                continue;
            }
            if (chunk.chunkX != chunkX || chunk.chunkZ != chunkZ) {
                continue;
            }
            sender.sendMessage(new LiteralText(formatChunk(chunk, i, mask)));
            return;
        }
        sender.sendMessage(new LiteralText(String.format("Chunk (%d, %d) is not loaded!", chunkX, chunkZ)));
    }

    private static Map<Long, WorldChunk> tempRemovedChunks = new HashMap<>();

    protected void add(CommandSource sender, int x, int z) {
        long hash = ChunkPos.toLong(x, z);
        WorldChunk chunk = tempRemovedChunks.remove(hash);
        if (chunk == null) {
            sender.sendMessage(new LiteralText(String.format("Chunk (%d, %d) wasn't found", x, z)));
            return;
        }
        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = getLoadedChunks(sender);
        loadedChunks.put(hash, chunk);
        sender.sendMessage(new LiteralText(String.format("Chunk (%d, %d) has been added back", x, z)));
    }

    protected void remove(CommandSource sender, int x, int z) {
        long hash = ChunkPos.toLong(x, z);

        Long2ObjectOpenHashMap<WorldChunk> loadedChunks = getLoadedChunks(sender);
        if (!loadedChunks.containsKey(hash)) {
            sender.sendMessage(new LiteralText(String.format("Chunk (%d, %d) is not in loaded list", x, z)));
            return;
        }
        WorldChunk chunk = loadedChunks.remove(hash);
        tempRemovedChunks.put(hash, chunk);
        sender.sendMessage(new LiteralText(String.format("Chunk (%d, %d) has been removed", x, z)));
    }

    public String formatChunk(WorldChunk chunk, int pos, int mask) {
        if (chunk == null) {
            return String.format("%d: null", pos);
        }

        return String.format("%d: %s(%d, %d) %d",
            pos, getChunkDescriber(chunk), chunk.chunkX, chunk.chunkZ,
            getKeyClass(chunk, mask));
    }

    public String getChunkDescriber(WorldChunk chunk) {
        int x = chunk.chunkX, z = chunk.chunkZ;
        long hash = ChunkPos.toLong(x, z);
        String describer = "";
        if (chunk.getWorld().isSpawnChunk(x, z)) {
            describer += "S ";
        }
        if (((hash ^ (hash >>> 16)) & 0xFFFF) == 0) {
            describer += "0 ";
        }
        return describer;
    }

    public static long getKeyClass(WorldChunk chunk, int mask) {
        return HashCommon.mix(ChunkPos.toLong(chunk.chunkX, chunk.chunkZ)) & mask;
    }

    public static int getMaxField(Long2ObjectOpenHashMap<?> hashMap) throws NoSuchFieldException, IllegalAccessException {
        Field maxFill = Long2ObjectOpenHashMap.class.getDeclaredField("maxFill");
        maxFill.setAccessible(true);
        return (int) maxFill.get(hashMap);
    }

    public static int getMask(Long2ObjectOpenHashMap<?> hashMap) throws NoSuchFieldException, IllegalAccessException {
        Field mask = Long2ObjectOpenHashMap.class.getDeclaredField("mask");
        mask.setAccessible(true);
        return (int) mask.get(hashMap);
    }

    public static float getFillLevel(Long2ObjectOpenHashMap<?> hashMap) throws NoSuchFieldException, IllegalAccessException {
        return (float) hashMap.size() / getMaxField(hashMap);
    }

    public static Object[] getValues(Long2ObjectOpenHashMap<?> hashMap) throws NoSuchFieldException, IllegalAccessException {
        Field value = Long2ObjectOpenHashMap.class.getDeclaredField("value");
        value.setAccessible(true);
        return (Object[]) value.get(hashMap);
    }

    @Override
    public List<String> getSuggestions(MinecraftServer server, CommandSource sender, String[] args, @Nullable BlockPos targetPos) {

        if (!HaruhikageAddonSettings.loadedChunksCommand) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            return AbstractCommand.suggestMatching(args, "size", "inspect", "search", "remove", "add", "dump");
        }

        switch (args[0]) {
            case "inspect":
                switch (args[args.length - 1]) {
                    case "class":
                    case "from":
                    case "to":
                        return Collections.emptyList();
                }
                return AbstractCommand.suggestMatching(args, "class", "from", "to");
            case "search":
            case "remove":
            case "add":
                if (args.length > 3) {
                    return Collections.emptyList();
                }
                return getChunkCompletions(sender, args, 2);
        }

        return Collections.emptyList();
    }


    public List<String> getChunkCompletions(CommandSource sender, String[] args, int index) {
        int chunkX = sender.getSourceBlockPos().getX() >> 4;
        int chunkZ = sender.getSourceBlockPos().getZ() >> 4;

        if (args.length == index) {
            return AbstractCommand.suggestMatching(args, Integer.toString(chunkX), "~");
        } else if (args.length == index + 1) {
            return AbstractCommand.suggestMatching(args, Integer.toString(chunkZ), "~");
        } else {
            return Collections.emptyList();
        }
    }
}
