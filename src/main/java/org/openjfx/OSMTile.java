package org.openjfx;

/* ------------------ OpenStreetMaps ------------------
	-> Note: OpenStreetMap uses the Mercator Projection which has evenly spaced longitudes but not latitudes!
	https://wiki.openstreetmap.org/wiki/Zoom_levels:
	Level  # Tiles             Tile width         m / pixel      ~ Scale          Examples of areas to represent
	                           (° of longitudes)  (on Equator)   (on screen)
	0      1                   360                156 412        1:500 million    whole world
	1      4                   180                78 206         1:250 million
	2      16                  90                 39 103         1:150 million    subcontinental area
	3      64                  45                 19 551         1:70 million     largest country
	4      256                 22.5               9 776          1:35 million
	5      1 024               11.25              4 888          1:15 million     large African country
	6      4 096               5.625              2 444          1:10 million     large European country
	7      16 384              2.813              1 222          1:4 million      small country, US state
	8      65 536              1.406              610.984        1:2 million
	9      262 144             0.703              305.492        1:1 million      wide area, large metropolitan area
	10     1 048 576           0.352              152.746        1:500 thousand   metropolitan area
	11     4 194 304           0.176              76.373         1:250 thousand   city
	12     16 777 216          0.088              38.187         1:150 thousand   town, or city district
	13     67 108 864          0.044              19.093         1:70 thousand    village, or suburb
	14     268 435 456         0.022              9.547          1:35 thousand
	15     1 073 741 824       0.011              4.773          1:15 thousand    small road
	16     4 294 967 296       0.005              2.387          1:8 thousand     street
	17     17 179 869 184      0.003              1.193          1:4 thousand     block, park, addresses
	18     68 719 476 736      0.001              0.596          1:2 thousand     some buildings, trees
	19     274 877 906 944     0.0005             0.298          1:1 thousand     local highway and crossing details
	20     1 099 511 627 776   0.00025            0.149          1:5 hundred      A mid-sized building
*/

import javax.print.attribute.standard.Media;
import java.awt.*;
import java.io.*;
import java.lang.Math;
import java.net.URL;
import java.net.MalformedURLException;
import java.net.HttpURLConnection;
import java.net.URLConnection;
import java.util.HashMap;
import java.util.Scanner; // needed for customPlaceholderURL
// For debugging version only:
import java.awt.image.BufferedImage;

// ========== Used in the OnlineMapData class to represent an OpenStreetMap-Tile ==========
// All latitudes here are the ACTUAL latitudes, i.e. North positive & South negative!!!
public class OSMTile {

	public static final boolean DEBUG = false;

	public int x;
	public int y;
	public int zoomLevel;
	public Image img; // stored to prevent multiple unnecessary downloads from the Web-API

	// ----- ----- ----- ----- ----- ----- Static functions: ----- ----- ----- ----- ----- -----

	public static double getTileWidthInDegrees(int zoomLevel) {
		return 360.0/Math.pow(2,zoomLevel);
	}

