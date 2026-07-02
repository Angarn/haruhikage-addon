package haruhikage.utils.cluster;

import net.minecraft.block.Blocks;
import net.minecraft.block.HopperBlock;
import net.minecraft.block.TorchBlock;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.state.BlockState;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import net.minecraft.world.World;

import java.util.*;

// credit: RoyanAB, Void-Skeleton

public class MSTGeneration {
    public static List<MSTEdge> generateMST(List<ChunkPos> chunks) {
        int n = chunks.size();
        if (n <= 1) {
            return new ArrayList<>();
        }

        List<int[]> edges = new ArrayList<>();
        Integer[] order = new Integer[n];
        int[] x = new int[n], z = new int[n];
        for (int i = 0; i < n; i++) {
            order[i] = i;
            x[i] = chunks.get(i).x;
            z[i] = chunks.get(i).z;
        }
        ;

        for (int t = 0; t < 4; t++) {
            Arrays.sort(order, Comparator.comparingInt(a -> x[a] + z[a]));

            TreeMap<Integer, Integer> active = new TreeMap<>(Comparator.reverseOrder());

            for (int idx : order) {
                SortedMap<Integer, Integer> candidates = active.tailMap(x[idx], true);

                while (!candidates.isEmpty()) {
                    int key = candidates.firstKey();
                    int j = active.get(key);

                    if (x[idx] - z[idx] > x[j] - z[j]) {
                        break;
                    }

                    edges.add(new int[] { (x[idx] - x[j]) + (z[idx] - z[j]), idx, j });

                    active.remove(key);
                }
                active.put(x[idx], idx);
            }

            for (int i = 0; i < n; i++) {
                if ((t & 1) == 1) {
                    x[i] = -x[i];
                } else {
                    int tmp = x[i];
                    x[i] = z[i];
                    z[i] = tmp;
                }
            }
        }

        edges.sort(Comparator.comparingInt(e -> e[0]));
        int[] p = new int[n];
        for (int i = 0; i < n; i++) {
            p[i] = i;
        }

        List<MSTEdge> mst = new ArrayList<>();
        for (int[] e : edges) {
            int u = e[1];
            while (p[u] != u) {
                u = p[u] = p[p[u]];
            }
            int v = e[2];
            while (p[v] != v) {
                v = p[v] = p[p[v]];
            }

            if (u != v) {
                p[u] = v;
                mst.add(new MSTEdge(chunks.get(e[1]), chunks.get(e[2])));
                if (mst.size() == n - 1) {
                    break;
                }
            }
        }

        return mst;
    }

    public static Set<ChunkPos> generateStructureMap(ChunkPos clusterCornerPos, List<MSTGeneration.MSTEdge> MSTEdgeList, List<ChunkPos> clusterChunks) {
        Set<ChunkPos> structureMap = new HashSet<>();
        if (clusterChunks.isEmpty() || MSTEdgeList.isEmpty()) {
            return structureMap;
        }

        Map<ChunkPos, List<ChunkPos>> graph = new HashMap<>();
        for (MSTGeneration.MSTEdge e : MSTEdgeList) {
            graph.computeIfAbsent(e.c1, k -> new ArrayList<>()).add(e.c2);
            graph.computeIfAbsent(e.c2, k -> new ArrayList<>()).add(e.c1);
        }

        buildLoadingGridDFS(clusterCornerPos, graph, structureMap);
        return structureMap;
    }

    public static void buildLoadingGridDFS(ChunkPos startChunk, Map<ChunkPos, List<ChunkPos>> graph, Set<ChunkPos> structureMap) {
        for (ChunkPos endChunk : graph.getOrDefault(startChunk, Collections.emptyList())) {
            if (!structureMap.contains(endChunk)) {
                int currentX = startChunk.x;
                int currentZ = startChunk.z;

                int endX = endChunk.x;
                int endZ = endChunk.z;

                int stepX = Integer.compare(endX, currentX);
                int stepZ = Integer.compare(endZ, currentZ);

                while (true) {
                    structureMap.add(new ChunkPos(currentX, currentZ));

                    if (currentX != endX) {
                        currentX += stepX;
                    } else if (currentZ != endZ) {
                        currentZ += stepZ;
                    } else {
                        break;
                    }
                }
                buildLoadingGridDFS(endChunk, graph, structureMap);
            }
        }
    }

