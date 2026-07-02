package haruhikage.utils.cluster;

import it.unimi.dsi.fastutil.HashCommon;
import it.unimi.dsi.fastutil.objects.Object2IntOpenHashMap;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.Direction;
import org.apache.commons.lang3.tuple.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

// credit: RoyanAB, Void-Skeleton

public class ClusterUtil {
    public static final List<ChunkPos> EXCLUDED_RELATIVE_POS = Arrays.asList(new ChunkPos(0, 0), new ChunkPos(0, 1), new ChunkPos(1, 0), new ChunkPos(1, 1));

    public static List<ChunkPos> computeClusterChunks(
        int hashSize,
        int maximalClusterHeight,
        int hashStart,
        int length,
        Object2IntOpenHashMap<ChunkPos>[] hashChunkToHeight,
        int[][] cumulativeHashHeightToChunkCount
    ) {
        int mask = hashSize - 1;
        List<ChunkPos> clusterChunks = new ArrayList<>();

        int requiredHeight = 0, currentLength = 0, currentHash = hashStart;

        while (currentLength < length) {
            int remainingLength = ((currentHash - hashStart) & mask) - currentLength;
            if (cumulativeHashHeightToChunkCount[currentHash][requiredHeight] < remainingLength || currentHash + 2 > hashSize) {
                requiredHeight++;
                if (requiredHeight >= maximalClusterHeight) {
                    return null;
                }
                currentLength = 0;
                currentHash = hashStart;
                continue;
            }
            currentLength += cumulativeHashHeightToChunkCount[currentHash][requiredHeight];

            currentHash = (currentHash + 1) & mask;
        }

        final int targetHeight = requiredHeight;

        for (int hash = hashStart; hash != currentHash; hash = (hash + 1) & mask) {
            Object2IntOpenHashMap<ChunkPos> chunkMap = hashChunkToHeight[hash];

            if (chunkMap != null) {
                chunkMap.forEach((chunkPos, height) -> {
                    if (height <= targetHeight) {
                        clusterChunks.add(chunkPos);
                    }
                });
            }
        }

        return clusterChunks;
    }

    public static int[] precomputeTargetChunk(int hashSize, ChunkPos targetPos, List<ChunkPos> excludedRelativePos) {
        int ptr = 0;
        int[] clusteredHashes = new int[21];
        for (int x = -2; x <= 2; x++) {
            for (int z = -2; z <= 2; z++) {
                if (!excludedRelativePos.contains(new ChunkPos(x, z))) {
                    clusteredHashes[ptr++] = hashChunkPos(new ChunkPos(targetPos.x + x, targetPos.z + z), hashSize);
                }
            }
        }
        Arrays.sort(clusteredHashes);
        return clusteredHashes;
    }

    public static Pair<Object2IntOpenHashMap<ChunkPos>[], int[][]> precompute(
        int hashSize,
        int maximalClusterHeight,
        int clusterWidth,
        ChunkPos clusterCorner,
        Direction clusterWidthDir,
        Direction clusterHeightDir
    ) {
        int[][] hashHeightToChunkCount = new int[hashSize][maximalClusterHeight];
        int[][] cumulativeHashHeightToChunkCount = new int[hashSize][maximalClusterHeight];

        @SuppressWarnings("unchecked")
        Object2IntOpenHashMap<ChunkPos>[] hashChunkToHeight = new Object2IntOpenHashMap[hashSize];

        for (int dw = 0; dw < clusterWidth; dw++) {
            for (int dh = 0; dh < maximalClusterHeight; dh++) {
                ChunkPos pos = new ChunkPos(
                    clusterCorner.x + dw * clusterWidthDir.getOffsetX() + dh * clusterHeightDir.getOffsetX(),
                    clusterCorner.z + dw * clusterWidthDir.getOffsetZ() + dh * clusterHeightDir.getOffsetZ());
                int hash = hashChunkPos(pos, hashSize);
                hashHeightToChunkCount[hash][dh]++;
                if (hashChunkToHeight[hash] == null) {
                    hashChunkToHeight[hash] = new Object2IntOpenHashMap<>();
                }
                hashChunkToHeight[hash].put(pos, dh);
            }
        }

        for (int hash = 0; hash < hashSize; hash++) {
            int cumulative = 0;
            for (int dh = 0; dh < maximalClusterHeight; dh++) {
                cumulativeHashHeightToChunkCount[hash][dh] = (cumulative += hashHeightToChunkCount[hash][dh]);
            }
        }

        return Pair.of(hashChunkToHeight, cumulativeHashHeightToChunkCount);
    }

    public static int getHashLength(int idx, int[] clusteredHashes, int hashStart, int desiredClustering) {
        int cumulativeClustering = 0;

        for (int step = 0; step < clusteredHashes.length; step++) {
            int currentLength = clusteredHashes[(idx + step) % clusteredHashes.length];

            cumulativeClustering += currentLength - hashStart;

            if (cumulativeClustering >= desiredClustering) {
//                return currentLength - (cumulativeClustering - desiredClustering) / step;
                return (2 * desiredClustering) / (step + 1);
            }
        }

        return -1;
    }

    public static int hashChunkPos(ChunkPos pos, int hashSize) {
        return (int) (HashCommon.mix(ChunkPos.toLong(pos.x, pos.z)) & (hashSize - 1));
    }

    public static int nextPowerOfTwo(int v) {
        v |= v >> 1;
        v |= v >> 2;
        v |= v >> 4;
        v |= v >> 8;
        v |= v >> 16;
        return v + 1;
    }
}