	/**
	* @param urlString the URL of the image we want to download
	* @param userAgent !!NEW in GUI version!!: the User Agent to use when downloading the image
	 *                  (when this is either null or the empty String, the default User Agent is used!)
	* @return (returning null or throwing an Exception has exactly the same effect!)
	*/
	private static BufferedImage downloadImageFromURL(String urlString, // !!NEW in GUI version!!: BufferedImage instead of Image
											  String userAgent) // !!NEW in GUI version!!
			throws IOException, MalformedURLException {
		if (userAgent == null || "".equals(userAgent.trim())) {
			// The simple way we did it in the non-GUI version. However, no way to specify a custom User Agent!!
			return javax.imageio.ImageIO.read(new java.net.URL(urlString)); // -> https://stackoverflow.com/questions/5882005/how-to-download-image-from-any-web-page-in-java
		} else { // Download the image with a custom User Agent:
			// 1) --> https://stackoverflow.com/questions/5882005/how-to-download-image-from-any-web-page-in-java
			// 2) --> https://stackoverflow.com/questions/3360712/java-load-image-from-urlconnection
			URL url = new URL(urlString); // see 1)
			URLConnection con = url.openConnection();
			con.setRequestProperty("User-Agent", userAgent);
			Image img;
			try (BufferedInputStream in = new BufferedInputStream(con.getInputStream())) { // see 2)
				// Note: https://stackoverflow.com/questions/24362980/when-i-close-a-bufferedinputstream-is-the-underlying-inputstream-also-closed
				ByteArrayOutputStream byteArrayOut = new ByteArrayOutputStream();
				int c;
				while ((c = in.read()) != -1) {
					byteArrayOut.write(c);
				}
				img = Toolkit.getDefaultToolkit().createImage(byteArrayOut.toByteArray());
			}
			// ----- Now wait until the Image is fully loaded: -----
			// -> https://stackoverflow.com/questions/4260500/how-to-wait-until-an-image-is-fully-loaded-in-java
			// -> the dirty 'while (img.getWidth(null) == -1) {Thread.onSpinWait();}' is not enough waiting!!!
			// -> https://docs.oracle.com/javase/6/docs/api/java/awt/MediaTracker.html
			MediaTracker mediaTracker = new MediaTracker(new java.awt.Canvas()); // (we cannot pass null as the argument!)
			mediaTracker.addImage(img, 0);
			try {
				mediaTracker.waitForID(0);
			} catch (InterruptedException ex) {
				//
			}
			return ImageRepairTools.toBufferedImage(img);
		}
	}

	// ----- ----- ----- ----- ----- ----- Static functions. ----- ----- ----- ----- ----- -----

	// Constructor 1: "Standard/Original OSM Tile Identifiers"
	public OSMTile(int x, int y, int zoomLevel) {
		this.x = x;
		this.y = y;
		this.zoomLevel = zoomLevel;
	}

	// Constructor 2: Find the one(!) OSM tile with that zoomLevel that contains the coordinates specified
	// !!!Code copied from: https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames !!!
	/*
	public static String getTileNumber(final double lat, final double lon, final int zoom) {
		int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
		int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
		if (xtile < 0)
			xtile=0;
		if (xtile >= (1<<zoom))
			xtile=((1<<zoom)-1);
		if (ytile < 0)
			ytile=0;
		if (ytile >= (1<<zoom))
			ytile=((1<<zoom)-1);
		return("" + zoom + "/" + xtile + "/" + ytile);
	}
	*/
	public OSMTile(final double lat, final double lon, final int zoom) {
		this.zoomLevel = zoom;
		int xtile = (int)Math.floor( (lon + 180) / 360 * (1<<zoom) ) ;
		int ytile = (int)Math.floor( (1 - Math.log(Math.tan(Math.toRadians(lat)) + 1 / Math.cos(Math.toRadians(lat))) / Math.PI) / 2 * (1<<zoom) ) ;
		if (xtile < 0) {xtile=0;}
		if (xtile >= (1<<zoom)) {xtile=((1<<zoom)-1);}
		if (ytile < 0) {ytile=0;}
		if (ytile >= (1<<zoom)) {ytile=((1<<zoom)-1);}
		this.x = xtile;
		this.y = ytile;
	}

    @Override // -> https://www.geeksforgeeks.org/overriding-equals-method-in-java/
    public boolean equals(Object o) {
    	if (o == null) {return false;}
    	if (o == this) {return true;}
    	if (!(o instanceof OSMTile)) {return false;}
    	OSMTile t = (OSMTile) o; // Typecast
    	return (this.x==t.x && this.y==t.y && this.zoomLevel==t.zoomLevel);
    }

    // Deliberately NOT using Cloneable here!!
 	public OSMTile duplicate() {
 		return new OSMTile(this.x, this.y, this.zoomLevel);
 	}