    public static void buildLoadingGrid(
        ChunkPos clusterCornerPos,
        List<MSTGeneration.MSTEdge> MSTEdgeList,
        List<ChunkPos> clusterChunks,
        World world,
        int y,
        BlockState state
    ) {
        Set<ChunkPos> visited = new HashSet<>();
        Set<BlockPos> blockVisited = new HashSet<>();
        Map<ChunkPos, List<ChunkPos>> graph = new HashMap<>();
        if (clusterChunks.isEmpty() || MSTEdgeList.isEmpty()) {
            return;
        }

        for (MSTGeneration.MSTEdge e : MSTEdgeList) {
            graph.computeIfAbsent(e.c1, k -> new ArrayList<>()).add(e.c2);
            graph.computeIfAbsent(e.c2, k -> new ArrayList<>()).add(e.c1);
        }
        MSTGeneration.buildLoadingGridDFS(clusterCornerPos, graph, visited, blockVisited, world, y, state);

        clusterChunks.forEach(chunkPos -> {
            MSTGeneration.setChunkCentre(world, y, chunkPos);
        });
    }

    public static void buildLoadingGridDFS(ChunkPos startChunk, Map<ChunkPos, List<ChunkPos>> graph, Set<ChunkPos> visited, Set<BlockPos> blockVisited,
                                           World world, int y, BlockState buildingBlock) {
        for (ChunkPos endChunk : graph.getOrDefault(startChunk, Collections.emptyList())) {
            if (visited.add(endChunk)) {
                connectBlocksAsPath(world, y, buildingBlock, startChunk, endChunk, blockVisited);
                buildLoadingGridDFS(endChunk, graph, visited, blockVisited, world, y, buildingBlock);
            }
        }
    }

    public static void connectBlocksAsPath(World world, int y, BlockState buildingBlock, ChunkPos c1, ChunkPos c2, Set<BlockPos> blockVisited) {
        int currentX = (c1.x << 4) + 8;
        int currentZ = (c1.z << 4) + 8;

        int endX = (c2.x << 4) + 8;
        int endZ = (c2.z << 4) + 8;

        int stepX = Integer.compare(endX, currentX);
        int stepZ = Integer.compare(endZ, currentZ);

        while (true) {
            BlockPos pos = new BlockPos(currentX, y, currentZ);
            if (!blockVisited.contains(pos)) {
                if (currentX >> 4 != (currentX + stepX) >> 4) {
                    world.setBlockState(pos, Blocks.CHEST.defaultState(), 2);
                } else if (currentZ >> 4 != (currentZ + stepZ) >> 4) {
                    world.setBlockState(pos, Blocks.CHEST.defaultState(), 2);
                } else {
                    world.setBlockState(pos, buildingBlock, 2);
                }
                blockVisited.add(pos);
            }

            if (currentX != endX) {
                currentX += stepX;
            } else if (currentZ != endZ) {
                currentZ += stepZ;
            } else {
                break;
            }
        }
    }

    public static void setChunkCentre(World world, int y, ChunkPos chunk) {
        int centreX = (chunk.x << 4) + 8;
        int centreZ = (chunk.z << 4) + 8;
        world.setBlockState(
            new BlockPos(centreX, y, centreZ),
            Blocks.HOPPER.defaultState().set(HopperBlock.FACING, Direction.DOWN).set(HopperBlock.ENABLED, true),
            2
        );
        world.setBlockState(new BlockPos(centreX, y, centreZ).up(), Blocks.TORCH.defaultState().set(TorchBlock.FACING, Direction.UP));
        BlockEntity tileEntity = world.getBlockEntity(new BlockPos(centreX, y, centreZ));
        if (tileEntity instanceof HopperBlockEntity) {
            HopperBlockEntity hopper = (HopperBlockEntity) tileEntity;
            for (int i = 0; i < 5; i++) {
                hopper.setStack(i, new ItemStack(Items.SHEARS, 1));
            }
        }
    }

    public static class MSTEdge {
        public final ChunkPos c1;
        public final ChunkPos c2;

        public MSTEdge(ChunkPos c1, ChunkPos c2) {
            this.c1 = c1;
            this.c2 = c2;
        }
    }
}

