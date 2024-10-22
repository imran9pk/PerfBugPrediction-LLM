package boofcv.abst.geo.fitting;

import boofcv.abst.geo.Estimate1ofPnP;
import boofcv.struct.geo.Point2D3D;
import georegression.struct.se.Se3_F64;
import org.ddogleg.fitting.modelset.ModelGenerator;

import java.util.List;

public class GenerateMotionPnP implements ModelGenerator<Se3_F64,Point2D3D> {

	Estimate1ofPnP alg;

	public GenerateMotionPnP(Estimate1ofPnP alg) {
		this.alg = alg;
	}

	@Override
	public boolean generate(List<Point2D3D> dataSet, Se3_F64 model ) {
		return alg.process(dataSet,model);
	}

	@Override
	public int getMinimumPoints() {
		return alg.getMinimumPoints();
	}
}
