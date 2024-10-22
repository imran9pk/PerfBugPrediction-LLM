package boofcv.alg.fiducial.square;

import boofcv.abst.geo.Estimate1ofPnP;
import boofcv.abst.geo.EstimateNofPnP;
import boofcv.abst.geo.RefinePnP;
import boofcv.alg.distort.LensDistortionNarrowFOV;
import boofcv.factory.geo.EnumPNP;
import boofcv.factory.geo.FactoryMultiView;
import boofcv.struct.distort.Point2Transform2_F64;
import boofcv.struct.geo.Point2D3D;
import georegression.geometry.GeometryMath_F64;
import georegression.geometry.UtilPolygons2D_F64;
import georegression.struct.line.LineParametric3D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.struct.shapes.Quadrilateral_F64;
import georegression.transform.se.SePointOps_F64;
import org.ddogleg.struct.DogArray;

import java.util.ArrayList;
import java.util.List;

public class QuadPoseEstimator {

	public static final double SMALL_PIXELS = 60.0;

	public static final double FUDGE_FACTOR = 0.5;

	private EstimateNofPnP p3p;
	private RefinePnP refine;

	private Estimate1ofPnP epnp = FactoryMultiView.pnp_1(EnumPNP.EPNP, 50, 0);

	protected Point2Transform2_F64 pixelToNorm;
	protected Point2Transform2_F64 normToPixel;

	protected List<Point2D3D> points = new ArrayList<>();

	protected List<Point2D_F64> listObs = new ArrayList<>();

	private List<Point2D3D> inputP3P = new ArrayList<>();
	private DogArray<Se3_F64> solutions = new DogArray(Se3_F64::new);
	private Se3_F64 outputFiducialToCamera = new Se3_F64();
	private Se3_F64 foundEPNP = new Se3_F64();

	private double outputError;

	private Point3D_F64 cameraP3 = new Point3D_F64();
	private Point2D_F64 predicted = new Point2D_F64();

	protected double bestError;
	protected Se3_F64 bestPose = new Se3_F64();

	Quadrilateral_F64 pixelCorners = new Quadrilateral_F64();
	Quadrilateral_F64 normCorners = new Quadrilateral_F64();

	Point2D_F64 center = new Point2D_F64();

	LineParametric3D_F64 ray = new LineParametric3D_F64();

	public QuadPoseEstimator( double refineTol, int refineIterations ) {
		this(FactoryMultiView.pnp_N(EnumPNP.P3P_GRUNERT, -1),
				FactoryMultiView.pnpRefine(refineTol, refineIterations));
	}

	public QuadPoseEstimator( EstimateNofPnP p3p, RefinePnP refine ) {
		this.p3p = p3p;
		this.refine = refine;

		for (int i = 0; i < 4; i++) {
			points.add(new Point2D3D());
		}
	}

	public void setLensDistoriton( LensDistortionNarrowFOV distortion ) {
		pixelToNorm = distortion.undistort_F64(true, false);
		normToPixel = distortion.distort_F64(false, true);
	}

	public void setFiducial( double x0, double y0, double x1, double y1,
							 double x2, double y2, double x3, double y3 ) {
		points.get(0).location.setTo(x0, y0, 0);
		points.get(1).location.setTo(x1, y1, 0);
		points.get(2).location.setTo(x2, y2, 0);
		points.get(3).location.setTo(x3, y3, 0);
	}

	public void pixelToMarker( double pixelX, double pixelY, Point2D_F64 marker ) {

		pixelToNorm.compute(pixelX, pixelY, marker);
		cameraP3.setTo(marker.x, marker.y, 1);

		GeometryMath_F64.multTran(outputFiducialToCamera.R, cameraP3, ray.slope);
		GeometryMath_F64.multTran(outputFiducialToCamera.R, outputFiducialToCamera.T, ray.p);
		ray.p.scale(-1);

		double t = -ray.p.z/ray.slope.z;

		marker.x = ray.p.x + ray.slope.x*t;
		marker.y = ray.p.y + ray.slope.y*t;
	}

