package haruhikage.command;

import carpet.commands.CarpetAbstractCommand;
import carpet.utils.Messenger;
import carpet.utils.MixinGlobals;
import haruhikage.HaruhikageAddonSettings;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.ColoredBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.DyeColor;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.text.LiteralText;
import net.minecraft.util.BitStorage;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.chunk.*;
import org.jetbrains.annotations.Nullable;

import java.lang.ref.WeakReference;
import java.util.*;

public class PaletteCommand extends CarpetAbstractCommand {

    @Override
    public String getName() {
        return "palette";
    }

    @Override
    public String getUsage(CommandSource source) {
        return "palette <bits | size | get | match | print | fill> <X> <Y> <Z> [normal | full] [4-8 | 13]";
    }

    @Override
    public void run(MinecraftServer server, CommandSource source, String[] args) throws CommandException {
        if (!HaruhikageAddonSettings.paletteCommand) {
            Messenger.m(source, "r Command not active! Enable it with /carpet paletteCommand true");
            return;
        }

        try {
            BlockPos pos = new BlockPos(source.getSourceBlockPos().getX(), source.getSourceBlockPos().getY(), source.getSourceBlockPos().getZ());
            if (args.length >= 4) {
                pos = parseBlockPos(source, args, 1, false);
            }

            World world = source.getSourceWorld();
            WorldChunk chunk = world.getChunk(pos);
            WorldChunkSection[] sections = chunk.getSections();
            int h = MathHelper.clamp(pos.getY() >> 4, 0, 15);
            WorldChunkSection subchunk = sections[h];
            if (subchunk == null) {
                source.sendMessage(new LiteralText("Empty subchunk!"));
                return;
            }

            PalettedContainer palettedContainer = subchunk.getBlockStateStorage();
            Palette palette = palettedContainer.palette;
            int bits = palettedContainer.bits;
            int size = PaletteCommand.getSize(palette);

            switch (args[0]) {
                case "bits":
                    source.sendMessage(new LiteralText(palette.getClass().getSimpleName() + " bits: " + bits));
                    return;
                case "size":
                    source.sendMessage(new LiteralText(palette.getClass().getSimpleName() + " size: " + size));
                    Set<BlockState> blockStates = new HashSet<>();
                    for (int x = 0; x < 16; x++) {
                        for (int y = 0; y < 16; y++) {
                            for (int z = 0; z < 16; z++) {
                                blockStates.add(subchunk.getBlockState(x, y, z));
                            }
                        }
                    }
                    source.sendMessage(new LiteralText("Current number of blockstates: " + blockStates.size()));
                    return;
                case "print":
                    source.sendMessage(new LiteralText(palette.getClass().getSimpleName() + " bits: " + bits + " size: " + size));
                    for (int i = 0; i < size; i++) {
                        BlockState blockState = palette.valueFor(i);
                        String bitString = String.format("%" + bits + "s", Integer.toBinaryString(i)).replace(' ', '0');
                        String s = bitString + " " + String.valueOf(blockState).replace("minecraft:", "");
                        source.sendMessage(new LiteralText(s));
                    }
                    return;
                case "get":
                    if (args.length < 4) {
                        throw new IncorrectUsageException("palette get <X> <Y> <Z> [normal | full]");
                    }
                    boolean isFull = args.length >= 5 && args[4].equals("full");
                    this.infoPalette(source, palettedContainer, pos, isFull, null);
                    return;
                case "match":
                    try {
                        Block block = AbstractCommand.parseBlock(source, args[4]);
                        BlockState blockState = null;
                        if (args.length >= 6) {
                            blockState = AbstractCommand.parseBlockState(block, args[5]);
                        } else if (block != null) {
                            blockState = block.defaultState();
                        }
                        if (blockState != null) {
                            this.infoPalette(source, palettedContainer, pos, false, blockState);
                        } else {
                            throw new IncorrectUsageException("palette match <X> <Y> <Z> block [meta]");
                        }
                    } catch (Exception e) {
                        throw new IncorrectUsageException("palette match <X> <Y> <Z> block [meta]");
                    }
                    return;
                case "fill":
                    int type = 1;
                    if (args.length >= 5) {
                        if ("full".equals(args[4])) {
                            type = 2;
                        } else if ("restore".equals(args[4])) {
                            type = 3;
                        }
                    }
                    int bitSize = args.length >= 6 ? parseInt(args[5]) : bits;
                    fill(source, pos, type, bitSize);
                    return;
                default:
                    throw new IncorrectUsageException("palette fill <X> <Y> <Z> [normal | full | restore] [4-8 | 13]");
            }
        } catch (Exception e) {
            e.printStackTrace();
            if (e instanceof IncorrectUsageException) {
                throw e;
            }
            throw new IncorrectUsageException(getUsage(source));
        }
    }

