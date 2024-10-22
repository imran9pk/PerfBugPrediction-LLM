package boofcv.alg.filter.misc;

import boofcv.alg.filter.misc.impl.*;
import boofcv.concurrency.BoofConcurrency;
import boofcv.struct.image.*;

@SuppressWarnings({"Duplicates", "rawtypes"})
public class AverageDownSampleOps {
	public static int downSampleSize( int length, int squareWidth ) {
		int ret = length/squareWidth;
		if (length%squareWidth != 0)
			ret++;

		return ret;
	}

	public static void reshapeDown( ImageBase image, int inputWidth, int inputHeight, int squareWidth ) {
		int w = downSampleSize(inputWidth, squareWidth);
		int h = downSampleSize(inputHeight, squareWidth);

		image.reshape(w, h);
	}

	public static <T extends ImageGray<T>> T downMaxPixels( T full, T small, int maxPixels ) {
		if (full.width*full.height > maxPixels) {
			double scale = Math.sqrt(maxPixels)/Math.sqrt(full.width*full.height);
			small.reshape((int)(full.width*scale + 0.5), (int)(full.height*scale + 0.5));

			AverageDownSampleOps.down(full, small);
			return small;
		}
		return full;
	}

	public static void down( ImageBase input, int sampleWidth, ImageBase output ) {
		switch (input.getImageType().getFamily()) {
			case GRAY -> {
				down((ImageGray)input, sampleWidth, (ImageGray)output);
				return;
			}
			case PLANAR -> {
				down((Planar)input, sampleWidth, (Planar)output);
				return;
			}
			case INTERLEAVED -> throw new IllegalArgumentException("Interleaved images are not yet supported");
		}
		throw new IllegalArgumentException("Unknown image type");
	}

	public static void down( ImageGray input, int sampleWidth, ImageGray output ) {
		if (input instanceof GrayU8) {
			down((GrayU8)input, sampleWidth, (GrayI8)output);
		} else if (input instanceof GrayS8) {
			down((GrayS8)input, sampleWidth, (GrayI8)output);
		} else if (input instanceof GrayU16) {
			down((GrayU16)input, sampleWidth, (GrayI16)output);
		} else if (input instanceof GrayS16) {
			down((GrayS16)input, sampleWidth, (GrayI16)output);
		} else if (input instanceof GrayS32) {
			down((GrayS32)input, sampleWidth, (GrayS32)output);
		} else if (input instanceof GrayF32) {
			down((GrayF32)input, sampleWidth, (GrayF32)output);
		} else if (input instanceof GrayF64) {
			down((GrayF64)input, sampleWidth, (GrayF64)output);
		} else {
			throw new IllegalArgumentException("Unknown image type");
		}
	}

	public static <T extends ImageBase<T>>
	void down( T input, T output ) {
		if (ImageGray.class.isAssignableFrom(input.getClass())) {
			if (BoofConcurrency.USE_CONCURRENT) {
				if (input instanceof GrayU8) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample_MT.horizontal((GrayU8)input, middle);
					ImplAverageDownSample_MT.vertical(middle, (GrayI8)output);
				} else if (input instanceof GrayU16) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample_MT.horizontal((GrayU16)input, middle);
					ImplAverageDownSample_MT.vertical(middle, (GrayU16)output);
				} else if (input instanceof GrayF32) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample_MT.horizontal((GrayF32)input, middle);
					ImplAverageDownSample_MT.vertical(middle, (GrayF32)output);
				} else if (input instanceof GrayF64) {
					GrayF64 middle = new GrayF64(output.width, input.height);
					ImplAverageDownSample_MT.horizontal((GrayF64)input, middle);
					ImplAverageDownSample_MT.vertical(middle, (GrayF64)output);
				} else {
					throw new IllegalArgumentException("Unknown image type");
				}
			} else {
				if (input instanceof GrayU8) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample.horizontal((GrayU8)input, middle);
					ImplAverageDownSample.vertical(middle, (GrayI8)output);
				} else if (input instanceof GrayU16) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample.horizontal((GrayU16)input, middle);
					ImplAverageDownSample.vertical(middle, (GrayU16)output);
				} else if (input instanceof GrayF32) {
					GrayF32 middle = new GrayF32(output.width, input.height);
					ImplAverageDownSample.horizontal((GrayF32)input, middle);
					ImplAverageDownSample.vertical(middle, (GrayF32)output);
				} else if (input instanceof GrayF64) {
					GrayF64 middle = new GrayF64(output.width, input.height);
					ImplAverageDownSample.horizontal((GrayF64)input, middle);
					ImplAverageDownSample.vertical(middle, (GrayF64)output);
				} else {
					throw new IllegalArgumentException("Unknown image type");
				}
			}
		} else if (Planar.class.isAssignableFrom(input.getClass())) {
			down((Planar)input, (Planar)output);
		}
	}

	public static <T extends ImageGray<T>> void down( Planar<T> input,
													  int sampleWidth, Planar<T> output ) {
		for (int band = 0; band < input.getNumBands(); band++) {
			down(input.getBand(band), sampleWidth, output.getBand(band));
		}
	}

	public static <T extends ImageGray<T>> void down( Planar<T> input, Planar<T> output ) {
		output.setNumberOfBands(input.getNumBands());
		for (int band = 0; band < input.getNumBands(); band++) {
			down(input.getBand(band), output.getBand(band));
		}
	}

	public static void down( GrayU8 input, int sampleWidth, GrayI8 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayS8 input, int sampleWidth, GrayI8 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayU16 input, int sampleWidth, GrayI16 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayS16 input, int sampleWidth, GrayI16 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayS32 input, int sampleWidth, GrayS32 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayF32 input, int sampleWidth, GrayF32 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}

	public static void down( GrayF64 input, int sampleWidth, GrayF64 output ) {
		if (sampleWidth == 2) {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSample2_MT.down(input, output);
			} else {
				ImplAverageDownSample2.down(input, output);
			}
		} else {
			if (BoofConcurrency.USE_CONCURRENT) {
				ImplAverageDownSampleN_MT.down(input, sampleWidth, output);
			} else {
				ImplAverageDownSampleN.down(input, sampleWidth, output);
			}
		}
	}
}
