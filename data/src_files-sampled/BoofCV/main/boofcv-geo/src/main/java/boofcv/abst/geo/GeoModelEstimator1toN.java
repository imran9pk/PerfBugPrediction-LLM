package boofcv.abst.geo;

import boofcv.struct.geo.GeoModelEstimator1;
import boofcv.struct.geo.GeoModelEstimatorN;
import org.ddogleg.struct.DogArray;

import java.util.List;

public class GeoModelEstimator1toN<Model,Point> implements GeoModelEstimatorN<Model,Point> {

	private GeoModelEstimator1<Model,Point> alg;

	public GeoModelEstimator1toN(GeoModelEstimator1<Model, Point> alg) {
		this.alg = alg;
	}

	@Override
	public boolean process(List<Point> points, DogArray<Model> estimatedModels) {
		estimatedModels.reset();

		Model m = estimatedModels.grow();

		if( alg.process(points,m) ) {
			return true;
		} else {
			estimatedModels.reset();
		}

		return false;
	}

	@Override
	public int getMinimumPoints() {
		return alg.getMinimumPoints();
	}
}
