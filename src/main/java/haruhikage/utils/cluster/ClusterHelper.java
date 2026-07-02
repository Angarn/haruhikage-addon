package haruhikage.utils.cluster;

import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.tuple.Pair;
import org.jetbrains.annotations.NotNull;

import java.util.*;

// credit: RoyanAB, Void-Skeleton

public class ClusterHelper {

    public static ClusterHelper.ClusterData tempData;

    private static ClusterHelper.ClusterData testTargetChunk(
        ClusterHelper.ClusterData data,
        Pair<Object2IntOpenHashMap<ChunkPos>[], int[][]> preData,
        ChunkPos targetPos,
        List<ChunkPos> excludedRelativePos
    ) {
        final int[] clusteredHashes = ClusterUtil.precomputeTargetChunk(data.hashSize, targetPos, excludedRelativePos);

        int bestHashStart = 0;
        List<MSTGeneration.MSTEdge> bestMSTEdgeList = Collections.emptyList();
        List<ChunkPos> bestClusterChunks = Collections.emptyList();
        int minLoadingGridSize = Integer.MAX_VALUE;
        try {
            for (int idx = 0; idx < 21; idx++) {
                int hashStart = clusteredHashes[idx];
                int hashLength = ClusterUtil.getHashLength(idx, clusteredHashes, hashStart, data.desiredClustering);
                if (hashLength < 0) {
                    continue;
                }

                List<ChunkPos> clusterChunks = ClusterUtil.computeClusterChunks(
                    data.hashSize,
                    data.maximalClusterHeight,
                    hashStart,
                    hashLength,
                    preData.getLeft(),
                    preData.getRight()
                );
                if (clusterChunks == null) {
                    continue;
                }

                clusterChunks.add(data.clusterCornerPos);

                List<MSTGeneration.MSTEdge> mstEdgeList = MSTGeneration.generateMST(clusterChunks);
                Set<ChunkPos> structureMap = MSTGeneration.generateStructureMap(data.clusterCornerPos, mstEdgeList, clusterChunks);

                if (structureMap.size() < minLoadingGridSize) {
                    minLoadingGridSize = structureMap.size();
                    bestHashStart = hashStart;
                    bestMSTEdgeList = mstEdgeList;
                    bestClusterChunks = clusterChunks;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }

        if (minLoadingGridSize == Integer.MAX_VALUE || bestHashStart == 0 || bestClusterChunks.isEmpty() || bestMSTEdgeList.isEmpty()) {
            return null;
        }

        ClusterHelper.ClusterData computedData = data.copy();
        computedData.hashStart = bestHashStart;
        computedData.MSTEdgeList = bestMSTEdgeList;
        computedData.clusterChunks = bestClusterChunks;
        computedData.loadingGridSize = minLoadingGridSize;
        return computedData;
    }

    public static ClusterHelper.ClusterData compute(ClusterHelper.ClusterData data) {
        try {
            Pair<Object2IntOpenHashMap<ChunkPos>[], int[][]> preData = ClusterUtil.precompute(
                data.hashSize, data.maximalClusterHeight, data.clusterWidth, data.clusterCornerPos, data.widthDir, data.heightDir
            );
            ClusterHelper.ClusterData bestData = data.copy();

            List<ChunkPos> tasks = new ArrayList<>();
            for (int sx = data.startPos.x; sx < data.endPos.x; sx++) {
                for (int sz = data.startPos.z; sz < data.endPos.z; sz++) {
                    tasks.add(new ChunkPos(sx, sz));
                }
            }

            tasks.parallelStream()
                .map(chunk -> {
                    System.out.println(chunk.toString());
                    ClusterHelper.ClusterData computedData = testTargetChunk(data, preData, chunk, ClusterUtil.EXCLUDED_RELATIVE_POS);
                    if (computedData == null) {
                        return null;
                    }
                    return new AbstractMap.SimpleEntry<>(chunk, computedData);
                })
                .filter(Objects::nonNull)
                .min(Comparator.comparingInt(entry -> entry.getValue().loadingGridSize))
                .ifPresent(bestEntry -> {
                    ChunkPos bestChunk = bestEntry.getKey();
                    ClusterHelper.ClusterData computedData = bestEntry.getValue();

                    bestData.hashStart = computedData.hashStart;
                    bestData.MSTEdgeList = computedData.MSTEdgeList;
                    bestData.clusterChunks = computedData.clusterChunks;
                    bestData.loadingGridSize = computedData.loadingGridSize;
                    bestData.targetChunk = bestChunk;
                });

            tempData = bestData;
            return bestData;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    public static class ClusterData {
        public ChunkPos startPos;
        public ChunkPos endPos;
        public ChunkPos clusterCornerPos;
        public Direction widthDir;
        public Direction heightDir;
        public int clusterWidth;
        public int maximalClusterHeight;
        public int hashSize;
        public int desiredClustering;

        public int hashStart;
        public List<MSTGeneration.MSTEdge> MSTEdgeList;
        public List<ChunkPos> clusterChunks;
        public int loadingGridSize;
        public ChunkPos targetChunk;

        public ClusterData(
            ChunkPos startPos,
            ChunkPos endPos,
            ChunkPos clusterCornerPos,
            Direction widthDir,
            Direction heightDir,
            int clusterWidth,
            int maximalClusterHeight,
            int hashSize,
            int desiredClustering
        ) {
            this.startPos = startPos;
            this.endPos = endPos;
            this.clusterCornerPos = clusterCornerPos;
            this.widthDir = widthDir;
            this.heightDir = heightDir;
            this.clusterWidth = clusterWidth;
            this.maximalClusterHeight = maximalClusterHeight;
            this.hashSize = hashSize;
            this.desiredClustering = desiredClustering;
        }

        public ClusterData(NbtCompound compound) {
            if (compound.contains("startX") && compound.contains("startZ")) {
                this.startPos = new ChunkPos(
                    compound.getInt("startX"),
                    compound.getInt("startZ")
                );
            }
            if (compound.contains("endX") && compound.contains("endZ")) {
                this.endPos = new ChunkPos(
                    compound.getInt("endX"),
                    compound.getInt("endZ")
                );
            }
            if (compound.contains("clusterCornerX") && compound.contains("clusterCornerZ")) {
                this.clusterCornerPos = new ChunkPos(
                    compound.getInt("clusterCornerX"),
                    compound.getInt("clusterCornerZ")
                );
            }

            Direction facing;
            if (compound.contains("widthDir") && ((facing = Direction.BY_NAME.get(compound.getString("widthDir"))) != null)
                && facing.getOffsetY() == 0) {
                this.widthDir = facing;
            } else {
                this.widthDir = Direction.EAST;
            }
            if (compound.contains("heightDir") && ((facing = Direction.BY_NAME.get(compound.getString("heightDir"))) != null)
                && facing.getOffsetY() == 0 && facing.getAxis() != widthDir.getAxis()) {
                this.heightDir = facing;
            } else {
                this.heightDir = Direction.SOUTH;
            }

            if (compound.contains("clusterWidth")) {
                this.clusterWidth = Math.max(0, compound.getInt("clusterWidth"));
            }
            if (compound.contains("maxClusterHeight")) {
                this.maximalClusterHeight = Math.max(0, compound.getInt("maxClusterHeight"));
            }

            if (compound.contains("hashSize")) {
                this.hashSize = ClusterUtil.nextPowerOfTwo(Math.max(1, compound.getInt("hashSize") - 1));
            }
            if (compound.contains("desiredClustering")) {
                this.desiredClustering = Math.max(0, compound.getInt("desiredClustering"));
            }
        }

        public NbtCompound writeToNBT(NbtCompound compound) {
            compound.putInt("startX", startPos.x);
            compound.putInt("startZ", startPos.z);

            compound.putInt("endX", endPos.x);
            compound.putInt("endZ", endPos.z);

            compound.putInt("clusterCornerX", clusterCornerPos.x);
            compound.putInt("clusterCornerZ", clusterCornerPos.z);

            compound.putString("widthDir", widthDir.getName());
            compound.putString("heightDir", heightDir.getName());

            compound.putInt("clusterWidth", clusterWidth);
            compound.putInt("maxClusterHeight", maximalClusterHeight);

            compound.putInt("hashSize", hashSize);
            compound.putInt("desiredClustering", desiredClustering);
            return compound;
        }

        public ClusterData copy() {
            return new ClusterData(
                this.startPos, this.endPos, this.clusterCornerPos,
                this.widthDir, this.heightDir, this.clusterWidth,
                this.maximalClusterHeight, this.hashSize, this.desiredClustering
            );
        }
    }
}

