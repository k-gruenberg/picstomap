package org.openjfx;

import java.awt.*;
import java.awt.image.*;

public final class ImageRepairTools {

	private ImageRepairTools() {} // private constructor to prevent instantiation (Utility class)

	public static Image removeTransparentPixels(Image img) {
		// First: convert Image to BufferedImage
		BufferedImage bimg = toBufferedImage(img);
		// Second: loop through EVERY pixel, when encountering a transparent one, replace it with a "mixture" of its neighbors
		int width = bimg.getWidth();
		int height = bimg.getHeight();
		for (int x = 1; x < width-1; x++) { // do not check the pixels at the corners/borders, as neighbors there are difficult to access...
			for (int y = 1; y < height-1; y++) {
				try {
					if (isTransparent(bimg, x, y)) {
						boolean topIsTransparent = isTransparent(bimg, x, y-1);
						boolean bottomIsTransparent = isTransparent(bimg, x, y+1);
						boolean leftIsTransparent = isTransparent(bimg, x-1, y);
						boolean rightIsTransparent = isTransparent(bimg, x+1, y);
						int topRGB = bimg.getRGB(x, y-1);
						int bottomRGB = bimg.getRGB(x, y+1);
						int leftRGB = bimg.getRGB(x-1, y);
						int rightRGB = bimg.getRGB(x+1, y);
						int redAverage = 0, greenAverage = 0, blueAverage = 0;
						if (topIsTransparent && bottomIsTransparent && leftIsTransparent && rightIsTransparent) {
							// we have to repair this special pixel using its diagonal neighbors:
							int topLeftRGB = bimg.getRGB(x-1, y-1);
							int topRightRGB = bimg.getRGB(x+1, y-1);
							int bottomLeftRGB = bimg.getRGB(x-1, y+1);
							int bottomRightRGB = bimg.getRGB(x+1, y+1);
							redAverage = avg(red(topLeftRGB) , red(topRightRGB) , red(bottomLeftRGB) , red(bottomRightRGB));
							greenAverage = avg(green(topLeftRGB) , green(topRightRGB) , green(bottomLeftRGB) , green(bottomRightRGB));
							blueAverage = avg(blue(topLeftRGB) , blue(topRightRGB) , blue(bottomLeftRGB) , blue(bottomRightRGB));
						} else if (topIsTransparent && bottomIsTransparent && leftIsTransparent && !rightIsTransparent) {
							// ONLY the right pixel is not transparent, just copy that one:
							redAverage = red(rightRGB);
							greenAverage = green(rightRGB);
							blueAverage = blue(rightRGB);
						} else if (topIsTransparent && bottomIsTransparent && !leftIsTransparent && rightIsTransparent) {
							// ONLY the left pixel is not transparent, just copy that one:
							redAverage = red(leftRGB);
							greenAverage = green(leftRGB);
							blueAverage = blue(leftRGB);
						} else if (topIsTransparent && bottomIsTransparent && !leftIsTransparent && !rightIsTransparent) {
							// Use average of left & right pixels:
							redAverage = avg(red(leftRGB) , red(rightRGB));
							greenAverage = avg(green(leftRGB) , green(rightRGB));
							blueAverage = avg(blue(leftRGB) , blue(rightRGB));
						} else if (topIsTransparent && !bottomIsTransparent && leftIsTransparent && rightIsTransparent) {
							// ONLY the bottom pixel is not transparent, just copy that one:
							redAverage = red(bottomRGB);
							greenAverage = green(bottomRGB);
							blueAverage = blue(bottomRGB);
						} else if (topIsTransparent && !bottomIsTransparent && leftIsTransparent && !rightIsTransparent) {
							// Use average of bottom & right pixels:
							redAverage = avg(red(bottomRGB) , red(rightRGB));
							greenAverage = avg(green(bottomRGB) , green(rightRGB));
							blueAverage = avg(blue(bottomRGB) , blue(rightRGB));
						} else if (topIsTransparent && !bottomIsTransparent && !leftIsTransparent && rightIsTransparent) {
							// Use average of bottom & left pixels:
							redAverage = avg(red(bottomRGB) , red(leftRGB));
							greenAverage = avg(green(bottomRGB) , green(leftRGB));
							blueAverage = avg(blue(bottomRGB) , blue(leftRGB));
						} else if (topIsTransparent && !bottomIsTransparent && !leftIsTransparent && !rightIsTransparent) {
							// Use all neighbors, except top one:
							redAverage = avg(red(bottomRGB) , red(leftRGB) , red(rightRGB));
							greenAverage = avg(green(bottomRGB) , green(leftRGB) , green(rightRGB));
							blueAverage = avg(blue(bottomRGB) , blue(leftRGB) , blue(rightRGB));
						} else if (!topIsTransparent && bottomIsTransparent && leftIsTransparent && rightIsTransparent) {
							// ONLY the top pixel is not transparent, just copy that one:
							redAverage = red(topRGB);
							greenAverage = green(topRGB);
							blueAverage = blue(topRGB);
						} else if (!topIsTransparent && bottomIsTransparent && leftIsTransparent && !rightIsTransparent) {
							// Use average of top & right pixels:
							redAverage = avg(red(topRGB) , red(rightRGB));
							greenAverage = avg(green(topRGB) , green(rightRGB));
							blueAverage = avg(blue(topRGB) , blue(rightRGB));
						} else if (!topIsTransparent && bottomIsTransparent && !leftIsTransparent && rightIsTransparent) {
							// Use average of top & left pixels:
							redAverage = avg(red(topRGB) , red(leftRGB));
							greenAverage = avg(green(topRGB) , green(leftRGB));
							blueAverage = avg(blue(topRGB) , blue(leftRGB));
						} else if (!topIsTransparent && bottomIsTransparent && !leftIsTransparent && !rightIsTransparent) {
							// Use all neighbors, except bottom one:
							redAverage = avg(red(topRGB) , red(leftRGB) , red(rightRGB));
							greenAverage = avg(green(topRGB) , green(leftRGB) , green(rightRGB));
							blueAverage = avg(blue(topRGB) , blue(leftRGB) , blue(rightRGB));
						} else if (!topIsTransparent && !bottomIsTransparent && leftIsTransparent && rightIsTransparent) {
							// Use average of top & bottom pixels:
							redAverage = avg(red(topRGB) , red(bottomRGB));
							greenAverage = avg(green(topRGB) , green(bottomRGB));
							blueAverage = avg(blue(topRGB) , blue(bottomRGB));
						} else if (!topIsTransparent && !bottomIsTransparent && leftIsTransparent && !rightIsTransparent) {
							// Use all neighbors, except left one:
							redAverage = avg(red(topRGB) , red(bottomRGB) , red(rightRGB));
							greenAverage = avg(green(topRGB) , green(bottomRGB) , green(rightRGB));
							blueAverage = avg(blue(topRGB) , blue(bottomRGB) , blue(rightRGB));
						} else if (!topIsTransparent && !bottomIsTransparent && !leftIsTransparent && rightIsTransparent) {
							// Use all neighbors, except right one:
							redAverage = avg(red(topRGB) , red(bottomRGB) , red(leftRGB));
							greenAverage = avg(green(topRGB) , green(bottomRGB) , green(leftRGB));
							blueAverage = avg(blue(topRGB) , blue(bottomRGB) , blue(leftRGB));
						} else if (!topIsTransparent && !bottomIsTransparent && !leftIsTransparent && !rightIsTransparent) {
							// lonely transparent pixel: we can use all 4 neighbors:
							redAverage = avg(red(topRGB) , red(bottomRGB) , red(leftRGB) , red(rightRGB));
							greenAverage = avg(green(topRGB) , green(bottomRGB) , green(leftRGB) , green(rightRGB));
							blueAverage = avg(blue(topRGB) , blue(bottomRGB) , blue(leftRGB) , blue(rightRGB));
						}
						// JavaDoc BufferedImage: public void setRGB(int x, int y, int rgb)
						bimg.setRGB(x, y, rgb(redAverage, greenAverage, blueAverage));
					}
				} catch (Exception ex) {
					// quitly continue with next pixel...
				}
			}
		}
		return bimg;
	}

