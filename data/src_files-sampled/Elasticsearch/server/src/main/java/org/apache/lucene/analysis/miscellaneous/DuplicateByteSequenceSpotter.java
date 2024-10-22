package org.apache.lucene.analysis.miscellaneous;

import org.apache.lucene.util.RamUsageEstimator;

public class DuplicateByteSequenceSpotter {
    public static final int TREE_DEPTH = 6;
    public static final int MAX_HIT_COUNT = 255;
    private final TreeNode root;
    private boolean sequenceBufferFilled = false;
    private final byte[] sequenceBuffer = new byte[TREE_DEPTH];
    private int nextFreePos = 0;

    private final int[] nodesAllocatedByDepth;
    private int nodesResizedByDepth;
    private long bytesAllocated;
    static final long TREE_NODE_OBJECT_SIZE = RamUsageEstimator.NUM_BYTES_OBJECT_HEADER + RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    static final long ROOT_TREE_NODE_OBJECT_SIZE = TREE_NODE_OBJECT_SIZE + RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    static final long LIGHTWEIGHT_TREE_NODE_OBJECT_SIZE = TREE_NODE_OBJECT_SIZE + RamUsageEstimator.NUM_BYTES_OBJECT_REF;
    static final long LEAF_NODE_OBJECT_SIZE = TREE_NODE_OBJECT_SIZE + Short.BYTES + Integer.BYTES;

    public DuplicateByteSequenceSpotter() {
        this.nodesAllocatedByDepth = new int[4];
        this.bytesAllocated = 0;
        root = new RootTreeNode((byte) 1, null, 0);
    }

    public void startNewSequence() {
        sequenceBufferFilled = false;
        nextFreePos = 0;
    }

    public short addByte(byte b) {
        sequenceBuffer[nextFreePos] = b;
        nextFreePos++;
        if (nextFreePos >= sequenceBuffer.length) {
            nextFreePos = 0;
            sequenceBufferFilled = true;
        }
        if (sequenceBufferFilled == false) {
            return 0;
        }
        TreeNode node = root;
        int p = nextFreePos;

        node = node.add(sequenceBuffer[p], 0);
        p = nextBufferPos(p);
        node = node.add(sequenceBuffer[p], 1);
        p = nextBufferPos(p);
        node = node.add(sequenceBuffer[p], 2);

        p = nextBufferPos(p);
        int sequence = 0xFF & sequenceBuffer[p];
        p = nextBufferPos(p);
        sequence = sequence << 8 | (0xFF & sequenceBuffer[p]);
        p = nextBufferPos(p);
        sequence = sequence << 8 | (0xFF & sequenceBuffer[p]);
        return (short) (node.add(sequence << 8) - 1);
    }

    private int nextBufferPos(int p) {
        p++;
        if (p >= sequenceBuffer.length) {
            p = 0;
        }
        return p;
    }

    abstract class TreeNode {

        TreeNode(byte key, TreeNode parentNode, int depth) {
            nodesAllocatedByDepth[depth]++;
        }

        public abstract TreeNode add(byte b, int depth);

        public abstract short add(int byteSequence);
    }

    class RootTreeNode extends TreeNode {

        TreeNode[] children;

        RootTreeNode(byte key, TreeNode parentNode, int depth) {
            super(key, parentNode, depth);
            bytesAllocated += ROOT_TREE_NODE_OBJECT_SIZE;
        }

        public TreeNode add(byte b, int depth) {
            if (children == null) {
                children = new TreeNode[256];
                bytesAllocated += (RamUsageEstimator.NUM_BYTES_OBJECT_REF * 256);
            }
            int bIndex = 0xFF & b;
            TreeNode node = children[bIndex];
            if (node == null) {
                if (depth <= 1) {
                    node = new RootTreeNode(b, this, depth);
                } else {
                    node = new LightweightTreeNode(b, this, depth);
                }
                children[bIndex] = node;
            }
            return node;
        }

        @Override
        public short add(int byteSequence) {
            throw new UnsupportedOperationException("Root nodes do not support byte sequences encoded as integers");
        }

    }

    final class LightweightTreeNode extends TreeNode {

        int[] children = null;

        LightweightTreeNode(byte key, TreeNode parentNode, int depth) {
            super(key, parentNode, depth);
            bytesAllocated += LIGHTWEIGHT_TREE_NODE_OBJECT_SIZE;

        }

        @Override
        public short add(int byteSequence) {
            if (children == null) {
                children = new int[1];
                bytesAllocated += RamUsageEstimator.NUM_BYTES_ARRAY_HEADER + Integer.BYTES;                
                children[0] = byteSequence + 1;
                return 1;
            }
            for (int i = 0; i < children.length; i++) {
                int child = children[i];
                if (byteSequence == (child & 0xFFFFFF00)) {
                    int hitCount = child & 0xFF;
                    if (hitCount < MAX_HIT_COUNT) {
                        children[i]++;
                    }
                    return (short) (hitCount + 1);
                }
            }
            int[] newChildren = new int[children.length + 1];
            bytesAllocated += Integer.BYTES;

            System.arraycopy(children, 0, newChildren, 0, children.length);
            children = newChildren;
            children[newChildren.length - 1] = byteSequence + 1;
            nodesResizedByDepth++;
            return 1;
        }

        @Override
        public TreeNode add(byte b, int depth) {
            throw new UnsupportedOperationException("Leaf nodes do not take byte sequences");
        }

    }

    public final long getEstimatedSizeInBytes() {
        return bytesAllocated;
    }

    public int[] getNodesAllocatedByDepth() {
        return nodesAllocatedByDepth.clone();
    }

    public int getNodesResizedByDepth() {
        return nodesResizedByDepth;
    }

}
