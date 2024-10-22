package boofcv.alg.filter.derivative.impl;

import boofcv.struct.border.ImageBorder_S32;
import boofcv.struct.image.GrayU8;
import boofcv.struct.sparse.GradientValue_I32;
import boofcv.struct.sparse.SparseImageGradient;

public class GradientSparseSobel_U8 implements SparseImageGradient<GrayU8,GradientValue_I32> {

	GrayU8 input;
	ImageBorder_S32<GrayU8> border;
	GradientValue_I32 gradient = new GradientValue_I32();

	public GradientSparseSobel_U8(ImageBorder_S32<GrayU8> border) {
		this.border = border;
	}

	@Override
	public GradientValue_I32 compute(int x, int y) {
		int a00,a01,a02,a10,a12,a20,a21,a22;
		if( x >= 1 && y >= 1 && x < input.width - 1 && y < input.height - 1 ) {
			int s = input.stride;
			int tl = input.startIndex + input.stride*(y-1) + x-1;

			a00 = input.data[tl       ] & 0xFF;
			a01 = input.data[tl+1     ] & 0xFF;
			a02 = input.data[tl+2     ] & 0xFF;
			a10 = input.data[tl   + s ] & 0xFF;
			a12 = input.data[tl+2 + s ] & 0xFF;
			a20 = input.data[tl   + 2*s] & 0xFF;
			a21 = input.data[tl+1 + 2*s] & 0xFF;
			a22 = input.data[tl+2 + 2*s] & 0xFF;
		} else {
			a00 = border.get(x-1,y-1);
			a01 = border.get(x  ,y-1);
			a02 = border.get(x+1,y-1);
			a10 = border.get(x-1,y  );
			a12 = border.get(x+1,y  );
			a20 = border.get(x-1,y+1);
			a21 = border.get(x  ,y+1);
			a22 = border.get(x+1,y+1);
		}
		gradient.y = -(a00 + 2*a01 + a02);
		gradient.y += (a20 + 2*a21 + a22);

		gradient.x = -(a00 + 2*a10 + a20);
		gradient.x += (a02 + 2*a12 + a22);

		return gradient;
	}

	@Override
	public Class<GradientValue_I32> getGradientType() {
		return GradientValue_I32.class;
	}

	@Override
	public void setImage(GrayU8 input) {
		this.input = input;
		if( border != null ) {
			border.setImage(input);
		}
	}

	@Override
	public boolean isInBounds(int x, int y) {
		return border != null || (x >= 1 && y >= 1 && x < input.width - 1 && y < input.height - 1);
	}
}
