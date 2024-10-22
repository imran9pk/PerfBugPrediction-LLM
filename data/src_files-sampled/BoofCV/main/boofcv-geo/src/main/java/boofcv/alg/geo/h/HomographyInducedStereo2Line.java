package boofcv.alg.geo.h;

import boofcv.alg.geo.MultiViewOps;
import boofcv.struct.geo.PairLineNorm;
import georegression.geometry.GeometryMath_F64;
import georegression.geometry.UtilPlane3D_F64;
import georegression.metric.Intersection3D_F64;
import georegression.struct.line.LineParametric3D_F64;
import georegression.struct.plane.PlaneGeneral3D_F64;
import georegression.struct.plane.PlaneNormal3D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.point.Vector3D_F64;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;

public class HomographyInducedStereo2Line {

	private final Point3D_F64 e2 = new Point3D_F64();
	private final DMatrixRMaj A = new DMatrixRMaj(3, 3);

	private final DMatrixRMaj H = new DMatrixRMaj(3, 3);

	private final AdjustHomographyMatrix adjust = new AdjustHomographyMatrix();

	private final Point3D_F64 Al0 = new Point3D_F64();
	private final Point3D_F64 Al1 = new Point3D_F64();

	private final Point3D_F64 v = new Point3D_F64();
	private final DMatrixRMaj av = new DMatrixRMaj(3, 3);

	private final PlaneGeneral3D_F64 planeA = new PlaneGeneral3D_F64();
	private final PlaneGeneral3D_F64 planeB = new PlaneGeneral3D_F64();

	private final LineParametric3D_F64 intersect0 = new LineParametric3D_F64();
	private final LineParametric3D_F64 intersect1 = new LineParametric3D_F64();

	private final PlaneNormal3D_F64 pi = new PlaneNormal3D_F64();
	private final Vector3D_F64 from0to1 = new Vector3D_F64();

	private final PlaneGeneral3D_F64 pi_gen = new PlaneGeneral3D_F64();

	public void setFundamental( DMatrixRMaj F, Point3D_F64 e2 ) {
		if (e2 != null)
			this.e2.setTo(e2);
		else {
			MultiViewOps.extractEpipoles(F, new Point3D_F64(), this.e2);
		}
		GeometryMath_F64.multCrossA(this.e2, F, A);
	}

	public boolean process( PairLineNorm line0, PairLineNorm line1 ) {

		double a0 = GeometryMath_F64.dot(e2, line0.l2);
		double a1 = GeometryMath_F64.dot(e2, line1.l2);

		GeometryMath_F64.multTran(A, line0.l2, Al0);
		GeometryMath_F64.multTran(A, line1.l2, Al1);

		planeA.setTo(line0.l1.x, line0.l1.y, line0.l1.z, 0);
		planeB.setTo(Al0.x, Al0.y, Al0.z, a0);

		if (!Intersection3D_F64.intersection(planeA, planeB, intersect0))
			return false;
		intersect0.slope.normalize(); planeA.setTo(line1.l1.x, line1.l1.y, line1.l1.z, 0);
		planeB.setTo(Al1.x, Al1.y, Al1.z, a1);

		if (!Intersection3D_F64.intersection(planeA, planeB, intersect1))
			return false;

		intersect1.slope.normalize();

		from0to1.x = intersect1.p.x - intersect0.p.x;
		from0to1.y = intersect1.p.y - intersect0.p.y;
		from0to1.z = intersect1.p.z - intersect0.p.z;

		GeometryMath_F64.cross(intersect0.slope, from0to1, pi.n);
		pi.p.setTo(intersect0.p);

		UtilPlane3D_F64.convert(pi, pi_gen);

		v.setTo(pi_gen.A/pi_gen.D, pi_gen.B/pi_gen.D, pi_gen.C/pi_gen.D);

		GeometryMath_F64.outerProd(e2, v, av);
		CommonOps_DDRM.subtract(A, av, H);

		adjust.adjust(H, line0);

		return true;
	}

	public DMatrixRMaj getHomography() {
		return H;
	}
}
