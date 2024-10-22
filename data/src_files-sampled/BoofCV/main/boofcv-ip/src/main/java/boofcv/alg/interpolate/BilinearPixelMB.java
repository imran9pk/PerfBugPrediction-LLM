package boofcv.alg.interpolate;

import boofcv.struct.border.ImageBorder;
import boofcv.struct.image.ImageInterleaved;

public abstract class BilinearPixelMB<T extends ImageInterleaved<T>> implements InterpolatePixelMB<T> {

	protected ImageBorder<T> border;
	protected T orig;
	protected int stride;
	protected int width;
	protected int height;

	@Override
	public void setBorder( ImageBorder<T> border ) {
		this.border = border;
	}

	@Override
	public void setImage( T image ) {
		if (border != null)
			border.setImage(image);
		this.orig = image;
		this.stride = orig.getStride();
		this.width = orig.getWidth();
		this.height = orig.getHeight();
	}

	@Override
	public T getImage() {
		return orig;
	}

	@Override
	public boolean isInFastBounds( float x, float y ) {
		return !(x < 0 || y < 0 || x > width - 2 || y > height - 2);
	}

	@Override
	public int getFastBorderX() {
		return 1;
	}

	@Override
	public int getFastBorderY() {
		return 1;
	}

	@Override
	public ImageBorder<T> getBorder() {
		return border;
	}
}
