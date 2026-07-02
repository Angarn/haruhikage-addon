package haruhikage.command;

import carpet.CarpetServer;
import carpet.commands.CarpetAbstractCommand;
import carpet.utils.Messenger;
import haruhikage.HaruhikageAddonSettings;
import haruhikage.utils.cluster.ClusterHelper;
import haruhikage.utils.cluster.MSTGeneration;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.block.state.BlockState;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtException;
import net.minecraft.nbt.SnbtParser;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.command.AbstractCommand;
import net.minecraft.server.command.exception.CommandException;
import net.minecraft.server.command.exception.IncorrectUsageException;
import net.minecraft.server.command.source.CommandSource;
import net.minecraft.util.HttpUtil;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.jetbrains.annotations.Nullable;

import java.util.*;
import java.util.concurrent.Executors;

public class ClusterCommand extends CarpetAbstractCommand {

    public ClusterHelper.ClusterData tempData = null;

    @Override
    public String getName() {
        return "cluster";
    }

    @Override
    public String getUsage(CommandSource sender) {
        return "cluster peek/read <SNBT as parameters>/compute/loadCluster/loadGrid/constructLoader";
    }

    @Override
    public List<String> getSuggestions(MinecraftServer server, CommandSource sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return CarpetAbstractCommand.suggestMatching(args, "peek", "read", "compute", "loadCluster", "loadGrid", "constructLoader");
        }
        if (args.length <= 1) {
            return Collections.emptyList();
        }
        switch (args[0]) {
            case "read": {
                if (args.length == 2) {
                    return Collections.singletonList("{hashSize:?,startX:?,startZ:?,endX:?,endZ:?,clusterCornerX:?,clusterCornerZ:?,clusterWidth:?,maxClusterHeight:?,widthDir:east,heightDir:south,desiredClustering:5000}");
                }
                break;
            }
            case "constructLoader": {
                if (args.length == 2) {
                    return CarpetAbstractCommand.suggestMatching(args, Block.REGISTRY.keySet());
                }
                if (args.length == 3) {
                    return Collections.singletonList("0");
                }
                if (args.length == 4) {
                    return Collections.singletonList("128");
                }
                break;
            }
        }

