package boofcv.alg.geo.h;

import boofcv.alg.geo.LowLevelMultiViewOps;
import boofcv.alg.geo.NormalizationPoint2D;
import boofcv.struct.geo.AssociatedPair;
import org.ejml.data.DMatrix2x2;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.fixed.CommonOps_DDF2;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.linsol.svd.SolveNullSpaceSvd_DDRM;
import org.ejml.interfaces.SolveNullSpace;

import java.util.List;

public class HomographyTotalLeastSquares {
	SolveNullSpace<DMatrixRMaj> solverNull = new SolveNullSpaceSvd_DDRM();
	DMatrixRMaj nullspace = new DMatrixRMaj(3,1);

	protected NormalizationPoint2D N1 = new NormalizationPoint2D();
	protected NormalizationPoint2D N2 = new NormalizationPoint2D();

	DMatrixRMaj X1 = new DMatrixRMaj(1,1);
	DMatrixRMaj X2 = new DMatrixRMaj(1,1);

	DMatrixRMaj A = new DMatrixRMaj(1,1);

	private DMatrixRMaj P_plus = new DMatrixRMaj(1,1);
	private double XP_bar[] = new double[4];

	public boolean process(List<AssociatedPair> points , DMatrixRMaj foundH ) {
		if (points.size() < 4)
			throw new IllegalArgumentException("Must be at least 4 points.");

		LowLevelMultiViewOps.computeNormalization(points, N1, N2);
		LowLevelMultiViewOps.applyNormalization(points,N1,N2,X1,X2);

		constructA678();

		if( !solverNull.process(A,1,nullspace))
			return false;

		DMatrixRMaj H = foundH;
		H.data[6] = nullspace.data[0];
		H.data[7] = nullspace.data[1];
		H.data[8] = nullspace.data[2];

		H.data[2] = -(XP_bar[0]*H.data[6] + XP_bar[1]*H.data[7]);
		H.data[5] = -(XP_bar[2]*H.data[6] + XP_bar[3]*H.data[7]);

		backsubstitution0134(P_plus,X1,X2,H.data);

		HomographyDirectLinearTransform.undoNormalizationH(foundH,N1,N2);

		CommonOps_DDRM.scale(1.0/foundH.get(2,2),foundH);

		return true;
	}

	static void backsubstitution0134(DMatrixRMaj P_plus, DMatrixRMaj P , DMatrixRMaj X ,
									 double H[] ) {
		final int N = P.numRows;
		DMatrixRMaj tmp = new DMatrixRMaj(N*2, 1);

		double H6 = H[6];
		double H7 = H[7];
		double H8 = H[8];

		for (int i = 0, index = 0; i < N; i++) {
			double x = -X.data[index],y = -X.data[index+1];
			double sum = P.data[index++] * H6 + P.data[index++] * H7 + H8;
			tmp.data[i] = x * sum;
			tmp.data[i+N] = y * sum;
		}

		double h0=0,h1=0,h3=0,h4=0;
		for (int i = 0; i < N; i++) {
			double P_pls_0 = P_plus.data[i];
			double P_pls_1 = P_plus.data[i+N];

			double tmp_i = tmp.data[i];
			double tmp_j = tmp.data[i+N];

			h0 += P_pls_0*tmp_i;
			h1 += P_pls_1*tmp_i;
			h3 += P_pls_0*tmp_j;
			h4 += P_pls_1*tmp_j;
		}

		H[0] = -h0;
		H[1] = -h1;
		H[3] = -h3;
		H[4] = -h4;
	}

