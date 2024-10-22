package boofcv.gui.image;

import boofcv.misc.BoofMiscOps;

import javax.swing.*;
import java.awt.*;
import java.awt.image.BufferedImage;

public class AnimatePanel extends JPanel {

	BufferedImage images[];
	long previousTime;
	int period;
	int frame;

	Timer timer;

	public AnimatePanel( int period , BufferedImage... images) {
		this.period = period;
		if( images.length > 0 ) {
			this.images = images;
			setPreferredSize(new Dimension(images[0].getWidth(), images[0].getHeight()));
		}
	}

	public void setAnimation( BufferedImage... images ) {
		if( images.length == 0 )
			throw new IllegalArgumentException("Can't be of length 0");
		this.frame = 0;
		this.images = images;
	}

	public AnimatePanel start() {
		if( timer != null )
			throw new IllegalArgumentException("Already running");

		timer = new Timer();
		timer.start();
		return this;
	}

	public void stop() {
		timer.running = false;
		timer = null;
	}

	@Override
	public void paintComponent(Graphics g) {
		super.paintComponent(g);
		Graphics2D g2 = (Graphics2D)g;

		if( images == null )
			return;

		if( previousTime <= System.currentTimeMillis() ) {
			previousTime = System.currentTimeMillis()+period-1;
			frame = (frame+1)%images.length;
		}

		g2.drawImage(images[frame], 0, 0, this);
	}

	private class Timer extends Thread {

		public volatile boolean running = true;

		@Override
		public void run() {
			previousTime = 0;
			synchronized(this){
				while( running ) {
					BoofMiscOps.sleep(period);
					repaint();
				}
			}
		}
	}
}
