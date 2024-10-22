package boofcv.alg.tracker.circulant;

import boofcv.abst.feature.detect.peak.SearchLocalPeak;
import boofcv.abst.transform.fft.DiscreteFourierTransform;
import boofcv.alg.interpolate.InterpolatePixelS;
import boofcv.alg.misc.PixelMath;
import boofcv.alg.transform.fft.DiscreteFourierTransformOps;
import boofcv.factory.feature.detect.peak.ConfigMeanShiftSearch;
import boofcv.factory.feature.detect.peak.FactorySearchLocalPeak;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayF64;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.InterleavedF64;
import georegression.struct.shapes.RectangleLength2D_F32;

import java.util.Random;

public class CirculantTracker<T extends ImageGray<T>> {

	private final double output_sigma_factor;

	private final double sigma;

	private final double lambda;
	private final double interp_factor;

	private final double maxPixelValue;

	private final double padding;

	private int imageWidth, imageHeight;

	private final DiscreteFourierTransform<GrayF64, InterleavedF64> fft = DiscreteFourierTransformOps.createTransformF64();

	protected GrayF64 templateNew = new GrayF64(1, 1);
	protected GrayF64 template = new GrayF64(1, 1);

	protected GrayF64 cosine = new GrayF64(1, 1);

	private final GrayF64 k = new GrayF64(1, 1);
	private final InterleavedF64 kf = new InterleavedF64(1, 1, 2);

	private final InterleavedF64 alphaf = new InterleavedF64(1, 1, 2);
	private final InterleavedF64 newAlphaf = new InterleavedF64(1, 1, 2);

	protected RectangleLength2D_F32 regionTrack = new RectangleLength2D_F32();
	protected RectangleLength2D_F32 regionOut = new RectangleLength2D_F32();

	protected GrayF64 gaussianWeight = new GrayF64(1, 1);
	protected InterleavedF64 gaussianWeightDFT = new InterleavedF64(1, 1, 2);

	private final GrayF64 response = new GrayF64(1, 1);

	private final GrayF64 tmpReal0 = new GrayF64(1, 1);
	private final GrayF64 tmpReal1 = new GrayF64(1, 1);

	private final InterleavedF64 tmpFourier0 = new InterleavedF64(1, 1, 2);
	private final InterleavedF64 tmpFourier1 = new InterleavedF64(1, 1, 2);
	private final InterleavedF64 tmpFourier2 = new InterleavedF64(1, 1, 2);

	private final InterpolatePixelS<T> interp;

	private final SearchLocalPeak<GrayF64> localPeak =
			FactorySearchLocalPeak.meanShiftUniform(new ConfigMeanShiftSearch(5, 1e-4), GrayF64.class);

	protected float offX, offY;

	private final int workRegionSize;
	private float stepX, stepY;

	private final Random rand = new Random(234);

	public CirculantTracker( double output_sigma_factor, double sigma, double lambda, double interp_factor,
							 double padding,
							 int workRegionSize,
							 double maxPixelValue,
							 InterpolatePixelS<T> interp ) {
		if (workRegionSize < 3)
			throw new IllegalArgumentException("Minimum size of work region is 3 pixels.");

		this.output_sigma_factor = output_sigma_factor;
		this.sigma = sigma;
		this.lambda = lambda;
		this.interp_factor = interp_factor;
		this.maxPixelValue = maxPixelValue;
		this.interp = interp;

		this.padding = padding;
		this.workRegionSize = workRegionSize;

		resizeImages(workRegionSize);
		computeCosineWindow(cosine);
		computeGaussianWeights(workRegionSize);

		localPeak.setImage(response);
	}

	public void initialize( T image, int x0, int y0, int regionWidth, int regionHeight ) {

		this.imageWidth = image.width;
		this.imageHeight = image.height;

		setTrackLocation(x0, y0, regionWidth, regionHeight);

		initialLearning(image);
	}

	public void setTrackLocation( int x0, int y0, int regionWidth, int regionHeight ) {
		if (imageWidth < regionWidth || imageHeight < regionHeight)
			throw new IllegalArgumentException("Track region is larger than input image: " + regionWidth + " " + regionHeight);

		regionOut.width = regionWidth;
		regionOut.height = regionHeight;

		int w = (int)(regionWidth*(1 + padding));
		int h = (int)(regionHeight*(1 + padding));
		int cx = x0 + regionWidth/2;
		int cy = y0 + regionHeight/2;

		this.regionTrack.width = w;
		this.regionTrack.height = h;
		this.regionTrack.x0 = cx - w/2;
		this.regionTrack.y0 = cy - h/2;

		stepX = (w - 1)/(float)(workRegionSize - 1);
		stepY = (h - 1)/(float)(workRegionSize - 1);

		updateRegionOut();
	}

