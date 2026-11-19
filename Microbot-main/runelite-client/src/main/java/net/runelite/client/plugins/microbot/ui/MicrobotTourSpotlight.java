package net.runelite.client.plugins.microbot.ui;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.RenderingHints;
import java.awt.geom.Area;
import java.awt.geom.RoundRectangle2D;
import javax.swing.JComponent;
import javax.swing.JLayeredPane;
import javax.swing.SwingUtilities;
import net.runelite.client.ui.ColorScheme;

class MicrobotTourSpotlight
{
	private static final Color DIM = new Color(0, 0, 0, 140);
	private static final Color GLOW = new Color(ColorScheme.BRAND_ORANGE.getRed(), ColorScheme.BRAND_ORANGE.getGreen(), ColorScheme.BRAND_ORANGE.getBlue(), 70);
	private static final int INFLATE = 3;
	private static final int ARC = 8;

	private final JLayeredPane layeredPane;
	private final DimPane sidebarPane = new DimPane();
	private final DimPane menuBarPane = new DimPane();

	MicrobotTourSpotlight(JLayeredPane layeredPane)
	{
		this.layeredPane = layeredPane;
		layeredPane.add(sidebarPane, JLayeredPane.PALETTE_LAYER);
		layeredPane.add(menuBarPane, JLayeredPane.PALETTE_LAYER);
	}

	void update(Rectangle sidebar, Rectangle menuBar, Rectangle target)
	{
		if (target == null)
		{
			hide();
			return;
		}
		sidebarPane.apply(sidebar, target);
		menuBarPane.apply(menuBar, target);
	}

	void hide()
	{
		sidebarPane.apply(null, null);
		menuBarPane.apply(null, null);
	}

	void remove()
	{
		layeredPane.remove(sidebarPane);
		layeredPane.remove(menuBarPane);
		layeredPane.repaint();
	}

	private Rectangle toLayered(Rectangle screen)
	{
		Point origin = screen.getLocation();
		SwingUtilities.convertPointFromScreen(origin, layeredPane);
		return new Rectangle(origin, screen.getSize());
	}

	private class DimPane extends JComponent
	{
		private Rectangle hole;

		DimPane()
		{
			setOpaque(false);
			setVisible(false);
		}

		void apply(Rectangle area, Rectangle target)
		{
			if (area == null || area.isEmpty() || !layeredPane.isShowing())
			{
				if (isVisible())
				{
					setVisible(false);
					layeredPane.repaint(getBounds());
				}
				return;
			}
			Rectangle bounds = toLayered(area);
			Rectangle targetBounds = toLayered(target);
			targetBounds.translate(-bounds.x, -bounds.y);
			if (!bounds.equals(getBounds()))
			{
				layeredPane.repaint(getBounds());
				setBounds(bounds);
			}
			hole = targetBounds;
			setVisible(true);
			repaint();
		}

		@Override
		public boolean contains(int x, int y)
		{
			return false;
		}

		@Override
		protected void paintComponent(Graphics g)
		{
			Graphics2D g2 = (Graphics2D) g.create();
			try
			{
				g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
				Area dim = new Area(new Rectangle(0, 0, getWidth(), getHeight()));
				RoundRectangle2D cutout = null;
				if (hole != null)
				{
					cutout = new RoundRectangle2D.Double(hole.x - INFLATE, hole.y - INFLATE,
						hole.width + INFLATE * 2, hole.height + INFLATE * 2, ARC, ARC);
					dim.subtract(new Area(cutout));
				}
				g2.setColor(DIM);
				g2.fill(dim);
				if (cutout != null && cutout.intersects(0, 0, getWidth(), getHeight()))
				{
					g2.setColor(GLOW);
					g2.setStroke(new BasicStroke(4f));
					g2.draw(cutout);
					g2.setColor(ColorScheme.BRAND_ORANGE);
					g2.setStroke(new BasicStroke(2f));
					g2.draw(cutout);
				}
			}
			finally
			{
				g2.dispose();
			}
		}
	}
}
