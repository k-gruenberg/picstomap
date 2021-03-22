package org.openjfx;

import java.io.File;
import java.awt.Color;
import java.awt.Font;
import java.awt.image.BufferedImage;
import java.awt.Graphics;
import java.awt.Graphics2D; // only Graphics2D supports drawing a line with thickness!
import java.awt.Image; // used in drawOnlineMapBackground()
import java.awt.BasicStroke; // drawLine() with thickness! (-> https://stackoverflow.com/questions/2839508/java2d-increase-the-line-width)
// https://stackoverflow.com/questions/17225783/setcolor-in-jframe-does-not-work
// "To answer your question, I would guess the the getGraphics() method returns a new object every time you invoke the method."
import java.util.Scanner; // for user input (grayscale yes/no)
import java.util.function.Consumer;

public class Map {

	public static final int MARGIN = 30; // number of pixels to the left of minLongitude, etc...
	public static final int MAX_DIMENSIONS = 2000; // maximum width/height (without margin)

	public BufferedImage img;
	public Color currentColor = Color.RED; // !!NEW: a) change color depending on day & b) let the user specify the color
	double minLatitude, maxLatitude, minLongitude, maxLongitude;
	int width, height;
	int numberOfPoints;

	/**
	* @param minLatitude (-1)*ACTUAL_MAX_LATITUDE
	* @param maxLatitude (-1)*ACTUAL_MIN_LATITUDE
	* @param numberOfPoints to determine whether to draw large points and thick lines or small points and thin lines...
	*/
	public Map(double minLatitude, double maxLatitude, double minLongitude, double maxLongitude, int numberOfPoints) {
		this.numberOfPoints = numberOfPoints;

		this.minLatitude = minLatitude;
		this.maxLatitude = maxLatitude;
		this.minLongitude = minLongitude;
		this.maxLongitude = maxLongitude;

		double latitudeDiff = maxLatitude-minLatitude;
		double longitudeDiff = maxLongitude-minLongitude;
		double maxDiff = java.lang.Math.max(latitudeDiff, longitudeDiff);
		double scalingFactor = MAX_DIMENSIONS/maxDiff;
		this.width = (int)(scalingFactor*(maxLongitude-minLongitude)) + 2*MARGIN;
		this.height = (int)(scalingFactor*(maxLatitude-minLatitude)) + 2*MARGIN;

		// Javadoc Constructor: BufferedImage(int width, int height, int imageType)
		this.img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
		// -> https://www.javamex.com/tutorials/graphics/bufferedimage.shtml
		// !! "if you require your image to support transparency (also called alpha), then use BufferedImage.TYPE_INT_ARGB"
	
		this.drawFrame(); // to "see" the MARGIN
	}

	/**
	* Draws a point at lat/long coordinatas:
	* (don't forget to add top left margin)
	*/
	public void drawPoint(double latitude, double longitude, boolean isStartPoint) {
		int DOT_SIZE;
		final int MAX_DOT_SIZE = 30; // dot size should be no larger than MAX_DOT_SIZE
		final int MIN_DOT_SIZE = 10;
		int x = MARGIN + longitudeToXValue(longitude, width, minLongitude, maxLongitude);
		int y = MARGIN + latitudeToYValue(latitude, height, minLatitude, maxLatitude);
		Graphics gr = this.img.getGraphics();
		DOT_SIZE = (int)java.lang.Math.max(MIN_DOT_SIZE,MAX_DOT_SIZE - (this.numberOfPoints/5)); // 25 points -> size 25 ; 50 points -> size 20 ; 75 points -> size 15 ; 100 points -> MIN_DOT_SIZE
		if (isStartPoint) {
			gr.setColor(Color.GREEN);
			DOT_SIZE += 5; // the starting dot isn't just green, it's also a bit bigger
		} else {
			gr.setColor(this.currentColor);
		}
		gr.fillArc(x-((DOT_SIZE-1)/2), y-((DOT_SIZE-1)/2), DOT_SIZE-1, DOT_SIZE-1, 0, 360);
		// => https://docs.oracle.com/javase/7/docs/api/java/awt/Graphics.html#drawArc(int,%20int,%20int,%20int,%20int,%20int)
		// fillArc(int x,int y,int width,int height,int startAngle,int arcAngle)
		// -> x,y : coordinate of the upper-left corner of the arc to be filled
		// -> The resulting arc covers an area width + 1 pixels wide by height + 1 pixels tall.
		// -> The resulting arc begins at startAngle and extends for arcAngle degrees.

		// !!NEW: Mark the green starting point with an additional black "X" inside it:
		if (isStartPoint) {
			this.drawCharacter('X', x, y, DOT_SIZE-2, Color.BLACK);
		}
	}

