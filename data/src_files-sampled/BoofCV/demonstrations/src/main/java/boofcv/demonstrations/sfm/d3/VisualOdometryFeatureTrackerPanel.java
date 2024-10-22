package boofcv.demonstrations.sfm.d3;

import javax.swing.*;

public class VisualOdometryFeatureTrackerPanel extends VisualOdometryAlgorithmPanel {
	JTextArea displayTracks;
	JTextArea displayInliers;

	public VisualOdometryFeatureTrackerPanel() {
		displayTracks = createTextInfo();
		displayInliers = createTextInfo();

		addLabeledV(displayTracks,  "Tracks", this);
		addLabeledV(displayInliers, "Inliers", this);
		addHorizontalGlue(this);
	}

	public void setNumTracks( int totalTracks ) {
		displayTracks.setText(String.format("%5d",totalTracks));
	}

	public void setNumInliers(int totalInliers) {
		displayInliers.setText(String.format("%5d",totalInliers));
	}

	private JTextArea createTextInfo() {
		JTextArea comp = new JTextArea(1,6);
		comp.setMaximumSize(comp.getPreferredSize());
		comp.setEditable(false);
		return comp;
	}
}
