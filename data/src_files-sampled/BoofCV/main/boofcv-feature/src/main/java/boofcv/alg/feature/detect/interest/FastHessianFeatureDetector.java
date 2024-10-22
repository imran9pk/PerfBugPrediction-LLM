package boofcv.alg.feature.detect.interest;

import boofcv.abst.feature.detect.extract.NonMaxSuppression;
import boofcv.alg.feature.detect.intensity.GIntegralImageFeatureIntensity;
import boofcv.alg.feature.detect.selector.FeatureSelectLimitIntensity;
import boofcv.alg.feature.detect.selector.SampleIntensityImage;
import boofcv.alg.feature.detect.selector.SampleIntensityScalePoint;
import boofcv.alg.transform.ii.DerivativeIntegralImage;
import boofcv.alg.transform.ii.GIntegralImageOps;
import boofcv.alg.transform.ii.IntegralKernel;
import boofcv.core.image.ImageBorderValue;
import boofcv.struct.QueueCorner;
import boofcv.struct.border.ImageBorder_F32;
import boofcv.struct.feature.ScalePoint;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageGray;
import georegression.struct.point.Point2D_I16;
import lombok.Getter;
import org.ddogleg.struct.DogArray;
import org.ddogleg.struct.FastAccess;
import org.ddogleg.struct.FastArray;

import java.util.List;

public class FastHessianFeatureDetector<II extends ImageGray<II>> {

	private II integral;

	private final NonMaxSuppression extractor;
	private final FeatureSelectLimitIntensity<Point2D_I16> selectFeaturesInScale;
	private final FastArray<Point2D_I16> selectedScale = new FastArray<>(Point2D_I16.class);
	public int maxFeaturesPerScale = -1;

	private final FeatureSelectLimitIntensity<ScalePoint> selectFeaturesAll;
	private final FastArray<ScalePoint> selectedAll = new FastArray<>(ScalePoint.class);
	public int maxFeaturesAll = -1;

	private GrayF32[] intensity;
	private int spaceIndex = 0;
	private final QueueCorner foundFeatures = new QueueCorner(100);

	private final DogArray<ScalePoint> featuresAllScales = new DogArray<>(10, ScalePoint::new);

	private @Getter final int initialSize;
	private @Getter final int scaleStepSize;
	private @Getter final int numberOfOctaves;

	private final int[] sizes;

	protected IntegralKernel kerXX;
	protected IntegralKernel kerYY;

	protected IntegralKernel hessXX = new IntegralKernel(2);
	protected IntegralKernel hessYY = new IntegralKernel(2);
	protected IntegralKernel hessXY = new IntegralKernel(2);

	protected ImageBorder_F32 inten0 = new ImageBorderValue.Value_F32(0.0f);
	protected ImageBorder_F32 inten2 = new ImageBorderValue.Value_F32(0.0f);

	private final int initialSampleRate;

	public FastHessianFeatureDetector( NonMaxSuppression extractor,
									   FeatureSelectLimitIntensity<Point2D_I16> selectFeaturesInScale,
									   FeatureSelectLimitIntensity<ScalePoint> selectFeaturesAll,
									   int initialSampleRate, int initialSize,
									   int numberScalesPerOctave,
									   int numberOfOctaves, int scaleStepSize ) {
		this.extractor = extractor;
		this.selectFeaturesInScale = selectFeaturesInScale;
		this.selectFeaturesAll = selectFeaturesAll;
		this.initialSampleRate = initialSampleRate;
		this.initialSize = initialSize;
		this.numberOfOctaves = numberOfOctaves;
		this.scaleStepSize = scaleStepSize;

		sizes = new int[numberScalesPerOctave];

		selectFeaturesInScale.setSampler(new SampleIntensityImage.I16());
		selectFeaturesAll.setSampler(new SampleIntensityScalePoint());
	}

	public void detect( II integral ) {
		if (intensity == null) {
			intensity = new GrayF32[3];
			for (int i = 0; i < intensity.length; i++) {
				intensity[i] = new GrayF32(integral.width, integral.height);
			}
		}
		featuresAllScales.reset();

		int skip = initialSampleRate;
		int sizeStep = scaleStepSize;
		int octaveSize = initialSize;
		for (int octave = 0; octave < numberOfOctaves; octave++) {
			for (int i = 0; i < sizes.length; i++) {
				sizes[i] = octaveSize + i*sizeStep;
			}
			int maxSize = sizes[sizes.length - 1];
			if (maxSize > integral.width || maxSize > integral.height)
				break;
			detectOctave(integral, skip, sizes);
			skip += skip;
			octaveSize += sizeStep;
			sizeStep += sizeStep;
		}
		if (maxFeaturesAll > 0)
			selectFeaturesAll.select(null, integral.width, integral.height, true, null, featuresAllScales, maxFeaturesAll, selectedAll);
	}

