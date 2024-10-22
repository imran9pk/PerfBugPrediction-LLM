package org.elasticsearch.index.shard;

import org.apache.logging.log4j.Logger;
import org.elasticsearch.core.internal.io.IOUtils;
import org.elasticsearch.cluster.metadata.IndexMetaData;
import org.elasticsearch.common.xcontent.NamedXContentRegistry;
import org.elasticsearch.env.NodeEnvironment;
import org.elasticsearch.env.ShardLock;
import org.elasticsearch.index.IndexSettings;

import java.io.IOException;
import java.math.BigInteger;
import java.nio.file.FileStore;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

public final class ShardPath {
    public static final String INDEX_FOLDER_NAME = "index";
    public static final String TRANSLOG_FOLDER_NAME = "translog";

    private final Path path;
    private final ShardId shardId;
    private final Path shardStatePath;
    private final boolean isCustomDataPath;

    public ShardPath(boolean isCustomDataPath, Path dataPath, Path shardStatePath, ShardId shardId) {
        assert dataPath.getFileName().toString().equals(Integer.toString(shardId.id())) :
            "dataPath must end with the shard ID but didn't: " + dataPath.toString();
        assert shardStatePath.getFileName().toString().equals(Integer.toString(shardId.id())) :
            "shardStatePath must end with the shard ID but didn't: " + dataPath.toString();
        assert dataPath.getParent().getFileName().toString().equals(shardId.getIndex().getUUID()) :
            "dataPath must end with index path id but didn't: " + dataPath.toString();
        assert shardStatePath.getParent().getFileName().toString().equals(shardId.getIndex().getUUID()) :
            "shardStatePath must end with index path id but didn't: " + dataPath.toString();
        if (isCustomDataPath && dataPath.equals(shardStatePath)) {
            throw new IllegalArgumentException("shard state path must be different to the data path when using custom data paths");
        }
        this.isCustomDataPath = isCustomDataPath;
        this.path = dataPath;
        this.shardId = shardId;
        this.shardStatePath = shardStatePath;
    }

    public Path resolveTranslog() {
        return path.resolve(TRANSLOG_FOLDER_NAME);
    }

    public Path resolveIndex() {
        return path.resolve(INDEX_FOLDER_NAME);
    }

    public Path getDataPath() {
        return path;
    }

    public boolean exists() {
        return Files.exists(path);
    }

    public ShardId getShardId() {
        return shardId;
    }

    public Path getShardStatePath() {
        return shardStatePath;
    }

    public Path getRootDataPath() {
        Path noIndexShardId = getDataPath().getParent().getParent();
        return isCustomDataPath ? noIndexShardId : noIndexShardId.getParent(); }

    public Path getRootStatePath() {
        return getShardStatePath().getParent().getParent().getParent(); }

    public boolean isCustomDataPath() {
        return isCustomDataPath;
    }

    public static ShardPath loadShardPath(Logger logger, NodeEnvironment env,
                                                ShardId shardId, IndexSettings indexSettings) throws IOException {
        final Path[] paths = env.availableShardPaths(shardId);
        final int nodeLockId = env.getNodeLockId();
        final Path sharedDataPath = env.sharedDataPath();
        return loadShardPath(logger, shardId, indexSettings, paths, nodeLockId, sharedDataPath);
    }

    public static ShardPath loadShardPath(Logger logger, ShardId shardId, IndexSettings indexSettings, Path[] availableShardPaths,
                                           int nodeLockId, Path sharedDataPath) throws IOException {
        final String indexUUID = indexSettings.getUUID();
        Path loadedPath = null;
        for (Path path : availableShardPaths) {
            ShardStateMetaData load = ShardStateMetaData.FORMAT.loadLatestState(logger, NamedXContentRegistry.EMPTY, path);
            if (load != null) {
                if (load.indexUUID.equals(indexUUID) == false && IndexMetaData.INDEX_UUID_NA_VALUE.equals(load.indexUUID) == false) {
                    logger.warn("{} found shard on path: [{}] with a different index UUID - this "
                        + "shard seems to be leftover from a different index with the same name. "
                        + "Remove the leftover shard in order to reuse the path with the current index", shardId, path);
                    throw new IllegalStateException(shardId + " index UUID in shard state was: " + load.indexUUID
                        + " expected: " + indexUUID + " on shard path: " + path);
                }
                if (loadedPath == null) {
                    loadedPath = path;
                } else{
                    throw new IllegalStateException(shardId + " more than one shard state found");
                }
            }

        }
        if (loadedPath == null) {
            return null;
        } else {
            final Path dataPath;
            final Path statePath = loadedPath;
            if (indexSettings.hasCustomDataPath()) {
                dataPath = NodeEnvironment.resolveCustomLocation(indexSettings, shardId, sharedDataPath, nodeLockId);
            } else {
                dataPath = statePath;
            }
            logger.debug("{} loaded data path [{}], state path [{}]", shardId, dataPath, statePath);
            return new ShardPath(indexSettings.hasCustomDataPath(), dataPath, statePath, shardId);
        }
    }

