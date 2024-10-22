package boofcv.alg.geo;

import boofcv.abst.geo.TriangulateNViewsMetricH;
import boofcv.abst.geo.bundle.BundleAdjustmentCamera;
import boofcv.abst.geo.bundle.SceneObservations;
import boofcv.abst.geo.bundle.SceneStructureCommon;
import boofcv.abst.geo.bundle.SceneStructureMetric;
import boofcv.alg.distort.brown.RemoveBrownPtoN_F64;
import boofcv.alg.geo.bundle.cameras.BundlePinhole;
import boofcv.alg.geo.bundle.cameras.BundlePinholeBrown;
import boofcv.alg.geo.bundle.cameras.BundlePinholeSimplified;
import boofcv.alg.geo.f.FundamentalExtractEpipoles;
import boofcv.alg.geo.f.FundamentalToProjective;
import boofcv.alg.geo.h.HomographyInducedStereo2Line;
import boofcv.alg.geo.h.HomographyInducedStereo3Pts;
import boofcv.alg.geo.h.HomographyInducedStereoLinePt;
import boofcv.alg.geo.impl.ProjectiveToIdentity;
import boofcv.alg.geo.structure.DecomposeAbsoluteDualQuadratic;
import boofcv.alg.geo.trifocal.TrifocalExtractGeometries;
import boofcv.alg.geo.trifocal.TrifocalTransfer;
import boofcv.factory.geo.ConfigTriangulation;
import boofcv.factory.geo.FactoryMultiView;
import boofcv.misc.BoofLambdas;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.calib.CameraPinhole;
import boofcv.struct.geo.*;
import georegression.geometry.GeometryMath_F64;
import georegression.geometry.UtilLine2D_F64;
import georegression.geometry.UtilPoint3D_F64;
import georegression.metric.Intersection2D_F64;
import georegression.struct.GeoTuple3D_F64;
import georegression.struct.line.LineGeneral2D_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.point.Point4D_F64;
import georegression.struct.point.Vector3D_F64;
import georegression.struct.se.Se3_F64;
import georegression.transform.se.SePointOps_F64;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.DogArray_F64;
import org.ddogleg.struct.Tuple2;
import org.ddogleg.struct.Tuple3;
import org.ejml.UtilEjml;
import org.ejml.data.DMatrix3;
import org.ejml.data.DMatrix3x3;
import org.ejml.data.DMatrix4x4;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.fixed.CommonOps_DDF4;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.SingularOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition_F64;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class MultiViewOps {

	public static TrifocalTensor createTrifocal( DMatrixRMaj P2, DMatrixRMaj P3,
												 @Nullable TrifocalTensor ret ) {
		if (ret == null)
			ret = new TrifocalTensor();

		for (int i = 0; i < 3; i++) {
			DMatrixRMaj T = ret.getT(i);

			int index = 0;
			for (int j = 0; j < 3; j++) {
				double a_left = P2.get(j, i);
				double a_right = P2.get(j, 3);

				for (int k = 0; k < 3; k++) {
					T.data[index++] = a_left*P3.get(k, 3) - a_right*P3.get(k, i);
				}
			}
		}

		return ret;
	}

	public static TrifocalTensor createTrifocal( DMatrixRMaj P1, DMatrixRMaj P2, DMatrixRMaj P3,
												 @Nullable TrifocalTensor ret ) {
		if (ret == null)
			ret = new TrifocalTensor();

		double scale = 1;
		scale = Math.max(scale, CommonOps_DDRM.elementMaxAbs(P1));
		scale = Math.max(scale, CommonOps_DDRM.elementMaxAbs(P2));
		scale = Math.max(scale, CommonOps_DDRM.elementMaxAbs(P3));

		DMatrixRMaj A = new DMatrixRMaj(4, 4);

		double sign = 1;
		for (int i = 0; i < 3; i++) {
			DMatrixRMaj T = ret.getT(i);

			for (int row = 0, cnt = 0; row < 3; row++) {
				if (row != i) {
					CommonOps_DDRM.extract(P1, row, row + 1, 0, 4, A, cnt, 0);
					for (int col = 0; col < 4; col++) {
						A.data[cnt*4 + col] /= scale;
					}
					cnt++;
				}
			}

			for (int q = 0; q < 3; q++) {
				CommonOps_DDRM.extract(P2, q, q + 1, 0, 4, A, 2, 0);

				for (int col = 0; col < 4; col++) {
					A.data[2*4 + col] /= scale;
				}

				for (int r = 0; r < 3; r++) {
					CommonOps_DDRM.extract(P3, r, r + 1, 0, 4, A, 3, 0);
					for (int col = 0; col < 4; col++) {
						A.data[3*4 + col] /= scale;
					}

					double v = CommonOps_DDRM.det(A);
					T.set(q, r, sign*v*scale);  }
			}

			sign *= -1;
		}

		return ret;
	}

	public static TrifocalTensor createTrifocal( Se3_F64 P2, Se3_F64 P3,
												 @Nullable TrifocalTensor ret ) {
		if (ret == null)
			ret = new TrifocalTensor();

		DMatrixRMaj R2 = P2.getR();
		DMatrixRMaj R3 = P3.getR();
		Vector3D_F64 T2 = P2.getT();
		Vector3D_F64 T3 = P3.getT();

		for (int col = 0; col < 3; col++) {
			DMatrixRMaj T = ret.getT(col);

			int index = 0;
			for (int i = 0; i < 3; i++) {
				double a_left = R2.unsafe_get(i, col);
				double a_right = T2.getIdx(i);

				for (int j = 0; j < 3; j++) {
					T.data[index++] = a_left*T3.getIdx(j) - a_right*R3.unsafe_get(j, col);
				}
			}
		}

		return ret;
	}

	public static Vector3D_F64 constraint( TrifocalTensor tensor,
										   Vector3D_F64 l1, Vector3D_F64 l2, Vector3D_F64 l3,
										   @Nullable Vector3D_F64 ret ) {
		if (ret == null)
			ret = new Vector3D_F64();

		double x = GeometryMath_F64.innerProd(l2, tensor.T1, l3);
		double y = GeometryMath_F64.innerProd(l2, tensor.T2, l3);
		double z = GeometryMath_F64.innerProd(l2, tensor.T3, l3);

		GeometryMath_F64.cross(new Vector3D_F64(x, y, z), l1, ret);

		return ret;
	}

	public static double constraint( TrifocalTensor tensor,
									 Point2D_F64 p1, Vector3D_F64 l2, Vector3D_F64 l3 ) {
		DMatrixRMaj sum = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.add(p1.x, tensor.T1, sum, sum);
		CommonOps_DDRM.add(p1.y, tensor.T2, sum, sum);
		CommonOps_DDRM.add(tensor.T3, sum, sum);

		return GeometryMath_F64.innerProd(l2, sum, l3);
	}

	public static Vector3D_F64 constraint( TrifocalTensor tensor,
										   Point2D_F64 p1, Vector3D_F64 l2, Point2D_F64 p3,
										   Vector3D_F64 ret ) {
		if (ret == null)
			ret = new Vector3D_F64();

		DMatrixRMaj sum = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.add(p1.x, tensor.T1, sum, sum);
		CommonOps_DDRM.add(p1.y, tensor.T2, sum, sum);
		CommonOps_DDRM.add(tensor.T3, sum, sum);

		Vector3D_F64 tempV = new Vector3D_F64();
		GeometryMath_F64.multTran(sum, l2, tempV);

		GeometryMath_F64.cross(tempV, new Vector3D_F64(p3.x, p3.y, 1), ret);

		return ret;
	}

	public static Vector3D_F64 constraint( TrifocalTensor tensor,
										   Point2D_F64 p1, Point2D_F64 p2, Vector3D_F64 l3,
										   Vector3D_F64 ret ) {
		if (ret == null)
			ret = new Vector3D_F64();

		DMatrixRMaj sum = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.add(p1.x, tensor.T1, sum, sum);
		CommonOps_DDRM.add(p1.y, tensor.T2, sum, sum);
		CommonOps_DDRM.add(tensor.T3, sum, sum);

		DMatrixRMaj cross2 = GeometryMath_F64.crossMatrix(p2.x, p2.y, 1, null);

		DMatrixRMaj temp = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.mult(cross2, sum, temp);
		GeometryMath_F64.mult(temp, l3, ret);

		return ret;
	}

	public static DMatrixRMaj constraint( TrifocalTensor tensor,
										  Point2D_F64 p1, Point2D_F64 p2, Point2D_F64 p3,
										  DMatrixRMaj ret ) {
		if (ret == null)
			ret = new DMatrixRMaj(3, 3);

		DMatrixRMaj sum = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.add(p1.x, tensor.T1, p1.y, tensor.T2, sum);
		CommonOps_DDRM.add(sum, tensor.T3, sum);

		DMatrixRMaj cross2 = GeometryMath_F64.crossMatrix(p2.x, p2.y, 1, null);
		DMatrixRMaj cross3 = GeometryMath_F64.crossMatrix(p3.x, p3.y, 1, null);

		DMatrixRMaj temp = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.mult(cross2, sum, temp);
		CommonOps_DDRM.mult(temp, cross3, ret);

		return ret;
	}

	public static double constraint( DMatrixRMaj F, Point2D_F64 p1, Point2D_F64 p2 ) {
		return GeometryMath_F64.innerProd(p2, F, p1);
	}

	public static Point2D_F64 constraintHomography( DMatrixRMaj H, Point2D_F64 p1, Point2D_F64 outputP2 ) {
		if (outputP2 == null)
			outputP2 = new Point2D_F64();

		GeometryMath_F64.mult(H, p1, outputP2);

		return outputP2;
	}

	public static DMatrixRMaj inducedHomography13( TrifocalTensor tensor,
												   Vector3D_F64 line2,
												   DMatrixRMaj output ) {
		if (output == null)
			output = new DMatrixRMaj(3, 3);

		DMatrixRMaj T = tensor.T1;

		output.data[0] = T.data[0]*line2.x + T.data[3]*line2.y + T.data[6]*line2.z;
		output.data[3] = T.data[1]*line2.x + T.data[4]*line2.y + T.data[7]*line2.z;
		output.data[6] = T.data[2]*line2.x + T.data[5]*line2.y + T.data[8]*line2.z;

		T = tensor.T2;
		output.data[1] = T.data[0]*line2.x + T.data[3]*line2.y + T.data[6]*line2.z;
		output.data[4] = T.data[1]*line2.x + T.data[4]*line2.y + T.data[7]*line2.z;
		output.data[7] = T.data[2]*line2.x + T.data[5]*line2.y + T.data[8]*line2.z;

		T = tensor.T3;
		output.data[2] = T.data[0]*line2.x + T.data[3]*line2.y + T.data[6]*line2.z;
		output.data[5] = T.data[1]*line2.x + T.data[4]*line2.y + T.data[7]*line2.z;
		output.data[8] = T.data[2]*line2.x + T.data[5]*line2.y + T.data[8]*line2.z;

return output;
	}

	public static DMatrixRMaj inducedHomography12( TrifocalTensor tensor,
												   Vector3D_F64 line3,
												   DMatrixRMaj output ) {
		if (output == null)
			output = new DMatrixRMaj(3, 3);

		DMatrixRMaj T = tensor.T1;
		output.data[0] = T.data[0]*line3.x + T.data[1]*line3.y + T.data[2]*line3.z;
		output.data[3] = T.data[3]*line3.x + T.data[4]*line3.y + T.data[5]*line3.z;
		output.data[6] = T.data[6]*line3.x + T.data[7]*line3.y + T.data[8]*line3.z;

		T = tensor.T2;
		output.data[1] = T.data[0]*line3.x + T.data[1]*line3.y + T.data[2]*line3.z;
		output.data[4] = T.data[3]*line3.x + T.data[4]*line3.y + T.data[5]*line3.z;
		output.data[7] = T.data[6]*line3.x + T.data[7]*line3.y + T.data[8]*line3.z;

		T = tensor.T3;
		output.data[2] = T.data[0]*line3.x + T.data[1]*line3.y + T.data[2]*line3.z;
		output.data[5] = T.data[3]*line3.x + T.data[4]*line3.y + T.data[5]*line3.z;
		output.data[8] = T.data[6]*line3.x + T.data[7]*line3.y + T.data[8]*line3.z;

return output;
	}

	public static DMatrixRMaj fundamentalToHomography3Pts( DMatrixRMaj F,
														   AssociatedPair p1, AssociatedPair p2, AssociatedPair p3 ) {
		HomographyInducedStereo3Pts alg = new HomographyInducedStereo3Pts();

		alg.setFundamental(F, null);
		if (!alg.process(p1, p2, p3))
			return null;
		return alg.getHomography();
	}

	public static DMatrixRMaj fundamentalToHomographyLinePt( DMatrixRMaj F, PairLineNorm line, AssociatedPair point ) {
		HomographyInducedStereoLinePt alg = new HomographyInducedStereoLinePt();

		alg.setFundamental(F, null);
		alg.process(line, point);
		return alg.getHomography();
	}

	public static DMatrixRMaj fundamentalToHomography2Lines( DMatrixRMaj F, PairLineNorm line0, PairLineNorm line1 ) {
		HomographyInducedStereo2Line alg = new HomographyInducedStereo2Line();

		alg.setFundamental(F, null);
		if (!alg.process(line0, line1))
			return null;
		return alg.getHomography();
	}

	public static void extractEpipoles( TrifocalTensor tensor, Point3D_F64 e2, Point3D_F64 e3 ) {
		TrifocalExtractGeometries e = new TrifocalExtractGeometries();
		e.setTensor(tensor);
		e.extractEpipoles(e2, e3);
	}

	public static void trifocalToFundamental( TrifocalTensor tensor, DMatrixRMaj F21, DMatrixRMaj F31 ) {
		TrifocalExtractGeometries e = new TrifocalExtractGeometries();
		e.setTensor(tensor);
		e.extractFundmental(F21, F31);
	}

	public static void trifocalToCameraMatrices( TrifocalTensor tensor, DMatrixRMaj P2, DMatrixRMaj P3 ) {
		TrifocalExtractGeometries e = new TrifocalExtractGeometries();
		e.setTensor(tensor);
		e.extractCamera(P2, P3);
	}

	public static DMatrixRMaj createEssential( DMatrixRMaj R, Vector3D_F64 T, @Nullable DMatrixRMaj E ) {
		if (E == null)
			E = new DMatrixRMaj(3, 3);

		DMatrixRMaj T_hat = GeometryMath_F64.crossMatrix(T, null);
		CommonOps_DDRM.mult(T_hat, R, E);

		return E;
	}

	public static DMatrixRMaj createFundamental( DMatrixRMaj E, DMatrixRMaj K ) {
		DMatrixRMaj K_inv = new DMatrixRMaj(3, 3);
		CommonOps_DDRM.invert(K, K_inv);

		DMatrixRMaj F = new DMatrixRMaj(3, 3);
		PerspectiveOps.multTranA(K_inv, E, K_inv, F);

		return F;
	}

	public static DMatrixRMaj createFundamental( DMatrixRMaj E, CameraPinhole intrinsic ) {
		DMatrixRMaj K = PerspectiveOps.pinholeToMatrix(intrinsic, (DMatrixRMaj)null);
		return createFundamental(E, K);
	}

	public static DMatrixRMaj createFundamental( DMatrixRMaj E,
												 DMatrixRMaj K1, DMatrixRMaj K2 ) {
		DMatrixRMaj K1_inv = new DMatrixRMaj(3, 3);
		CommonOps_DDRM.invert(K1, K1_inv);
		DMatrixRMaj K2_inv = new DMatrixRMaj(3, 3);
		CommonOps_DDRM.invert(K2, K2_inv);

		DMatrixRMaj F = new DMatrixRMaj(3, 3);
		DMatrixRMaj temp = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.multTransA(K2_inv, E, temp);
		CommonOps_DDRM.mult(temp, K1_inv, F);

		return F;
	}

	public static DMatrixRMaj createFundamental( DMatrixRMaj R, Vector3D_F64 T,
												 DMatrixRMaj K1, DMatrixRMaj K2, @Nullable DMatrixRMaj F ) {
		if (F == null)
			F = new DMatrixRMaj(3, 3);
		else
			F.reshape(3, 3);

		createEssential(R, T, F);
		F.setTo(createFundamental(F, K1, K2));
		return F;
	}

	public static DMatrixRMaj createHomography( DMatrixRMaj R, Vector3D_F64 T,
												double d, Vector3D_F64 N ) {
		DMatrixRMaj H = new DMatrixRMaj(3, 3);

		GeometryMath_F64.outerProd(T, N, H);
		CommonOps_DDRM.divide(H, d);
		CommonOps_DDRM.addEquals(H, R);

		return H;
	}

	public static DMatrixRMaj createHomography( DMatrixRMaj R, Vector3D_F64 T,
												double d, Vector3D_F64 N,
												DMatrixRMaj K ) {
		DMatrixRMaj temp = new DMatrixRMaj(3, 3);
		DMatrixRMaj K_inv = new DMatrixRMaj(3, 3);

		DMatrixRMaj H = createHomography(R, T, d, N);

		CommonOps_DDRM.mult(K, H, temp);

		CommonOps_DDRM.invert(K, K_inv);
		CommonOps_DDRM.mult(temp, K_inv, H);

		return H;
	}

	public static void extractEpipoles( DMatrixRMaj F, Point3D_F64 e1, Point3D_F64 e2 ) {
		FundamentalExtractEpipoles alg = new FundamentalExtractEpipoles();
		alg.process(F, e1, e2);
	}

	public static DMatrixRMaj fundamentalToProjective( DMatrixRMaj F, Point3D_F64 e2, Vector3D_F64 v, double lambda ) {

		FundamentalToProjective f2p = new FundamentalToProjective();
		DMatrixRMaj P = new DMatrixRMaj(3, 4);
		f2p.twoView(F, e2, v, lambda, P);
		return P;
	}

	public static DMatrixRMaj projectiveToFundamental( DMatrixRMaj P1, DMatrixRMaj P2, @Nullable DMatrixRMaj F21 ) {
		if (F21 == null)
			F21 = new DMatrixRMaj(3, 3);

		ProjectiveToIdentity p2i = new ProjectiveToIdentity();
		if (!p2i.process(P1))
			throw new RuntimeException("Failed!");

		DMatrixRMaj P1inv = p2i.getPseudoInvP();
		DMatrixRMaj U = p2i.getU();

		DMatrixRMaj e = new DMatrixRMaj(3, 1);
		CommonOps_DDRM.mult(P2, U, e);

		DMatrixRMaj tmp = new DMatrixRMaj(3, 4);
		DMatrixRMaj e_skew = new DMatrixRMaj(3, 3);
		GeometryMath_F64.crossMatrix(e.data[0], e.data[1], e.data[2], e_skew);
		CommonOps_DDRM.mult(e_skew, P2, tmp);
		CommonOps_DDRM.mult(tmp, P1inv, F21);

		return F21;
	}

	public static DMatrixRMaj projectiveToFundamental( DMatrixRMaj P2, @Nullable DMatrixRMaj F21 ) {
		if (F21 == null)
			F21 = new DMatrixRMaj(3, 3);

		double b1 = P2.unsafe_get(0, 3);
		double b2 = P2.unsafe_get(1, 3);
		double b3 = P2.unsafe_get(2, 3);

		double a11 = P2.data[0], a12 = P2.data[1], a13 = P2.data[2];
		double a21 = P2.data[4], a22 = P2.data[5], a23 = P2.data[6];
		double a31 = P2.data[8], a32 = P2.data[9], a33 = P2.data[10];

		F21.data[0] = -b3*a21 + b2*a31;
		F21.data[1] = -b3*a22 + b2*a32;
		F21.data[2] = -b3*a23 + b2*a33;
		F21.data[3] = b3*a11 - b1*a31;
		F21.data[4] = b3*a12 - b1*a32;
		F21.data[5] = b3*a13 - b1*a33;
		F21.data[6] = -b2*a11 + b1*a21;
		F21.data[7] = -b2*a12 + b1*a22;
		F21.data[8] = -b2*a13 + b1*a23;

		return F21;
	}

	public static DMatrixRMaj fundamentalToProjective( DMatrixRMaj F ) {
		FundamentalToProjective f2p = new FundamentalToProjective();
		DMatrixRMaj P = new DMatrixRMaj(3, 4);
		f2p.twoView(F, P);
		return P;
	}

	public static DMatrixRMaj fundamentalToEssential( DMatrixRMaj F, DMatrixRMaj K, @Nullable DMatrixRMaj outputE ) {
		return fundamentalToEssential(F, K, K, outputE);
	}

	public static DMatrixRMaj fundamentalToEssential( DMatrixRMaj F, DMatrixRMaj K1, DMatrixRMaj K2,
													  @Nullable DMatrixRMaj outputE ) {
		if (outputE == null)
			outputE = new DMatrixRMaj(3, 3);

		PerspectiveOps.multTranA(K2, F, K1, outputE);

		SingularValueDecomposition_F64<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(true, true, false);

		svd.decompose(outputE);
		DMatrixRMaj U = svd.getU(null, false);
		DMatrixRMaj W = svd.getW(null);
		DMatrixRMaj V = svd.getV(null, false);

		SingularOps_DDRM.descendingOrder(U, false, W, V, false);
		W.set(0, 0, 1);
		W.set(1, 1, 1);
		W.set(2, 2, 0);

		PerspectiveOps.multTranC(U, W, V, outputE);

		return outputE;
	}

	public static void fundamentalToProjective( DMatrixRMaj F21, DMatrixRMaj F31, DMatrixRMaj F32,
												DMatrixRMaj P2, DMatrixRMaj P3 ) {
		FundamentalToProjective alg = new FundamentalToProjective();
		alg.threeView(F21, F31, F32, P2, P3);
	}

	public static DMatrixRMaj projectiveToIdentityH( DMatrixRMaj P, @Nullable DMatrixRMaj H ) {
		if (H == null)
			H = new DMatrixRMaj(4, 4);
		ProjectiveToIdentity alg = new ProjectiveToIdentity();
		if (!alg.process(P))
			throw new RuntimeException("WTF this failed?? Probably NaN in P");
		alg.computeH(H);
		return H;
	}

	public static void projectiveMakeFirstIdentity( List<DMatrixRMaj> cameraMatrices, @Nullable DMatrixRMaj H ) {
		BoofMiscOps.checkTrue(cameraMatrices.size() >= 1);
		H = projectiveToIdentityH(cameraMatrices.get(0), H);
		DMatrixRMaj tmp = new DMatrixRMaj(3, 4);
		for (int i = 0; i < cameraMatrices.size(); i++) {
			DMatrixRMaj P = cameraMatrices.get(i);
			CommonOps_DDRM.mult(P, H, tmp);
			P.setTo(tmp);
		}
	}

	public static boolean fundamentalCompatible3( DMatrixRMaj F21, DMatrixRMaj F31, DMatrixRMaj F32, double tol ) {
		FundamentalExtractEpipoles extractEpi = new FundamentalExtractEpipoles();

		Point3D_F64 e21 = new Point3D_F64();
		Point3D_F64 e12 = new Point3D_F64();
		Point3D_F64 e31 = new Point3D_F64();
		Point3D_F64 e13 = new Point3D_F64();
		Point3D_F64 e32 = new Point3D_F64();
		Point3D_F64 e23 = new Point3D_F64();

		extractEpi.process(F21, e21, e12);
		extractEpi.process(F31, e31, e13);
		extractEpi.process(F32, e32, e23);

		double score = 0;
		score += Math.abs(GeometryMath_F64.innerProd(e23, F21, e13));
		score += Math.abs(GeometryMath_F64.innerProd(e31, F31, e21));
		score += Math.abs(GeometryMath_F64.innerProd(e32, F32, e12));

		score /= 3;

		return score <= tol;
	}

	public static boolean decomposeMetricCamera( DMatrixRMaj cameraMatrix, DMatrixRMaj K, Se3_F64 worldToView ) {
		return new DecomposeProjectiveToMetric().decomposeMetricCamera(cameraMatrix, K, worldToView);
	}

	public static List<Se3_F64> decomposeEssential( DMatrixRMaj E21 ) {
		DecomposeEssential d = new DecomposeEssential();

		d.decompose(E21);

		return d.getSolutions();
	}

	public static List<Tuple2<Se3_F64, Vector3D_F64>> decomposeHomography( DMatrixRMaj H ) {
		DecomposeHomography d = new DecomposeHomography();

		d.decompose(H);

		List<Vector3D_F64> solutionsN = d.getSolutionsN();
		List<Se3_F64> solutionsSe = d.getSolutionsSE();

		List<Tuple2<Se3_F64, Vector3D_F64>> ret = new ArrayList<>();
		for (int i = 0; i < 4; i++) {
			ret.add(new Tuple2<>(solutionsSe.get(i), solutionsN.get(i)));
		}


		return ret;
	}

	public static void errorsHomographySymm( List<AssociatedPair> observations,
											 DMatrixRMaj H,
											 @Nullable DMatrixRMaj H_inv,
											 DogArray_F64 storage ) {
		storage.reset();
		if (H_inv == null)
			H_inv = new DMatrixRMaj(3, 3);
		CommonOps_DDRM.invert(H, H_inv);

		Point3D_F64 tmp = new Point3D_F64();

		for (int i = 0; i < observations.size(); i++) {
			AssociatedPair p = observations.get(i);

			double dx, dy;
			double error = 0;

			GeometryMath_F64.mult(H, p.p1, tmp);
			if (Math.abs(tmp.z) <= UtilEjml.EPS)
				continue;
			dx = p.p2.x - tmp.x/tmp.z;
			dy = p.p2.y - tmp.y/tmp.z;
			error += dx*dx + dy*dy;

			GeometryMath_F64.mult(H_inv, p.p2, tmp);
			if (Math.abs(tmp.z) <= UtilEjml.EPS)
				continue;
			dx = p.p1.x - tmp.x/tmp.z;
			dy = p.p1.y - tmp.y/tmp.z;
			error += dx*dx + dy*dy;

			storage.add(error);
		}
	}

	public static Point3D_F64 transfer_1_to_3( TrifocalTensor T, Point2D_F64 x1, Vector3D_F64 l2,
											   @Nullable Point3D_F64 x3 ) {
		if (x3 == null)
			x3 = new Point3D_F64();

		GeometryMath_F64.multTran(T.T1, l2, x3);
		double xx = x3.x*x1.x;
		double yy = x3.y*x1.x;
		double zz = x3.z*x1.x;
		GeometryMath_F64.multTran(T.T2, l2, x3);
		xx += x3.x*x1.y;
		yy += x3.y*x1.y;
		zz += x3.z*x1.y;
		GeometryMath_F64.multTran(T.T3, l2, x3);
		x3.x = xx + x3.x;
		x3.y = yy + x3.y;
		x3.z = zz + x3.z;

		return x3;
		}

	public static Point3D_F64 transfer_1_to_3( TrifocalTensor T, Point2D_F64 x1, Point2D_F64 x2,
											   @Nullable Point3D_F64 x3 ) {
		if (x3 == null)
			x3 = new Point3D_F64();

		var transfer = new TrifocalTransfer();
		transfer.setTrifocal(T);
		transfer.transfer_1_to_3(x1.x, x1.y, x2.x, x2.y, x3);

		return x3;
	}

	public static Point3D_F64 transfer_1_to_2( TrifocalTensor T, Point2D_F64 x1, Vector3D_F64 l3,
											   @Nullable Point3D_F64 x2 ) {
		if (x2 == null)
			x2 = new Point3D_F64();

		GeometryMath_F64.mult(T.T1, l3, x2);
		double xx = x2.x*x1.x;
		double yy = x2.y*x1.x;
		double zz = x2.z*x1.x;
		GeometryMath_F64.mult(T.T2, l3, x2);
		xx += x2.x*x1.y;
		yy += x2.y*x1.y;
		zz += x2.z*x1.y;
		GeometryMath_F64.mult(T.T3, l3, x2);
		x2.x = xx + x2.x;
		x2.y = yy + x2.y;
		x2.z = zz + x2.z;

		return x2;
	}

	public static Point3D_F64 transfer_1_to_2( TrifocalTensor T, Point2D_F64 x1, Point2D_F64 x3,
											   @Nullable Point3D_F64 x2 ) {
		if (x2 == null)
			x2 = new Point3D_F64();

		var transfer = new TrifocalTransfer();
		transfer.setTrifocal(T);
		transfer.transfer_1_to_2(x1.x, x1.y, x3.x, x3.y, x2);

		return x2;
	}

	public static boolean projectiveToMetric( DMatrixRMaj cameraMatrix, DMatrixRMaj H,
											  Se3_F64 worldToView, DMatrixRMaj K ) {
		return new DecomposeProjectiveToMetric().projectiveToMetric(cameraMatrix, H, worldToView, K);
	}

	public static boolean projectiveToMetricKnownK( DMatrixRMaj cameraMatrix,
													DMatrixRMaj H, DMatrixRMaj K,
													Se3_F64 worldToView ) {
		return new DecomposeProjectiveToMetric().projectiveToMetricKnownK(cameraMatrix, H, K, worldToView);
	}

	public static boolean enforceAbsoluteQuadraticConstraints( DMatrix4x4 Q, boolean zeroCenter, boolean zeroSkew ) {

		return enforceAbsoluteQuadraticConstraints(Q, zeroCenter, zeroSkew, null);
	}

	public static boolean enforceAbsoluteQuadraticConstraints( DMatrix4x4 Q, boolean zeroCenter, boolean zeroSkew,
															   @Nullable DecomposeAbsoluteDualQuadratic alg ) {

		if (alg == null)
			alg = new DecomposeAbsoluteDualQuadratic();

		if (Q.a33 < 0)
			CommonOps_DDF4.scale(-1, Q);

		if (!alg.decompose(Q))
			return false;

		DMatrix3x3 k = alg.getK();

		if (zeroCenter) {
			k.a13 = k.a23 = 0;
		}

		if (zeroSkew) {
			k.a12 = 0;
		}

		alg.recomputeQ(Q);
		return true;
	}

	public static boolean absoluteQuadraticToH( DMatrix4x4 Q, DMatrixRMaj H ) {
		DecomposeAbsoluteDualQuadratic alg = new DecomposeAbsoluteDualQuadratic();
		if (!alg.decompose(Q))
			return false;

		return alg.computeRectifyingHomography(H);
	}

	public static void rectifyHToAbsoluteQuadratic( DMatrixRMaj H, DMatrixRMaj Q ) {
		int indexQ = 0;
		for (int rowA = 0; rowA < 4; rowA++) {
			for (int colB = 0; colB < 4; colB++) {
				int indexA = rowA*4;
				int indexB = colB*4;
				double sum = 0;

				for (int i = 0; i < 3; i++) {
sum += H.data[indexA++]*H.data[indexB++];
				}

Q.data[indexQ++] = sum;
			}
		}
	}

	public static void canonicalRectifyingHomographyFromKPinf( DMatrixRMaj K, Point3D_F64 planeAtInfinity, DMatrixRMaj H ) {
		H.reshape(4, 4);
		CommonOps_DDRM.insert(K, H, 0, 0);

		double v1 = -(planeAtInfinity.x*K.data[0] + planeAtInfinity.y*K.data[3] + planeAtInfinity.z*K.data[6]);
		double v2 = -(planeAtInfinity.x*K.data[1] + planeAtInfinity.y*K.data[4] + planeAtInfinity.z*K.data[7]);
		double v3 = -(planeAtInfinity.x*K.data[2] + planeAtInfinity.y*K.data[5] + planeAtInfinity.z*K.data[8]);

		H.unsafe_set(3, 0, v1);
		H.unsafe_set(3, 1, v2);
		H.unsafe_set(3, 2, v3);
		H.unsafe_set(3, 3, 1.0);
		H.unsafe_set(0, 3, 0);
		H.unsafe_set(1, 3, 0);
		H.unsafe_set(2, 3, 0);
	}

	public static void intrinsicFromAbsoluteQuadratic( DMatrixRMaj Q, DMatrixRMaj P, CameraPinhole intrinsic ) {
		DMatrixRMaj tmp = new DMatrixRMaj(3, 4);
		DMatrixRMaj tmp2 = new DMatrixRMaj(3, 3);
		CommonOps_DDRM.mult(P, Q, tmp);
		CommonOps_DDRM.multTransB(tmp, P, tmp2);

		decomposeDiac(tmp2, intrinsic);
	}

	public static void decomposeDiac( double w11, double w12, double w13, double w22, double w23, double w33,
									  CameraPinhole intrinsic ) {
		double cx = w13/w33;
		double cy = w23/w33;
		double fy = Math.sqrt(Math.abs(w22/w33 - cy*cy));
		double skew = (w12/w33 - cx*cy)/fy;
		double fx = Math.sqrt(Math.abs(w11/w33 - skew*skew - cx*cx));

		intrinsic.cx = cx;
		intrinsic.cy = cy;
		intrinsic.fx = fx;
		intrinsic.fy = fy;
		intrinsic.skew = skew;
	}

	public static void decomposeDiac( DMatrixRMaj w, CameraPinhole intrinsic ) {
		decomposeDiac(w.data[0], w.data[1], w.data[2], w.data[4], w.data[5], w.data[8], intrinsic);
	}

	public static DMatrixRMaj createProjectiveToMetric( DMatrixRMaj K,
														double v1, double v2, double v3,
														double lambda,
														@Nullable DMatrixRMaj H ) {
		if (H == null)
			H = new DMatrixRMaj(4, 4);
		else
			H.reshape(4, 4);

		CommonOps_DDRM.insert(K, H, 0, 0);
		H.set(0, 3, 0);
		H.set(1, 3, 0);
		H.set(2, 3, 0);
		H.set(3, 0, v1);
		H.set(3, 1, v2);
		H.set(3, 2, v3);
		H.set(3, 3, lambda);

		return H;
	}

	public static boolean decomposeAbsDualQuadratic( DMatrix4x4 Q, DMatrix3x3 w, DMatrix3 p ) {
		DecomposeAbsoluteDualQuadratic alg = new DecomposeAbsoluteDualQuadratic();
		if (!alg.decompose(Q))
			return false;
		w.setTo(alg.getW());
		p.setTo(alg.getP());
		return true;
	}

	public static Tuple2<List<Point2D_F64>, List<Point2D_F64>> split2( List<AssociatedPair> input ) {
		List<Point2D_F64> list1 = new ArrayList<>();
		List<Point2D_F64> list2 = new ArrayList<>();

		for (int i = 0; i < input.size(); i++) {
			list1.add(input.get(i).p1);
			list2.add(input.get(i).p2);
		}

		return new Tuple2<>(list1, list2);
	}

	public static Tuple3<List<Point2D_F64>, List<Point2D_F64>, List<Point2D_F64>> split3( List<AssociatedTriple> input ) {
		List<Point2D_F64> list1 = new ArrayList<>();
		List<Point2D_F64> list2 = new ArrayList<>();
		List<Point2D_F64> list3 = new ArrayList<>();

		for (int i = 0; i < input.size(); i++) {
			list1.add(input.get(i).p1);
			list2.add(input.get(i).p2);
			list3.add(input.get(i).p3);
		}

		return new Tuple3<>(list1, list2, list3);
	}

	public static void triangulatePoints( SceneStructureMetric structure, SceneObservations observations ) {
		TriangulateNViewsMetricH triangulator = FactoryMultiView.triangulateNViewMetricH(ConfigTriangulation.GEOMETRIC());

		List<RemoveBrownPtoN_F64> list_p_to_n = new ArrayList<>();
		for (int i = 0; i < structure.cameras.size; i++) {
			RemoveBrownPtoN_F64 p2n = new RemoveBrownPtoN_F64();
			BundleAdjustmentCamera baseModel = Objects.requireNonNull(structure.cameras.data[i].model);
			if (baseModel instanceof BundlePinholeSimplified) {
				BundlePinholeSimplified cam = (BundlePinholeSimplified)baseModel;
				p2n.setK(cam.f, cam.f, 0, 0, 0).setDistortion(new double[]{cam.k1, cam.k2}, 0, 0);
			} else if (baseModel instanceof BundlePinhole) {
				BundlePinhole cam = (BundlePinhole)baseModel;
				p2n.setK(cam.fx, cam.fy, cam.skew, cam.cx, cam.cy).setDistortion(new double[]{0, 0}, 0, 0);
			} else if (baseModel instanceof BundlePinholeBrown) {
				BundlePinholeBrown cam = (BundlePinholeBrown)baseModel;
				p2n.setK(cam.fx, cam.fy, cam.skew, cam.cx, cam.cy).setDistortion(cam.radial, cam.t1, cam.t2);
			} else {
				throw new RuntimeException("Unknown camera model!");
			}
			list_p_to_n.add(p2n);
		}

		DogArray<Point2D_F64> normObs = new DogArray<>(Point2D_F64::new);
		normObs.resize(3);

		final boolean homogenous = structure.isHomogenous();
		Point4D_F64 X = new Point4D_F64();

		List<Se3_F64> worldToViews = new ArrayList<>();
		for (int i = 0; i < structure.points.size; i++) {
			normObs.reset();
			worldToViews.clear();
			SceneStructureCommon.Point sp = structure.points.get(i);
			for (int j = 0; j < sp.views.size; j++) {
				int viewIdx = sp.views.get(j);
				SceneStructureMetric.View v = structure.views.data[viewIdx];
				worldToViews.add(structure.getParentToView(v));

				Point2D_F64 n = normObs.grow();
				int pointidx = observations.views.get(viewIdx).point.indexOf(i);
				observations.views.get(viewIdx).getPixel(pointidx, n);
				list_p_to_n.get(v.camera).compute(n.x, n.y, n);
			}

			if (!triangulator.triangulate(normObs.toList(), worldToViews, X)) {
				throw new RuntimeException("Triangulation failed. Bad input?");
			}
			if (homogenous)
				sp.set(X.x, X.y, X.z, X.w);
			else
				sp.set(X.x/X.w, X.y/X.w, X.z/X.w);
		}
	}

	public static double findScale( DMatrixRMaj a, DMatrixRMaj b ) {
		BoofMiscOps.checkTrue(a.numRows == b.numRows);
		BoofMiscOps.checkTrue(a.numCols == b.numCols);

		final int N = a.getNumElements();
		double sum = 0.0;
		double weights = 0.0;
		for (int i = 0; i < N; i++) {
			double valA = a.data[i];
			double valB = b.data[i];
			if (valA == 0.0)
				continue;
			double weight = Math.abs(valB);
			sum += weight*(valB/valA);
			weights += weight;
		}

		return sum/weights;
	}

	public static double findScale( GeoTuple3D_F64<?> a, GeoTuple3D_F64<?> b ) {
		int which = UtilPoint3D_F64.axisLargestAbs(a);
		double bottom = a.getIdx(which);
		if (bottom == 0.0)
			return 0.0;
		return b.getIdx(which)/bottom;
	}

	public static List<List<Point2D_F64>>
	splits3Lists( List<AssociatedTriple> src, @Nullable List<List<Point2D_F64>> dst ) {
		if (dst == null) {
			dst = new ArrayList<>();
		}
		if (dst.size() != 3) {
			dst.clear();
			for (int i = 0; i < 3; i++) {
				dst.add(new ArrayList<>());
			}
		} else {
			for (int i = 0; i < 3; i++) {
				dst.get(i).clear();
			}
		}

		for (int i = 0; i < src.size(); i++) {
			AssociatedTriple a = src.get(i);
			dst.get(0).add(a.p1);
			dst.get(1).add(a.p2);
			dst.get(2).add(a.p3);
		}

		return dst;
	}

	public static void convertTr( List<AssociatedTriple> src, DogArray<AssociatedTuple> dst ) {
		dst.resize(src.size());
		if (src.size() == 0)
			return;
		BoofMiscOps.checkTrue(dst.get(0).size() == 3);

		for (int i = 0; i < src.size(); i++) {
			AssociatedTriple a = src.get(i);
			AssociatedTuple b = dst.get(i);
			b.set(0, a.p1);
			b.set(1, a.p2);
			b.set(2, a.p3);
		}
	}

	public static void convertTr( List<AssociatedTriple> src, int idx0, int idx1,
								  DogArray<AssociatedPair> dst ) {
		dst.resize(src.size());
		if (src.size() == 0)
			return;

		for (int i = 0; i < src.size(); i++) {
			AssociatedTriple a = src.get(i);
			dst.get(i).setTo(a.get(idx0), a.get(idx1));
		}
	}

	public static void convertTu( List<AssociatedTuple> src, int idx0, int idx1,
								  DogArray<AssociatedPair> dst ) {
		dst.resize(src.size());
		if (src.size() == 0)
			return;
		BoofMiscOps.checkTrue(src.get(0).size() == 3);

		for (int i = 0; i < src.size(); i++) {
			AssociatedTuple a = src.get(i);
			dst.get(i).setTo(a.get(idx0), a.get(idx1));
		}
	}

	public static double disparityToRange( double disparity, double focalLength, double baseline ) {
		if (disparity == 0.0)
			return Double.POSITIVE_INFINITY;
		if (disparity < 0.0)
			throw new IllegalArgumentException("Disparity can't be less than zero");
		return focalLength*baseline/disparity;
	}

	public static void scenePointsToPixels( SceneStructureMetric scene, int viewIdx,
											BoofLambdas.ProcessIndex2<Point3D_F64, Point2D_F64> function ) {
		Se3_F64 world_to_view = new Se3_F64();

		SceneStructureMetric.View view = scene.views.get(viewIdx);
		scene.getWorldToView(view, world_to_view, null);
		BundleAdjustmentCamera camera = Objects.requireNonNull(scene.getViewCamera(view).model);

		Point3D_F64 camPoint = new Point3D_F64();
		Point2D_F64 pixel = new Point2D_F64();

		for (int pointIdx = 0; pointIdx < scene.points.size; pointIdx++) {
			SceneStructureCommon.Point point = scene.points.get(pointIdx);
			double x = point.coordinate[0];
			double y = point.coordinate[1];
			double z = point.coordinate[2];
			double w = scene.isHomogenous() ? point.coordinate[3] : 1.0;

			SePointOps_F64.transformV(world_to_view, x, y, z, w, camPoint);
			camera.project(camPoint.x, camPoint.y, camPoint.z, pixel);
			function.process(pointIdx, camPoint, pixel);
		}
	}

	public static void sceneToCloud3( SceneStructureMetric scene, double tol,
									  BoofLambdas.ProcessIndex<Point3D_F64> func ) {

		Point3D_F64 out = new Point3D_F64();

		final boolean homogenous = scene.isHomogenous();

		for (int pointIdx = 0; pointIdx < scene.points.size; pointIdx++) {
			SceneStructureCommon.Point point = scene.points.get(pointIdx);
			double x = point.coordinate[0];
			double y = point.coordinate[1];
			double z = point.coordinate[2];

			if (homogenous) {
				double r = Math.sqrt(x*x + y*y + z*z);
				double w = point.coordinate[3];

				if (r*tol > Math.abs(w)) {
					continue;
				}

				x /= w;
				y /= w;
				z /= w;
			}

			out.setTo(x, y, z);
			func.process(pointIdx, out);
		}
	}

	public static void sceneToCloudH( SceneStructureMetric scene, BoofLambdas.ProcessIndex<Point4D_F64> func ) {

		Point4D_F64 out = new Point4D_F64();

		final boolean homogenous = scene.isHomogenous();

		for (int pointIdx = 0; pointIdx < scene.points.size; pointIdx++) {
			SceneStructureCommon.Point point = scene.points.get(pointIdx);
			double x = point.coordinate[0];
			double y = point.coordinate[1];
			double z = point.coordinate[2];
			double w = homogenous ? point.coordinate[3] : 1.0;
			out.setTo(x, y, z, w);
			func.process(pointIdx, out);
		}
	}

	public static double compatibleHomography( DMatrixRMaj F, DMatrixRMaj H ) {
		DMatrixRMaj tmp0 = new DMatrixRMaj(3, 3);
		DMatrixRMaj tmp1 = new DMatrixRMaj(3, 3);

		CommonOps_DDRM.multTransA(H, F, tmp0);
		CommonOps_DDRM.multTransA(F, H, tmp1);

		CommonOps_DDRM.add(tmp0, tmp1, tmp1);
		return NormOps_DDRM.normF(tmp1);
	}

	public static boolean homographyToFundamental( DMatrixRMaj H21, List<AssociatedPair> pairs, DMatrixRMaj F21 ) {
		List<LineGeneral2D_F64> epipolarLines = new ArrayList<>();

		Point2D_F64 h2 = new Point2D_F64();

		for (int i = 0; i < pairs.size(); i++) {
			AssociatedPair p = pairs.get(i);
			GeometryMath_F64.mult(H21, p.p1, h2);
			epipolarLines.add(UtilLine2D_F64.convert(p.p2, h2, (LineGeneral2D_F64)null));
		}

		Point3D_F64 epipole = Intersection2D_F64.intersection(epipolarLines, null);

		GeometryMath_F64.multCrossA(epipole, H21, F21);

		return true;
	}

	public static DMatrixRMaj homographyToCalibrated( DMatrixRMaj H21, DMatrixRMaj K1, DMatrixRMaj K2 ) {
		return null;
	}

	public static DMatrixRMaj homographyFromRotation( DMatrixRMaj R21, DMatrixRMaj K1, DMatrixRMaj K2,
													  @Nullable DMatrixRMaj H21 ) {
		if (H21 == null)
			H21 = new DMatrixRMaj(3, 3);

		DMatrixRMaj K1_inv = new DMatrixRMaj(3, 3);
		DMatrixRMaj KR = new DMatrixRMaj(3, 3);

		PerspectiveOps.invertCalibrationMatrix(K1, K1_inv);
		CommonOps_DDRM.mult(K2, R21, KR);
		CommonOps_DDRM.mult(KR, K1_inv, H21);

		return H21;
	}
}