    private void infoPalette(CommandSource source, PalettedContainer bsc, BlockPos pos, boolean full, BlockState blockState) {
        BitStorage bitStorage = bsc.storage;
        int bits = bitStorage.bits;
        int index = getIndex(pos);
        int i = index * bits;
        int j = i / 64;
        int k = ((index + 1) * bits - 1) / 64;
        int l = i % 64;
        long[] longArray = bitStorage.data;

        if (j == k) {
            displayJKBits(source, longArray[j], l, l + bits - 1, "");
        } else {
            displayJKBits(source, longArray[j], l, 64, "1");
            displayJKBits(source, longArray[k], 0, (l + bits - 1) % 64, "2");
        }

        if (full) {
            for (BlockPos bp : getArrayFromJK(j, k, bits, pos)) {
                source.sendMessage(new LiteralText(bp.toString()));
            }
        }

        if (blockState == null) {
            return;
        }
        if (!(bsc.palette instanceof GlobalPalette)) {
            source.sendMessage(new LiteralText("This subchunk doesn't have enough blockstates, add more blockstates."));
            return;
        }
        if (j == k) {
            source.sendMessage(new LiteralText("This location doesn't share two bit arrays."));
            return;
        }

        // match
        int blockStateBits = Block.STATE_REGISTRY.getId(blockState);
        int leftBits = 64 - l;
        int rightBits = bits - leftBits;
        int leftMask = (1 << leftBits) - 1;
        int rightMask = ((1 << rightBits) - 1) << leftBits;
        int blockStateMaskL = blockStateBits & leftMask;
        int blockStateMaskR = blockStateBits & rightMask;

        source.sendMessage(new LiteralText("Left bit match:"));
        for (int id = 0; id < Block.STATE_REGISTRY.size(); id++) {
            BlockState blockstate = Block.STATE_REGISTRY.get(id);
            if (blockstate != null) {
                int left = id & leftMask;
                if (left == blockStateMaskL) {
                    String bitString = String.format("%" + bits + "s", Integer.toBinaryString(id)).replace(' ', '0');
                    //String s = "§c" + bitString.substring(0, leftBits) + "§f" + bitString.substring(leftBits, leftBits + rightBits) + " " + blockstate.toString().replace("minecraft:", "");
                    String s = bitString + " " + blockstate.toString().replace("minecraft:", "");
                    source.sendMessage(new LiteralText(s));
                }
            }
        }

        source.sendMessage(new LiteralText("Right bit match:"));
        for (int id = 0; id < Block.STATE_REGISTRY.size(); id++) {
            BlockState blockstate = Block.STATE_REGISTRY.get(id);
            if (blockstate != null) {
                int right = id & rightMask;
                if (right == blockStateMaskR) {
                    String bitString = String.format("%" + bits + "s", Integer.toBinaryString(id)).replace(' ', '0');
                    //String s = bitString.substring(0, leftBits) +  "§c" + bitString.substring(leftBits, leftBits + rightBits) + "§f" + " " + blockstate.toString().replace("minecraft:", "");
                    String s = bitString + " " + blockstate.toString().replace("minecraft:", "");
                    source.sendMessage(new LiteralText(s));
                }
            }
        }
    }

    private static void displayJKBits(CommandSource sender, long longString, long l1, long l2, String append) {
        StringBuilder sb = new StringBuilder();
        String add = "§f";
        for (int bitNum = 0; bitNum < 64; bitNum++) {
            char s = (longString & 1) == 1 ? '1' : '0';
            longString = longString >> 1;
            if (bitNum == l1) add = "§c";
            sb.append(add).append(s);
            if (bitNum == l2) add = "§f";
        }
        sender.sendMessage(new LiteralText("§8L" + append + ":" + sb));
    }

    private static BlockPos[] getArrayFromJK(int j, int k, int bits, BlockPos pos) {
        BlockPos basePos = new BlockPos(pos.getX() >>> 4 << 4, pos.getY() >>> 4 << 4, pos.getZ() >>> 4 << 4);
        ArrayList<BlockPos> list = new ArrayList<>();
        for (int index = 0; index < 4096; index++) {
            int i = index * bits;
            int jj = i / 64;
            int kk = ((index + 1) * bits - 1) / 64;
            if (jj == j || kk == k || jj == k || kk == j) {
                list.add(getBlockIndex(index, basePos));
            }
        }
        return list.toArray(new BlockPos[0]);
    }

    private static int getIndex(BlockPos pos) {
        int x = pos.getX() & 15;
        int y = pos.getY() & 15;
        int z = pos.getZ() & 15;

        return y << 8 | z << 4 | x;
    }

