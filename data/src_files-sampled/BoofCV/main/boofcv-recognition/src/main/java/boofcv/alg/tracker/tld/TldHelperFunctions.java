package boofcv.alg.tracker.tld;

import boofcv.struct.ImageRectangle;
import georegression.struct.shapes.Rectangle2D_F64;
import georegression.struct.shapes.Rectangle2D_I32;

public class TldHelperFunctions {

	private ImageRectangle work = new ImageRectangle();

	public double computeOverlap( ImageRectangle a, ImageRectangle b ) {
		if (!a.intersection(b, work))
			return 0;

		int areaI = work.area();

		int bottom = a.area() + b.area() - areaI;

		return areaI/(double)bottom;
	}

	public static void convertRegion( Rectangle2D_F64 input, Rectangle2D_I32 output ) {
		output.x0 = (int)(input.p0.x + 0.5);
		output.x1 = (int)(input.p1.x + 0.5);
		output.y0 = (int)(input.p0.y + 0.5);
		output.y1 = (int)(input.p1.y + 0.5);
	}

	public static void convertRegion( Rectangle2D_I32 input, Rectangle2D_F64 output ) {
		output.p0.setTo(input.x0, input.y0);
		output.p1.setTo(input.x1, input.y1);
	}
}