	void constructA678() {
		final int N = X1.numRows;

		computePseudo(X1,P_plus);

		DMatrixRMaj PPpXP = new DMatrixRMaj(1,1);
		DMatrixRMaj PPpYP = new DMatrixRMaj(1,1);
		computePPXP(X1,P_plus,X2,0,PPpXP);
		computePPXP(X1,P_plus,X2,1,PPpYP);

		DMatrixRMaj PPpX = new DMatrixRMaj(1,1);
		DMatrixRMaj PPpY = new DMatrixRMaj(1,1);
		computePPpX(X1,P_plus,X2,0,PPpX);
		computePPpX(X1,P_plus,X2,1,PPpY);

		computeEq20(X2,X1,XP_bar);

		A.reshape(N *2,3);
		double XP_bar_x = XP_bar[0];
		double XP_bar_y = XP_bar[1];
		double YP_bar_x = XP_bar[2];
		double YP_bar_y = XP_bar[3];

		for (int i = 0, index = 0, indexA = 0; i < N; i++, index+=2) {
			double x = -X2.data[i*2];        double P_hat_x = X1.data[index];   double P_hat_y = X1.data[index+1]; A.data[indexA++] = x*P_hat_x - XP_bar_x - PPpXP.data[index];
			A.data[indexA++] = x*P_hat_y - XP_bar_y - PPpXP.data[index+1];

			A.data[indexA++] = x - PPpX.data[i];
		}
		for (int i = 0, index = 0, indexA = N*3; i < N; i++, index+=2) {
			double x = -X2.data[i*2+1];
			double P_hat_x = X1.data[index];
			double P_hat_y = X1.data[index+1];

			A.data[indexA++] = x*P_hat_x - YP_bar_x - PPpYP.data[index];
			A.data[indexA++] = x*P_hat_y - YP_bar_y - PPpYP.data[index+1];

			A.data[indexA++] = x - PPpY.data[i];
		}
	}

	static void computeEq20( DMatrixRMaj X , DMatrixRMaj P , double output[]) {
		final int N = X.numRows;

		double a00=0,a01=0,a10=0,a11=0;
		for (int i = 0, index = 0; i < N; i++,index+=2) {
			double x1 = X.data[index];
			double x2 = X.data[index+1];
			double p1 = P.data[index];
			double p2 = P.data[index+1];

			a00 += x1*p1;
			a01 += x1*p2;
			a10 += x2*p1;
			a11 += x2*p2;
		}
		output[0] = -a00/N;
		output[1] = -a01/N;
		output[2] = -a10/N;
		output[3] = -a11/N;
	}

	static void computePseudo(DMatrixRMaj A , DMatrixRMaj output ) {
		final int N = A.numRows;

		DMatrix2x2 m = new DMatrix2x2();
		DMatrix2x2 m_inv = new DMatrix2x2();

		for (int i = 0, index=0; i < N; i++) {
			double a_i1 = A.data[index++];
			double a_i2 = A.data[index++];
			m.a11 += a_i1*a_i1;
			m.a12 += a_i1*a_i2;
			m.a22 += a_i2*a_i2;
		}
		m.a21 = m.a12;
		CommonOps_DDF2.invert(m,m_inv);

		output.reshape(2,N);
		for (int i = 0,index=0; i < N; i++) {
			output.data[i] = A.data[index++]*m_inv.a11 + A.data[index++]*m_inv.a12;
		}
		int end = 2*N;
		for (int i = N,A_index=0; i < end; i++) {
			output.data[i] = A.data[A_index++]*m_inv.a21 + A.data[A_index++]*m_inv.a22;
		}
	}

	static void computePPXP( DMatrixRMaj P , DMatrixRMaj P_plus, DMatrixRMaj X , int offsetX,  DMatrixRMaj output ) {
		final int N = P.numRows;
		output.reshape(N,2);

		for (int i = 0, index=0; i < N; i++, index += 2) {
			double x = -X.data[index+offsetX];
			output.data[index] = x*P.data[index];
			output.data[index+1] = x*P.data[index+1];
		}

		double a00=0,a01=0,a10=0,a11=0;
		for (int i = 0, index=0; i < N; i++, index += 2) {
			a00 += P_plus.data[i]*output.data[index];
			a01 += P_plus.data[i]*output.data[index+1];
			a10 += P_plus.data[i+N]*output.data[index];
			a11 += P_plus.data[i+N]*output.data[index+1];
		}

		for (int i = 0, index=0; i < N; i++, index += 2) {
			output.data[index]   = P.data[index]*a00 + P.data[index+1]*a10;
			output.data[index+1] = P.data[index]*a01+ P.data[index+1]*a11;
		}
	}

	static void computePPpX( DMatrixRMaj P , DMatrixRMaj P_plus, DMatrixRMaj X , int offsetX,  DMatrixRMaj output ) {
		final int N = P.numRows;
		output.reshape(N,1);

		double a00=0,a10=0;
		for (int i = 0,indexX=offsetX; i < N; i++, indexX += 2) {
			double x = -X.data[indexX];
			a00 += x*P_plus.data[i];
			a10 += x*P_plus.data[i+N];
		}

		for (int i = 0,indexP=0; i < N; i++) {
			output.data[i] = a00*P.data[indexP++] + a10*P.data[indexP++];
		}

	}
}
