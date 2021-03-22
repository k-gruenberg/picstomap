package org.openjfx;

import java.io.File;
import java.util.Comparator; // for sorting an Array with null objects!
import java.lang.Math; // needed in: removeOutliers()
import java.util.ArrayList; // needed in: removeOutliers()
import java.awt.Color; // needed in: colorWheel()

public final class Main {

	public static final boolean DEBUG = false; // introduced for removeOutliers()

	public static void main(String[] args) {
		// --- Little Testing Area ---
		/*Picture[] testPicArray = new Picture[3];
		testPicArray[0] = new Picture(1234567, 11.223344, 22.334455);
		testPicArray[1] = new Picture(7654321, 33.445566, 44.556677);
		testPicArray[2] = new Picture(3141590, 55.667788, 66.778899);
		File testFile = new File(args[0], "test.csv");
		Picture.writeArrayToCSVFile(testPicArray, testFile);
		System.exit(0); //quit program*/
		// --- Little Testing Area ---
		// ------------------------------ USER COMMANDS: ------------------------------
		System.out.println("Info: Command Usage:");
		System.out.println("    {PATH_TO_FOLDER_WITH_PICTURES}"); // 1 arg
		System.out.println("    filterDate YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}"); // 3 args
		System.out.println("    filterDates YYYY/MM/DD YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}"); // 4 args
		System.out.println("    filterDateTime YYYY/MM/DD HH:MM HH:MM {PATH_TO_FOLDER_WITH_PICTURES}"); // 5 args
		System.out.println("    filterDatesTime YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM {PATH_TO_FOLDER_WITH_PICTURES}"); // 6 args
		System.out.println("    filterMillisecondRange mmm...mmm mmm...mmm {PATH_TO_FOLDER_WITH_PICTURES}"); // 4 args
		System.out.println("    test {PATH_TO_SINGLE_IMAGE}"); // 2 args
		System.out.println("    getMapImageOnly {CENTER_LATITUDE} {CENTER_LONGITUDE} {RADIUS_IN_KM} {PATH_TO_SAVE_DEST}"); // 5 args

		String pathToFolderWithPictures;
		PictureFilter picFilter;
		try {
			switch (args.length) {
				case 0:
					System.out.println("Error: No arguments given. Please provide path to folder with pictures as command line argument.");
					return;
				case 1: // "{PATH_TO_FOLDER_WITH_PICTURES}""
					pathToFolderWithPictures = args[0];
					picFilter = new PictureFilter(); // (Constructor 0: don't filter at all)
					break;
				case 2: // "test {PATH_TO_SINGLE_IMAGE}"
					if ("test".equals(args[0])) {
						File f = new File(args[1]); // {PATH_TO_SINGLE_IMAGE}
						Picture testPic = Picture.createFromFile(f);
						if (testPic == null) {
							System.out.println("testPic == null !");
						} else {
							System.out.println("Picture.toString(): " + testPic.toString());
							System.out.println("Picture.printableDateTime(): " + testPic.printableDateTime());
						}
						EXIFFile exifile = new EXIFFile(f);
						System.out.println("EXIFFile.getStartOfTIFFHeader(): " + exifile.getStartOfTIFFHeader());
						System.out.println("EXIFFile.readTimestamp(): " + exifile.readTimestamp());
						double[] gPSLatitude = exifile.readEXIFRATIONAL(2, 3);
						System.out.println("EXIFFile.readEXIFRATIONAL(2, 3): " + gPSLatitude[0] + "," + gPSLatitude[1] + "," + gPSLatitude[2]);
						System.out.println("EXIFFile.readEXIFASCIIsingle(1): " + exifile.readEXIFASCIIsingle(1));
						double[] gPSLongitude = exifile.readEXIFRATIONAL(4, 3);
						System.out.println("EXIFFile.readEXIFRATIONAL(4, 3): " + gPSLongitude[0] + "," + gPSLongitude[1] + "," + gPSLongitude[2]);
						System.out.println("EXIFFile.readEXIFASCIIsingle(3): " + exifile.readEXIFASCIIsingle(3));
						// ----- ----- ----- Testing for daddy: ----- ----- -----
						/*double[] gPSTimeStamp = exifile.readEXIFRATIONAL(7, 3);
						System.out.println("EXIFFile.readEXIFRATIONAL(7, 3): " + gPSTimeStamp[0] + "," + gPSTimeStamp[1] + "," + gPSTimeStamp[2]);
						int[] gPSDateStamp = exifile.readEXIFTag(29, 2, 11); // 2=ASCII
						String dateStamp = "";
						for (int i = 0; i<gPSDateStamp.length; i++) {
							dateStamp += (char)gPSDateStamp[i];
						}
						System.out.println("EXIFFile.readEXIFTag(29, 2, 11): " + dateStamp);*/
						// ----- ----- ----- ----- ----- ----- ----- ----- -----
						exifile.close();
						return; // user wanted just a test
					} else {
						System.out.println("Error: Invalid command.");
						return;
					}
				case 3: // "filterDate YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}"
					if ("filterDate".equals(args[0])) {
						pathToFolderWithPictures = args[2];
						picFilter = new PictureFilter(args[1]); // Constructor 1: filterDate YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}
						break;
					} else {
						System.out.println("Error: Invalid command.");
						return;
					}
				case 4:
					if ("filterDates".equals(args[0])) { // "filterDates YYYY/MM/DD YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}"
						pathToFolderWithPictures = args[3];
						picFilter = new PictureFilter(args[1], args[2]); // Constructor 2: filterDates YYYY/MM/DD YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}
						break;
					} else if ("filterMillisecondRange".equals(args[0])) { // "filterMillisecondRange mmm...mmm mmm...mmm {PATH_TO_FOLDER_WITH_PICTURES}"
						pathToFolderWithPictures = args[3];
						try {
							picFilter = new PictureFilter(Long.parseLong(args[1]), Long.parseLong(args[2])); // Constructor -1: filterMillisecondRange mmm...mmm mmm...mmm {PATH_TO_FOLDER_WITH_PICTURES}LDER_WITH_PICTURES}
						} catch (NumberFormatException nfex) {
							System.out.println("Error: Invalid numbers supplied to 'filterMillisecondRange' command.");
							return;
						}
						break;
					} else {
						System.out.println("Error: Invalid command.");
						return;
					}
				case 5:
					if ("filterDateTime".equals(args[0])) { // "filterDateTime YYYY/MM/DD HH:MM HH:MM {PATH_TO_FOLDER_WITH_PICTURES}"
						pathToFolderWithPictures = args[4];
						picFilter = new PictureFilter(args[1], args[2], args[3]); // Constructor 3: filterDateTime YYYY/MM/DD HH:MM HH:MM {PATH_TO_FOLDER_WITH_PICTURES}
						break;
					} else if ("getMapImageOnly".equals(args[0])) { // "getMapImageOnly {CENTER_LATITUDE} {CENTER_LONGITUDE} {RADIUS_IN_KM} {PATH_TO_SAVE_DEST}"
						double actualCenterLatitude = Double.parseDouble(args[1]); // stackoverflow: "parseDouble() returns a primitive double value. valueOf() returns an instance of the wrapper class Double"
						double actualCenterLongitude = Double.parseDouble(args[2]);
						double radiusInKM = Double.parseDouble(args[3]);
						File folder = new File(args[4]); // {PATH_TO_SAVE_DEST}
						// ---------- Calculate Latitude Radius: ----------
						// -> https://www.thoughtco.com/degree-of-latitude-and-longitude-distance-4070616
						// "Degrees of latitude are parallel so, for the most part, the distance between each degree remains constant."
						// "All you need to know is that each minute (1/60th of a degree) is approximately one mile."
						//     equator: 110.567km ; Tropic of Cancer/Capricorn: 110.948km ; poles: 111.699km
						// -> https://de.wikipedia.org/wiki/Seemeile
						// "Sie soll 1/60 Breitengrad – also einer Winkelminute – am Äquator entsprechen, wurde aber später mit exakt 1852,0 m definiert."
						final double DEFINITION_NAUTICAL_MILE_IN_KILOMETERS = 1.852; // 1.852 km/nm
						final double LATITUDES_OF_A_NAUTICAL_MILE = 1.0/60.0; // 0.0166666 lats/nm
						double latitudeRadiusInNauticalMiles = radiusInKM/DEFINITION_NAUTICAL_MILE_IN_KILOMETERS; // nm = km / (km/nm)
						double latitudeRadius = latitudeRadiusInNauticalMiles*LATITUDES_OF_A_NAUTICAL_MILE; // lats = nm * (lats/nm)
						// ---------- Calculate Longitude Radius: ----------
						// -> https://de.wikipedia.org/wiki/Geographische_L%C3%A4nge
						// "Eine Bogenminute entspricht entlang des Äquators und entlang der Meridiane einer Strecke von
						// einer Seemeile bzw. 1852 Meter, während die Strecke der Bogenminute auf den Breitenkreisen φ 
						// (nördlich oder südlich des Äquators) um den Faktor cosφ kleiner ist. "
						double degreesNorthOrSouthOfEquator = Math.abs(actualCenterLatitude);
						double radiansNorthOrSouthOfEquator = (Math.PI/180.0)*degreesNorthOrSouthOfEquator ;
						double longitudeRadius = latitudeRadius / Math.cos(radiansNorthOrSouthOfEquator); // !!! / NOT * !!!
						// ---------- ---------- ---------- ---------- ----------
						double actualMinLatitude = actualCenterLatitude - latitudeRadius;
						double actualMaxLatitude = actualCenterLatitude + latitudeRadius;
						double actualMinLongitude = actualCenterLongitude - longitudeRadius;
						double actualMaxLongitude =  actualCenterLongitude + longitudeRadius;
						final int NUMBER_OF_POINTS = 0; // increase to make the center red point smaller
						System.out.println("Info: Beginning to draw map...");
						Map m = new Map((-1)*actualMaxLatitude, (-1)*actualMinLatitude, actualMinLongitude, actualMaxLongitude, NUMBER_OF_POINTS);
						String errorMsg = m.drawOnlineMapBackground();
						if (errorMsg.equals("")) {
							System.out.println("Info: Drawing the map background from online data was successful!");
						} else {
							System.out.println("Error: Drawing the map background from online data failed: " + errorMsg);
							return; // no point in proceeding here, as there are literally no points to be drawn in this mode...
						}
						m.drawPoint((-1)*actualCenterLatitude, actualCenterLongitude, false); // draw red point at specified center (last param is boolean:isStartPoint)
						m.drawHeadline("Center latitude: " + args[1] + " | Center longitude: " + args[2] + " | Radius in km: " + args[3]);
						//m.drawCaption("");
						System.out.println("Info: Now saving the map image...");
						File outputfile = new File(folder, "PicsToMap-MapImage-0.png"); // Constructor: public File(File parent, String child)
						int counter = 0;
						while (outputfile.exists()) { // to prevent using a name already taken!!
							counter++;
							outputfile = new File(folder, "PicsToMap-MapImage-" + counter + ".png");
						}
						m.saveTo(outputfile); // save a PNG version of map to outputfile - DONE!
						System.out.println("Info: Done!");
						return; // getMapImageOnly is a seperate command!!!
					} else {
						System.out.println("Error: Invalid command.");
						return;
					}
				case 6: // filterDatesTime YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM {PATH_TO_FOLDER_WITH_PICTURES}
					if ("filterDatesTime".equals(args[0])) {
						pathToFolderWithPictures = args[5];
						picFilter = new PictureFilter(args[1], args[2], args[3], args[4]); // Constructor 4: filterDatesTime YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM {PATH_TO_FOLDER_WITH_PICTURES}
						break;
					} else {
						System.out.println("Error: Invalid command.");
						return;
					}
				default:
					System.out.println("Error: Invalid command. Too many arguments.");
					return;
			}
		} catch (Exception ex) {
			System.out.println("Error: An Exception occured interpreting your command: " + ex.toString());
			return;
		}
		// ------------------------------ USER COMMANDS. ------------------------------

		// -> https://stackoverflow.com/questions/5694385/getting-the-filenames-of-all-files-in-a-folder
		File folder = new File(pathToFolderWithPictures); // may throw FileNotFoundException
		if (!folder.isDirectory()) { // VERY NEWLY ADDED!
			System.out.println("Error: This is not a directory: " + folder.getName());
			return;
		}
		File[] allFiles = folder.listFiles(); // all Files in the folder specified by the user in cmd line

		// ------------------------------ NEW FEATURE: store Pictures in CSV file (filter-independent!) ------------------------------
		Picture[] allPictures; // all Pictures in the folder, non-Pictures are NULL objects if its the first time the app is run in this folder, otherwise they're non-existant (CSV does not list NULLs)
		final String csvFileName = "PicsToMap-Analyzed-Data.csv";
		File csvFile = new File(folder, csvFileName); // public File(File parent, String child) throws NullPointerException if child is null
		if (csvFile.exists()) { // PicsToMap has already analyzed the files in this folder
			System.out.println("Info: '" + csvFileName + "' found, extracting the data from there. To prevent this, delete/rename this file and restart the program.");
			allPictures = Picture.arrayFromCSVFile(csvFile); //!!!
			System.out.println("Info: Finished extracting the data (" + allPictures.length + " Pictures) from '" + csvFileName + "'. Total number of files in folder: " + allFiles.length);
		} else { // it's the first time PicsToMap is analyzing the files in this folder
			System.out.println("Info: '" + csvFileName + "' not found, extracting the data from all the image files individually. This may take a while...");
			// ---------- Previous code (slightly changed): ---------- ---------- ---------- ---------- ---------- ----------
			allPictures = new Picture[allFiles.length]; // we don't know how many of the files are Pictures yet, so we choose the maximum array size, non-Pictures will be set to NULL
			for (int i = 0; i < allFiles.length; i++) { // for every File in that folder:
				if (allFiles[i].getName().startsWith("EXCLUDE")) {
					System.out.println("Info: File \"" + allFiles[i].getName() + "\" begins with \"EXCLUDE\" is therefore excluded.");
					allPictures[i] = null;
				} else {
					allPictures[i] = Picture.createFromFile(allFiles[i]); // convert File to Picture class !!!apply filter later!!!
				}
				// ---- EXTRA FEATURE: show progress every 100 files ----
				if (((i+1) % 100) == 0) {
					System.out.println("Info: Progress: Finished analyzing " + (i+1) + "/" + allFiles.length + " files in this folder!");
				}
			}
			System.out.println("Info: Finished analyzing the " + allFiles.length + " files in this folder! Now saving the data into a CSV file for later use...");
			// ---------- ---------- ---------- ---------- ---------- ---------- ---------- ---------- ----------
			Picture.writeArrayToCSVFile(allPictures, csvFile); // creates new & writes to CSV File
			System.out.println("Info: Finished creating the CSV file: " + csvFileName);
		}
		// ------------------------------ NEW FEATURE: store Pictures in CSV file ------------------------------

		// Because of the CSV functionality, we now have to apply the filter after and NOT during analysis
		// (we want the CSV file to include ALL pictures, without filtering!!!)
		if (picFilter.filterAtAll) {
			System.out.println("Info: Now applying your filter...");
			int filterCounter = 0;
			Picture filtered;
			for (int i = 0; i < allPictures.length; i++) {
				// either changes nothing at all or sets allPictures[i]!=NULL to NULL (increasing the filterCounter):
				if (allPictures[i] != null) { // NULLs don't have to be filtered (filter(null) always returns null)
					filtered = picFilter.filter(allPictures[i]);
					if (filtered == null) {
						allPictures[i] = null; // = picFilter.filter(allPictures[i]);
						filterCounter++;
					}
				}
			}
			System.out.println("Info: Filter applied. Number of Pictures filtered out: " + filterCounter);
		} else {
			System.out.println("Info: No filter to apply!");
		}

		/* ---------- Previous code: ---------- ----------
		Picture[] allPictures = new Picture[allFiles.length]; // all Pictures in the folder, NULL objects for non-Pictures
		System.out.println("Info: Beginning to analyze the files in this folder...");
		for (int i = 0; i < allFiles.length; i++) { // for every File in that folder:
			if (allFiles[i].getName().startsWith("EXCLUDE")) {
				System.out.println("Info: File \"" + allFiles[i].getName() + "\" begins with \"EXCLUDE\" is therefore excluded.");
				allPictures[i] = null;
			} else {
				allPictures[i] = picFilter.filter(Picture.createFromFile(allFiles[i])); // convert File to Picture class & apply filter!
			}
			// ---- EXTRA FEATURE: show progress every 100 files ----
			if (((i+1) % 100) == 0) {
				System.out.println("Info: Progress: Finished analyzing " + (i+1) + "/" + allFiles.length + " files in this folder!");
			}
		}
		System.out.println("Info: Finished analyzing the files in this folder!");
		---------- ---------- ---------- ---------- */

		// ---- EXTRA FEATURE: remove outliers ----
		System.out.println("Info: Looking for & removing outliers...");
		try {
			removeOutliers(allPictures);
		} catch (ArrayIndexOutOfBoundsException ex) { // length is zero (when all pics==null), median tries to access element [-1]
			System.out.println("Error: Not even a single file in the specified folder is parsable for EXIF Data!");
			return;
		}
		System.out.println("Info: Finished looking for & removing outliers!");
		// ---- ---- ---- ----
		// Sort all Pictures by timestamp (NULL objects, i.e. non-Pictures to the very right):
		Comparator<Picture> comp = new Comparator<Picture>() {
			@Override
        	public int compare(Picture p1, Picture p2) {
        		if (p1 == null && p2 == null) {
        			return 0; // null == null
        		} else if (p1 == null && p2 != null) {
        			return (-1)*p2.compareTo(p1);
        		} else { // p1 != null
        			return p1.compareTo(p2);
        		}
        	}
		};
		System.out.println("Info: Beginning to sort the pictures by their timestamp...");
		java.util.Arrays.sort(allPictures, comp);
		System.out.println("Info: Finished to sorting the pictures by their timestamp!");
		/* -- java.util.Arrays.sort(T[] a, Comparator<? super T> c) --
		-> ComparableTimSort causes a NullPointerException so we have to use a Comparator (anonymous class)
		- https://stackoverflow.com/questions/14514467/sorting-array-with-null-values
		- https://docs.oracle.com/javase/7/docs/api/java/util/Comparator.html
		- http://javatricks.de/tricks/objekte-vergleichen-mit-comparable-und-comparator
		- https://javabeginners.de/Klassen_und_Interfaces/Anonyme_Klassen.php
		*/
		//DEBUG:System.out.println("pictureArrayToString: " + pictureArrayToString(allPictures)); // For debugging only!

		// ----- If none (or just one) of the files in the user-specified folder is EXIF-parsable: -----
		// otherwise there would be an uncatched NullPointerException in the main() method
		// or some very weird behaviour (???) in the case of just a single image
		if (allPictures[0] == null) {
			System.out.println("Error: Not even a single file in the specified folder is parsable for EXIF Data (consider changing your filter as well)!");
			return; // (this case should already be caught in the try-catch around removeOutliers, but is nevertheless included here for completeness)
		} else if (allPictures[1] == null) {
			System.out.println("Error: At least 2 image files in the folder (that go through your specified filter!) have to be parsable for EXIF Data to create a map!");
			return;
		} /*else { // !!NEW FEATURE!! ---- REMOVED because of the even newer CSV feature (see above)
			int numberTotal = allPictures.length; // total number of files in the folder specified by the user
			int numberParsed = indexOfLastNonNullElementInArray(allPictures)+1;
			System.out.println("Info: Out of " + numberTotal + " total files in your folder, " + numberParsed + " pictures were successfully parsed for EXIF data that will be included in the map.");
		}*/
		// ----- -----

		System.out.println("Info: Beginning to determine the boundaries of the map...");
		// Find minimum/maximum latitude/longitude, needed as the borders for the map:
		double minLatitude = allPictures[0].latitude, maxLatitude = allPictures[0].latitude;
		double minLongitude = allPictures[0].longitude, maxLongitude = allPictures[0].longitude;
		for (int i = 1; i < allPictures.length; i++) {
			if (allPictures[i] == null) { // no pictures to process anymore: cancel for loop
				break;
			}
			// update the four min/max variables:
			if (allPictures[i].latitude < minLatitude) { // new smallest latitude found
				minLatitude = allPictures[i].latitude; // update minLatitude
			} else if (allPictures[i].latitude > maxLatitude) { // new largest latitude found
				maxLatitude = allPictures[i].latitude; // update maxLatitude
			}
			if (allPictures[i].longitude < minLongitude) { // new smallest latitude found
				minLongitude = allPictures[i].longitude; // update minLatitude
			} else if (allPictures[i].longitude > maxLongitude) { // new largest latitude found
				maxLongitude = allPictures[i].longitude; // update maxLatitude
			}
		}
		System.out.println("Info: Finished determining the boundaries of the map!");

		// ---------- New: Prompt user whether to include a margin & if how wide that should be: ----------
		System.out.print("Prompt: Specify the size of the margin in % (i.e. enter 0 for no margin at all; default=20): ");
		java.util.Scanner scanner = new java.util.Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
		String userInput = scanner.nextLine();
		userInput = userInput.trim(); // JavaDoc: "trim() Returns a copy of the string, with leading and trailing whitespace omitted."
		if (userInput.endsWith("%")) { // remove th percentage sign, if the user entered one
			userInput = userInput.substring(0, userInput.length()-1); // beginIndex = inclusive; endIndex = exclusive (JavaDoc)
		}
		if ("".equals(userInput)) {
			userInput = "20"; // default=20
			System.out.println("Info: Using default: 20% margin.");
		}
		if ("0".equals(userInput)) {
			// no margin
			System.out.println("Info: No margin.");
		} else {
			// in(de)crease the min/max lat/long values accordingly:
			int percentage;
			try {
				percentage = Integer.parseInt(userInput);
			} catch (NumberFormatException ex) {
				// non-numerical user input: no margin -> use default=20
				System.out.println("Error: Non-numerical user input: Using default of 20% margin.");
				percentage = 20;
			}
			if (percentage < 0) { // don't allow negative values here, as that would mean not all points fit on the map anymore...
				System.out.println("Error: Negative user input: Using default of 20% margin.");
				percentage = 20; // fall back to default
			}
			double latitudeMargin = (percentage/100.0)*Math.abs(maxLatitude-minLatitude); // Math.abs() just to be sure...
			double longitudeMargin = (percentage/100.0)*Math.abs(maxLongitude-minLongitude);
			minLatitude -= latitudeMargin;
			maxLatitude += latitudeMargin;
			minLongitude -= longitudeMargin;
			maxLongitude += longitudeMargin;
		}
		// ---------- ---------- ---------- ---------- ---------- ---------- ---------- ---------- ----------

		ArrayList<Integer> legendNumbersList = new ArrayList<Integer>();
		ArrayList<Color> legendColorsList = new ArrayList<Color>();

		System.out.println("Info: Beginning to draw map...");
		// ===== Actual drawing of the map =====
		Map m = new Map(minLatitude, maxLatitude, minLongitude, maxLongitude, indexOfLastNonNullElementInArray(allPictures)+1);
		// Drawing of the background has to happen first (obviously):
		String errorMsg = m.drawOnlineMapBackground();
		if (errorMsg.equals("")) {
			System.out.println("Info: Drawing the map background from online data was successful!");
		} else {
			System.out.println("Warning: Drawing the map background from online data failed: " + errorMsg);
		}
		// *************** NEW FEATURE: Custom Colors *************** *************** ***************
		Color[] customColors = null; // will be null by default, otherwise only if user specifically asks for it
		System.out.print("Prompt: Do you want to use custom colors for drawing on the map? Hit ENTER if not, otherwise enter colors in hexadecimal format, comma-separated: ");
		java.util.Scanner scannerCustomColor = new java.util.Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
		String customColorsUserInput = scannerCustomColor.nextLine().trim();
		if (!"".equals(customColorsUserInput)) { // user DOES want costum colors...:
			try {
				String[] hexColors = customColorsUserInput.split(","); // "comma-separated"
				customColors = new Color[hexColors.length];
				// Turn hexColors String array into actual Color array:
				for (int i = 0; i < hexColors.length; i++) {
					// -> https://stackoverflow.com/questions/4129666/how-to-convert-hex-to-rgb-using-java
					int red = Integer.parseInt(hexColors[i].substring(0,2), 16);
					int green = Integer.parseInt(hexColors[i].substring(2,4), 16);
					int blue = Integer.parseInt(hexColors[i].substring(4,6), 16);
					customColors[i] = new Color(red, green, blue); // Constructor: Color(int r, int g, int b)
				}
			} catch (Exception e) { // expecting a NumberFormatException, due to user input
				System.out.println("Error: Your specified colors are in a wrong format! Falling back to default colors...");
				customColors = null;
			}
		}
		// *************** *************** *************** *************** *************** ***************
		// start at i=1 because the starting point will be drawn at the very end!
		for (int i = 1; i < allPictures.length; i++) { // for all pictures whose EXIF data shall be a point on the map:
			if (allPictures[i] == null) { // no pictures to process anymore: cancel for loop
				break;
			}
			// --- NEW FEATURE: change color every day: ---
			if (i==1 || (allPictures[i].getDayOfYear() != allPictures[i-1].getDayOfYear())) { // A new day has dawned. (or we just started drawing, otherwise the first day would always be default Color.RED)
				// Change the color we use for drawing:
				if (customColors == null) { // use the color wheel by J. Itten if user didn't specify own colors:
					m.currentColor = colorWheel(allPictures[i].getDayOfYear()); // !!very important to use DayOfYear and NOT DayOfMonth for the colorWheel; if we loop from day 30/31 to day 1 and still want different colors!
				} else { // go to the next color in the list of colors specified by the user:
					m.currentColor = customColors[allPictures[i].getDayOfYear() % customColors.length];
				}
				// Add this information to the map legend:
				legendNumbersList.add(allPictures[i].getDayOfMonth());
				legendColorsList.add(m.currentColor);
			}
			// --- --- --- --- --- --- --- --- --- --- ---
			m.drawPoint(allPictures[i].latitude, allPictures[i].longitude, false); // last param is boolean:isStartPoint
			// connect the newly drawn point to the previous one (unless its a different day!!):
			if (allPictures[i].getDayOfYear() == allPictures[i-1].getDayOfYear()) {
				m.drawLine(allPictures[i].latitude, allPictures[i].longitude, allPictures[i-1].latitude, allPictures[i-1].longitude);
			}
		}
		// !!NEW: Draw the (green) starting point last so that is doesn't vanish behind the other stuff:
		m.drawPoint(allPictures[0].latitude, allPictures[0].longitude, true); // last param is boolean:isStartPoint
		// ===== Actual drawing of the map =====
		m.drawHeadline(allPictures[0].printableDateTime() + " – " + lastNonNullElementInArray(allPictures).printableDateTime());
		// (draw headline after drawing the map so that it is definitely still fully visible)
		// ***** ***** NEW: draw a color legend ***** ***** ***** ***** ***** ***** ***** ***** ***** *****
		Integer[] legendNumbersIntegers = legendNumbersList.toArray(new Integer[0]);
		int[] legendNumbers = new int[legendNumbersIntegers.length];
		for (int i = 0; i < legendNumbersIntegers.length; i++) {
			legendNumbers[i] = legendNumbersIntegers[i]; // convert Integer Array into int Array ...
		}
		Color[] legendColors = legendColorsList.toArray(new Color[0]);
		m.drawLegend(legendNumbers, legendColors);
		// ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** *****
		// Write down min/max lat/long values at the bottom of the image ():
		// !!! South is positive, North is negative !!! Remember: top left is (0,0) --> therefore multiply all latitudes by (-1) & swap their min/max-order !!!
		String str = "Latitude (North/South) range: " + formatGeo(maxLatitude-minLatitude) + " (" + formatGeo((-1)*maxLatitude) + " to " + formatGeo((-1)*minLatitude) + ")";
		str += "  |  Longitude (East/West) range: " + formatGeo(maxLongitude-minLongitude) + " (" + formatGeo(minLongitude) + " to " + formatGeo(maxLongitude) + ")";
		str += "  |  Center: " + formatGeo((-1)*(minLatitude+(maxLatitude-minLatitude)/2)) + ","+ formatGeo(minLongitude+(maxLongitude-minLongitude)/2);
		m.drawCaption(str);
		System.out.println("Info: Finished drawing the map! Now saving it...");
		File outputfile;
		// *--------- ---------- Determine file name: ---------- ---------*
		String prefix = "PicsToMap-0";
		int prefixIndex = 0;
		while (doesFolderContainFileWithBeginning(folder, prefix)) {
			prefixIndex++;
			prefix = "PicsToMap-" + prefixIndex;
		}
		outputfile = new File(folder, prefix + picFilter.shortStringInfo() + ".png"); // Constructor: public File(File parent, String child)
		// Previous (bad) way of doing it:
		//int counter = 0;
		//while (outputfile.exists()) { // to prevent using a name already taken!!
		//	counter++;
		//	outputfile = new File(folder, "PicsToMap-" + counter + picFilter.shortStringInfo() + ".png");
		//}
		// *--------- ---------- ---------- ---------- ---------- ---------*
		assert !outputfile.exists();
		m.saveTo(outputfile); // save a PNG version of map to outputfile - DONE!
		System.out.println("Info: Done! Saved map as '" + outputfile.getName() + "'");
	}