	/**
	* Copy-pasted from: https://stackoverflow.com/questions/13605248/java-converting-image-to-bufferedimage
	* Converts a given Image into a BufferedImage
	*
	* @param img The Image to be converted
	* @return The converted BufferedImage
	*/
	public static BufferedImage toBufferedImage(Image img)
	{
	    if (img instanceof BufferedImage)
	    {
	        return (BufferedImage) img;
	    }

	    // Create a buffered image with transparency
	    BufferedImage bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);

	    // Draw the image on to the buffered image
	    Graphics2D bGr = bimage.createGraphics();
	    bGr.drawImage(img, 0, 0, null);
	    bGr.dispose();

	    // Return the buffered image
	    return bimage;
	}

	/**
	* Copied from: https://stackoverflow.com/questions/8978228/java-bufferedimage-how-to-know-if-a-pixel-is-transparent
	*/
	public static boolean isTransparent(BufferedImage bimg, int x, int y) {
		// JavaDoc getRGB(): "An ArrayOutOfBoundsException may be thrown if the coordinates are not in bounds. However, explicit bounds checking is not guaranteed."
		try {
			int pixel = bimg.getRGB(x,y);
			if( (pixel>>24) == 0x00 ) {
				return true;
			}
			return false;
		} catch (ArrayIndexOutOfBoundsException ex) {
			return true; // pretend a non-existant pixel IS transparent so it WILL NOT be used for calculations
		}
	}

	// ----- ----- Tiny RGB Helper functions: ----- -----

	/**
	* See: https://stackoverflow.com/questions/11951646/setrgb-in-java
	*/
	public static int rgb(int red, int green, int blue) {
		Color col = new Color(red, green, blue);
		return col.getRGB();
	}

	public static int red(int rgb) {
		// JavaDoc Color Constructor summary: Color(int rgb)
		// "Creates an opaque sRGB color with the specified combined RGB value consisting of the red component in bits 16-23, the green component in bits 8-15, and the blue component in bits 0-7."
		Color col = new Color(rgb);
		return col.getRed(); // "Returns the red component in the range 0-255 in the default sRGB space."
	}

	public static int green(int rgb) {
		// JavaDoc Color Constructor summary: Color(int rgb)
		// "Creates an opaque sRGB color with the specified combined RGB value consisting of the red component in bits 16-23, the green component in bits 8-15, and the blue component in bits 0-7."
		Color col = new Color(rgb);
		return col.getGreen(); // "Returns the green component in the range 0-255 in the default sRGB space."
	}

	public static int blue(int rgb) {
		// JavaDoc Color Constructor summary: Color(int rgb)
		// "Creates an opaque sRGB color with the specified combined RGB value consisting of the red component in bits 16-23, the green component in bits 8-15, and the blue component in bits 0-7."
		Color col = new Color(rgb);
		return col.getBlue(); // "Returns the blue component in the range 0-255 in the default sRGB space."
	}

	/*
	public static int avg(int value1) {
		return value1;
	}

	public static int avg(int value1, int value2) {
		return (value1+value2)/2;
	}

	public static int avg(int value1, int value2, int value3) {
		return (value1+value2+value3)/3;
	}

	public static int avg(int value1, int value2, int value3, int value4) {
		return (value1+value2+value3+value4)/4;
	} */

	public static int avg(int... ints) {
		int sum = 0;
		for (int i : ints) {
			sum += i;
		}
		return sum/ints.length;
	}

}
