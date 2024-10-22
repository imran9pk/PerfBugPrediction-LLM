package boofcv.alg.feature.detect.interest;

import boofcv.alg.feature.detect.intensity.IntegralImageFeatureIntensity;
import boofcv.alg.feature.detect.intensity.impl.ImplIntegralImageFeatureIntensity;
import boofcv.alg.misc.ImageMiscOps;
import boofcv.alg.transform.ii.IntegralImageOps;
import boofcv.concurrency.BoofConcurrency;
import boofcv.struct.image.GrayF32;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.openjdk.jmh.runner.options.TimeValue;

import java.util.Random;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.MILLISECONDS)
@Warmup(iterations = 2)
@Measurement(iterations = 5)
@State(Scope.Benchmark)
@Fork(value = 1)
public class BenchmarkFastHessianFeatureIntensity {
	@Param({"true","false"})
	public boolean concurrent=false;

	public int imageSize=2000;

	static int skip = 1;
	static int size = 15;

	GrayF32 original = new GrayF32(imageSize,imageSize);
	GrayF32 integral = new GrayF32(imageSize,imageSize);
	GrayF32 intensity = new GrayF32(imageSize,imageSize);

	@Setup public void setup() {
		BoofConcurrency.USE_CONCURRENT = concurrent;
		var rand = new Random(234234);

		ImageMiscOps.fillUniform(original,rand,0,200);
		IntegralImageOps.transform(original,integral);
	}

	@Benchmark public void Naive() {ImplIntegralImageFeatureIntensity.hessianNaive(integral,skip,size,intensity);}
	@Benchmark public void Standard() {IntegralImageFeatureIntensity.hessian(integral,skip,size,intensity);}
	public static void main(String[] args) throws RunnerException {
		Options opt = new OptionsBuilder()
				.include(BenchmarkFastHessianFeatureIntensity.class.getSimpleName())
				.warmupTime(TimeValue.seconds(1))
				.measurementTime(TimeValue.seconds(1))
				.build();

		new Runner(opt).run();
	}
}
