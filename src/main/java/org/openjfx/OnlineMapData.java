package org.openjfx;

// ========== Utility class for handling all the online stuff (getting the background map images) ==========
// All latitudes here are the ACTUAL latitudes, i.e. North positive & South negative!!!
import java.awt.Graphics;
import java.awt.Image;
import java.awt.image.BufferedImage;
import java.lang.Math; // for the abs() function
import java.util.Scanner; // for user input regarding a custom zoom level
// For debugging version only:
import java.awt.Color;
import java.awt.Font;
import java.util.function.Consumer;

public final class OnlineMapData {

	public static final boolean DEBUG = false;

	private OnlineMapData() {} // private constructor to prevent instantiation (Utility class)

	/*
	* Placeholder function, in case we might want to use a different API than OpenStreetMaps later on
	*
	public static Image buildMapImage(int width, int height, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude) {
		return buildMapImageFromOSMtiles(width, height, minLatitude, maxLatitude, minLongitude, maxLongitude);
	}*/

	/**
	* Puts the images/tiles (represented by the OSMTile class) together to form
	* @return a map image with the desired boundaries
	*/
	public static Image buildMapImageFromOSMTiles(int width, int height, double minLatitude, double maxLatitude, double minLongitude, double maxLongitude,
												   String tileServerURL, String userAgent, int osmZoomLevelChange, int maxTileDownloads, // !!NEW in GUI version!!
												   Consumer<String> updateMessageConsumer) { // !!NEW in GUI version!!
		// Javadoc Constructor: BufferedImage(int width, int height, int imageType)
		BufferedImage img = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB); // (ARGB enables transparency)
		// Step 1: Determine the optimum(!) zoom level for this map because
		// a) a zoom level too low would make it impossible(!) to fill the map & all the points would be in the wrong places
		// b) a zoom level too high would require unneccessary downloads from the OSM API (might result in an IP ban or similar)
		// ----- Old Method 2: (We only want to scale the tiles DOWN later & not up)
		int zoomLevel = 20; // (20 = OSM max zoom level)
		double mapWidthInDegrees = maxLongitude-minLongitude;
		for (int z = 0; z <= 20; z++) { // "zoom in" until map is guaranteed to be fillable (maximum OSM zoom Level is 20)
			double tileWidthInDegrees = OSMTile.getTileWidthInDegrees(z); // returns: 360.0/Math.pow(2,zoomLevel);
			int minNumberOfHorizontalTilesNeeded = (int)Math.ceil(mapWidthInDegrees/tileWidthInDegrees);
			int maxNumberOfHorizontalTilesNeeded = minNumberOfHorizontalTilesNeeded + 1;
			// !!! ONLY NOW DO WE CONSIDER PIXELS: !!!
			final int tileWidthInPixels = 256; 
			int mapWidthInPixels = width;
			if (minNumberOfHorizontalTilesNeeded*tileWidthInPixels < mapWidthInPixels) { // even at full width (256px) the tiles cannot fill the entire width of the map
				continue; // zoom level is still too small
			} else { // zoom level is not too small anymore:
				zoomLevel = z;
				break;
			}
		}
		// !!! New Method: Determine highest zoom level that is still unable to fill the map without enlarging the tiles beyond 256x256pixels
		zoomLevel--;
		/* ------------------------------------ Old Method 1 ------------------------------------
		//   --> find the highest zoom level that cannot(!) fill the map & increase that by 1
		//     --> find out by trial and error: begin with the highest zoomLevel & decrease until entire map image cannot be filled anymore...
		int zoomLevel = 20; // highest possible zoom level is 20 according to https://wiki.openstreetmap.org/wiki/Zoom_levels
		boolean mapCanBeFilled = true;
		while (mapCanBeFilled) { // find the highest zoom level that cannot(!) fill the map:
			zoomLevel--;
			// Gather all the necessary values to decide:
			final int tileWidthPixels = 256;
			int minHorizontalTileAmountNeededAtMaxRes = (int)Math.ceil(width/tileWidthPixels); // !!! CRUCIAL FORMULA !!!
			int maxHorizontalTileAmountNeededAtMaxRes = (int)Math.ceil(width/tileWidthPixels) + 1; // !!! CRUCIAL FORMULA !!!
			double tileWidthDegrees = OSMTile.getTileWidthInDegrees(zoomLevel); // returns: 360.0/Math.pow(2,zoomLevel);
			double mapWidthDegrees = maxLongitude-minLongitude;
			int minHorizontalTileAmountProvided = (int)Math.ceil(mapWidthDegrees/tileWidthDegrees);
			int maxHorizontalTileAmountProvided = (int)Math.ceil(mapWidthDegrees/tileWidthDegrees) + 1;
			// Decide whether map can be filled:
			mapCanBeFilled = !(maxHorizontalTileAmountProvided < minHorizontalTileAmountNeededAtMaxRes);
		} // zoomLevel is now the highest that cannot(!) fill the map
		zoomLevel++; // increase that zoom level by 1 so we can juuust fill the map again!
		------------------------------------ Old Method 1 ------------------------------------ */

