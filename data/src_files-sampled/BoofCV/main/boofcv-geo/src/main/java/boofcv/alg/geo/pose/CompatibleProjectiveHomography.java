package boofcv.alg.geo.pose;

import boofcv.alg.geo.PerspectiveOps;
import boofcv.misc.ConfigConverge;
import georegression.geometry.GeometryMath_F64;
import georegression.struct.point.Point2D_F64;
import georegression.struct.point.Point3D_F64;
import georegression.struct.point.Point4D_F64;
import org.ddogleg.optimization.FactoryOptimization;
import org.ddogleg.optimization.UnconstrainedLeastSquares;
import org.ddogleg.optimization.UtilOptimize;
import org.ddogleg.optimization.functions.FunctionNtoM;
import org.ddogleg.optimization.lm.ConfigLevenbergMarquardt;
import org.ddogleg.struct.DogArray;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.NormOps_DDRM;
import org.ejml.dense.row.RandomMatrices_DDRM;
import org.ejml.dense.row.factory.LinearSolverFactory_DDRM;
import org.ejml.dense.row.linsol.svd.SolveNullSpaceSvd_DDRM;
import org.ejml.dense.row.linsol.svd.SolvePseudoInverseSvd_DDRM;
import org.ejml.interfaces.SolveNullSpace;
import org.ejml.interfaces.linsol.LinearSolverDense;

import java.util.List;
import java.util.Random;

public class CompatibleProjectiveHomography {

	public LinearSolverDense<DMatrixRMaj> solver = LinearSolverFactory_DDRM.pseudoInverse(true);
	public SolveNullSpace<DMatrixRMaj> nullspace = new SolveNullSpaceSvd_DDRM();
	public SolvePseudoInverseSvd_DDRM solvePInv = new SolvePseudoInverseSvd_DDRM();
	DMatrixRMaj A = new DMatrixRMaj(1,1);
	DMatrixRMaj B = new DMatrixRMaj(1,1);

	private final Point4D_F64 a = new Point4D_F64();

	private final DogArray<DMatrixRMaj> copyA = new DogArray<>(()->new DMatrixRMaj(3,4));
	private final DogArray<DMatrixRMaj> copyB = new DogArray<>(()->new DMatrixRMaj(3,4));

	private final DMatrixRMaj RM = RandomMatrices_DDRM.orthogonal(4,4,new Random(3245));
	private final DMatrixRMaj tmp4x4 = new DMatrixRMaj(4,4);

	private final DMatrixRMaj PinvP = new DMatrixRMaj(4,4);
	private final DMatrixRMaj h = new DMatrixRMaj(1,1);

	private DistanceWorldEuclidean distanceWorld = new DistanceWorldEuclidean();
	private DistanceReprojection distanceRepojection = new DistanceReprojection();

	public UnconstrainedLeastSquares<DMatrixRMaj> lm;

	public final ConfigConverge configConverge = new ConfigConverge(1e-8,1e-8,500);

	public CompatibleProjectiveHomography() {
		ConfigLevenbergMarquardt config = new ConfigLevenbergMarquardt();
		lm = FactoryOptimization.levenbergMarquardt(config,false);
	}

	public boolean fitPoints(List<Point4D_F64> points1 , List<Point4D_F64> points2 , DMatrixRMaj H )
	{
		if( points1.size() != points2.size() )
			throw new IllegalArgumentException("Must have the same number in each list");
		if( points1.size() < 5 )
			throw new IllegalArgumentException("At least 5 points required");

		final int size = points1.size();

		A.reshape(size*3,16);

		for (int i = 0; i < size; i++) {
			Point4D_F64 a = points1.get(i);
			Point4D_F64 b = points2.get(i);

			double alpha = -(a.x + a.y + a.z + a.w);

			for (int j = 0; j < 3; j++) {
				int idx = 16*(3*i+j);
				double va = a.getIdx(j);
				for (int k = 0; k < 4; k++) {
					A.data[idx++] = va*b.x;
					A.data[idx++] = va*b.y;
					A.data[idx++] = va*b.z;
					A.data[idx++] = va*b.w;
				}
			}
			for (int j = 0; j < 3; j++) {
				int idx = 16*(3*i+j)+4*j;

				A.data[idx  ] += b.x*alpha;
				A.data[idx+1] += b.y*alpha;
				A.data[idx+2] += b.z*alpha;
				A.data[idx+3] += b.w*alpha;
			}
		}

		if( !nullspace.process(A,1,H) )
			return false;

		H.reshape(4,4);

		return true;
	}