	/**
	* Draws a line between 2 points:
	* (don't forget to add top left margin)
	*/
	public void drawLine(double lat1, double long1, double lat2, double long2) {
		int x1 = MARGIN + longitudeToXValue(long1, width, minLongitude, maxLongitude);
		int y1 = MARGIN + latitudeToYValue(lat1, height, minLatitude, maxLatitude);
		int x2 = MARGIN + longitudeToXValue(long2, width, minLongitude, maxLongitude);
		int y2 = MARGIN + latitudeToYValue(lat2, height, minLatitude, maxLatitude);
		// -> https://stackoverflow.com/questions/2839508/java2d-increase-the-line-width
		// "Note that the setStroke method is not available in the Graphics object. You have to cast it to a Graphics2D object."
		Graphics2D gr = (Graphics2D)this.img.getGraphics();
		gr.setColor(this.currentColor);
		final int MAX_STROKE = 10; // stroke should be no larger than MAX_STROKE
		final int MIN_STROKE = 3;
		int stroke = (int)java.lang.Math.max(MIN_STROKE,MAX_STROKE - (this.numberOfPoints/5)); // 25 points -> stroke 5 ; 50 points -> stroke MIN ; 75 points -> stroke MIN ; 100 points -> MIN_STROKE
		gr.setStroke(new BasicStroke(stroke));
		gr.draw(new java.awt.geom.Line2D.Float(x1, y1, x2, y2)); // JavaDoc: https://docs.oracle.com/javase/7/docs/api/java/awt/geom/Line2D.Float.html
		// Without thickness: gr.drawLine(x1, y1, x2, y2);
	}

	public void drawHeadline(String str) {
		final int FONT_SIZE = 16;
		Graphics gr = this.img.getGraphics();
		gr.setColor(Color.BLACK);
		Font f = new Font("Dialog", Font.PLAIN, FONT_SIZE);
		gr.setFont(f);
		gr.drawString(str, MARGIN, (MARGIN/2)+(FONT_SIZE/2)); // "The baseline of the leftmost character is at position (x, y)"
	}

	public void drawCaption(String str) {
		final int FONT_SIZE = 16;
		Graphics gr = this.img.getGraphics();
		gr.setColor(Color.BLACK);
		Font f = new Font("Dialog", Font.PLAIN, FONT_SIZE);
		gr.setFont(f);
		gr.drawString(str, MARGIN, height-(MARGIN/2)+(FONT_SIZE/2)); // "The baseline of the leftmost character is at position (x, y)"
	}

	// useful for drawing the "X" for the start point in drawPoint() function; also used in drawSquareWithTwoDigitNumber():
	public void drawCharacter(char chr, int centerX, int centerY, int size, Color clr) {
		//System.out.println("Debug: drawCharacter(): centerX="+centerX + " , centerY="+centerY + " , size="+size);
		Graphics gr = this.img.getGraphics();
		gr.setColor(clr);
		Font f = getPixelDefinedFont("Arial", size, gr);
		int charWidth = gr.getFontMetrics(f).charWidth(chr);
		int charHeight = gr.getFontMetrics(f).getAscent(); // = "The font ascent is the distance from the font's baseline to the top of most alphanumeric characters." !! getHeight() = getAscent() + getDescent() + getLeading() !!
		//System.out.println("Debug: drawCharacter(): charWidth="+charWidth + " , charHeight="+charHeight);
		gr.setFont(f);
		gr.drawString(""+chr, centerX-(charWidth/2), centerY+(charHeight/2)); // "The baseline of the leftmost character is at position (x, y)"
	}