		zoomLevel += osmZoomLevelChange; // !!NEW in GUI version!!

		/* ----- Console input is now redundant in new GUI version!!: -----
		System.out.println("Info: OpenStreetMap Zoom Level " + zoomLevel + " was chosen (for each tile).");
		// ---------- NEW: User Prompt to specify own zoom level ----------
		System.out.print("Prompt: You can also specifiy a custom zoom level (only recommended after having tested it with the default), or hit ENTER to use default: ");
		Scanner scanner1 = new Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
		String userInput1 = scanner1.nextLine();
		if ("".equals(userInput1)) {
			// use calculated zoom level
		} else {
			try {
				zoomLevel = Integer.parseInt(userInput1);
			} catch (NumberFormatException ex) {
				// use calculated zoom level
			}
		}
		 ----- ----- ----- */

		// ---------- ---------- ---------- ---------- ---------- ----------
		// Step 2: Now, actually build the map out of the tiles row by row, beginning top left (using the smart functions of the OSMTile class):
		final OSMTile topLeftTile = new OSMTile(maxLatitude, minLongitude, zoomLevel); // "starting position"
		final OSMTile bottomRightTile = new OSMTile(minLatitude, maxLongitude, zoomLevel); // "end position"
		OSMTile currentTile = topLeftTile.duplicate();
		OSMTile previousRowBeginning = topLeftTile.duplicate();
		int downloadCounter = 0; // to prevent stressing the Web-API too much in case of a coding error, to prevent an IP ban
		int downloadFailCounter = 0; // !!NEW in GUI version!! (because we're not printing to console anymore, see below)
		//System.out.println("Info: Downloading OSM Tiles from \"" + OSMTile.defaultPlaceholderURL + "\":"); // !!REMOVED in GUI version!!
		//final int MAX_DOWNLOADS = 100; // !!REMOVED in GUI version!!
		//final int ABSOLUTE_MAX_DOWNLOADS = 500; // !!REMOVED in GUI version!!
		//boolean proceed = false; // !!REMOVED in GUI version!!
		while (true) {
			/* ----- ----- ----- !!REMOVED in GUI version!!: ----- ----- -----
			if ((!proceed) && (downloadCounter > MAX_DOWNLOADS)) {
				System.out.println("Error: Too many OSM tile downloads (" + downloadCounter + ")!!");
				System.out.print("Prompt: Enter PROCEED to proceed anyways (this may lead to your IP being blocked by OSM), or anything else to cancel downloads: ");
				Scanner scanner2 = new Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
				String userInput2 = scanner2.nextLine();
				if (!("PROCEED".equals(userInput2))) { // if anything except "PROCEED" was entered:
					return img; // return the map image as far as it got generated
				} else {
					proceed = true;
				}
			} else if (downloadCounter > ABSOLUTE_MAX_DOWNLOADS) {
				System.out.println("Error: Waay too many OSM tile downloads (" + downloadCounter + ")!!");
				return img; // return the map image as far as it got generated
			}
			 ----- ----- ----- */

			// !!NEW in GUI version: user-specified max tile download number (also no "proceed" and "ABSOLUTE_MAX" anymore!)
			if (downloadCounter > maxTileDownloads) {
				// No error message or anything, just stop downloading at this point.
				// The user is going to notice the missing tiles anyway!
				return img;
			}

			// --- Draw the current tile onto the BufferedImage: ---
			// $$$ -> https://docs.oracle.com/javase/tutorial/2d/images/drawimage.html
			// $$$ boolean Graphics.drawImage(Image img, int x, int y, ImageObserver observer);
			// $$$ The x,y location specifies the position for the top-left of the image. (...) The observer parameter (..) usually is null.
			double topLat = currentTile.getTopLatitude();
			double leftLong = currentTile.getLeftLongitude();
			double latitudesPerYpixel = (maxLatitude-minLatitude)/height; //!
			double longitudesPerXpixel = (maxLongitude-minLongitude)/width; //!
			int x = longitudeRegularDegreesToTopLeftXPixels(leftLong, minLongitude, longitudesPerXpixel); // see 2 helper functions in this(!) class
			int y = latitudeRegularDegreesToTopLeftYPixels(topLat, maxLatitude, latitudesPerYpixel);
			// Function: public Image downloadScaledImage(int totalPixelWidth, int totalPixelHeight, double totalLatitudeHeight, double totalLongitudeWidth) {...}
			Image downloadedImage = currentTile.downloadScaledImage(width, height, Math.abs(minLatitude-maxLatitude), Math.abs(minLongitude-maxLongitude),
					tileServerURL, userAgent); // !!THESE TWO PARAMS ARE NEW in GUI version!!
			if (downloadedImage == null) {
				//System.out.println("(Error: Tile download no. " + downloadCounter + " failed!)"); // !!REMOVED in GUI version!!
				downloadFailCounter++; // !!NEW in GUI version!!
				// if one tile couldn't be downloaded for whatever reason, don't cancel everything, just proceed as normal...
			} else {
				//System.out.print("."); // !!! creates a series of dots (one for every successful download) to show progress somehow // !!REMOVED in GUI version!!
				img.getGraphics().drawImage(downloadedImage, x, y, null);
				if (DEBUG) { // index every tile printed with a little red number (at all 4 corners)
					final int LARGE_BIT = 25;
					final int SMALL_BIT = 12;
					Graphics gr = img.getGraphics();
					gr.setColor(Color.RED);
					Font f = new Font("Dialog", Font.PLAIN, 12);
					gr.setFont(f);
					gr.drawString(""+downloadCounter, x+SMALL_BIT, y+LARGE_BIT); // "The baseline of the leftmost character is at position (x, y)"
					gr.drawString(""+downloadCounter, x+downloadedImage.getWidth(null)-LARGE_BIT, y+LARGE_BIT);
					gr.drawString(""+downloadCounter, x+SMALL_BIT, y+downloadedImage.getHeight(null)-SMALL_BIT);
					gr.drawString(""+downloadCounter, x+downloadedImage.getWidth(null)-LARGE_BIT, y+downloadedImage.getHeight(null)-SMALL_BIT);
				}
			}
			downloadCounter++;
			// !!NEW in GUI version!! (no .......... in console anymore!):
			updateMessageConsumer.accept("Drawing map background: Download progress: " + downloadCounter + " (of which " + downloadFailCounter + " failed)");
			// --- If we're currently at the bottom-right tile, we're done - exit the while loop: ---
			if (currentTile.equals(bottomRightTile)) {break;}
			// --- If we're currently below the bottom boundary of the image, we're also done (but someting went wrong) - exit the while loop: ---
			if (currentTile.getTopLatitude() <= minLatitude) {System.out.println("Error: currentTile.getTopLatitude() <= minLatitude");break;}
			// --- Go to the next tile (i.e. the one to its right): ---
			currentTile = currentTile.getTileRight();
			// --- If the left of the (next) tile is now right of the right boundary of the image, go to next row: ---
			if (currentTile.getLeftLongitude() >= maxLongitude) {
				previousRowBeginning = previousRowBeginning.getTileBelow();
				currentTile = previousRowBeginning.duplicate();
			}
		}
		//System.out.print("\n"); // line break after the series of dots (".........") // !!REMOVED in GUI version!!
		return img;
	}

	private static int latitudeRegularDegreesToTopLeftYPixels(double latitude, double topMaxLatitude, double latitudesPerYpixel) {
		return (int)((topMaxLatitude-latitude)*(1/latitudesPerYpixel));
	}

	private static int longitudeRegularDegreesToTopLeftXPixels(double longitude, double leftMinLongitude, double longitudesPerXpixel) {
		return (int)((longitude-leftMinLongitude)*(1/longitudesPerXpixel));
	}

}
