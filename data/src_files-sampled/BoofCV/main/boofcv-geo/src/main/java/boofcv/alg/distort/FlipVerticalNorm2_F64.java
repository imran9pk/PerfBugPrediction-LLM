package boofcv.alg.distort;

import boofcv.struct.distort.Point2Transform2_F64;
import georegression.struct.point.Point2D_F64;

public class FlipVerticalNorm2_F64 implements Point2Transform2_F64 {

	Point2Transform2_F64 pixelToNormalized;
	int height;

	public FlipVerticalNorm2_F64( Point2Transform2_F64 pixelToNormalized, int imageHeight ) {
		this.pixelToNormalized = pixelToNormalized;
		this.height = imageHeight - 1;
	}

	@Override
	public void compute( double x, double y, Point2D_F64 out ) {
		pixelToNormalized.compute(x, height - y, out);
	}

	@Override
	public FlipVerticalNorm2_F64 copyConcurrent() {
		return new FlipVerticalNorm2_F64(pixelToNormalized.copyConcurrent(), height);
	}
}