	public void saveTo(File outputfile) {
		try {
			javax.imageio.ImageIO.write(img, "png", outputfile); // save a PNG version of map to outputfile - DONE!
		} catch (java.io.IOException ex) {
			System.out.println("IOException when trying to write the output image!:");
			ex.printStackTrace();
		}
	}

	// ----- NEW FOR THE GUI VERSION -----
	public BufferedImage getImage() {
		return this.img;
	}

	// ---> old console version <---
	public String drawOnlineMapBackground() {
		// Ask user whether he wants to have the map background in grayscale:
		System.out.print("Prompt: Do you want a grayscale map background? Enter 'yes' (default) or 'no': ");
		Scanner scanner1 = new Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
		String userInput1 = scanner1.nextLine();
		final String DEFAULT_TILE_SERVER = "https://a.tile.openstreetmap.de/${z}/${x}/${y}.png";
		final String DEFAULT_USER_AGENT = "";
		final int DEFAULT_ZOOM_CHANGE = 0;
		final int DEFAULT_MAX_DOWNLOADS = 500;
		final Consumer<String> DEFAULT_CONSUMER = str -> {};
		// -> a consumer that does exactly nothing: https://stackoverflow.com/questions/29851525/is-there-a-method-reference-for-a-no-op-nop-that-can-be-used-for-anything-lamb
		if (!"no".equalsIgnoreCase(userInput1)) { // ('yes' is default)
			return drawOnlineMapBackground(true, 0, DEFAULT_TILE_SERVER, DEFAULT_USER_AGENT, DEFAULT_ZOOM_CHANGE, DEFAULT_MAX_DOWNLOADS, DEFAULT_CONSUMER);
		} else {
			return drawOnlineMapBackground(false, 0, DEFAULT_TILE_SERVER, DEFAULT_USER_AGENT, DEFAULT_ZOOM_CHANGE, DEFAULT_MAX_DOWNLOADS, DEFAULT_CONSUMER);
		}
	}

	/** ---> new GUI version <---
	* Uses the OnlineMapData class to draw an appropriate background map from online data
	* @param grayscale whether the background should be drawn in grayscale or not
	* @param graynessPercentage 'how gray' the background should be (javax.swing.GrayFilter setting)
	* @param tileServerURL the URL of the OSM Tile Server
	* @param userAgent the User Agent to use for the GET requests sent to the OSM Tile Server
	* @param osmZoomLevelChange 0 to use the recommended OSM Zoom Level, +1 or -1 else
	* @param maxTileDownloads how many OSM tiles should be downloaded at maximum
	 * @param updateMessageConsumer a Consumer (function that accepts a String and returns void) we're sending our progress messages to
	* @return empty String "" if successful, contains error message otherwise
	*/
	public String drawOnlineMapBackground(boolean grayscale, int graynessPercentage,
										  String tileServerURL, String userAgent, int osmZoomLevelChange, int maxTileDownloads,
										  Consumer<String> updateMessageConsumer) {
		Image mapImg = null; // !!SCOPE!!
		try {
			// !! Don't forget to multiply latitudes by (-1) as the OnlineMapData class works with the ACTUAL coordinates!
			// !!!!!!!!!!!!!!!!!!!! AND DON'T FORGET TO SWAP MIN/MAX LATITUDE AFTER THAT !!!!!!!!!!!!!!!!!!!
			mapImg = OnlineMapData.buildMapImageFromOSMTiles(width-2*MARGIN,height-2*MARGIN, (-1)*maxLatitude, (-1)*minLatitude, minLongitude, maxLongitude,
					tileServerURL, userAgent, osmZoomLevelChange, maxTileDownloads, updateMessageConsumer); // TODO!!
		} catch (Exception ex) { // getting the image online failed
			ex.printStackTrace();
			return "Exception: " + ex.toString();
		}
		if (mapImg == null) { // getting the image online failed
			return "mapImg == null";
		} else {
			// -> https://stackoverflow.com/questions/9131678/convert-a-rgb-image-to-grayscale-image-reducing-the-memory-in-java
			if (grayscale) {
				// convert mapImg to grayscale:
				java.awt.image.ImageFilter filter = new javax.swing.GrayFilter(true, graynessPercentage); // JavaDoc: "an int in the range 0..100 that determines the percentage of gray, where 100 is the darkest gray, and 0 is the lightest"
				java.awt.image.ImageProducer producer = new java.awt.image.FilteredImageSource(mapImg.getSource(), filter);  
				mapImg = java.awt.Toolkit.getDefaultToolkit().createImage(producer);
			}
			// !!!!! Remove Transparent Lines: !!!!!
			updateMessageConsumer.accept("Drawing map background: Beginning to remove transparent pixels...");
			mapImg = ImageRepairTools.removeTransparentPixels(mapImg);
			updateMessageConsumer.accept("Drawing map background: Finished removing transparent pixels.");
			// -> https://docs.oracle.com/javase/tutorial/2d/images/drawimage.html
			// boolean Graphics.drawImage(Image img, int x, int y, ImageObserver observer);
			// The x,y location specifies the position for the top-left of the image. (...) The observer parameter (..) usually is null.
			this.img.getGraphics().drawImage(mapImg, MARGIN, MARGIN, null);
			return ""; //successful
		}
	}