	protected void detectOctave( II integral, int skip, int... featureSize ) {

		this.integral = integral;
		int w = integral.width/skip;
		int h = integral.height/skip;

		for (int i = 0; i < intensity.length; i++) {
			intensity[i].reshape(w, h);
		}

		for (int i = 0; i < featureSize.length; i++) {
			GIntegralImageFeatureIntensity.hessian(integral, skip, featureSize[i], intensity[spaceIndex],
					hessXX, hessYY, hessXY);

			spaceIndex++;
			if (spaceIndex >= 3)
				spaceIndex = 0;

			if (i >= 2) {
				findLocalScaleSpaceMax(featureSize, i - 1, skip);
			}
		}
	}

	private void findLocalScaleSpaceMax( int[] size, int level, int skip ) {
		int index0 = spaceIndex;
		int index1 = (spaceIndex + 1)%3;
		int index2 = (spaceIndex + 2)%3;

		inten0.setImage(intensity[index0]); GrayF32 inten1 = intensity[index1];
		inten2.setImage(intensity[index2]);

		foundFeatures.reset();
		extractor.setIgnoreBorder(size[level]/(2*skip));
		extractor.process(intensity[index1], null, null, null, foundFeatures);

		int ignoreRadius = extractor.getIgnoreBorder() + extractor.getSearchRadius();
		int ignoreWidth = intensity[index1].width - ignoreRadius;
		int ignoreHeight = intensity[index1].height - ignoreRadius;

		FastAccess<Point2D_I16> features;
		if (maxFeaturesPerScale > 0) {
			selectFeaturesInScale.select(intensity[index1], -1, -1, true, null, foundFeatures, maxFeaturesPerScale, selectedScale);
			features = selectedScale;
		} else {
			features = foundFeatures;
		}

		int levelSize = size[level];
		int sizeStep = levelSize - size[level - 1];

		featuresAllScales.reserve(featuresAllScales.size + features.size);

		for (int i = 0; i < features.size; i++) {
			Point2D_I16 f = features.get(i);

			if (f.x < ignoreRadius || f.x >= ignoreWidth || f.y < ignoreRadius || f.y >= ignoreHeight)
				continue;

			float intenF = inten1.get(f.x, f.y);

			if (checkMax(inten0, intenF, f.x, f.y) && checkMax(inten2, intenF, f.x, f.y)) {

				float peakX = polyPeak(inten1.get(f.x - 1, f.y), inten1.get(f.x, f.y), inten1.get(f.x + 1, f.y));
				float peakY = polyPeak(inten1.get(f.x, f.y - 1), inten1.get(f.x, f.y), inten1.get(f.x, f.y + 1));
				float peakS = polyPeak(inten0.get(f.x, f.y), inten1.get(f.x, f.y), inten2.get(f.x, f.y));

				float interpX = (f.x + peakX)*skip;
				float interpY = (f.y + peakY)*skip;
				float interpS = levelSize + peakS*sizeStep;

				double scale = 1.2*interpS/9.0;
				boolean white = computeLaplaceSign((int)(interpX + 0.5), (int)(interpY + 0.5), scale);
				featuresAllScales.grow().setTo(interpX, interpY, scale, white, intenF);
			}
		}
	}

	public boolean computeLaplaceSign( int x, int y, double scale ) {
		int s = (int)Math.ceil(scale);
		kerXX = DerivativeIntegralImage.kernelDerivXX(9*s, kerXX);
		kerYY = DerivativeIntegralImage.kernelDerivYY(9*s, kerYY);
		double lap = GIntegralImageOps.convolveSparse(integral, kerXX, x, y);
		lap += GIntegralImageOps.convolveSparse(integral, kerYY, x, y);

		return lap > 0;
	}

	protected static boolean checkMax( ImageBorder_F32 inten, float bestScore, int c_x, int c_y ) {
		for (int y = c_y - 1; y <= c_y + 1; y++) {
			for (int x = c_x - 1; x <= c_x + 1; x++) {
				if (inten.get(x, y) >= bestScore) {
					return false;
				}
			}
		}
		return true;
	}

	public static float polyPeak( float lower, float middle, float upper ) {
float a = 0.5f*lower - middle + 0.5f*upper;
		float b = 0.5f*upper - 0.5f*lower;

		if (a == 0.0f) {
			return 0.0f;
		} else {
			return -b/(2.0f*a);
		}
	}

	public static double polyPeak( double lower, double middle, double upper ) {
double a = 0.5*lower - middle + 0.5*upper;
		double b = 0.5*upper - 0.5*lower;

		if (a == 0.0) { return 0.0;
		} else {
			return -b/(2.0*a);
		}
	}

	public static double polyPeak( double lower, double middle, double upper,
								   double lowerVal, double middleVal, double upperVal ) {
		double offset = polyPeak(lower, middle, upper);
		if (offset < 0) {
			return -lowerVal*offset + (1.0 + offset)*middle;
		} else {
			return upperVal*offset + offset*middle;
		}
	}

	public List<ScalePoint> getFoundFeatures() {
		return (maxFeaturesAll <= 0 ? featuresAllScales : selectedAll).toList();
	}

	public int getSmallestWidth() {
		return initialSize;
	}
}