	public boolean process( Quadrilateral_F64 corners, boolean unitsPixels ) {

		if (unitsPixels) {
			pixelCorners.setTo(corners);
			pixelToNorm.compute(corners.a.x, corners.a.y, normCorners.a);
			pixelToNorm.compute(corners.b.x, corners.b.y, normCorners.b);
			pixelToNorm.compute(corners.c.x, corners.c.y, normCorners.c);
			pixelToNorm.compute(corners.d.x, corners.d.y, normCorners.d);
		} else {
			normCorners.setTo(corners);
			normToPixel.compute(corners.a.x, corners.a.y, pixelCorners.a);
			normToPixel.compute(corners.b.x, corners.b.y, pixelCorners.b);
			normToPixel.compute(corners.c.x, corners.c.y, pixelCorners.c);
			normToPixel.compute(corners.d.x, corners.d.y, pixelCorners.d);
		}

		if (estimate(pixelCorners, normCorners, outputFiducialToCamera)) {
			outputError = computeErrors(outputFiducialToCamera);
			return true;
		} else {
			return false;
		}
	}

	protected boolean estimate( Quadrilateral_F64 cornersPixels,
								Quadrilateral_F64 cornersNorm,
								Se3_F64 foundFiducialToCamera ) {
		listObs.clear();
		listObs.add(cornersPixels.a);
		listObs.add(cornersPixels.b);
		listObs.add(cornersPixels.c);
		listObs.add(cornersPixels.d);

		points.get(0).observation.setTo(cornersNorm.a);
		points.get(1).observation.setTo(cornersNorm.b);
		points.get(2).observation.setTo(cornersNorm.c);
		points.get(3).observation.setTo(cornersNorm.d);

		bestError = Double.MAX_VALUE;
		estimateP3P(0);
		estimateP3P(1);
		estimateP3P(2);
		estimateP3P(3);

		if (bestError == Double.MAX_VALUE)
			return false;

		inputP3P.clear();
		for (int i = 0; i < 4; i++) {
			inputP3P.add(points.get(i));
		}

		if (bestError > 2) {
			if (epnp.process(inputP3P, foundEPNP)) {
				if (foundEPNP.T.z > 0) {
					double error = computeErrors(foundEPNP);
if (error < bestError) {
						bestPose.setTo(foundEPNP);
					}
				}
			}
		}

		if (!refine.fitModel(inputP3P, bestPose, foundFiducialToCamera)) {
			foundFiducialToCamera.setTo(bestPose);
			return true;
		}

		return true;
	}

	protected void estimateP3P( int excluded ) {

		inputP3P.clear();
		for (int i = 0; i < 4; i++) {
			if (i != excluded) {
				inputP3P.add(points.get(i));
			}
		}

		solutions.reset();
		if (!p3p.process(inputP3P, solutions)) {
return;
		}


		for (int i = 0; i < solutions.size; i++) {
			double error = computeErrors(solutions.get(i));

			if (error < bestError) {
				bestError = error;
				bestPose.setTo(solutions.get(i));
			}
		}
	}

	protected void enlarge( Quadrilateral_F64 corners, double scale ) {

		UtilPolygons2D_F64.center(corners, center);

		extend(center, corners.a, scale);
		extend(center, corners.b, scale);
		extend(center, corners.c, scale);
		extend(center, corners.d, scale);
	}

	protected void extend( Point2D_F64 pivot, Point2D_F64 corner, double scale ) {
		corner.x = pivot.x + (corner.x - pivot.x)*scale;
		corner.y = pivot.y + (corner.y - pivot.y)*scale;
	}

	protected double computeErrors( Se3_F64 fiducialToCamera ) {
		if (fiducialToCamera.T.z < 0) {
			return Double.MAX_VALUE;
		}

		double maxError = 0;

		for (int i = 0; i < 4; i++) {
			maxError = Math.max(maxError, computePixelError(fiducialToCamera, points.get(i).location, listObs.get(i)));
		}

		return maxError;
	}

	private double computePixelError( Se3_F64 fiducialToCamera, Point3D_F64 X, Point2D_F64 pixel ) {
		SePointOps_F64.transform(fiducialToCamera, X, cameraP3);

		normToPixel.compute(cameraP3.x/cameraP3.z, cameraP3.y/cameraP3.z, predicted);

		return predicted.distance(pixel);
	}

	public Se3_F64 getWorldToCamera() {
		return outputFiducialToCamera;
	}

	public double getError() {
		return outputError;
	}

	public List<Point2D3D> createCopyPoints2D3D() {
		List<Point2D3D> out = new ArrayList<>();

		for (int i = 0; i < 4; i++) {
			out.add(points.get(i).copy());
		}
		return out;
	}
}