    private static BlockPos getBlockIndex(int index, BlockPos pos) {
        int x = (pos.getX() & ~0xF) | (index & 0xF);
        int y = (pos.getY() & ~0xF) | ((index >>> 8) & 0xF);
        int z = (pos.getZ() & ~0xF) | ((index >>> 4) & 0xF);

        return new BlockPos(x, y, z);
    }

    public static int getSize(Palette palette) {
        if (palette instanceof LinearPalette) {
            return  ((LinearPalette) palette).size;
        } else if (palette instanceof HashMapPalette) {
            return  ((HashMapPalette) palette).values.size();
        } else if (palette instanceof GlobalPalette) {
            return Block.STATE_REGISTRY.size();
        }
        throw new UnsupportedOperationException("Unknown palette class " + palette);
    }

    @Override
    public List<String> getSuggestions(MinecraftServer server, CommandSource sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return AbstractCommand.suggestMatching(args, "bits", "size", "get", "match", "print", "fill");
        } else if (args.length >= 2 && args.length <= 4) {
            return AbstractCommand.suggestCoordinate(args, 1, targetPos);
        } else if (args.length == 5 && (args[0].equals("get") || args[0].equals("fill"))) {
            return AbstractCommand.suggestMatching(args, "full", "normal");
        } else if (args.length == 5 && args[0].equals("match")) {
            return AbstractCommand.suggestMatching(args, Block.REGISTRY.keySet());
        } else if (args.length == 6 && args[0].equals("fill")) {
            return AbstractCommand.suggestMatching(args, "4", "5", "6", "7", "8", "13");
        } else {
            return Collections.emptyList();
        }
    }

    // prevent cross-world restore
    private static WeakReference<World> backupWorld = null;
    private static BlockState[] backup = null;
    private static Map<BlockPos, BlockEntity> tileEntityList = new HashMap<>();

    private static void fill(CommandSource source, BlockPos pos, int type, int bitSize) {
        // prevent fill updates, using setBlockState flag=2 to send block changes to client
        MixinGlobals.pushYeetUpdateFlags();
        World world = source.getSourceWorld();
        if (backupWorld == null || backupWorld.get() != world) {
            backupWorld = null;
            backup = null;
            tileEntityList.clear();
        }

        if (type != 3 && backup != null) {
            type = 3;
        }

        if (type == 3) {
            source.sendMessage(new LiteralText("Restoring backup of blocks and block entities"));
            if (backup == null) {
                source.sendMessage(new LiteralText("No backup to restore!"));
                return;
            }
        } else {
            source.sendMessage(new LiteralText("Filling with type " + type + ", bitSize: " + bitSize));
            backup = new BlockState[4096];
            backupWorld = new WeakReference<>(world);
            tileEntityList = new HashMap<>();
        }

        BlockPos basePos = new BlockPos(pos.getX() >>> 4 << 4, pos.getY() >>> 4 << 4, pos.getZ() >>> 4 << 4);
        int color = -1;
        int storeJ = -1;

        for (int i = 0; i < 4096; i++) {
            BlockPos set = getBlockIndex(i, basePos);
            if (type == 1) {
                // normal
                int j = i * bitSize / 64;
                int k = ((i + 1) * bitSize - 1) / 64;

                if (j != k) {
                    backup[i] = world.getBlockState(set);
                    BlockEntity te = world.getBlockEntity(set);
                    if (te != null) {
                        tileEntityList.put(set, te);
                        world.removeBlockEntity(set);
                    }
                    world.setBlockState(set, Blocks.GLASS.defaultState(), 2);
                }
            } else if (type == 2) {
                // full
                backup[i] = world.getBlockState(set);
                BlockEntity te = world.getBlockEntity(set);
                if (te != null) {
                    tileEntityList.put(set, te);
                    world.removeBlockEntity(set);
                }
                int j = i * bitSize / 64;
                int k = ((i + 1) * bitSize - 1) / 64;

                if (j != storeJ) {
                    storeJ = j;
                    color = (color + 1) & 15;
                }
                BlockState state = (j != k)
                    ? Blocks.GLASS.defaultState()
                    : Blocks.STAINED_GLASS.defaultState().set(ColoredBlock.COLOR, DyeColor.byMetadata(color));
                world.setBlockState(set, state, 2);

            } else if (type == 3) {
                // restore
                if (backup[i] != null) {
                    world.setBlockState(set, backup[i], 2);
                    BlockEntity te = tileEntityList.get(set);
                    if (te != null) {
                        world.removeBlockEntity(set);
                        te.cancelRemoval();
                        world.setBlockEntity(set, te);
                    }
                }
            }
        }
        if (type == 3) {
            backup = null;
            backupWorld = null;
            tileEntityList.clear();
        }
        MixinGlobals.restoreYeetUpdateFlags();
    }
}