	public boolean fitCameras(List<DMatrixRMaj> cameras1 , List<DMatrixRMaj> cameras2 , DMatrixRMaj H ) {
		if( cameras1.size() != cameras2.size() )
			throw new IllegalArgumentException("Must have the same number in each list");
		if( cameras1.size() < 2 )
			throw new IllegalArgumentException("At least two cameras are required");

		final int size = cameras1.size();

		copyA.reset();
		copyB.reset();
		for (int i = 0; i < size; i++) {
			CommonOps_DDRM.mult(cameras1.get(i),RM,copyA.grow());
			CommonOps_DDRM.mult(cameras2.get(i),RM,copyB.grow());
		}

		H.reshape(4,4);
		h.reshape(4,1);
		A.reshape(size*3,4);

		for (int col = 0; col < 4; col++) {
			for (int i = 0; i < size; i++) {
				DMatrixRMaj P1 = copyA.get(i);
				DMatrixRMaj P2 = copyB.get(i);

				cameraCrossProductMatrix(P1,P2,col,i*3);
			}

			if( !nullspace.process(A,1,h) )
				return false;

			CommonOps_DDRM.insert(h,H,0,col);
		}

		resolveColumnScale(copyA.toList(), copyB.toList(), H);

		CommonOps_DDRM.mult(RM,H,tmp4x4);
		CommonOps_DDRM.multTransB(tmp4x4,RM,H);

		return true;
	}

	private void resolveColumnScale(List<DMatrixRMaj> cameras1, List<DMatrixRMaj> cameras2, DMatrixRMaj H) {
		B.reshape(3,1);
		h.zero();

		int selected = -1;
		double smallestRatio = Double.MAX_VALUE;
		for (int cameraIdx = 0; cameraIdx < cameras1.size(); cameraIdx++) {
			CommonOps_DDRM.mult(cameras1.get(cameraIdx),H,A);

			double worstRatio = 0.0;
			for (int col = 0; col < 4; col++) {
				CommonOps_DDRM.extractColumn(A,col,B);

				double maxValueAbs = CommonOps_DDRM.elementMaxAbs(B);
				double minValueAbs = CommonOps_DDRM.elementMinAbs(B);

				double ratio = maxValueAbs!=0.0?minValueAbs/maxValueAbs:1.0;

				worstRatio = Math.max(worstRatio,ratio);
			}

			if( worstRatio < smallestRatio ) {
				smallestRatio = worstRatio;
				selected = cameraIdx;
			}
		}

		CommonOps_DDRM.mult(cameras1.get(selected),H,A);
		for (int col = 0; col < 4; col++) {
			CommonOps_DDRM.extractColumn(A,col,B);
			double found = NormOps_DDRM.normF(B);
			int indexMaxAbs = -1;
			double maxAbs = 0;
			for (int i = 0; i < 3; i++) {
				if( Math.abs(B.data[i]) > maxAbs ) {
					maxAbs = Math.abs(B.data[i]);
					indexMaxAbs = i;
				}
			}
			double foundSign = Math.signum(B.data[indexMaxAbs]);
			CommonOps_DDRM.extractColumn(cameras2.get(selected),col,B);
			double expected = NormOps_DDRM.normF(B);
			double scale = expected/found;
			if( foundSign != Math.signum(B.data[indexMaxAbs]))
				scale *= -1;

			for (int row = 0; row < 4; row++) {
				H.set(row,col, H.get(row,col)*scale);
			}
		}
	}