    public static void deleteLeftoverShardDirectory(Logger logger, NodeEnvironment env,
                                                            ShardLock lock, IndexSettings indexSettings) throws IOException {
        final String indexUUID = indexSettings.getUUID();
        final Path[] paths = env.availableShardPaths(lock.getShardId());
        for (Path path : paths) {
            ShardStateMetaData load = ShardStateMetaData.FORMAT.loadLatestState(logger, NamedXContentRegistry.EMPTY, path);
            if (load != null) {
                if (load.indexUUID.equals(indexUUID) == false && IndexMetaData.INDEX_UUID_NA_VALUE.equals(load.indexUUID) == false) {
                    logger.warn("{} deleting leftover shard on path: [{}] with a different index UUID", lock.getShardId(), path);
                    assert Files.isDirectory(path) : path + " is not a directory";
                    NodeEnvironment.acquireFSLockForPaths(indexSettings, paths);
                    IOUtils.rm(path);
                }
            }
        }
    }

    public static ShardPath selectNewPathForShard(NodeEnvironment env, ShardId shardId, IndexSettings indexSettings,
                                                  long avgShardSizeInBytes, Map<Path,Integer> dataPathToShardCount) throws IOException {

        final Path dataPath;
        final Path statePath;

        if (indexSettings.hasCustomDataPath()) {
            dataPath = env.resolveCustomLocation(indexSettings, shardId);
            statePath = env.nodePaths()[0].resolve(shardId);
        } else {
            BigInteger totFreeSpace = BigInteger.ZERO;
            for (NodeEnvironment.NodePath nodePath : env.nodePaths()) {
                totFreeSpace = totFreeSpace.add(BigInteger.valueOf(nodePath.fileStore.getUsableSpace()));
            }

            BigInteger estShardSizeInBytes = BigInteger.valueOf(avgShardSizeInBytes).max(totFreeSpace.divide(BigInteger.valueOf(20)));

            final NodeEnvironment.NodePath[] paths = env.nodePaths();

            NodeEnvironment.NodePath bestPath = getPathWithMostFreeSpace(env);

            if (paths.length != 1) {
                int shardCount = indexSettings.getNumberOfShards();
                int maxShardsPerPath = Math.floorDiv(shardCount, paths.length) + ((shardCount % paths.length) == 0 ? 0 : 1);

                Map<NodeEnvironment.NodePath, Long> pathToShardCount = env.shardCountPerPath(shardId.getIndex());

                final Map<NodeEnvironment.NodePath, BigInteger> pathsToSpace = new HashMap<>(paths.length);
                for (NodeEnvironment.NodePath nodePath : paths) {
                    FileStore fileStore = nodePath.fileStore;
                    BigInteger usableBytes = BigInteger.valueOf(fileStore.getUsableSpace());
                    pathsToSpace.put(nodePath, usableBytes);
                }

                bestPath = Arrays.stream(paths)
                        .filter((path) -> pathsToSpace.get(path).subtract(estShardSizeInBytes).compareTo(BigInteger.ZERO) > 0)
                        .sorted((p1, p2) -> {
                                int cmp = Long.compare(pathToShardCount.getOrDefault(p1, 0L),
                                    pathToShardCount.getOrDefault(p2, 0L));
                                if (cmp == 0) {
                                    cmp = Integer.compare(dataPathToShardCount.getOrDefault(p1.path, 0),
                                            dataPathToShardCount.getOrDefault(p2.path, 0));
                                    if (cmp == 0) {
                                        cmp = pathsToSpace.get(p2).compareTo(pathsToSpace.get(p1));
                                    }
                                }
                                return cmp;
                            })
                        )
                        );
            }

            statePath = bestPath.resolve(shardId);
            dataPath = statePath;
        }
        return new ShardPath(indexSettings.hasCustomDataPath(), dataPath, statePath, shardId);
    }

    static NodeEnvironment.NodePath getPathWithMostFreeSpace(NodeEnvironment env) throws IOException {
        final NodeEnvironment.NodePath[] paths = env.nodePaths();
        NodeEnvironment.NodePath bestPath = null;
        long maxUsableBytes = Long.MIN_VALUE;
        for (NodeEnvironment.NodePath nodePath : paths) {
            FileStore fileStore = nodePath.fileStore;
            long usableBytes = fileStore.getUsableSpace();
            assert usableBytes >= 0 : "usable bytes must be >= 0, got: " + usableBytes;

            if (bestPath == null || usableBytes > maxUsableBytes) {
                maxUsableBytes = usableBytes;
                bestPath = nodePath;
            }
        }
        return bestPath;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ShardPath shardPath = (ShardPath) o;
        if (shardId != null ? !shardId.equals(shardPath.shardId) : shardPath.shardId != null) {
            return false;
        }
        if (path != null ? !path.equals(shardPath.path) : shardPath.path != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        int result = path != null ? path.hashCode() : 0;
        result = 31 * result + (shardId != null ? shardId.hashCode() : 0);
        return result;
    }

    @Override
    public String toString() {
        return "ShardPath{" +
                "path=" + path +
                ", shard=" + shardId +
                '}';
    }
}
