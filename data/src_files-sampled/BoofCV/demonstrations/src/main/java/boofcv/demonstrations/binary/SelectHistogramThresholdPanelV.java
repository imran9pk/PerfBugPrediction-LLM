package boofcv.demonstrations.binary;

import boofcv.gui.StandardAlgConfigPanel;
import boofcv.gui.binary.HistogramThresholdPanel;

import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class SelectHistogramThresholdPanelV extends StandardAlgConfigPanel implements ChangeListener, ActionListener {
	JSlider thresholdLevel;
	HistogramThresholdPanel histogramPanel;
	JButton toggleButton;

	Listener listener;

	int valueThreshold;
	boolean valueDown;

	public SelectHistogramThresholdPanelV( int threshold,
										   boolean directionDown ) {
		this.valueThreshold = threshold;
		this.valueDown = directionDown;

		histogramPanel = new HistogramThresholdPanel(256, 256);
		histogramPanel.setPreferredSize(new Dimension(120, 60));
		histogramPanel.setMaximumSize(histogramPanel.getPreferredSize());
		histogramPanel.setThreshold(valueThreshold, valueDown);

		thresholdLevel = new JSlider(JSlider.HORIZONTAL, 0, 255, valueThreshold);
		thresholdLevel.setMajorTickSpacing(20);
		thresholdLevel.setPaintTicks(true);
		thresholdLevel.addChangeListener(this);
		thresholdLevel.setValue(threshold);

		toggleButton = new JButton();
		toggleButton.setPreferredSize(new Dimension(100, 30));
		toggleButton.setMaximumSize(toggleButton.getPreferredSize());
		toggleButton.setMinimumSize(toggleButton.getPreferredSize());
		setToggleText();
		toggleButton.addActionListener(this);

		addAlignCenter(histogramPanel);
		addSeparator();
		addAlignCenter(thresholdLevel);
		addAlignCenter(toggleButton);
	}

	public HistogramThresholdPanel getHistogramPanel() {
		return histogramPanel;
	}

	public void setListener( Listener listener ) {
		this.listener = listener;
	}

	public int getThreshold() {
		return valueThreshold;
	}

	public boolean isDown() {
		return valueDown;
	}

	private void setToggleText() {
		if (valueDown)
			toggleButton.setText("down");
		else
			toggleButton.setText("Up");
	}

	@Override
	public void stateChanged( ChangeEvent e ) {

		if (e.getSource() == thresholdLevel) {
			int oldValue = valueThreshold;
			valueThreshold = ((Number)thresholdLevel.getValue()).intValue();
			if (oldValue == valueThreshold)
				return;
		}

		histogramPanel.setThreshold(valueThreshold, valueDown);
		histogramPanel.repaint();

		if (listener != null)
			listener.histogramThresholdChange();
	}

	@Override
	public void actionPerformed( ActionEvent e ) {
		if (e.getSource() == toggleButton) {
			valueDown = !valueDown;
			setToggleText();
		}

		histogramPanel.setThreshold(valueThreshold, valueDown);
		histogramPanel.repaint();

		if (listener != null)
			listener.histogramThresholdChange();
	}

	public void setThreshold( int threshold ) {
		valueThreshold = threshold;
		thresholdLevel.setValue(threshold);
		histogramPanel.setThreshold(valueThreshold, valueDown);
	}

	public static interface Listener {
		public void histogramThresholdChange();
	}
}