        return Collections.emptyList();
    }

    @SuppressWarnings("deprecation")
    @Override
    public void run(MinecraftServer server, CommandSource sender, String[] args) throws CommandException {
        if (!CarpetAbstractCommand.canUseCommand(sender, HaruhikageAddonSettings.clusterCommand)) {
            Messenger.m(sender, "r Command not active! Enable it with /carpet clusterCommand true");
            return;
        }
        if (args.length < 1) {
            return;
        }
        switch (args[0]) {
            case "peek":
                if (this.tempData == null) {
                    throw new CommandException("No cluster parameters loaded! Use /cluster read");
                }
                Messenger.m(sender, "w " + tempData.writeToNBT(new NbtCompound()).toString());
                break;
            case "read":
                if (args.length < 2) {
                    throw new IncorrectUsageException(
                        "cluster read {hashSize:?,startX:?,startZ:?,endX:?,endZ:?,clusterCornerX:?,clusterCornerZ:?,clusterWidth:?,maxClusterHeight:?,widthDir:east,heightDir:south,desiredClustering:5000}\n"
                            + "[startX endX) [startZ, endZ): chunk coordinates to search for optimal falling block swap target chunk\n"
                            + "clusterCornerX, clusterCornerZ, clusterWidth widthDir, maxClusterHeight heightDir: chunk coordinates and direction to calculate optimal cluster in. Avoid world diagonals!\n"
                            + "hashSize: chunk hashmap size, desiredClustering: total clustering of chunks in 5x5 around target chunk excluding 2x2"
                    );
                }
                try {
                    NbtCompound compound = SnbtParser.parse(args[1]);
                    tempData = new ClusterHelper.ClusterData(compound);

                    Messenger.m(sender, "w Data parsed from SNBT! ");
                    this.validateReadParameters(sender);

                } catch (NbtException e) {
                    throw new CommandException("Malformed NBT");
                }
                break;
            case "compute":
                HttpUtil.DOWNLOAD_THREAD_FACTORY.submit(() -> {
                    long startMs = System.currentTimeMillis();
                    ClusterHelper.ClusterData computedData = ClusterHelper.compute(tempData);
                    if (computedData == null) {
                        CarpetServer.minecraftServer.submit(Executors.callable(() -> {
                            Messenger.m(sender, "r Calculation failed");
                        }));
                    } else {
                        CarpetServer.minecraftServer.submit(Executors.callable(() -> {
                            Messenger.m(sender, "w Computation of optimal cluster chunks completed in " +
                                (System.currentTimeMillis() - startMs) + " milliseconds, and the grid has " + computedData.loadingGridSize + " chunks");
                            Messenger.m(sender, "m The optimal target chunk is " + computedData.targetChunk);
                        }));
                    }
                });
                Messenger.m(sender, "y Cluster computation started on multiple threads. Please wait patiently. ");
                break;
            case "loadCluster":
                ClusterHelper.tempData.clusterChunks.forEach(chunkPos -> {
                    sender.getSourceWorld().getChunkAt(chunkPos.x, chunkPos.z);
                });
                Messenger.m(sender, "c Cluster chunks loaded! ");
                break;
            case "loadGrid":
                Set<ChunkPos> structureMap = MSTGeneration.generateStructureMap(ClusterHelper.tempData.clusterCornerPos, ClusterHelper.tempData.MSTEdgeList, ClusterHelper.tempData.clusterChunks);
                structureMap.forEach(chunkPos -> {
                    sender.getSourceWorld().getChunkAt(chunkPos.x, chunkPos.z);
                });
                Messenger.m(sender, "l Loading grid loaded! ");
                break;
            case "constructLoader":
                int y = 128;
                Block block = Blocks.QUARTZ_BLOCK;
                int metadata = 0;
                if (args.length >= 2) {
                    block = AbstractCommand.parseBlock(sender, args[1]);
                }
                if (args.length >= 3) {
                    metadata = Integer.parseInt(args[2]);
                }
                BlockState buildingBlock = block.getStateFromMetadata(metadata);
                if (args.length >= 4) {
                    y = Integer.parseInt(args[3]);
                }
                Messenger.m(sender, "d Building cluster grid with " + block.getName() + " at y" + y);
                MSTGeneration.buildLoadingGrid(
                    ClusterHelper.tempData.clusterCornerPos,
                    ClusterHelper.tempData.MSTEdgeList,
                    ClusterHelper.tempData.clusterChunks,
                    sender.getSourceWorld(),
                    y,
                    buildingBlock
                );
                Messenger.m(sender, "y Cluster grid loader has been constructed!!! ");
                break;
            default:
                throw new IncorrectUsageException(getUsage(sender));
        }
    }

    public void validateReadParameters(CommandSource sender) throws IncorrectUsageException {
        if (tempData.startPos.x >= tempData.endPos.x) {
            throw new IncorrectUsageException("startX should be < endX");
        }
        if (tempData.startPos.z >= tempData.endPos.z) {
            throw new IncorrectUsageException("startZ should be < endZ");
        }
        if (Integer.bitCount(tempData.hashSize) != 1) {
            throw new IncorrectUsageException("hashSize should be a power of 2");
        }

        if (tempData.clusterWidth * tempData.maximalClusterHeight < tempData.hashSize) {
            Messenger.m(sender, "y Warning: clusterWidth * maximalClusterHeight should be > hashSize for best results");
        }

        int x0 = tempData.clusterCornerPos.x + (Direction.WEST.equals(tempData.widthDir) ? -1 : 1) * tempData.clusterWidth;
        int minX = Math.min(tempData.clusterCornerPos.x, x0);
        int maxX = Math.max(tempData.clusterCornerPos.x, x0);

        int z0 = tempData.clusterCornerPos.z + (Direction.NORTH.equals(tempData.heightDir) ? -1 : 1) * tempData.maximalClusterHeight;
        int minZ = Math.min(tempData.clusterCornerPos.z, z0);
        int maxZ = Math.max(tempData.clusterCornerPos.z, z0);

        if (Math.max(minX, minZ) <= Math.min(maxX, maxZ) || Math.max(minX, -maxZ) <= Math.min(maxX, -minZ)) {
            Messenger.m(sender, "y Warning: cluster grid may cross over world diagonals!");
        }
    }
}