	// Helper functions:

	private void drawFrame() {
		Graphics gr = this.img.getGraphics();
		gr.setColor(Color.BLACK);
		gr.drawRect(MARGIN, MARGIN, width-2*MARGIN, height-2*MARGIN);
		// drawRect(int x, int y, int width, int height) – Draws the outline of the specified rectangle.
	}

	/**
	* Assuming (0,0) is at top left corner
	* @param height 2*MARGIN is already included!
	* @return MARGIN is added on later!
	*/
	private static int latitudeToYValue(double latitude, int height, double minLat, double maxLat) {
		double proportion = (latitude-minLat)/(maxLat-minLat);
		return (int)(proportion*(height-2*MARGIN));
	}

	/**
	* Assuming (0,0) is at top left corner
	* @param width 2*MARGIN is already included!
	* @return MARGIN is added on later!
	*/
	private static int longitudeToXValue(double longitude, int width, double minLong, double maxLong) {
		double proportion = (longitude-minLong)/(maxLong-minLong);
		return (int)(proportion*(width-2*MARGIN));
	}

	// For drawing the starting "X" character:
	// -> https://stackoverflow.com/questions/5829703/java-getting-a-font-with-a-specific-height-in-pixels
	private static Font getPixelDefinedFont(String name, int pixelHeight, Graphics gr) {
		// try out which font size is correct, a binary search would be more efficient but a complete overkill for an application this small
		Font answerFont;
		java.awt.FontMetrics fm;
		for (int i = 0; i <= 1000; i++) { // stop search at some point
			answerFont = new Font(name, Font.PLAIN, i);
			fm = gr.getFontMetrics(answerFont); // -> https://docs.oracle.com/javase/tutorial/2d/text/measuringtext.html
			if (fm.getAscent() >= pixelHeight) { // found!! //  = "The font ascent is the distance from the font's baseline to the top of most alphanumeric characters." !! getHeight() = getAscent() + getDescent() + getLeading() !!
				return answerFont;
			}
		}
		return null; // search failed!
	}

	// ---------- ---------- NEW: Legend Feature ---------- ----------

