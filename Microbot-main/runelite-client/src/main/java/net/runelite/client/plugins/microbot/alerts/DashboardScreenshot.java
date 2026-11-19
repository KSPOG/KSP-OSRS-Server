package net.runelite.client.plugins.microbot.alerts;

import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;

public final class DashboardScreenshot
{
	static final int MAX_WIDTH = 1280;
	static final float[] QUALITIES = { 0.8f, 0.6f, 0.4f };
	static final int MAX_BYTES = 1_000_000;

	private DashboardScreenshot()
	{
	}

	public static byte[] encode(BufferedImage frame) throws IOException
	{
		BufferedImage image = toRgb(frame);
		byte[] jpeg = null;
		for (float quality : QUALITIES)
		{
			jpeg = encode(image, quality);
			if (jpeg.length <= MAX_BYTES)
			{
				break;
			}
		}
		return jpeg;
	}

	private static byte[] encode(BufferedImage image, float quality) throws IOException
	{
		ImageWriter writer = ImageIO.getImageWritersByFormatName("jpg").next();
		ByteArrayOutputStream out = new ByteArrayOutputStream();
		try (ImageOutputStream stream = ImageIO.createImageOutputStream(out))
		{
			writer.setOutput(stream);
			ImageWriteParam param = writer.getDefaultWriteParam();
			param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
			param.setCompressionQuality(quality);
			writer.write(null, new IIOImage(image, null, null), param);
		}
		finally
		{
			writer.dispose();
		}
		return out.toByteArray();
	}

	private static BufferedImage toRgb(BufferedImage frame)
	{
		double scale = Math.min(1.0, (double) MAX_WIDTH / frame.getWidth());
		int width = Math.max(1, (int) Math.round(frame.getWidth() * scale));
		int height = Math.max(1, (int) Math.round(frame.getHeight() * scale));
		BufferedImage rgb = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		Graphics2D graphics = rgb.createGraphics();
		try
		{
			graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			graphics.drawImage(frame, 0, 0, width, height, null);
		}
		finally
		{
			graphics.dispose();
		}
		return rgb;
	}
}