	protected void initialLearning( T image ) {
		get_subwindow(image, template);

		dense_gauss_kernel(sigma, template, template, k);
		fft.forward(k, kf);

		computeAlphas(gaussianWeightDFT, kf, lambda, alphaf);
	}

	protected static void computeCosineWindow( GrayF64 cosine ) {
		double[] cosX = new double[cosine.width];
		for (int x = 0; x < cosine.width; x++) {
			cosX[x] = 0.5*(1 - Math.cos(2.0*Math.PI*x/(cosine.width - 1)));
		}
		for (int y = 0; y < cosine.height; y++) {
			int index = cosine.startIndex + y*cosine.stride;
			double cosY = 0.5*(1 - Math.cos(2.0*Math.PI*y/(cosine.height - 1)));
			for (int x = 0; x < cosine.width; x++) {
				cosine.data[index++] = cosX[x]*cosY;
			}
		}
	}

	protected void computeGaussianWeights( int width ) {
		double output_sigma = Math.sqrt(width*width)*output_sigma_factor;

		double left = -0.5/(output_sigma*output_sigma);

		int radius = width/2;

		for (int y = 0; y < gaussianWeight.height; y++) {
			int index = gaussianWeight.startIndex + y*gaussianWeight.stride;

			double ry = y - radius;

			for (int x = 0; x < width; x++) {
				double rx = x - radius;

				gaussianWeight.data[index++] = Math.exp(left*(ry*ry + rx*rx));
			}
		}

		fft.forward(gaussianWeight, gaussianWeightDFT);
	}

	protected void resizeImages( int workRegionSize ) {
		templateNew.reshape(workRegionSize, workRegionSize);
		template.reshape(workRegionSize, workRegionSize);
		cosine.reshape(workRegionSize, workRegionSize);
		k.reshape(workRegionSize, workRegionSize);
		kf.reshape(workRegionSize, workRegionSize);
		alphaf.reshape(workRegionSize, workRegionSize);
		newAlphaf.reshape(workRegionSize, workRegionSize);
		response.reshape(workRegionSize, workRegionSize);
		tmpReal0.reshape(workRegionSize, workRegionSize);
		tmpReal1.reshape(workRegionSize, workRegionSize);
		tmpFourier0.reshape(workRegionSize, workRegionSize);
		tmpFourier1.reshape(workRegionSize, workRegionSize);
		tmpFourier2.reshape(workRegionSize, workRegionSize);
		gaussianWeight.reshape(workRegionSize, workRegionSize);
		gaussianWeightDFT.reshape(workRegionSize, workRegionSize);
	}

	public void performTracking( T image ) {
		if (image.width != imageWidth || image.height != imageHeight)
			throw new IllegalArgumentException("Tracking image size is not the same as " +
					"input image. Expected " + imageWidth + " x " + imageHeight);
		updateTrackLocation(image);
		if (interp_factor != 0)
			performLearning(image);
	}

	protected void updateTrackLocation( T image ) {
		get_subwindow(image, templateNew);

		dense_gauss_kernel(sigma, templateNew, template, k);

		fft.forward(k, kf);

		DiscreteFourierTransformOps.multiplyComplex(alphaf, kf, tmpFourier0);
		fft.inverse(tmpFourier0, response);

		int N = response.width*response.height;
		int indexBest = -1;
		double valueBest = -1;
		for (int i = 0; i < N; i++) {
			double v = response.data[i];
			if (v > valueBest) {
				valueBest = v;
				indexBest = i;
			}
		}

		int peakX = indexBest%response.width;
		int peakY = indexBest/response.width;

		subpixelPeak(peakX, peakY);

		float deltaX = (peakX + offX) - templateNew.width/2;
		float deltaY = (peakY + offY) - templateNew.height/2;

		regionTrack.x0 = regionTrack.x0 + deltaX*stepX;
		regionTrack.y0 = regionTrack.y0 + deltaY*stepY;

		updateRegionOut();
	}

	protected void subpixelPeak( int peakX, int peakY ) {
		int r = Math.min(2, response.width/25);
		if (r < 0)
			return;

		localPeak.setSearchRadius(r);
		localPeak.search(peakX, peakY);

		offX = localPeak.getPeakX() - peakX;
		offY = localPeak.getPeakY() - peakY;
	}

	private void updateRegionOut() {
		regionOut.x0 = (regionTrack.x0 + ((int)regionTrack.width)/2) - ((int)regionOut.width)/2;
		regionOut.y0 = (regionTrack.y0 + ((int)regionTrack.height)/2) - ((int)regionOut.height)/2;
	}