	public void drawSquareWithTwoDigitNumber(int x, int y, int sideLength, Color clr, int twoDigitNumber) throws IllegalArgumentException {
		// 0) Turn the twoDigitNumber into a String (with leading zero for numbers < 10)
		String str;
		if (twoDigitNumber < 0 || twoDigitNumber > 99) {
			throw new IllegalArgumentException("drawSquareWithTwoDigitNumber(): twoDigitNumber not bewtween 0 and 99!");
		} else {
			java.text.DecimalFormat df = new java.text.DecimalFormat("00"); // -> https://stackoverflow.com/questions/50532/how-do-i-format-a-number-in-java
			str = df.format(twoDigitNumber); // 3 -> "03", 11 -> "11", etc.
		}
		// 1) Draw the Square:
		Graphics gr = this.img.getGraphics();
		gr.setColor(clr);
		/*
		fillRect
		public abstract void fillRect(int x, int y, int width, int height)
		Fills the specified rectangle. The left and right edges of the rectangle are at x and x + width - 1.
		The top and bottom edges are at y and y + height - 1. The resulting rectangle covers an area width pixels wide by height pixels tall.
		The rectangle is filled using the graphics context's current color.
		*/
		gr.fillRect(x, y, sideLength, sideLength);
		/* ---- Works, but looks very confusing to the eye... ----
		// 2) Invert the Color:
		int red = clr.getRed();
		int green = clr.getGreen();
		int blue = clr.getBlue();
		// JavaDoc: Color(int r, int g, int b) - Creates an opaque sRGB color with the specified red, green, and blue values in the range (0 - 255).
		Color invColor = new Color(255-red,255-green,255-blue);
		// gr.setColor(invColor);
		*/
		// 2) Choose an appropriate text color (either black or white):
		Color txtColor;
		if (isColorBright(clr)) { // if Color clr is bright:
			txtColor = Color.BLACK;
		} else {
			txtColor = Color.WHITE;
		}
		// 3) Draw the text (two-digit number):
		char char1 = str.charAt(0);
		char char2 = str.charAt(1);
		// public void drawCharacter(char chr, int centerX, int centerY, int size, Color clr) {...}
		int size = sideLength - 6;
		int yPos = y + (sideLength/2);
		this.drawCharacter(char1, x + (sideLength)/3, yPos, size, txtColor);
		this.drawCharacter(char2, x + (2*sideLength)/3, yPos, size, txtColor);
	}

	public void drawLegend(int[] numbers, Color[] colors) {
		if (numbers == null || colors == null || numbers.length == 0 || colors.length == 0 || numbers.length != colors.length) {
			return;
		}
		assert numbers.length == colors.length;
		// Draw from right to left!!!
		final int GAP = 2;
		final int SQUARE_SIZE = MARGIN-2*GAP;
		for (int i = numbers.length-1; i>=0; i--) {
			try {
				int x = this.width + (-MARGIN + GAP + 1) - (numbers.length-i)*(GAP+SQUARE_SIZE); // !!! (partly found out through testing...?!)
				this.drawSquareWithTwoDigitNumber(x, GAP, SQUARE_SIZE, colors[i], numbers[i]);
			} catch (IllegalArgumentException ex) {
				// number not between 0 and 99 -> proceed with next number
			}
		}
	}

	// Used in drawSquareWithTwoDigitNumber() to choose text color as either black or white
	private static boolean isColorBright(Color clr) {
		/*
		https://docs.oracle.com/javase/7/docs/api/java/awt/Color.html#RGBtoHSB(int,%20int,%20int,%20float[])
		RGBtoHSB
		public static float[] RGBtoHSB(int r, int g, int b, float[] hsbvals)
		Converts the components of a color, as specified by the default RGB model, to an equivalent set of values for hue, saturation, and brightness that are the three components of the HSB model.
		If the hsbvals argument is null, then a new array is allocated to return the result. Otherwise, the method returns the array hsbvals, with the values put into that array.
		Parameters:
			r - the red component of the color
			g - the green component of the color
			b - the blue component of the color
			hsbvals - the array used to return the three HSB values, or null
		Returns:
			an array of three elements containing the hue, saturation, and brightness (in that order), of the color with the indicated red, green, and blue components.
		*/
		int red = clr.getRed();
		int green = clr.getGreen();
		int blue = clr.getBlue();
		float[] hsbvals = Color.RGBtoHSB(red, green, blue, null);
		float brightness = hsbvals[2];
		// JavaDoc Color.HSBtoRGB :
		// "The saturation and brightness components should be floating-point values between zero and one (numbers in the range 0.0-1.0). The hue component can be any floating-point number."
		return (brightness >= 0.5f);
	}

}