	void cameraCrossProductMatrix( DMatrixRMaj P1 , DMatrixRMaj P2, int column , int rowInA )
	{
		double b1 = P2.unsafe_get(0,column);
		double b2 = P2.unsafe_get(1,column);
		double b3 = P2.unsafe_get(2,column);

		double a11 = P1.unsafe_get(0,0), a12 = P1.unsafe_get(0,1), a13 = P1.unsafe_get(0,2), a14 = P1.unsafe_get(0,3);
		double a21 = P1.unsafe_get(1,0), a22 = P1.unsafe_get(1,1), a23 = P1.unsafe_get(1,2), a24 = P1.unsafe_get(1,3);
		double a31 = P1.unsafe_get(2,0), a32 = P1.unsafe_get(2,1), a33 = P1.unsafe_get(2,2), a34 = P1.unsafe_get(2,3);

		A.data[rowInA*4    ] = b2*a31 - b3*a21;
		A.data[rowInA*4 + 1] = b2*a32 - b3*a22;
		A.data[rowInA*4 + 2] = b2*a33 - b3*a23;
		A.data[rowInA*4 + 3] = b2*a34 - b3*a24;

		rowInA += 1;
		A.data[rowInA*4    ] = b3*a11 - b1*a31;
		A.data[rowInA*4 + 1] = b3*a12 - b1*a32;
		A.data[rowInA*4 + 2] = b3*a13 - b1*a33;
		A.data[rowInA*4 + 3] = b3*a14 - b1*a34;

		rowInA += 1;
		A.data[rowInA*4    ] = b1*a21 - b2*a11;
		A.data[rowInA*4 + 1] = b1*a22 - b2*a12;
		A.data[rowInA*4 + 2] = b1*a23 - b2*a13;
		A.data[rowInA*4 + 3] = b1*a24 - b2*a14;
	}

	public boolean fitCameraPoints(DMatrixRMaj camera1 , DMatrixRMaj camera2 ,
								   List<Point4D_F64> points1 , List<Point4D_F64> points2 ,
								   DMatrixRMaj H ) {
		if( points1.size() != points2.size() )
			throw new IllegalArgumentException("Lists must be the same size");
		if( points1.size() < 2 )
			throw new IllegalArgumentException("A minimum of two points are required");

		if( !solvePInv.setA(camera1) )
			return false;
		if( !nullspace.process(camera1,1,h) )
			return false;

		solvePInv.solve(camera2,PinvP);

		final int size = points1.size();
		A.reshape(size*3,4);
		B.reshape(size*3,1);

		for (int i = 0,idxA=0,idxB=0; i < size; i++) {
			Point4D_F64 p1 = points1.get(i);
			Point4D_F64 p2 = points2.get(i);

			GeometryMath_F64.mult(PinvP,p2,a);

double a_sum = a.x+a.y+a.z+a.w;
			double h_sum = h.data[0]+h.data[1]+h.data[2]+h.data[3];
			double x_sum = p1.x+p1.y+p1.z+p1.w;

			for (int k = 0; k < 3; k++) {
				double b_k = h.data[k]*x_sum - h_sum*p1.getIdx(k);
				double c_k = p1.getIdx(k)*a_sum - x_sum*a.getIdx(k);

				A.data[idxA++] = b_k*p2.x;
				A.data[idxA++] = b_k*p2.y;
				A.data[idxA++] = b_k*p2.z;
				A.data[idxA++] = b_k*p2.w;

				B.data[idxB++] = c_k;
			}
		}

if( !solver.setA(A) )
			return false;
		H.reshape(4,1);
		solver.solve(B,H);

		a.setTo(H.data[0],H.data[1],H.data[2],H.data[3]);
		H.reshape(4,4);
		for (int i = 0; i < 4; i++) {
			for (int j = 0; j < 4; j++) {
				H.data[i*4+j] = PinvP.get(i,j) + h.data[i]*a.getIdx(j);
			}
		}

		return true;
	}

