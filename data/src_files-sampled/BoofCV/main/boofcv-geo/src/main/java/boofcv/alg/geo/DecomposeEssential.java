package boofcv.alg.geo;

import georegression.struct.point.Vector3D_F64;
import georegression.struct.se.Se3_F64;
import lombok.Getter;
import org.ejml.data.DMatrixRMaj;
import org.ejml.dense.row.CommonOps_DDRM;
import org.ejml.dense.row.SingularOps_DDRM;
import org.ejml.dense.row.factory.DecompositionFactory_DDRM;
import org.ejml.interfaces.decomposition.SingularValueDecomposition;

import java.util.ArrayList;
import java.util.List;

public class DecomposeEssential {

	private final SingularValueDecomposition<DMatrixRMaj> svd = DecompositionFactory_DDRM.svd(3, 3, true, true, false);

	DMatrixRMaj U, S, V;

	List<Se3_F64> solutions = new ArrayList<>();

	DMatrixRMaj E_copy = new DMatrixRMaj(3, 3);

	DMatrixRMaj temp = new DMatrixRMaj(3, 3);
	DMatrixRMaj W = new DMatrixRMaj(3, 3);

	@Getter double translationLength;

	public DecomposeEssential() {
		solutions.add(new Se3_F64());
		solutions.add(new Se3_F64());
		solutions.add(new Se3_F64());
		solutions.add(new Se3_F64());

		W.set(0, 1, -1);
		W.set(1, 0, 1);
		W.set(2, 2, 1);
	}

	public void decompose( DMatrixRMaj E ) {
		if (svd.inputModified()) {
			E_copy.setTo(E);
			E = E_copy;
		}

		if (!svd.decompose(E))
			throw new RuntimeException("Svd some how failed");

		U = svd.getU(U, false);
		V = svd.getV(V, false);
		S = svd.getW(S);

		SingularOps_DDRM.descendingOrder(U, false, S, V, false);

		translationLength = Math.abs(S.get(0, 0) + S.get(1, 1))/2;

		decompose(U, V);
	}

	public void decompose( DMatrixRMaj U, DMatrixRMaj V ) {
		if (CommonOps_DDRM.det(U) < 0) {
			CommonOps_DDRM.scale(-1, U);
		}

		if (CommonOps_DDRM.det(V) < 0) {
			CommonOps_DDRM.scale(-1, V);
		}

		extractTransform(U, V, solutions.get(0), true, true);
		extractTransform(U, V, solutions.get(1), true, false);
		extractTransform(U, V, solutions.get(2), false, false);
		extractTransform(U, V, solutions.get(3), false, true);
	}

	public List<Se3_F64> getSolutions() {
		return solutions;
	}

	private void extractTransform( DMatrixRMaj U, DMatrixRMaj V,
								   Se3_F64 se, boolean optionA, boolean optionB ) {
		DMatrixRMaj R = se.getR();
		Vector3D_F64 T = se.getT();

		if (optionA)
			CommonOps_DDRM.multTransB(U, W, temp);
		else
			CommonOps_DDRM.mult(U, W, temp);

		CommonOps_DDRM.multTransB(temp, V, R);

		T.x = U.get(0, 2);
		T.y = U.get(1, 2);
		T.z = U.get(2, 2);

		if (optionB)
			T.scale(-1);
	}
}
