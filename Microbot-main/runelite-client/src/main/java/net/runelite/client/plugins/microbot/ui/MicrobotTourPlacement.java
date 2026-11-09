package net.runelite.client.plugins.microbot.ui;

import java.awt.Dimension;
import java.awt.Point;
import java.awt.Rectangle;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.Value;

final class MicrobotTourPlacement
{
	static final int GAP = 12;

	enum Side
	{
		LEFT,
		RIGHT,
		ABOVE,
		BELOW,
		CENTER
	}

	@Value
	static class Result
	{
		Point location;
		Side side;
	}

	private MicrobotTourPlacement()
	{
	}

	static Result place(Rectangle target, Rectangle avoid, Dimension size, Rectangle frame, Rectangle screen, Side preferred, int gap)
	{
		if (target == null)
		{
			Point centered = new Point(
				frame.x + (frame.width - size.width) / 2,
				frame.y + (frame.height - size.height) / 2);
			return new Result(clamp(centered, size, frame, screen), Side.CENTER);
		}

		Set<Side> order = new LinkedHashSet<>();
		if (preferred != null && preferred != Side.CENTER)
		{
			order.add(preferred);
		}
		order.add(Side.LEFT);
		order.add(Side.BELOW);
		order.add(Side.ABOVE);
		order.add(Side.RIGHT);

		Point fallback = null;
		Side fallbackSide = null;
		for (Side side : order)
		{
			Point candidate = candidate(side, target, avoid, size, frame, gap);
			if (fallback == null)
			{
				fallback = candidate;
				fallbackSide = side;
			}
			if (fits(side, candidate, size, frame))
			{
				return new Result(clamp(candidate, size, frame, screen), side);
			}
		}
		return new Result(clamp(fallback, size, frame, screen), fallbackSide);
	}

	private static Point candidate(Side side, Rectangle target, Rectangle avoid, Dimension size, Rectangle frame, int gap)
	{
		int centerY = target.y + target.height / 2 - size.height / 2;
		boolean rightHalf = target.x + target.width / 2 > frame.x + frame.width / 2;
		int alignedX = rightHalf ? target.x + target.width - size.width : target.x;
		switch (side)
		{
			case LEFT:
				int leftEdge = avoid == null ? target.x : Math.min(target.x, avoid.x);
				return new Point(leftEdge - gap - size.width, centerY);
			case RIGHT:
				return new Point(target.x + target.width + gap, centerY);
			case ABOVE:
				return new Point(alignedX, target.y - gap - size.height);
			default:
				return new Point(alignedX, target.y + target.height + gap);
		}
	}

	private static boolean fits(Side side, Point p, Dimension size, Rectangle frame)
	{
		switch (side)
		{
			case LEFT:
				return p.x >= frame.x && size.height <= frame.height;
			case RIGHT:
				return p.x + size.width <= frame.x + frame.width && size.height <= frame.height;
			case ABOVE:
				return p.y >= frame.y && size.width <= frame.width;
			default:
				return p.y + size.height <= frame.y + frame.height && size.width <= frame.width;
		}
	}

	private static Point clamp(Point p, Dimension size, Rectangle frame, Rectangle screen)
	{
		Rectangle horizontal = size.width <= frame.width || screen == null ? frame : screen;
		Rectangle vertical = size.height <= frame.height || screen == null ? frame : screen;
		return new Point(
			clamp(p.x, horizontal.x, horizontal.x + horizontal.width - size.width),
			clamp(p.y, vertical.y, vertical.y + vertical.height - size.height));
	}

	private static int clamp(int value, int min, int max)
	{
		if (max < min)
		{
			return min;
		}
		return Math.max(min, Math.min(max, value));
	}
}
