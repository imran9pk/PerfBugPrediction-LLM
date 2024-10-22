package boofcv.factory.feature.detect.line;

import boofcv.factory.filter.binary.ConfigThreshold;
import boofcv.factory.filter.binary.ThresholdType;
import boofcv.struct.ConfigLength;
import boofcv.struct.Configuration;

public class ConfigHoughBinary implements Configuration {

	public Binarization binarization = Binarization.EDGE;

	public ConfigThreshold thresholdImage = ConfigThreshold.global(ThresholdType.GLOBAL_OTSU);

	public ConfigEdgeThreshold thresholdEdge = new ConfigEdgeThreshold();

	public int localMaxRadius = 1;
	public ConfigLength minCounts = ConfigLength.relative(0.001,1);

	public int maxLines = 10;

	public double mergeAngle = Math.PI*0.05;

	public double mergeDistance = 10;

	public ConfigHoughBinary() {}

	public ConfigHoughBinary(int maxLines) {
		this.maxLines = maxLines;
	}

	public void setTo( ConfigHoughBinary src ) {
		this.binarization = src.binarization;
		this.thresholdImage.setTo(src.thresholdImage);
		this.thresholdEdge.setTo(src.thresholdEdge);
		this.localMaxRadius = src.localMaxRadius;
		this.minCounts.setTo(src.minCounts);
		this.maxLines = src.maxLines;
		this.mergeAngle = src.mergeAngle;
		this.mergeDistance = src.mergeDistance;
	}

	@Override
	public void checkValidity() {

	}

	public enum Binarization {
		IMAGE,
		EDGE
	}
}
