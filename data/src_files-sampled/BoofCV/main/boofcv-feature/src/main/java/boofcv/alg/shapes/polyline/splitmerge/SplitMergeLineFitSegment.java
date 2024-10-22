package boofcv.alg.shapes.polyline.splitmerge;

import boofcv.struct.ConfigLength;
import georegression.metric.Distance2D_F64;
import georegression.struct.point.Point2D_I32;
import org.ddogleg.struct.DogArray_I32;

import java.util.List;

@Deprecated
public class SplitMergeLineFitSegment extends SplitMergeLineFit {

	public SplitMergeLineFitSegment( double splitFraction, ConfigLength minimumSplit, int maxIterations ) {
		super(splitFraction, minimumSplit, maxIterations);
	}

	@Override
	public boolean _process( List<Point2D_I32> list ) {
		if (list.size() <= 2) { return false;
		}

		splits.add(0);
		splitPixels(0, list.size() - 1);
		splits.add(list.size() - 1);

		for (int i = 0; i < maxIterations; i++) {
			boolean changed = mergeSegments();
			if (!changed && !splitSegments())
				break;

			if (splits.size() <= 2 || splits.size() >= abortSplits)
				break;
		}

		return true;
	}

	protected void splitPixels( int indexStart, int indexStop ) {
		if (indexStart + 1 >= indexStop)
			return;

		int indexSplit = selectSplitBetween(indexStart, indexStop);

		if (indexSplit >= 0) {
			splitPixels(indexStart, indexSplit);
			splits.add(indexSplit);
			splitPixels(indexSplit, indexStop);
		}
	}

	protected boolean splitSegments() {
		boolean change = false;

		work.reset();
		for (int i = 0; i < splits.size - 1; i++) {
			int start = splits.data[i];
			int end = splits.data[i + 1];

			int bestIndex = selectSplitBetween(start, end);
			if (bestIndex >= 0) {
				change |= true;
				work.add(start);
				work.add(bestIndex);
			} else {
				work.add(start);
			}
		}
		work.add(splits.data[splits.size - 1]);

		DogArray_I32 tmp = work;
		work = splits;
		splits = tmp;

		return change;
	}

	protected int selectSplitBetween( int indexStart, int indexEnd ) {

		Point2D_I32 a = contour.get(indexStart);
		Point2D_I32 c = contour.get(indexEnd);

		line.p.setTo(a.x, a.y);
		line.slope.setTo(c.x - a.x, c.y - a.y);

		int bestIndex = -1;
		double bestDistanceSq = splitThresholdSq(contour.get(indexStart), contour.get(indexEnd));

		int minLength = Math.max(1, minimumSideLengthPixel);int length = indexEnd - indexStart - minLength;

		for (int i = minLength; i <= length; i++) {
			int index = indexStart + i;
			Point2D_I32 b = contour.get(index);
			point2D.setTo(b.x, b.y);

			double dist = Distance2D_F64.distanceSq(line, point2D);
			if (dist >= bestDistanceSq) {
				bestDistanceSq = dist;
				bestIndex = index;
			}
		}
		return bestIndex;
	}

	protected boolean mergeSegments() {
		if (splits.size <= 2)
			return false;

		boolean change = false;
		work.reset();

		work.add(splits.data[0]);

		for (int i = 0; i < splits.size - 2; i++) {
			if (selectSplitBetween(splits.data[i], splits.data[i + 2]) < 0) {
				change = true;
			} else {
				work.add(splits.data[i + 1]);
			}
		}

		work.add(splits.data[splits.size - 1]);

		DogArray_I32 tmp = work;
		work = splits;
		splits = tmp;

		return change;
	}
}
