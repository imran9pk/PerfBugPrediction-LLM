package boofcv.examples.geometry;

import boofcv.abst.feature.detect.interest.ConfigPointDetector;
import boofcv.abst.feature.detect.interest.PointDetectorTypes;
import boofcv.abst.sfm.d2.ImageMotion2D;
import boofcv.abst.sfm.d2.PlToGrayMotion2D;
import boofcv.abst.tracker.PointTracker;
import boofcv.alg.sfm.d2.StitchingFromMotion2D;
import boofcv.factory.sfm.FactoryMotion2D;
import boofcv.factory.tracker.FactoryPointTracker;
import boofcv.gui.image.ImageGridPanel;
import boofcv.gui.image.ShowImages;
import boofcv.io.MediaManager;
import boofcv.io.UtilIO;
import boofcv.io.image.ConvertBufferedImage;
import boofcv.io.image.SimpleImageSequence;
import boofcv.io.wrapper.DefaultMediaManager;
import boofcv.misc.BoofMiscOps;
import boofcv.struct.image.GrayF32;
import boofcv.struct.image.ImageType;
import boofcv.struct.image.Planar;
import georegression.struct.homography.Homography2D_F64;

import java.awt.image.BufferedImage;

public class ExampleVideoStabilization {
	public static void main( String[] args ) {
		ConfigPointDetector configDetector = new ConfigPointDetector();
		configDetector.type = PointDetectorTypes.SHI_TOMASI;
		configDetector.general.maxFeatures = 300;
		configDetector.general.threshold = 10;
		configDetector.general.radius = 2;

		PointTracker<GrayF32> tracker = FactoryPointTracker.klt(4, configDetector, 3,
				GrayF32.class, GrayF32.class);

		ImageMotion2D<GrayF32, Homography2D_F64> motion2D =
				FactoryMotion2D.createMotion2D(200, 3, 2, 30, 0.6, 0.5, false, tracker, new Homography2D_F64());

		ImageMotion2D<Planar<GrayF32>, Homography2D_F64> motion2DColor =
				new PlToGrayMotion2D<>(motion2D, GrayF32.class);

		StitchingFromMotion2D<Planar<GrayF32>, Homography2D_F64>
				stabilize = FactoryMotion2D.createVideoStitch(0.5, motion2DColor, ImageType.pl(3, GrayF32.class));

		MediaManager media = DefaultMediaManager.INSTANCE;
		String fileName = UtilIO.pathExample("shake.mjpeg");
		SimpleImageSequence<Planar<GrayF32>> video =
				media.openVideo(fileName, ImageType.pl(3, GrayF32.class));

		Planar<GrayF32> frame = video.next();

		stabilize.configure(frame.width, frame.height, null);
		stabilize.process(frame);

		ImageGridPanel gui = new ImageGridPanel(1, 2);
		gui.setImage(0, 0, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
		gui.setImage(0, 1, new BufferedImage(frame.width, frame.height, BufferedImage.TYPE_INT_RGB));
		gui.autoSetPreferredSize();

		ShowImages.showWindow(gui, "Example Stabilization", true);

		while (video.hasNext()) {
			if (!stabilize.process(video.next()))
				throw new RuntimeException("Don't forget to handle failures!");

			ConvertBufferedImage.convertTo(frame, gui.getImage(0, 0), true);
			ConvertBufferedImage.convertTo(stabilize.getStitchedImage(), gui.getImage(0, 1), true);

			gui.repaint();

			BoofMiscOps.pause(50);
		}
	}
}
