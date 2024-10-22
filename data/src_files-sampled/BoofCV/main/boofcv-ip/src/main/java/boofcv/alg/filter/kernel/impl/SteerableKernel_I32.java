package boofcv.alg.filter.kernel.impl;

import boofcv.alg.filter.kernel.KernelMath;
import boofcv.alg.filter.kernel.SteerableCoefficients;
import boofcv.alg.filter.kernel.SteerableKernel;
import boofcv.struct.convolve.Kernel2D;
import boofcv.struct.convolve.Kernel2D_S32;

public class SteerableKernel_I32 implements SteerableKernel<Kernel2D_S32> {

	private Kernel2D_S32 output;

	private SteerableCoefficients coef;
	private Kernel2D[] basis;

	@Override
	public void setBasis( SteerableCoefficients coef,
						  Kernel2D... basis ) {
		this.coef = coef;
		this.basis = basis;

		int width = basis[0].width;
		output = new Kernel2D_S32(width);
	}

	@Override
	public Kernel2D_S32 compute( double angle ) {
		KernelMath.fill(output, 0);

		int N = output.width*output.width;

		for (int i = 0; i < basis.length; i++) {
			double c = coef.compute(angle, i);

			Kernel2D_S32 k = (Kernel2D_S32)basis[i];

			for (int j = 0; j < N; j++) {
				output.data[j] += (int)(k.data[j]*c);
			}
		}

		return output;
	}

	@Override
	public int getBasisSize() {
		return basis.length;
	}

	@Override
	public Kernel2D_S32 getBasis( int index ) {
		return (Kernel2D_S32)basis[index];
	}
}