	/**
	* NEW FEATURE: Necessary to draw different days in different colors!
	* Takes
	* @param day modulo 12 to select
	* @return one of the 12 colors in the color wheel by Johannes Itten (1961)
	* -> https://de.wikipedia.org/wiki/Farbkreis#Ittens_Farbkreis
	* -> https://de.wikipedia.org/wiki/Datei:Farbkreis_Itten_1961.svg
	*
	* -> https://docs.oracle.com/javase/7/docs/api/java/awt/Color.html
	* Constructor Summary:
	*   Color(int r, int g, int b)
    *   Creates an opaque sRGB color with the specified red, green, and blue values in the range (0 - 255).
	*/
	public static Color colorWheel(int day) {
		switch (day % 12) { // next day's color should be as different as possible!; the smallest number that does NOT divide 12 is 5
			case 0: return new Color(244, 229, 0); // yellow
			case 5: return new Color(253, 198, 11); // light orange
			case 10: return new Color(241, 142, 28); // orange
			case 3: return new Color(234, 98, 31); // dark orange
			case 8: return new Color(227, 35, 34); // red
			case 1: return new Color(196, 3, 125); // pink
			case 6: return new Color(109, 57, 139); // purple
			case 11: return new Color(68, 78, 153); // dark blue
			case 4: return new Color(42, 113, 176); // blue
			case 9: return new Color(6, 150, 187); // light blue
			case 2: return new Color(0, 142, 91); // dark green
			case 7: return new Color(140, 187, 38); // light green
			default: return null; // should NEVER be reached as 0 <= (day % 12) <= 11
		}
	}

