package org.elasticsearch.common.util;

import org.apache.lucene.util.ArrayUtil;
import org.apache.lucene.util.RamUsageEstimator;

import java.util.Arrays;

import static org.elasticsearch.common.util.PageCacheRecycler.INT_PAGE_SIZE;

final class BigFloatArray extends AbstractBigArray implements FloatArray {

    private static final BigFloatArray ESTIMATOR = new BigFloatArray(0, BigArrays.NON_RECYCLING_INSTANCE, false);

    private int[][] pages;

    BigFloatArray(long size, BigArrays bigArrays, boolean clearOnResize) {
        super(INT_PAGE_SIZE, bigArrays, clearOnResize);
        this.size = size;
        pages = new int[numPages(size)][];
        for (int i = 0; i < pages.length; ++i) {
            pages[i] = newIntPage(i);
        }
    }

    @Override
    public float set(long index, float value) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        final int[] page = pages[pageIndex];
        final float ret = Float.intBitsToFloat(page[indexInPage]);
        page[indexInPage] = Float.floatToRawIntBits(value);
        return ret;
    }

    @Override
    public float increment(long index, float inc) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        final int[] page = pages[pageIndex];
        return page[indexInPage] = Float.floatToRawIntBits(Float.intBitsToFloat(page[indexInPage]) + inc);
    }

    @Override
    public float get(long index) {
        final int pageIndex = pageIndex(index);
        final int indexInPage = indexInPage(index);
        return Float.intBitsToFloat(pages[pageIndex][indexInPage]);
    }

    @Override
    protected int numBytesPerElement() {
        return Float.BYTES;
    }

    @Override
    public void resize(long newSize) {
        final int numPages = numPages(newSize);
        if (numPages > pages.length) {
            pages = Arrays.copyOf(pages, ArrayUtil.oversize(numPages, RamUsageEstimator.NUM_BYTES_OBJECT_REF));
        }
        for (int i = numPages - 1; i >= 0 && pages[i] == null; --i) {
            pages[i] = newIntPage(i);
        }
        for (int i = numPages; i < pages.length && pages[i] != null; ++i) {
            pages[i] = null;
            releasePage(i);
        }
        this.size = newSize;
    }

    @Override
    public void fill(long fromIndex, long toIndex, float value) {
        if (fromIndex > toIndex) {
            throw new IllegalArgumentException();
        }
        final int intBits = Float.floatToRawIntBits(value);
        final int fromPage = pageIndex(fromIndex);
        final int toPage = pageIndex(toIndex - 1);
        if (fromPage == toPage) {
            Arrays.fill(pages[fromPage], indexInPage(fromIndex), indexInPage(toIndex - 1) + 1, intBits);
        } else {
            Arrays.fill(pages[fromPage], indexInPage(fromIndex), pages[fromPage].length, intBits);
            for (int i = fromPage + 1; i < toPage; ++i) {
                Arrays.fill(pages[i], intBits);
            }
            Arrays.fill(pages[toPage], 0, indexInPage(toIndex - 1) + 1, intBits);
        }
    }

    public static long estimateRamBytes(final long size) {
        return ESTIMATOR.ramBytesEstimated(size);
    }

}