	/**
	* Gives you the tile in already correctly scaled form:
	* (for parameters tileServerURL and userAgent see downloadImage())
	*/
	public Image downloadScaledImage(int totalPixelWidth, int totalPixelHeight, double totalLatitudeHeight, double totalLongitudeWidth,
									 String tileServerURL, String userAgent) {  // !!NEW in GUI version!!
		// First: Process the 4 arguments:
		double destVerticalLatitudesPerPixel = totalLatitudeHeight/totalPixelHeight;
		double destHorizontalLongitudesPerPixel = totalLongitudeWidth/totalPixelWidth;
		//
		Image nonScaledImage = this.downloadImage(tileServerURL, userAgent); // !!PARAMS NEW in GUI version!!
		if (nonScaledImage == null) { // error downloading
			return null;
		}
		int initWidth = nonScaledImage.getWidth(null); // (ImageObserver observer) --> should be 256px
		int initHeight = nonScaledImage.getHeight(null);
		double initVerticalLatitudesPerPixel = this.getLatitudeHeight()/initHeight;
		double initHorizontalLongitudesPerPixel = this.getLongitudeWidth()/initWidth; 
		// 
		int destWidth = (int)(initWidth*(initHorizontalLongitudesPerPixel/destHorizontalLongitudesPerPixel)); // !!!!!!!!!!!!!!! INVERSE !!!!!!!!!!!!!!!
		int destHeight = (int)(initHeight*(initVerticalLatitudesPerPixel/destVerticalLatitudesPerPixel)); // !!!!!!!!!!!!!!! INVERSE !!!!!!!!!!!!!!!
		/* ---REMOVED---  ---REMOVED---  ---REMOVED---  ---REMOVED---
		//Check that we are really just scaling down and not up (i.e. that we chose the correct zoom level before):
		if ((destWidth > initWidth) || (destHeight > initHeight)) {
			System.out.println("Error: Scaling OSM tile up instead of down!!");
			return null;
		}
		---REMOVED---  ---REMOVED---  ---REMOVED---  ---REMOVED--- */
		// JavaDoc: public Image getScaledInstance(int width, int height, int hints)
		//     If either width or height is a negative number then a value is substituted to maintain the aspect ratio of the original image dimensions.
		//     hints - flags to indicate the type of algorithm to use for image resampling.
		//     SCALE_DEFAULT, SCALE_FAST, SCALE_SMOOTH, SCALE_REPLICATE, SCALE_AREA_AVERAGING
		return nonScaledImage.getScaledInstance(destWidth, destHeight, Image.SCALE_SMOOTH);
	}

	/* ----- OLD WAY OF DOING IT (before GUI version): -----
	public static final String defaultPlaceholderURL = "https://a.tile.openstreetmap.de/${z}/${x}/${y}.png";
	// For alternatives see: https://wiki.openstreetmap.org/wiki/Tile_servers

	public static String customPlaceholderURL = ""; // global variable (lost when class gets unloaded! -> https://stackoverflow.com/questions/4646577/global-variables-in-java)
	// The first time a grab from defaultPlaceholderURL fails, the user will be asked whether to use a custom one.
	 */