	public void performLearning( T image ) {
		get_subwindow(image, templateNew);

		dense_gauss_kernel(sigma, templateNew, templateNew, k);
		fft.forward(k, kf);

		computeAlphas(gaussianWeightDFT, kf, lambda, newAlphaf);

		int N = alphaf.width*alphaf.height*2;
		for (int i = 0; i < N; i++) {
			alphaf.data[i] = (1 - interp_factor)*alphaf.data[i] + interp_factor*newAlphaf.data[i];
		}

		N = templateNew.width*templateNew.height;
		for (int i = 0; i < N; i++) {
			template.data[i] = (1 - interp_factor)*template.data[i] + interp_factor*templateNew.data[i];
		}
	}

	public void dense_gauss_kernel( double sigma, GrayF64 x, GrayF64 y, GrayF64 k ) {

		InterleavedF64 xf = tmpFourier0, yf, xyf = tmpFourier2;
		GrayF64 xy = tmpReal0;
		double yy;

		fft.forward(x, xf);
		double xx = imageDotProduct(x);

		if (x != y) {
			yf = tmpFourier1;
			fft.forward(y, yf);
			yy = imageDotProduct(y);
		} else {
			yf = xf;
			yy = xx;
		}

		elementMultConjB(xf, yf, xyf);
		fft.inverse(xyf, xy);
		circshift(xy, tmpReal1);

		gaussianKernel(xx, yy, tmpReal1, sigma, k);
	}

	public static void circshift( GrayF64 a, GrayF64 b ) {
		int w2 = a.width/2;
		int h2 = b.height/2;

		for (int y = 0; y < a.height; y++) {
			int yy = (y + h2)%a.height;

			for (int x = 0; x < a.width; x++) {
				int xx = (x + w2)%a.width;

				b.set(xx, yy, a.get(x, y));
			}
		}
	}

	public static double imageDotProduct( GrayF64 a ) {

		double total = 0;

		int N = a.width*a.height;
		for (int index = 0; index < N; index++) {
			double value = a.data[index];
			total += value*value;
		}

		return total;
	}

	public static void elementMultConjB( InterleavedF64 a, InterleavedF64 b, InterleavedF64 output ) {
		for (int y = 0; y < a.height; y++) {

			int index = a.startIndex + y*a.stride;

			for (int x = 0; x < a.width; x++, index += 2) {

				double realA = a.data[index];
				double imgA = a.data[index + 1];
				double realB = b.data[index];
				double imgB = b.data[index + 1];

				output.data[index] = realA*realB + imgA*imgB;
				output.data[index + 1] = -realA*imgB + imgA*realB;
			}
		}
	}

	protected static void computeAlphas( InterleavedF64 yf, InterleavedF64 kf, double lambda,
										 InterleavedF64 alphaf ) {

		for (int y = 0; y < kf.height; y++) {

			int index = yf.startIndex + y*yf.stride;

			for (int x = 0; x < kf.width; x++, index += 2) {
				double a = yf.data[index];
				double b = yf.data[index + 1];

				double c = kf.data[index] + lambda;
				double d = kf.data[index + 1];

				double bottom = c*c + d*d;

				alphaf.data[index] = (a*c + b*d)/bottom;
				alphaf.data[index + 1] = (b*c - a*d)/bottom;
			}
		}
	}

	protected static void gaussianKernel( double xx, double yy, GrayF64 xy, double sigma, GrayF64 output ) {
		double sigma2 = sigma*sigma;
		double N = xy.width*xy.height;

		for (int y = 0; y < xy.height; y++) {
			int index = xy.startIndex + y*xy.stride;

			for (int x = 0; x < xy.width; x++, index++) {

				double value = (xx + yy - 2*xy.data[index])/N;

				double v = Math.exp(-Math.max(0, value)/sigma2);

				output.data[index] = v;
			}
		}
	}

	protected void get_subwindow( T image, GrayF64 output ) {

		interp.setImage(image);
		int index = 0;
		for (int y = 0; y < workRegionSize; y++) {
			float yy = regionTrack.y0 + y*stepY;

			for (int x = 0; x < workRegionSize; x++) {
				float xx = regionTrack.x0 + x*stepX;

				if (interp.isInFastBounds(xx, yy))
					output.data[index++] = interp.get_fast(xx, yy);
				else if (BoofMiscOps.isInside(image, xx, yy))
					output.data[index++] = interp.get(xx, yy);
				else {
					output.data[index++] = rand.nextFloat()*maxPixelValue;
				}
			}
		}

		PixelMath.divide(output, maxPixelValue, output);
		PixelMath.plus(output, -0.5f, output);
		PixelMath.multiply(output, cosine, output);
	}

	public RectangleLength2D_F32 getTargetLocation() {
		return regionOut;
	}

	public GrayF64 getTargetTemplate() {
		return template;
	}

	public GrayF64 getResponse() {
		return response;
	}
}
