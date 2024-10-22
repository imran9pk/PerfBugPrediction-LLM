package boofcv.alg.filter.binary;

import boofcv.abst.filter.binary.InputToBinary;
import boofcv.struct.ConfigLength;
import boofcv.struct.image.GrayU8;
import boofcv.struct.image.ImageGray;
import boofcv.struct.image.ImageType;
import boofcv.struct.lists.RecycleStack;

import java.util.Arrays;

@SuppressWarnings("Duplicates")
public class ThresholdLocalOtsu implements InputToBinary<GrayU8> {

	ImageType<GrayU8> imageType = ImageType.single(GrayU8.class);

	private final boolean useOtsu2;
	private final double tuning;
	private final boolean down;
	private final double scale;

	ConfigLength regionWidthLength;
	int regionWidth;

	int numPixels;

	RecycleStack<ApplyHelper> helpers = new RecycleStack<>(ApplyHelper::new);

	public ThresholdLocalOtsu( boolean otsu2, ConfigLength regionWidthLength, double tuning, double scale, boolean down ) {
		this.regionWidthLength = regionWidthLength;
		this.useOtsu2 = otsu2;
		this.tuning = tuning;
		this.scale = scale;
		this.down = down;
	}

	@Override
	public void process( GrayU8 input, GrayU8 output ) {
		output.reshape(input.width, input.height);

		regionWidth = regionWidthLength.computeI(Math.min(input.width, input.height));

		if (input.width < regionWidth || input.height < regionWidth) {
			regionWidth = Math.min(input.width, input.height);
		}

		numPixels = regionWidth*regionWidth;

		int y0 = regionWidth/2;
		int y1 = input.height - (regionWidth - y0);
		int x0 = regionWidth/2;
		int x1 = input.width - (regionWidth - x0);

		final byte a, b;
		if (down) {
			a = 1;
			b = 0;
		} else {
			a = 0;
			b = 1;
		}

		process(input, output, x0, y0, x1, y1, a, b);
	}

	protected void process( GrayU8 input, GrayU8 output, int x0, int y0, int x1, int y1, byte a, byte b ) {
		ApplyHelper h = helpers.pop();
		for (int y = y0; y < y1; y++) {
			int indexInput = input.startIndex + y*input.stride + x0;
			int indexOutput = output.startIndex + y*output.stride + x0;

			h.computeHistogram(0, y - y0, input);
			output.data[indexOutput++] = (input.data[indexInput++] & 0xFF) <= h.otsu.threshold ? a : b;

			for (int x = x0 + 1; x < x1; x++) {
				h.updateHistogramX(x - x0, y - y0, input);
				output.data[indexOutput++] = (input.data[indexInput++] & 0xFF) <= h.otsu.threshold ? a : b;
			}
		}

		applyToBorder(input, output, y0, y1, x0, x1, h);
		helpers.recycle(h);
	}

	void applyToBorder( GrayU8 input, GrayU8 output, int y0, int y1, int x0, int x1, ApplyHelper h ) {
		h.computeHistogram(0, 0, input);
		h.applyToBlock(0, 0, x0 + 1, y0 + 1, input, output);
		for (int x = x0 + 1; x < x1; x++) {
			h.updateHistogramX(x - x0, 0, input);
			h.applyToBlock(x, 0, x + 1, y0, input, output);
		}
		h.updateHistogramX(x1 - x0, 0, input);
		h.applyToBlock(x1, 0, input.width, y0 + 1, input, output);

		for (int y = y0 + 1; y < y1; y++) {
			h.updateHistogramY(x1 - x0, y - y0, input);
			h.applyToBlock(x1, y, input.width, y + 1, input, output);
		}

		h.updateHistogramY(x1 - x0, y1 - y0, input);
		h.applyToBlock(x1, y1, input.width, input.height, input, output);

		h.computeHistogram(0, 0, input);

		for (int y = y0 + 1; y < y1; y++) {
			h.updateHistogramY(0, y - y0, input);
			h.applyToBlock(0, y, x0, y + 1, input, output);
		}

		h.updateHistogramY(0, y1 - y0, input);
		h.applyToBlock(0, y1, x0 + 1, input.height, input, output);

		for (int x = x0 + 1; x < x1; x++) {
			h.updateHistogramX(x - x0, y1 - y0, input);
			h.applyToBlock(x, y1, x + 1, input.height, input, output);
		}
	}

	class ApplyHelper {
		int[] histogram = new int[256];
		ComputeOtsu otsu = new ComputeOtsu(useOtsu2, tuning, down, scale);

		private void applyToBlock( int x0, int y0, int x1, int y1, GrayU8 input, GrayU8 output ) {

			final byte a, b;
			if (otsu.down) {
				a = 1;
				b = 0;
			} else {
				a = 0;
				b = 1;
			}

			for (int y = y0; y < y1; y++) {
				int indexInput = input.startIndex + y*input.stride + x0;
				int indexOutput = output.startIndex + y*output.stride + x0;
				int end = indexOutput + (x1 - x0);
				while (indexOutput < end) {
					output.data[indexOutput++] = (input.data[indexInput++] & 0xFF) <= otsu.threshold ? a : b;
				}
			}
		}

		void computeHistogram( int x0, int y0, GrayU8 input ) {

			Arrays.fill(histogram, 0);
			for (int y = 0; y < regionWidth; y++) {
				int indexInput = input.startIndex + (y0 + y)*input.stride + x0;
				for (int x = 0; x < regionWidth; x++) {
					histogram[input.data[indexInput++] & 0xFF]++;
				}
			}
			otsu.compute(histogram, histogram.length, numPixels);
		}

		void updateHistogramX( int x0, int y0, GrayU8 input ) {
			if (x0 <= 0) return;
			int indexInput = input.startIndex + y0*input.stride + x0 - 1;
			for (int y = 0; y < regionWidth; y++) {
				histogram[input.data[indexInput] & 0xFF]--;
				histogram[input.data[indexInput + regionWidth] & 0xFF]++;
				indexInput += input.stride;
			}
			otsu.compute(histogram, histogram.length, numPixels);
		}

		void updateHistogramY( int x0, int y0, GrayU8 input ) {
			if (y0 <= 0) return;
			int offset = regionWidth*input.stride;
			for (int x = 0; x < regionWidth; x++) {
				int indexInput = input.startIndex + (y0 - 1)*input.stride + x0 + x;
				histogram[input.data[indexInput] & 0xFF]--;
				histogram[input.data[indexInput + offset] & 0xFF]++;
			}
			otsu.compute(histogram, histogram.length, numPixels);
		}
	}

	@Override
	public ImageType<GrayU8> getInputType() {
		return imageType;
	}
}