	/**
	* ===== Uses OpenStreetMap Tile Server to get map tile as image =====
	* See: https://wiki.openstreetmap.org/wiki/Tile_servers
	* and: https://wiki.openstreetmap.org/wiki/Zoom_levels
	* example: https://a.tile.openstreetmap.org/7/63/42.png
	*
	* https://wiki.openstreetmap.org/wiki/Tiles
	* "Map tiles are typically 256×256 pixel images."
	* "Tiles are not always in these dimensions; for example there could be 64×64 pixel images for mobile use,
	* however 256×256 pixel images are a de facto standard. 512×512 pixel seems to be the usual size of high-resolution tiles."
	*
	* !!! -> https://stackoverflow.com/questions/8751482/patternsyntaxexception-illegal-repetition-when-using-regex-in-java
	* "The { and } are special in Java's regex dialect (...) You should escape them"
	*
	* @param tileServerURL placeholder URL, e.g. "https://a.tile.openstreetmap.de/${z}/${x}/${y}.png"
	* @param userAgent the HTTP User-Agent to use for downloading the image
	*/
	private Image downloadImage(String tileServerURL, String userAgent) {  // !!PARAMS NEW in GUI version!!
		//Just placeholder images for debugging(!):
		if (DEBUG) {
			if (this.img == null) {
				this.img = this.getPlaceholderImage();
			}
			return this.img;
		}
		// Non-debugging mode:
		// ----- ----- ----- !!NEW FEATURE: Temporary Storage: ----- ----- -----
		if (this.img == null) {
			this.img = getTemporarilyStoredTile(tileServerURL); // will return null if this tile is not temporarily stored
		}
		// ----- ----- ----- ----- ----- ----- ----- ----- ----- ----- -----  -----
		if (this.img == null) {
			assert !("".equals(tileServerURL));
			String url = tileServerURL.replaceFirst("\\$\\{x\\}", ""+this.x); // Regex $ means "matches the end of the string"
			url = url.replaceFirst("\\$\\{y\\}", ""+this.y); // -> https://stackoverflow.com/questions/8751482/patternsyntaxexception-illegal-repetition-when-using-regex-in-java
			url = url.replaceFirst("\\$\\{z\\}", ""+this.zoomLevel);
			// Actual download (of image from URL):
			try {
				this.img = downloadImageFromURL(url, userAgent);
				this.saveAsTemporarilyStoredTile(tileServerURL); //NEW
			} catch (Exception ex) {
				/* OLD:
				// ----- ----- Ask user whether to use an alternative URL for downloads: ----- -----
				System.out.println("--- Prompt: An error occured downloading a tile from URL: " + url);
				System.out.println("--- Error message: " + ex.toString());
				System.out.println("--- The currently used placeholder URL is: " + placeholderURL);
				System.out.println("--- If you'd like to change that, enter the new placeholder URL below, if not just hit ENTER; or enter \"R\" to reset to default:");
				Scanner scanner = new Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
				String userInput = scanner.nextLine();
				if ("".equals(userInput)) {
					// don't chanage anything
				} else if ("R".equals(userInput)) {
					customPlaceholderURL = ""; // reset to default (defaultPlaceholderURL)
				} else {
					customPlaceholderURL = userInput;
				}
				// ----- ----- ----- ----- ----- */
				this.img = null;
				// returning null will cause downloadScaledImage() to return null which will cause an error message in OnlineMapData.buildMapImageFromOSMtiles()
			}
		}
		// When a tile downloaded is not 256×256 pixels!!:
		if ((this.img != null) && ((this.img.getWidth(null) != 256) || (this.img.getHeight(null) != 256))) {
			System.out.println("Error: OSM tile downloaded is not 256×256 pixels!!");
			return null;
		}
		return this.img;
	}
	/**
	* @return a placeholder instead of the actual map tile (for debugging!)
	*/
	public Image getPlaceholderImage() {
			BufferedImage placeholderImg = new BufferedImage(256, 256, BufferedImage.TYPE_INT_ARGB);
			Graphics gr = placeholderImg.getGraphics();
			// Create a chessboard pattern:
			if ((this.x+this.y) % 2 == 0) {
				gr.setColor(Color.LIGHT_GRAY);
			} else {
				gr.setColor(Color.DARK_GRAY);
			}
			// Javadoc: fillRect(int x, int y, int width, int height)
			// " The left and right edges of the rectangle are at x and x + width - 1.
			// The top and bottom edges are at y and y + height - 1.
			// The resulting rectangle covers an area width pixels wide by height pixels tall."
			gr.fillRect(0, 0, 256, 256);
			// Text:
			gr.setColor(Color.BLACK);
			String str = "" + this.zoomLevel + "/" + this.x + "/" + this.y;
			int fontsize = 36-3*Math.max(0,str.length()-8); // decrease the fontsize for longer strings
			Font f = new Font("Dialog", Font.PLAIN, fontsize);
			gr.setFont(f);
			gr.drawString(str, 50, (256/2)+(36/2)); // "The baseline of the leftmost character is at position (x, y)"
			return placeholderImg;
	}

	/*
	Code copied from: https://wiki.openstreetmap.org/wiki/Slippy_map_tilenames

	class BoundingBox {
    	double north;
    	double south;
    	double east;
    	double west;   
  	}
	BoundingBox tile2boundingBox(final int x, final int y, final int zoom) {
		BoundingBox bb = new BoundingBox();
		bb.north = tile2lat(y, zoom);
		bb.south = tile2lat(y + 1, zoom);
		bb.west = tile2lon(x, zoom);
		bb.east = tile2lon(x + 1, zoom);
		return bb;
	}

	static double tile2lon(int x, int z) {
		return x / Math.pow(2.0, z) * 360.0 - 180;
	}

	static double tile2lat(int y, int z) {
		double n = Math.PI - (2.0 * Math.PI * y) / Math.pow(2.0, z);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}
	*/

	// ------------------ Latitude (North/South) functions: ------------------

