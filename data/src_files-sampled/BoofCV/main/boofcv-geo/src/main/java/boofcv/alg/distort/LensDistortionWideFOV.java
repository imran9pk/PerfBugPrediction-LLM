package boofcv.alg.distort;

import boofcv.struct.distort.Point2Transform3_F32;
import boofcv.struct.distort.Point2Transform3_F64;
import boofcv.struct.distort.Point3Transform2_F32;
import boofcv.struct.distort.Point3Transform2_F64;

public interface LensDistortionWideFOV {

	Point3Transform2_F64 distortStoP_F64();

	Point3Transform2_F32 distortStoP_F32();

	Point2Transform3_F64 undistortPtoS_F64();

	Point2Transform3_F32 undistortPtoS_F32();
}
