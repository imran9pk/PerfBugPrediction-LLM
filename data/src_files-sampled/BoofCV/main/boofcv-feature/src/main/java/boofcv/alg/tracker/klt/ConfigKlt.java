package boofcv.alg.tracker.klt;

import boofcv.struct.Configuration;

public class ConfigKlt implements Configuration {

	public int forbiddenBorder;

	public float maxPerPixelError = 25;
	public int maxIterations = 15;  public float minDeterminant = 0.001f;
	public float minPositionDelta = 0.01f;

	public float driftFracTol = 1.0f;

	@Override
	public void checkValidity() {
		if (driftFracTol < 0)
			throw new IllegalArgumentException("driftFracTol must be >= 0");
	}

	public void setTo( ConfigKlt src ) {
		this.forbiddenBorder = src.forbiddenBorder;
		this.maxPerPixelError = src.maxPerPixelError;
		this.maxIterations = src.maxIterations;
		this.minDeterminant = src.minDeterminant;
		this.minPositionDelta = src.minPositionDelta;
		this.driftFracTol = src.driftFracTol;
	}
}
