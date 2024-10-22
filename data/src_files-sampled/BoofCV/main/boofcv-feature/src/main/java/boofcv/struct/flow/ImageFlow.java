package boofcv.struct.flow;

public class ImageFlow {
	public int width,height;

	public D data[] = new D[0];

	public ImageFlow(int width, int height) {
		reshape(width,height);
	}

	public void reshape( int width , int height ) {
		int N = width*height;

		if( data.length < N ) {
			D tmp[] = new D[N];
			System.arraycopy(data,0,tmp,0,data.length);
			for( int i = data.length; i < N; i++ )
				tmp[i] = new D();
			data = tmp;
		}
		this.width = width;
		this.height = height;
	}

	public void invalidateAll() {
		int N = width*height;
		for( int i = 0; i < N; i++ )
			data[i].x = Float.NaN;
	}

	public void fillZero() {
		int N = width*height;
		for( int i = 0; i < N; i++ ) {
			D d = data[i];
			d.x = d.y = 0;
		}
	}

	public D get( int x , int y ) {
		if( !isInBounds(x,y))
			throw new IllegalArgumentException("Requested pixel is out of bounds: "+x+" "+y);

		return data[y*width+x];
	}

	public D unsafe_get( int x , int y ) {
		return data[y*width+x];
	}

	public final boolean isInBounds(int x, int y) {
		return x >= 0 && x < width && y >= 0 && y < height;
	}

	public int getWidth() {
		return width;
	}

	public int getHeight() {
		return height;
	}

	public void setTo( ImageFlow flow ) {
		int N = width*height;
		for( int i = 0; i < N; i++ ) {
			data[i].set( flow.data[i] );
		}
	}

	public static class D
	{
		public float x,y;

		public void set( D d ) {
			this.x = d.x;
			this.y = d.y;
		}

		public void set( float x , float y ) {
			this.x = x;
			this.y = y;
		}

		public void markInvalid() {
			x = Float.NaN;
		}

		public float getX() {
			return x;
		}

		public float getY() {
			return y;
		}

		public boolean isValid() {
			return !Float.isNaN(x);
		}
	}
}
