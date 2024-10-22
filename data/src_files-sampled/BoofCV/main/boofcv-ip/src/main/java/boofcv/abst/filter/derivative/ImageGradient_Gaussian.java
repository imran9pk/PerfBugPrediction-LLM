package boofcv.abst.filter.derivative;

import boofcv.alg.filter.convolve.GConvolveImageOps;
import boofcv.core.image.border.FactoryImageBorder;
import boofcv.factory.filter.kernel.FactoryKernelGaussian;
import boofcv.struct.border.BorderType;
import boofcv.struct.border.ImageBorder;
import boofcv.struct.convolve.Kernel1D;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;

import static boofcv.factory.filter.kernel.FactoryKernelGaussian.sigmaForRadius;


public class ImageGradient_Gaussian<I extends ImageGray<I>, D extends ImageGray<D>>
		implements ImageGradient<I, D> {

	private BorderType borderType = BorderType.EXTENDED;
	ImageBorder border;

	private I storage;

	private Class<D> derivType;

	Kernel1D kernelBlur;
	Kernel1D kernelDeriv;

	Class<I> imageType;

	public ImageGradient_Gaussian(int radius , Class<I> inputType , Class<D> derivType) {
		this(sigmaForRadius(radius,0),radius,inputType,derivType);
	}

	public ImageGradient_Gaussian(double sigma, int radius,
								  Class<I> inputType , Class<D> derivType ) {
		this.imageType = inputType;
		this.derivType = derivType;

		if( radius <= 0 )
			radius = FactoryKernelGaussian.radiusForSigma(sigma,1);
		else if( sigma <= 0 )
			sigma = FactoryKernelGaussian.sigmaForRadius(radius,1);

		kernelBlur = FactoryKernelGaussian.gaussian1D(inputType,sigma,radius);
		kernelDeriv = FactoryKernelGaussian.derivativeI(inputType,1,sigma,radius);
		border = FactoryImageBorder.single(borderType, derivType);
	}

	@SuppressWarnings({"unchecked"})
	@Override
	public void process( I inputImage , D derivX, D derivY ) {

		if( storage == null ) {
			storage = (I)inputImage.createNew(inputImage.width,inputImage.height );
		} else {
			storage.reshape(inputImage.width,inputImage.height);
		}

		GConvolveImageOps.verticalNormalized(kernelBlur,inputImage,storage);
		GConvolveImageOps.horizontal(kernelDeriv,storage,derivX,border );
		GConvolveImageOps.horizontalNormalized(kernelBlur,inputImage,storage);
		GConvolveImageOps.vertical(kernelDeriv,storage,derivY,border );
	}

	@Override
	public void setBorderType(BorderType type) {
		this.borderType = type;
		border = FactoryImageBorder.single(borderType, derivType);
	}

	@Override
	public BorderType getBorderType() {
		return borderType;
	}

	@Override
	public int getBorder() {
		return 0;
	}

	@Override
	public ImageType<I> getInputType() {
		return ImageType.single(imageType);
	}

	@Override
	public ImageType<D> getDerivativeType() {
		return ImageType.single(derivType);
	}
}
