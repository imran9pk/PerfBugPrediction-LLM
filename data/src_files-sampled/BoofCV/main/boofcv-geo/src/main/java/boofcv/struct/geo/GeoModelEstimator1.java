package boofcv.struct.geo;

import java.util.List;

public interface GeoModelEstimator1<Model,Sample> {
	boolean process( List<Sample> points , Model estimatedModel );

	int getMinimumPoints();
}