	public double getTopLatitude() { // i.e. North Bounding Box
		double n = Math.PI - (2.0 * Math.PI * this.y) / Math.pow(2.0, this.zoomLevel);
		return Math.toDegrees(Math.atan(Math.sinh(n)));
	}

	public double getBottomLatitude() { // i.e. South Bounding Box
		return this.getTileBelow().getTopLatitude(); // y+1
	}

	public double getCenterLatitude() {
		return (this.getTopLatitude()+this.getBottomLatitude())/2.0;
	}

	public double getLatitudeHeight() {
		return java.lang.Math.abs(this.getTopLatitude()-this.getBottomLatitude());
	}

	// ------------------ Longitude (East/West) functions: ------------------

	public double getLeftLongitude() { // i.e. West Bounding Box
		return this.x / Math.pow(2.0, this.zoomLevel) * 360.0 - 180;
	}

	public double getRightLongitude() { // i.e. East Bounding Box
		return this.getTileRight().getLeftLongitude(); // x+1
	}

	public double getCenterLongitude() {
		return (this.getLeftLongitude()+this.getRightLongitude())/2.0;
	}

	public double getLongitudeWidth() {
		return java.lang.Math.abs(this.getLeftLongitude()-this.getRightLongitude());
	}

	// ------------------ Relation to other tiles: ------------------------------------

	public OSMTile getTileAbove() {
		return new OSMTile(this.x, this.y-1, this.zoomLevel);
	}

	public OSMTile getTileRight() {
		return new OSMTile(this.x+1, this.y, this.zoomLevel);
	}

	public OSMTile getTileBelow() {
		return new OSMTile(this.x, this.y+1, this.zoomLevel);
	}

	public OSMTile getTileLeft() {
		return new OSMTile(this.x-1, this.y, this.zoomLevel);
	}

	/* *
	* Static helper function for downloading an image from a URL because apparently
	* just doing return javax.imageio.ImageIO.read(new java.net.URL(url));
	* throws an IOException!!
	* -> use temporary files:
	* @return returning null or throwing an Exception has exactly the same effect!  
	*
	private static Image downloadImageFromURL(String urlString) throws IOException, MalformedURLException {
		// Won't work with openstreetmap.org !!!:
		return javax.imageio.ImageIO.read(new java.net.URL(urlString)); // -> https://stackoverflow.com/questions/5882005/how-to-download-image-from-any-web-page-in-java
		/*
		// -> https://stackoverflow.com/questions/10292792/getting-image-from-url-java :
		File temporaryFile = File.createTempFile("temp_img",".png"); // -> https://docs.oracle.com/javase/7/docs/api/java/io/File.html#createTempFile(java.lang.String,%20java.lang.String)
		URL url = new URL(urlString);
    	InputStream is = url.openStream();
    	OutputStream os = new FileOutputStream(temporaryFile);
    	byte[] b = new byte[2048];
    	int length;
		while ((length = is.read(b)) != -1) {
			os.write(b, 0, length);
		}
		is.close();
		os.close();
		// Now read the image from "temp_img.png"
		return javax.imageio.ImageIO.read(temporaryFile);
		*
	}*/




	// ---> NEW! TEMPORARY STORAGE FUNCTIONS, cross-platform AND much simpler code, saved in memory (in a static Collection) instead of on disk:

	// Format: "z-x-y-PLACEHOLDER_URL" --mapped to--> Image
	final static HashMap<String, Image> temporarilyStoredImages = new HashMap<>();

	private Image getTemporarilyStoredTile(String tileServerURL) {
		return temporarilyStoredImages.get("" + this.zoomLevel + "-" + this.x + "-" + this.y + "-" + tileServerURL);
		// (will return null if no mapping exists for that key)
	}

	private void saveAsTemporarilyStoredTile(String tileServerURL) {
		temporarilyStoredImages.put("" + this.zoomLevel + "-" + this.x + "-" + this.y + "-" + tileServerURL
				, this.img);
	}