	public static int indexOfLastNonNullElementInArray(Picture[] arr) {
		for (int i = arr.length-1; i >= 0; i--) {
			if (arr[i] != null) {
				return i;
			}
		}
		return -1; // all elements in array are null
	}

	public static Picture lastNonNullElementInArray(Picture[] arr) {
		for (int i = arr.length-1; i >= 0; i--) {
			if (arr[i] != null) {
				return arr[i];
			}
		}
		return null; // all elements in array are null
	}

	// Formats a latitude/longitude double to 7 decimal places after the comma. (Google Maps also has 7 in their URLs)
	public static String formatGeo(double latOrLong) {
		return String.format("%.7f", latOrLong);
	}

	// For debugging purposes only :(
	public static String pictureArrayToString(Picture[] picArray) {
		String answer = "[ ";
		int len = picArray.length;
		for (int i = 0; i < len; i++) {
			Picture p = picArray[i];
			if (p == null) {
				answer += "NULL , ";
			} else {
				answer += p.timestamp + " , ";
			}
		}
		answer += "]";
		return answer;
	}

	public static void removeOutliers(Picture[] pics) { // (NEWLY ADDED)
		removeOutliers(pics, 5);
	}

	// ----- EXTRA FEATURE: remove extreme outliers, for example an image taken in Lima from a folder with all other images from Rio
	// -> this function replaces them with NULLs
	// -> and also prints out a "Warning:" message to command line prompt
	public static void removeOutliers(Picture[] pics, final double MAX_ALLOWED_STANDARD_DEVIATIONS) { // (2ND PARAMETER NEWLY ADDED)
		Double medianLatitude, medianLongitude;
		Double standardDeviationFromMedianLatitude, standardDeviationFromMedianLongitude;
		// ----- 1) Extract & seperate all the latitudes & longitudes from the Picture[] array: -----
		ArrayList<Double> latitudes = new ArrayList<Double>();
		ArrayList<Double> longitudes = new ArrayList<Double>();
		for (Picture p : pics) {
			if (p != null) {
				latitudes.add(p.latitude);
				longitudes.add(p.longitude);
			}
		}
		Double[] latitudeList = latitudes.toArray(new Double[0]); // -> https://stackoverflow.com/questions/7876954/arraylist-toarray-not-converting-to-the-correct-type
		Double[] longitudeList = longitudes.toArray(new Double[0]); // -> https://docs.oracle.com/javase/7/docs/api/java/util/ArrayList.html#toArray(T[])
		if (DEBUG) {System.out.println("--latitudeList: " + java.util.Arrays.toString(latitudeList));System.out.println("--longitudeList: " + java.util.Arrays.toString(longitudeList));}
		// ----- 2) Determine median & standard deviation for both the latitudes & the longitudes: -----
		java.util.Arrays.sort(latitudeList);
		medianLatitude = latitudeList[(int)Math.ceil(latitudeList.length/2.0)-1]; // -> "Skript Algorithmen und Datenstrukturen", Definition 5.22, page 105
		java.util.Arrays.sort(longitudeList);
		medianLongitude = longitudeList[(int)Math.ceil(longitudeList.length/2.0)-1]; // !!! NEWLY DISCOVERED MISTAKE: 2 instead of 2.0
		if (DEBUG) {System.out.println("--medianLatitude: " + medianLatitude);System.out.println("--medianLongitude: " + medianLongitude);}
		/* ---- Formula Standard Deviation: ----
		-> https://de.wikipedia.org/wiki/Empirische_Varianz
		s := + SQRT( (1/(n-1)) * SIGMA( (xi - AVG)^2 ) )
		However, we will use the median instead of the average here.
		*/
		Double sigmaLatitudes = 0.0;
		for (Double lat : latitudeList) {
			sigmaLatitudes += (lat-medianLatitude)*(lat-medianLatitude);
		}
		standardDeviationFromMedianLatitude = Math.sqrt((1.0/(latitudeList.length-1)) * sigmaLatitudes); // !! 1.0 VERY IMPORTANT because result will be 0.0 otherwise !!
		Double sigmaLongitudes = 0.0;
		for (Double lon : longitudeList) {
			sigmaLongitudes += (lon-medianLongitude)*(lon-medianLongitude);
		}
		standardDeviationFromMedianLongitude = Math.sqrt((1.0/(longitudeList.length-1)) * sigmaLongitudes);
		if (DEBUG) {System.out.println("--sigmaLatitudes: " + sigmaLatitudes);System.out.println("--sigmaLongitudes: " + sigmaLongitudes);}
		if (DEBUG) {System.out.println("--standardDeviationFromMedianLatitude: " + standardDeviationFromMedianLatitude);System.out.println("--standardDeviationFromMedianLongitude: " + standardDeviationFromMedianLongitude);}
		// ----- 3) Loop through all Pictures in the array to look for outliers: -----
		Picture pic;
		int reverseGeocodeCallCounter = 0; // because of the Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
		for (int i = 0; i < pics.length; i++) {
			pic = pics[i];
			if (pic != null) { // (some of the elements in pics[] ARE going to be null for sure!)
				Double latitudeDeviation = Math.abs(pic.latitude-medianLatitude);
				Double longitudeDeviation = Math.abs(pic.longitude-medianLongitude);
				// Check if it's an outlier:
				if ((latitudeDeviation > MAX_ALLOWED_STANDARD_DEVIATIONS*standardDeviationFromMedianLatitude) || (longitudeDeviation > MAX_ALLOWED_STANDARD_DEVIATIONS*standardDeviationFromMedianLongitude)) {
					// 1) inform the user of the found outlier via the command prompt:
					System.out.println("Warning: Outlier detected!");
					System.out.println("    --> " + pic.toString());
					if (reverseGeocodeCallCounter < 2) { // do a total of 2 calls of reverseGeocode() maximum!!
						// !!! Dont't forget to multiply latitude by (-1) !!! -> will lead to "<error>Unable to geocode</error>" !!!
						System.out.println("    --> Location (via Reverse Geocoding): " + NominatimAPI.reverseGeocode((-1)*pic.latitude, pic.longitude));
						reverseGeocodeCallCounter++;
					}
					System.out.println("    --> Latitude Deviation = " + (latitudeDeviation/standardDeviationFromMedianLatitude));
					System.out.println("    --> Longitude Deviation = " + (longitudeDeviation/standardDeviationFromMedianLongitude));
					// 2) remove the outlier:
					pics[i] = null;
				}
				// If neither latitude nor longitude are outliers, just proceed...
			}
		}

	}

	/**
	* Helps improve the naming/numbering of the PNG output files, when using different parameters!!
	* (So that they are shown in your file explorer in the order that you generated them!!)
	* @return True iff folder contains a file that begins with fileNameBeginning
	*/
	public static boolean doesFolderContainFileWithBeginning(File folder, String fileNameBeginning) {
		if (folder == null || fileNameBeginning == null || !folder.isDirectory()) {
			return false;
		}
		File[] allFiles = folder.listFiles(); // all Files in the folder
		for (File f : allFiles) {
			if (f.getName().startsWith(fileNameBeginning)) {
				return true; // folder DOES contain file with beginning...
			}
		}
		return false; // no file name with that beginning found
	}

}