	public void refineWorld(List<Point3D_F64> scene1 , List<Point3D_F64> scene2 , DMatrixRMaj H )
	{
		if( H.numCols != 4 || H.numRows != 4 )
			throw new IllegalArgumentException("Expected 4x4 matrix for H");

		distanceWorld.scene1 = scene1;
		distanceWorld.scene2 = scene2;

		lm.setFunction(distanceWorld,null);
		lm.initialize(H.data,1e-8,1e-8);

		UtilOptimize.process(lm,configConverge.maxIterations);

		System.arraycopy(lm.getParameters(),0,H.data,0,16);

		distanceWorld.scene1 = null;
		distanceWorld.scene2 = null;
	}

	public void refineReprojection(List<DMatrixRMaj> cameras1, List<Point4D_F64> scene1 ,
									List<Point4D_F64> scene2 , DMatrixRMaj H )
	{
		if( H.numCols != 4 || H.numRows != 4 )
			throw new IllegalArgumentException("Expected 4x4 matrix for H");
		if( scene1.size() != scene2.size() || scene1.size() <= 0 )
			throw new IllegalArgumentException("Lists must have equal size and be not empty");
		if( cameras1.isEmpty() )
			throw new IllegalArgumentException("Camera must not be empty");


		distanceRepojection.cameras1 = cameras1;
		distanceRepojection.scene1 = scene1;
		distanceRepojection.scene2 = scene2;

		lm.setFunction(distanceRepojection,null);
		lm.initialize(H.data,configConverge.ftol,configConverge.gtol);

		UtilOptimize.process(lm,configConverge.maxIterations);

		System.arraycopy(lm.getParameters(),0,H.data,0,16);

		distanceRepojection.cameras1 = null;
		distanceRepojection.scene1 = null;
		distanceRepojection.scene2 = null;
	}

	private static class DistanceWorldEuclidean implements FunctionNtoM {

		List<Point3D_F64> scene1;
		List<Point3D_F64> scene2;

		Point3D_F64 ba = new Point3D_F64();

		DMatrixRMaj H = new DMatrixRMaj(4,4);

		@Override
		public void process(double[] input, double[] output) {
			H.data = input;

			for (int i = 0, idx=0; i < scene1.size(); i++) {
				Point3D_F64 a = scene1.get(i);
				Point3D_F64 b = scene2.get(i);

				GeometryMath_F64.mult4(H,b,ba);

				output[idx++] = a.x-ba.x;
				output[idx++] = a.y-ba.y;
				output[idx++] = a.z-ba.z;
			}
		}

		@Override
		public int getNumOfInputsN() {
			return 16; }

		@Override
		public int getNumOfOutputsM() {
			return scene1.size()*3;
		}
	}

	private static class DistanceReprojection implements FunctionNtoM {

		List<DMatrixRMaj> cameras1;

		List<Point4D_F64> scene1;
		List<Point4D_F64> scene2;

		Point4D_F64 ba = new Point4D_F64();

		DMatrixRMaj H = new DMatrixRMaj(4,4);

		Point2D_F64 pixel1 = new Point2D_F64();
		Point2D_F64 pixel2 = new Point2D_F64();

		@Override
		public void process(double[] input, double[] output) {
			H.data = input;

			int outputIdx = 0;
			for (int viewIdx = 0; viewIdx < cameras1.size(); viewIdx++) {
				DMatrixRMaj P = cameras1.get(viewIdx);

				for (int pointIdx = 0; pointIdx < scene1.size(); pointIdx++) {
					Point4D_F64 a = scene1.get(pointIdx);
					Point4D_F64 b = scene2.get(pointIdx);

					GeometryMath_F64.mult(H,b,ba);
					PerspectiveOps.renderPixel(P,a,pixel1); PerspectiveOps.renderPixel(P,ba,pixel2);

					output[outputIdx++] = pixel1.x - pixel2.x;
					output[outputIdx++] = pixel1.y - pixel2.y;
				}
			}
		}

		@Override
		public int getNumOfInputsN() {
			return 16; }

		@Override
		public int getNumOfOutputsM() {
			return cameras1.size()*scene1.size()*2;
		}
	}
}