	/* ===== OLD WAY OF DOING IT: =====

	// ---------- ---------- (OLD) TEMPORARY STORAGE FUNCTIONS, entirely optional, only work on Mac OS so far: ---------- ----------

	/**
	* New feature:
	* when creating many maps of the (approximate) same area after each other, prevent that we have to download a
	* tile more than once, by storing it in a temporary folder
	* useful because a) it's faster
	*            and b) we don't stress the OSM tile server any more than we need to!!
	*
	private Image getTemporarilyStoredTile() {
		if (!osSupportsTempStorage()) {
			return null;
		}
		// The file name of a temporarily stored tile is: "z-x-y.png"
		String fileName = "" + this.zoomLevel + "-" + this.x + "-" + this.y + ".png";
		File homeDir = getHomeDirectory();
		if (homeDir == null) {
			return null; // couldn't get home directory (unsopported Operating System)
		}
		File tempDir = new File(homeDir, "PicsToMap-Temporarily-Stored-OSM-Tiles");
		if (!tempDir.exists() || !tempDir.isDirectory()) {
			return null; // temp folder doesn't even exist
		}
		File tempFile = new File(tempDir, fileName);
		if (tempFile.exists()) {
			Image returnImage = null;
			try {
				returnImage = javax.imageio.ImageIO.read(tempFile);
			} catch (IOException ioex) {
				return null; // IO error!
			}
			return returnImage;
		} else {
			return null;
		}
	}

	private void saveAsTemporarilyStoredTile() {
		if (osSupportsTempStorage()) {
			// The file name of a temporarily stored tile is: "z-x-y.png"
			String fileName = "" + this.zoomLevel + "-" + this.x + "-" + this.y + ".png";
			File homeDir = getHomeDirectory();
			if (homeDir == null) {
				return; // couldn't get home directory (unsopported Operating System)
			}
			File tempDir = new File(homeDir, "PicsToMap-Temporarily-Stored-OSM-Tiles");
			if (!tempDir.exists()) { // tempDir hasn't been created yet:
				tempDir.mkdir(); // JavaDoc: "Creates the directory named by this abstract pathname."
				// See also: -> https://stackoverflow.com/questions/3634853/how-to-create-a-directory-in-java
			} else if (!tempDir.isDirectory()) { // tempDir is there but its a file and not a folder!!!
				return; // error!
			}
			File tempFile = new File(tempDir, fileName);
			if (tempFile.exists()) {
				return; // this tile is already temporarily stored!!
			} else {
				// temporarily store this tile IF it has already been downloaded:
				if (this.img != null) {
					// See also: Map.saveTo()
					try {
						// javax.imageio.ImageIO.write(this.img, "png", tempFile); // save a PNG version
						// method ImageIO.write(RenderedImage,String,File) is not applicable
      					// (argument mismatch; Image cannot be converted to RenderedImage)
      					// -> https://stackoverflow.com/questions/13605248/java-converting-image-to-bufferedimage
      					if (this.img instanceof BufferedImage) { // simple cast is possible!
      						javax.imageio.ImageIO.write((BufferedImage)this.img, "png", tempFile);
      					} else { // casting not possible, convert Image to BufferedImage:
      						// Create a buffered image with transparency
    						BufferedImage bimage = new BufferedImage(this.img.getWidth(null), this.img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
    						// Draw the image on to the buffered image
							java.awt.Graphics2D bGr = bimage.createGraphics();
    						bGr.drawImage(this.img, 0, 0, null);
    						bGr.dispose();
    						javax.imageio.ImageIO.write(bimage, "png", tempFile);
      					}
      					
					} catch (IOException ioex) {
						// IO error!
					}
				}
			}
		}
	}

	// Little Helper Function:
	private static boolean osSupportsTempStorage() {
		// -> https://stackoverflow.com/questions/228477/how-do-i-programmatically-determine-operating-system-in-java
		String operatingSystem = System.getProperty("os.name"); // e.g. "Mac OS X" or "Windows 8.1"
		if ("Mac OS X".equals(operatingSystem)) {
			return true; // this functionality is currently only supported/tested on Mac OS !!!
		} else {
			return false;
		}
	}

	private static File getHomeDirectory() {
		if (osSupportsTempStorage()) {
			// -> https://stackoverflow.com/questions/9677692/getting-my-documents-path-in-java
			String homeDirPath = System.getProperty("user.home"); // e.g. "/Users/kendrick"
			return new File(homeDirPath);
		} else {
			return null;
		}
	}


	 */

}
