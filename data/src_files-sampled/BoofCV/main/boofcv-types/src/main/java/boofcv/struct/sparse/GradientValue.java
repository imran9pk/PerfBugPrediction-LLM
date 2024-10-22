package boofcv.struct.sparse;

public interface GradientValue {

	void set( double dx, double dy );

	double getX();

	double getY();
}
