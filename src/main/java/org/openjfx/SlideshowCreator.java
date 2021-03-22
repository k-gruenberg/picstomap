package org.openjfx;

import java.io.File;
import java.nio.file.*; // for copying: see copyFile() function
import java.util.ArrayList;
import java.util.Comparator;
import java.util.function.Function;
import java.lang.Math; // conversion between degrees and radians (see distanceBetweenTwoCoordinates function)
// Needed for handling CSV files (copied from the Picture class):
//import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;

// !!! This class uses ACTUAL latitude/longitude values !!!
public final class SlideshowCreator {

	public static final String CSV_FILE_NAME = "SlideshowCreator-Analyzed-Data.csv";

	// Prviously: File Name | Timestamp | ACTUAL Latitude | ACTUAL Longitude | GridX | GridY
	// Now:       File Name | Timestamp | ACTUAL Latitude | ACTUAL Longitude | GPS-Speed | GridX | GridY
	public static final int COLUMN_FILE_NAME = 0;
	public static final int COLUMN_TIMESTAMP = 1;
	public static final int COLUMN_LATITUDE = 2;
	public static final int COLUMN_LONGITUDE = 3;
	public static final int COLUMN_GPS_SPEED = 4;
	public static final int COLUMN_GRID_X = 5;
	public static final int COLUMN_GRID_Y = 6;
	public static final int COLUMNS = 7;


	// Command line arguments: {NUMBER_OF_PICTURES_IN_SLIDESHOW} {PATH_TO_FOLDER_WITH_PICTURES}

	public static void main(String[] args) {

		final boolean TESTING = false;
		if (TESTING) { // Tests 1,1.5,2,3 ALL SUCCESSFUL!!!

			// 1) Test: void sort2DStringArray(String[][] arr, int columnIndexToSortBy)
			String[][] testArr = new String[][]{
				{"file1.jpg","100","12.3456","34.764","",""},
				{"file2.jpg","50","5.6733","7.889","",""},
				{"file3.jpg","110","1.111","22.355","",""},
				{"file4.jpg","10","-9.999","10.001","",""},
				{"file5.jpg","25","33.334","-66.643","",""},
				{"file6.jpg","12","-77.77","-77.777","",""},
				{"file7.jpg","1","34.555","-76.567","",""},
				{"file8.jpg","2","1.0","-1.0","",""},
				{"file9.jpg","0","0.0001","-0.0001","",""},
				{"file10.jpg","30","0.0","0.0","",""}
			};
			// syntax see: -> https://stackoverflow.com/questions/12231453/syntax-for-creating-a-two-dimensional-array
			sort2DStringArray(testArr, 1);
			// Print out result:
			for (int i=0; i<testArr.length; i++) {
				String line = "";
				for (int j=0; j<testArr[i].length; j++) {
					line += testArr[i][j] + " , ";
				}
				System.out.println(line);
			}

			System.out.println("---------- ---------- ----------");

			// 1.5) Test: int applyGridTo2DStringArray(String[][] arr, double gridSpacing)
			double gridSpacing = 1.0;
			int testResult = applyGridTo2DStringArray(testArr, gridSpacing);
			System.out.println("number of grid squares filled with at leat one Picture: " + testResult);
			// Print out result:
			for (int i=0; i<testArr.length; i++) {
				String line = "";
				for (int j=0; j<testArr[i].length; j++) {
					line += testArr[i][j] + " , ";
				}
				System.out.println(line);
			}

			System.out.println("---------- ---------- ----------");

			/*
			// 2) Test: void copyFile(File f, File destinationFolder, String destinationFileName)
			File testFile = new File("/Users/kendrick/Documents/TestFolder/SourceFolder/test.rtf");
			File testdestination = new File("/Users/kendrick/Documents/TestFolder/DestinationFolder");
			String testDestName = "copied-file.txt";
			copyFile(testFile, testdestination, testDestName);
			*/

			// 3) Test: double binarySearchDescending(int desiredReturnValue, double minInput, double maxInput, Function<double,int> func) throws IllegalArgumentException
			// Chosen test function: x -> 100-10*x (descending!)
			double result = binarySearchDescending(35, -10, 20, x -> (int)(100-10*x));
			System.out.println("");
			System.out.println("Binary Search Result:");
			System.out.println(result); // expected: 6.5 !!

			System.exit(0);
		}

		// =============== =============== 0th step: interpret the command line arguments: ===============

		int number_of_pictures_in_slideshow;
		File folder;

		if (args.length != 2) {
			System.out.println("Error: Expected arguments like this: {NUMBER_OF_PICTURES_IN_SLIDESHOW} {PATH_TO_FOLDER_WITH_PICTURES}");
			return; //exit
		} else {
			try {
				number_of_pictures_in_slideshow = Integer.parseInt(args[0]); // may throw NumberFormatException
				folder = new File(args[1]); // may throw FileNotFoundException
			} catch (Exception ex) {
				System.out.println("Error: An Exception occured interpreting your arguments: " + ex.toString());
				return;
			}
		}

		if (!folder.isDirectory()) {
			System.out.println("Error: You did not specify a directory!");
			return;
		}

		// ********** NEW: CSV FEATURE **********
		String[][] loadFromCSV = load2DStringArrayFromCSVFile(new File (folder, CSV_FILE_NAME));
		// ... will return NULL if a CSV file hasn't been created yet!
		String[][] tableAllPictures;
		if (loadFromCSV != null) {
			tableAllPictures = loadFromCSV;
			System.out.println("Info: Able to get the data from '" + CSV_FILE_NAME + "'!");
		} else { // loadFromCSV == null !!! ----> we have to load all the EXIF data manually!!
			System.out.println("Info: Unable to get the data from '" + CSV_FILE_NAME + "', analyzing files...");

			File[] allFiles = folder.listFiles(); // all Files in the folder specified by the user in cmd line
			int numberOfAllFiles = allFiles.length;
			if (numberOfAllFiles <= number_of_pictures_in_slideshow) {
				System.out.println("Error: There are " + numberOfAllFiles + " files in the folder you specified, which is less than or equal to " + number_of_pictures_in_slideshow + "!");
				return;
			}

			// =============== =============== 1st step: create a (sorted) table of all the picture data points we need: ===============
			// File Name | Timestamp | ACTUAL Latitude | ACTUAL Longitude | GPS-Speed | GridX | GridY
			// Will look like this for non-EXIF-pictures:
			// File Name | NULL | NULL | NULL | NULL | NULL | NULL
			String[][] tableAllFiles = new String[numberOfAllFiles][COLUMNS];
			for (int i=0; i<numberOfAllFiles; i++) {
				tableAllFiles[i][COLUMN_FILE_NAME] = allFiles[i].getName();
				try { // See also: Picture.createFromFile() !!
					/*
					double[] gPSLatitude = exifile.readEXIFRATIONAL(2, 3);
    				pic.latitude = gPSLatitude[0] + gPSLatitude[1]/60 + gPSLatitude[2]/3600; // Degrees + Minutes/60 + Seconds/3600
    				// === GPSLatitudeRef: Tag = 1 (0x0001), Type = ASCII(=2), Count = 2, Default = none ('N'/'S')
    				if (exifile.readEXIFASCIIsingle(1) == 'N') {pic.latitude *= -1;} // !!! South is positive, North is negative !!! Remember: top left is (0,0)
    				// === GPSLongitude: Tag = 4 (0x0004), Type = RATIONAL(=5), Count = 3, Default = none
    				// "The longitude is expressed as three RATIONAL values giving the degrees, minutes, and seconds, respectively."
    				double[] gPSLongitude = exifile.readEXIFRATIONAL(4, 3);
    				pic.longitude = gPSLongitude[0] + gPSLongitude[1]/60 + gPSLongitude[2]/3600; // Degrees + Minutes/60 + Seconds/3600
    				// === GPSLongitudeRef: Tag = 3 (0x0003), Type = ASCII(=2), Count = 2, Default = none ('E'/'W')
    				if (exifile.readEXIFASCIIsingle(3) == 'W') {pic.longitude *= -1;}
					*/
					EXIFFile exifile = new EXIFFile(allFiles[i]);
					double[] gPSLatitude = exifile.readEXIFRATIONAL(2, 3);
					int latitudeSign = (exifile.readEXIFASCIIsingle(1) == 'N') ? (+1) : (-1);
					double[] gPSLongitude = exifile.readEXIFRATIONAL(4, 3);
					int longitudeSign = (exifile.readEXIFASCIIsingle(3) == 'E') ? (+1) : (-1);
					// ***** NEW FEATURE: GPS-Speed (needed to avoid having loads of road pictures...) *****
					// Exif2-2.PDF:
					// GPSSpeed | Indicates the speed of GPS receiver movement. | Tag = 13 (D.H) | Type = RATIONAL | Count = 1 | Default = none
					// GPSSpeedRef | Indicates the unit used to express the GPS receiver speed of movement. 'K' 'M' and 'N' represents kilometers per hour, miles per hour, and knots. | Tag = 12 (C.H) | Type = ASCII | Count = 2 | Default = 'K' 
					double gPSSpeed = exifile.readEXIFRATIONAL(13, 1)[0]; // double[] readEXIFRATIONAL(final int tagNumber, final int count)
					char gPSSpeedRef;
					try {
						gPSSpeedRef = exifile.readEXIFASCIIsingle(12);
					} catch (Exception e) {
						gPSSpeedRef = 'K'; // Default = 'K' ; a camera maker might decide not to include this tag when using the default of km/h
					}
					double gPSSpeedInKMH;
					if (gPSSpeedRef == 'M') { // 'M' = miles per hour
						gPSSpeedInKMH = gPSSpeed*1.609344; // Source: Google "convert mph to kmh" ; https://de.wikipedia.org/wiki/Meile
					} else if (gPSSpeedRef == 'N') { // 'N' = knots
						gPSSpeedInKMH = gPSSpeed*1.852; // Source: Google "convert knots to kmh" ; https://de.wikipedia.org/wiki/Knoten_(Einheit) (siehe Seemeile...)
					} else { // Default = 'K' = kilometers per hour
						gPSSpeedInKMH = gPSSpeed;
					}
					// ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** ***** *****
					tableAllFiles[i][COLUMN_TIMESTAMP] = Long.toString(exifile.readTimestamp());
					tableAllFiles[i][COLUMN_LATITUDE] = Double.toString(latitudeSign*(gPSLatitude[0] + gPSLatitude[1]/60 + gPSLatitude[2]/3600));
					tableAllFiles[i][COLUMN_LONGITUDE] = Double.toString(longitudeSign*(gPSLongitude[0] + gPSLongitude[1]/60 + gPSLongitude[2]/3600));
					tableAllFiles[i][COLUMN_GPS_SPEED] = Double.toString(gPSSpeedInKMH);
					tableAllFiles[i][COLUMN_GRID_X] = null; // GridX and GridY columns will be used in the next step...
					tableAllFiles[i][COLUMN_GRID_Y] = null;
					exifile.close();
				} catch (Exception e) { // usually a FormatNotSupportedException or EXIFException
					tableAllFiles[i][COLUMN_TIMESTAMP] = null;
					tableAllFiles[i][COLUMN_LATITUDE] = null;
					tableAllFiles[i][COLUMN_LONGITUDE] = null;
					tableAllFiles[i][COLUMN_GPS_SPEED] = null;
					tableAllFiles[i][COLUMN_GRID_X] = null;
					tableAllFiles[i][COLUMN_GRID_Y] = null;
				}
				System.out.println("Progess: " + (i+1) + "/" + numberOfAllFiles + " files finished analyzing.");
			}
			// 1b) Remove all the NULL rows in our table:
			// public static String[][] removeNULLrowsFrom2DStringArrray(String[][] table, int columnIndex)
			tableAllPictures = removeNULLrowsFrom2DStringArrray(tableAllFiles, COLUMN_TIMESTAMP); // could also use COLUMN_LATITUDE or COLUMN_LONGITUDE instead of COLUMN_TIMESTAMP!
			if (tableAllPictures == null || tableAllPictures.length == 0) { // ?? !!
				System.out.println("Error: No EXIF-parsable pictures in your folder!");
				return;
			}
			/* Previously...: ****DEPRECATED!!****
			int nullRowCounter = 0;
			for (String[] row : tableAllFiles) {
				if (row[1] == null) { // could also use 2 or 3 instead of 1!
					nullRowCounter++;
				}
			}
			tableAllPictures = new String[numberOfAllFiles-nullRowCounter][4]; // before CSV Feature: initialized here: String[][] tableAllPictures
			int index = 0;
			for (String[] row : tableAllFiles) {
				if (row[1] != null) { // could also use 2 or 3 instead of 1!
					tableAllPictures[index] = row;
					index++;
				}
			}
			*/
			// 1c) Sort all the non-NULL rows by their timestamp in ascending order:
			System.out.println("Progess: Beginning to sort...");
			sort2DStringArray(tableAllPictures, COLUMN_TIMESTAMP);
			System.out.println("Progess: Finished sorting.");

			// ********** NEW: CSV FEATURE **********
			save2DStringArrayToCSVFile(tableAllPictures, new File(folder, CSV_FILE_NAME));
			System.out.println("Info: Saved the data to '" + CSV_FILE_NAME + "' for later use!");
		}

		// *************** 1.5th step: Prompt user to filter(!!!) by time or location *************** ***************

		String userInput = null;
		java.util.Scanner scanner = new java.util.Scanner(System.in); // -> https://www.w3schools.com/java/java_user_input.asp
		while (!"time".equalsIgnoreCase(userInput) && !"location".equalsIgnoreCase(userInput) && !"none".equalsIgnoreCase(userInput)) {
			System.out.print("Prompt: Do you want to filter your " + tableAllPictures.length + " pictures by 'time', 'location' or 'none'?: ");
			userInput = scanner.nextLine().trim();
		}
		if ("time".equalsIgnoreCase(userInput)) {
			// Note: See PictureFilter class:
			// public static long timestampFromYearMonthDayHourMinuteSecond(int year, int month, int day, int hour, int minute, int second)
			System.out.print("Prompt: Enter one of the two formats ('YYYY/MM/DD YYYY/MM/DD' or 'YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM'): ");
			userInput = scanner.nextLine().trim();
			int parameters = userInput.split(" ").length;
			if (parameters == 2 || parameters == 4) { // format 'YYYY/MM/DD YYYY/MM/DD' or 'YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM'
				long minTimestamp, maxTimestamp;
				if (parameters == 2) { // format 'YYYY/MM/DD YYYY/MM/DD'
					int year1 = Integer.parseInt(userInput.split(" ")[0].split("/")[0]);
					int month1 = Integer.parseInt(userInput.split(" ")[0].split("/")[1]);
					int day1 = Integer.parseInt(userInput.split(" ")[0].split("/")[2]);
					int year2 = Integer.parseInt(userInput.split(" ")[1].split("/")[0]);
					int month2 = Integer.parseInt(userInput.split(" ")[1].split("/")[1]);
					int day2 = Integer.parseInt(userInput.split(" ")[1].split("/")[2]);
					minTimestamp = PictureFilter.timestampFromYearMonthDayHourMinuteSecond(year1, month1, day1, 0, 0, 0);
					maxTimestamp = PictureFilter.timestampFromYearMonthDayHourMinuteSecond(year2, month2, day2, 23, 59, 59);
				} else { // parameters == 4 // format 'YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM'
					int year1 = Integer.parseInt(userInput.split(" ")[0].split("/")[0]);
					int month1 = Integer.parseInt(userInput.split(" ")[0].split("/")[1]);
					int day1 = Integer.parseInt(userInput.split(" ")[0].split("/")[2]);
					int hour1 = Integer.parseInt(userInput.split(" ")[1].split(":")[0]);
					int minute1 = Integer.parseInt(userInput.split(" ")[1].split(":")[1]);
					int year2 = Integer.parseInt(userInput.split(" ")[2].split("/")[0]);
					int month2 = Integer.parseInt(userInput.split(" ")[2].split("/")[1]);
					int day2 = Integer.parseInt(userInput.split(" ")[2].split("/")[2]);
					int hour2 = Integer.parseInt(userInput.split(" ")[3].split(":")[0]);
					int minute2 = Integer.parseInt(userInput.split(" ")[3].split(":")[1]);
					minTimestamp = PictureFilter.timestampFromYearMonthDayHourMinuteSecond(year1, month1, day1, hour1, minute1, 0);
					maxTimestamp = PictureFilter.timestampFromYearMonthDayHourMinuteSecond(year2, month2, day2, hour2, minute2, 59);
				}
				//public static String[][] filter2DStringArrayByTimestamp(String[][] arr, long minTimestamp, long maxTimestamp) {
				tableAllPictures = filter2DStringArrayByTimestamp(tableAllPictures, minTimestamp, maxTimestamp);
				if (tableAllPictures == null || tableAllPictures.length == 0) {
					System.out.println("Error: All pictures filtered out!");
					return; // quit
				}
				System.out.println("Info: Filtered by time!");
			} else {
				System.out.println("Error: Wrong format entered!");
				return; // quit
			}
		} else if ("location".equalsIgnoreCase(userInput)) {
			System.out.print("Prompt: Enter one of the two formats ('\"LOCATION_NAME_IN_QUOTES\" RADIUS_IN_KM' or 'LATITUDE_IN_DEGREES LONGITUDE_IN_DEGREES RADIUS_IN_KM'): ");
			userInput = scanner.nextLine().trim();
			int numberOfQuotes = userInput.split("\"").length - 1; // JavaDoc split(regex): "Trailing empty strings are therefore not included in the resulting array."
			int numberOfSpaces = userInput.split(" ").length - 1;
			if (numberOfQuotes == 2) { // format '"LOCATION_NAME_IN_QUOTES" RADIUS_IN_KM'
				String locationSearchString = userInput.split("\"")[1]; // whats inside the two quatation marks
				LatitudeLongitudePair latlongPair = NominatimAPI.search(locationSearchString);
				if (latlongPair == null) {
					System.out.println("Error: Unable to geo-locate '" + locationSearchString + "'!");
					return; // quit
				}
				double latitude = latlongPair.latitude;
				double longitude = latlongPair.longitude;
				System.out.println("Info: '" + locationSearchString + "' got located at lat:" + latitude + " , long:" + longitude);
				double radiusInKM = Double.parseDouble(userInput.split(" ")[(userInput.split(" ").length-1)]); //!!! last elemet in the SPACE-seperated array
				// public static String[][] filter2DStringArrayByLocation(String[][] arr, double latitude, double longitude, double radiusInKM) {
				tableAllPictures = filter2DStringArrayByLocation(tableAllPictures, latitude, longitude, radiusInKM);
				if (tableAllPictures == null || tableAllPictures.length == 0) {
					System.out.println("Error: All pictures filtered out!");
					return; // quit
				}
				System.out.println("Info: Filtered by location!");
			} else if (numberOfQuotes == 0 && numberOfSpaces == 2) { // format 'LATITUDE_IN_DEGREES LONGITUDE_IN_DEGREES RADIUS_IN_KM'
				double latitude = Double.parseDouble(userInput.split(" ")[0]);
				double longitude = Double.parseDouble(userInput.split(" ")[1]);
				double radiusInKM = Double.parseDouble(userInput.split(" ")[2]);
				// public static String[][] filter2DStringArrayByLocation(String[][] arr, double latitude, double longitude, double radiusInKM) {
				tableAllPictures = filter2DStringArrayByLocation(tableAllPictures, latitude, longitude, radiusInKM);
				if (tableAllPictures == null || tableAllPictures.length == 0) {
					System.out.println("Error: All pictures filtered out!");
					return; // quit
				}
				System.out.println("Info: Filtered by location!");
			} else {
				System.out.println("Error: Wrong format entered!");
				return; // quit
			}
		} // if "none".equalsIgnoreCase(userInput) -> do nothing!

		// *************** *************** *************** *************** *************** ***************

		// =============== 2nd step: find out which of the pictures to include in the slideshow; using a grid method: ===============
		// ===============           and create a list of the {number_of_pictures_in_slideshow} file names we want to include in our slideshow, sorted by timestamp: ===============

		//ArrayList<String> chosenFilesSortedList = new ArrayList<String>();
		//String[] chosenFilesSortedArray = chosenFilesSortedList.toArray(new String[0]);

		// One nautical mile = 1/60 latitude = 1852 meters => 1 meter = 1/1852 nautical miles = 1/111120 latitude degrees = 0.000008999 = approx. 0.00001
		// -> See also: https://www.usna.edu/Users/oceano/pguth/md_help/html/approx_equivalents.htm
		final double MIN_GRID_SPACING = 0.0000001; // approx. 1 centimetre; in lat/long degrees; (more or less arbitrary); remember Google Maps also uses 7 digits after the decimal...
		final double MAX_GRID_SPACING = 90.0;
		double perfectGridSpacing;
		try {
			// a new error occurs: "error: local variables referenced from a lambda expression must be final or effectively final"
			// -> https://stackoverflow.com/questions/27592379/local-variables-referenced-from-a-lambda-expression-must-be-final-or-effectively
			// -> solution: "You can just copy the value of ... into a final variable"
			final String[][] finalTableAllPictures = tableAllPictures;
			// double binarySearchDescending(int desiredReturnValue, double minInput, double maxInput, Function<Double,Integer> func) throws IllegalArgumentException
			perfectGridSpacing = binarySearchDescending(number_of_pictures_in_slideshow, MIN_GRID_SPACING, MAX_GRID_SPACING, dbl -> applyGridTo2DStringArray(finalTableAllPictures, dbl));
			tableAllPictures = finalTableAllPictures; // !!!!
		} catch (Exception e) {
			System.out.println("Error: Unable to determine a perfect grid spacing using a binary search!");
			return;
		}
		final int METERS_PER_DEGREE = 111120; // 1/60 degree = 1852 meters ; (111.120km*360=40.003km) -> See: https://de.wikipedia.org/wiki/Seemeile
		System.out.println("Progess: Perfect Grid Spacing determined using a binary search: " + perfectGridSpacing + " ( = approx. " + perfectGridSpacing*METERS_PER_DEGREE + " meters)");
				/////save2DStringArrayToCSVFile(tableAllPictures, new File(folder, "debug.csv"));
				/////System.exit(0);
		// tableAllPictures now has the correct Grid X and Y values! ; applyGridTo2DStringArray already applied to perfectGridSpacing by binarySearchDescending() in last iteration
		String[] chosenFilesSortedArray = choosePictureFiles(tableAllPictures, number_of_pictures_in_slideshow);
		if (TESTING) {
			System.out.println("--------------- Info: Chosen Files: ---------------");
			System.out.println(java.util.Arrays.toString(chosenFilesSortedArray));
			System.out.println("--------------- --------------- ---------------");
		}

		// =============== =============== 3rd step: create a new subfolder in this folder and copy duplicates of the photos in there, numbering them correctly with a prefix according to their timestamp: ===============
		System.out.println("Progess: Beginning to copy duplicates into subfolder..");

		File subfolder = new File(folder, "slideshow-" + number_of_pictures_in_slideshow);
		if (subfolder.exists()) {
			System.out.println("Error: '" + subfolder.getName() + "' exists already!");
			return;
		}
		if (!subfolder.mkdir()) { // "mkdir(): Creates the directory named by this abstract pathname."
			System.out.println("Error: '" + subfolder.getName() + "' directory could not be created!");
			return;
		}
		assert subfolder.exists() && subfolder.isDirectory();

		for (int i = 0; i < number_of_pictures_in_slideshow; i++) {
			File f = new File(folder, chosenFilesSortedArray[i]);
			copyFile(f, subfolder, "" + (i+1) + "-" + f.getName());
		}
		
		System.out.println("Progess: Done!");
	}

	// --> https://de.wikipedia.org/wiki/Bubblesort
	// "Allerdings nutzt diese einfachste Variante nicht die Eigenschaft aus, dass nach einer Iteration,
	// in der keine Vertauschungen stattfanden, auch in den restlichen Iterationen keine Vertauschungen
	// mehr stattfinden. Der folgende Pseudocode berücksichtigt dies: "
	/*
	bubbleSort2(Array A)
		n = A.size
		do{
			swapped = false
			for (i=0; i<n-1; ++i){
				if (A[i] > A[i+1]){
					 A.swap(i, i+1)
					swapped = true
				} // Ende if
			} // Ende for
			n = n-1
		} while (swapped)
	*/
	public static void sort2DStringArray(String[][] arr, int columnIndexToSortBy) {
		final long LARGE_TIMESTAMP = 253370764800000L; // year 9999, see https://www.unixtimestamp.com/index.php !!! Integer.MAX_VALUE << Long.MAX_VALUE
		// See Main, lines 274 to 287:
		// -> http://javatricks.de/tricks/objekte-vergleichen-mit-comparable-und-comparator
		Comparator<String[]> compStringArrByNumericValue = new Comparator<String[]>() {
			@Override
        	public int compare(String[] s1, String[] s2) { // first.compareTo(second); -1 Objekt ist kleiner als das übergebene Objekt
        		if (s1 == null && s2 == null) {
        			return 0; // null == null
        		} else if (s1 == null && s2 != null) { // NULLs shall be at the very end --> NULL >> everything
        			return (+1);
        		} else if (s1 != null && s2 == null) { // NULLs shall be at the very end --> NULL >> everything
        			return (-1);
        		} else { // s1 != null && s2 != null (usual case!!)
        			
        			long num1, num2;

        			try {
        				num1 = Long.parseLong(s1[columnIndexToSortBy]);
        			} catch (Exception e) { // Long.parseLong() fails:
        				num1 = LARGE_TIMESTAMP; // behave as if s1==null
        			}

        			try {
        				num2 = Long.parseLong(s2[columnIndexToSortBy]);
        			} catch (Exception e) { // Long.parseLong() fails:
        				num2 = LARGE_TIMESTAMP; // behave as if s2==null
        			}
        			
        			return java.lang.Math.toIntExact(num1-num2);
        		}
        	}
		};
		java.util.Arrays.sort(arr, compStringArrByNumericValue);

		/* --- Bubblesort: ---
		int n = arr.length;
		boolean swapped = false;
		do {
			swapped = false;
			for (i=0; i<n-1; ++i) {
				if (arr[i][columnIndexToSortBy] > arr[i+1][columnIndexToSortBy]) {
					// swap arr[i] and arr[i+1]:
					String[] temp = arr[i];
					arr[i] = arr[i+1];
					arr[i+1] = temp;
					swapped = true;
				}
			}
			n--;
		} while (swapped)
		*/
	}

	public static void copyFile(File f, File destinationFolder, String destinationFileName) {
		try {
			Path src = f.toPath();
			File destinationFile = new File(destinationFolder, f.getName());
			Path dst = destinationFile.toPath();
			// https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#copy(java.nio.file.Path,%20java.nio.file.Path,%20java.nio.file.CopyOption...)
			// https://www.baeldung.com/java-copy-file
			// https://stackoverflow.com/questions/16433915/how-to-copy-file-from-one-location-to-another-location
			Path target = Files.copy(src, dst); // StandardCopyOption.NOFOLLOW_LINKS -> cannot find symbol :(

			// Rename to destinationFileName:
			// https://docs.oracle.com/javase/7/docs/api/java/nio/file/Files.html#move(java.nio.file.Path,%20java.nio.file.Path,%20java.nio.file.CopyOption...)
			// "Usage Examples: Suppose we want to rename a file to "newname", keeping the file in the same directory: "
			Path ignore = Files.move(target, target.resolveSibling(destinationFileName));
		} catch (Exception e) {
			System.out.println("Copying File '" + f.getName() + "' to '" + destinationFolder.getName() + "' failed: " + e.toString());
		}
	}

	/** Binary Search for Functions that take in a Double and spit out an Integer:
	* (!!!!! under the assumption that LARGER input values lead to SMALLER output values !!!!!)
	* See: codereview.stackexchange.com -> "Passing a generic function as parameter"
	* and: https://de.wikipedia.org/wiki/Bin%C3%A4re_Suche
	* @return the double value which when given to Function func as an arguments gives {desiredReturnValue} as return value
	*         (between minInput & maxInput)
	*/
	public static double binarySearchDescending(int desiredReturnValue, double minInput, double maxInput, Function<Double,Integer> func) throws IllegalArgumentException {
		if (maxInput < minInput) {
			throw new IllegalArgumentException("binarySearchDescending(): maxInput < minInput");
		}

		double middle = (minInput + maxInput)/2;
		int functionEvaluation = func.apply(middle);
		if (functionEvaluation == desiredReturnValue) {
			return middle;
		} else if (functionEvaluation < desiredReturnValue) { // we want to increase function output -> decrease input
			return binarySearchDescending(desiredReturnValue, minInput, middle, func);
		} else { // functionEvaluation > desiredReturnValue   // we want to decrease function output -> increase input
			return binarySearchDescending(desiredReturnValue, middle, maxInput, func);
		}

	}

	// ----- ----- ----- Grid methods (MAIN part of this program actually...) ----- ----- -----

	public static final int EMPTY = Integer.MIN_VALUE;

	/**
	* @param arr the 6-column array created in the 1st step (tableAllPictures); sorted by timestamp (COLUMN_TIMESTAMPth column)
	* @param gridSpacing in degrees (of latitudes and longitudes)
	* @return the number of grid squares filled with at leat one Picture, i.e. the number of chosen pics (goal: number_of_pictures_in_slideshow)
	*/
	public static int applyGridTo2DStringArray(String[][] arr, double gridSpacing) {
		for (String[] row : arr) {
			try {
				row[COLUMN_GRID_X] = Integer.toString(longToGridX(Double.parseDouble(row[COLUMN_LONGITUDE]), gridSpacing));
				row[COLUMN_GRID_Y] = Integer.toString(latToGridY(Double.parseDouble(row[COLUMN_LATITUDE]), gridSpacing));
			} catch (Exception e) { // Double.parseDouble() failed!:
				System.out.println("Error in applyGridTo2DStringArray(): " + e.toString());
			}
		}
		// Determine the number of grid squares filled with at leat one Picture & return that value:
		int filledGridSquaresCounter = 0;

		int[][] encounteredXYpairs = new int[arr.length][2]; // arr.length is absolute maximum (i.e. every Picture in different grid square)
		// fill encounteredXYpairs with {EMPTY,EMPTY}s:
		for (int i=0; i<encounteredXYpairs.length; i++) {
			encounteredXYpairs[i][0] = EMPTY;
			encounteredXYpairs[i][1] = EMPTY;
		}
		// go through arr, see if XY pair has already been encountered, if not, add to encounteredXYpairs && filledGridSquaresCounter !!:
		for (String[] row : arr) {
			int x = Integer.parseInt(row[COLUMN_GRID_X]); // GridX column
			int y = Integer.parseInt(row[COLUMN_GRID_Y]); // GridY column
			boolean encountered = false;
			for (int i=0; i<encounteredXYpairs.length; i++) {
				if (encounteredXYpairs[i][0] == EMPTY || encounteredXYpairs[i][1] == EMPTY) {
					// empty, ignore
				} else {
					if (x == encounteredXYpairs[i][0] && y == encounteredXYpairs[i][1]) {
						encountered = true;
						break;
					}
				}
			}
			if (!encountered) {
				// Add XY pair 1) to encounteredXYpairs & 2) increase filledGridSquaresCounter!!
				// 1) Find first empty entry in encounteredXYpairs and write it in there:
				for (int i=0; i<encounteredXYpairs.length; i++) {
					if (encounteredXYpairs[i][0] == EMPTY || encounteredXYpairs[i][1] == EMPTY) {
						encounteredXYpairs[i][0] = x;
						encounteredXYpairs[i][1] = y;
						break;//!!!!!! STOP searching for next empty entry
					}
				}
				// 2) Increse filledGridSquaresCounter:
				filledGridSquaresCounter++;
			}
		}

		return filledGridSquaresCounter;
	}

	// Little helper functions for applyGridTo2DStringArray():
	public static int latToGridY(double lat, double gridSpacing) {return (int)(lat/gridSpacing);}
	public static int longToGridX(double lon, double gridSpacing) {return (int)(lon/gridSpacing);}

	/**
	* @param arr the 6-column array on which the binary search & apply grid functions have already been applied so that
	* @param numberOfFilledGridSquares == number_of_pictures_in_slideshow
	*/
	public static String[] choosePictureFiles(String[][] arr, int numberOfFilledGridSquares) {
		String[] answer = new String[numberOfFilledGridSquares];
		int[][] encounteredXYpairs = new int[numberOfFilledGridSquares][2];
		// fill encounteredXYpairs with {EMPTY,EMPTY}s:
		for (int i=0; i<encounteredXYpairs.length; i++) {
			encounteredXYpairs[i][0] = EMPTY;
			encounteredXYpairs[i][1] = EMPTY;
		}
		// go through arr, see if XY pair has already been encountered, if not, add to encounteredXYpairs && answer !!:
		for (String[] row : arr) {
			int x = Integer.parseInt(row[COLUMN_GRID_X]); // GridX column
			int y = Integer.parseInt(row[COLUMN_GRID_Y]); // GridY column
			boolean encountered = false;
			for (int i=0; i<encounteredXYpairs.length; i++) {
				if (encounteredXYpairs[i][0] == EMPTY || encounteredXYpairs[i][1] == EMPTY) {
					// empty, ignore
				} else {
					if (x == encounteredXYpairs[i][0] && y == encounteredXYpairs[i][1]) {
						encountered = true;
						break;
					}
				}
			}
			if (!encountered) {
				// Add XY pair 1) to encounteredXYpairs & 2) the corresponding file to the answer array!!
				// 1) Find first empty entry in encounteredXYpairs and write it in there:
				for (int i=0; i<encounteredXYpairs.length; i++) {
					if (encounteredXYpairs[i][0] == EMPTY || encounteredXYpairs[i][1] == EMPTY) {
						encounteredXYpairs[i][0] = x;
						encounteredXYpairs[i][1] = y;
						break;//!!!!!! STOP searching for next empty entry
					}
				}
				// 2) Find first empty entry in answer array and write file name in there:
				for (int i=0; i<answer.length; i++) {
					if (answer[i] == null || "".equals(answer[i])) { // !!! VERY IMPORTANT, a String array is initialized with NULLs, leads to returning NULL array!!
						answer[i] = row[0]; // File Name
						break;//!!!!!! STOP searching for next empty entry ---> leads to entire array being filled with the SAME picture!!!!! 
					}
				}
			}
		}
		return answer;
	}

	// *************** OPTIONAL Methods: used for filtering by time/location ***************

	// File Name | Timestamp | ACTUAL Latitude | ACTUAL Longitude | GPS-Speed | GridX | GridY
	// Will look like this for non-EXIF-pictures:
	// File Name | NULL | NULL | NULL | NULL | NULL | NULL

	/**
	* @param columnIndex which column index to check whether it is NULL
	*/
	public static String[][] removeNULLrowsFrom2DStringArrray(String[][] table, int columnIndex) { //...to avoid code duplication
		int tableWidth = -1; // (6 in our case!)
		// Prevent java.lang.NullPointerException if table[0] == null:
		for (int i=0; i<table.length; i++) { // find first non-NUll row to determine tableWidth
			if (table[i] != null) {
				tableWidth = table[i].length;
				break;
			}
		}
		if (tableWidth == -1) { // i.e. ALL rows in table are == null
			return null; // remove EVERYTHING
		}

		// Count the NULL rows:
		int nullRowCounter = 0;
		for (String[] row : table) {
			if (row == null || row[columnIndex] == null) { // NEW: row==null
				nullRowCounter++;
			}
		}

		// Create a new (smaller) array without the NULL rows:
		String[][] returnTable = new String[table.length-nullRowCounter][tableWidth];
		int index = 0;
		for (String[] row : table) {
			if (row != null && row[columnIndex] != null) { // NEW: row!=null
				returnTable[index] = row;
				index++;
			}
		}
		return returnTable;
	}

	// Format: 'LATITUDE_IN_DEGREES LONGITUDE_IN_DEGREES RADIUS_IN_KM'
	public static String[][] filter2DStringArrayByLocation(String[][] arr, double latitude, double longitude, double radiusInKM) {
		double lat, lon, distance;
		for (int i = 0; i < arr.length; i++) {
			lat = Double.parseDouble(arr[i][COLUMN_LATITUDE]);
			lon = Double.parseDouble(arr[i][COLUMN_LONGITUDE]);
			distance = distanceBetweenTwoCoordinates(latitude, longitude, lat, lon);
			if (distance > radiusInKM) { // too far away -> filter out!
				arr[i] = null;
			}
		}
		return removeNULLrowsFrom2DStringArrray(arr, COLUMN_TIMESTAMP); // columnIndex parameter irrelevant here, because we're setting the entire row to NULL!!
	}

	/** 
	* Helper function for: filter2DStringArrayByLocation()
	* See: -> https://stackoverflow.com/questions/365826/calculate-distance-between-2-gps-coordinates
	* @return the distacne between (lat1,long1) and (lat2,long2) in kilometers
	* Note: successfully tested in jshell:
	* distanceBetweenTwoCoordinates(52.51667,13.4,48.13333,11.583333) // Berlin -> Muenchen
	* $3 ==> 504.13458891761496
	*/
	public static double distanceBetweenTwoCoordinates(double lat1, double lon1, double lat2, double lon2) {
		// convert degrees to radians using Math.toRadians(double angdeg) // angdeg - an angle, in degrees
		final double earthRadiusKm = 6371.0;
		double dLat = Math.toRadians(lat2-lat1);
  		double dLon = Math.toRadians(lon2-lon1);
		lat1 = Math.toRadians(lat1);
		lat2 = Math.toRadians(lat2);
		double a = Math.sin(dLat/2) * Math.sin(dLat/2) + Math.sin(dLon/2) * Math.sin(dLon/2) * Math.cos(lat1) * Math.cos(lat2); 
		double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1-a)); 
		return earthRadiusKm*c;

	}

	// Format: 'YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM'
	public static String[][] filter2DStringArrayByTimestamp(String[][] arr, long minTimestamp, long maxTimestamp) {
		double timestamp;
		for (int i = 0; i < arr.length; i++) {
			timestamp = Double.parseDouble(arr[i][COLUMN_TIMESTAMP]);
			if (timestamp < minTimestamp || timestamp > maxTimestamp) { // unwanted timestamp -> filter out!
				arr[i] = null;
			}
		}
		return removeNULLrowsFrom2DStringArrray(arr, COLUMN_TIMESTAMP); // columnIndex parameter irrelevant here, because we're setting the entire row to NULL!!
	}

	// Note: See PictureFilter class:
	// public static long timestampFromYearMonthDayHourMinuteSecond(int year, int month, int day, int hour, int minute, int second)

	// *************** OPTIONAL Methods: saving the analysis in a CSV file, just like in the PicsToMap App (compare Picture class methods) ***************

	public static void save2DStringArrayToCSVFile(String[][] arr, File csvFile) { // Compare: Picture.writeArrayToCSVFile() !!
		if (arr == null || csvFile == null) {
			throw new NullPointerException("save2DStringArrayToCSVFile(): arr == null || csvFile == null");
		}
		if (!csvFile.exists()) { // if the csv file exists already, there's nothing to do here
			try (FileOutputStream out = new FileOutputStream(csvFile)) { // automatically creates file!
    			for (String[] row : arr) {
    				if (row != null) { // just ignore/skip null rows/entries
    					String line = "";
    					for (int i = 0; i < row.length; i++) {
    						line += encodeString(row[i]);
    						if (i != (row.length-1)) {
    							line += ","; // append a comma afterwards, except for the last value of course
    						}
    					}
    					out.write(line.getBytes());
    					out.write(10); // new line: "\n".getBytes() --> byte[1] { 10 }
    				}
    			}
    		} catch (java.io.IOException ex) {
    			// IO error!
    			System.out.println("Error: IO error while saving CSV file: " + ex.toString());
    		}
		}
	}

	public static String[][] load2DStringArrayFromCSVFile(File f) { // Compare: Picture.arrayFromCSVFile() !!
		if (f != null && f.exists() && !f.isDirectory() && f.getName().substring(f.getName().lastIndexOf(".")+1).toLowerCase().equals("csv")) {
    		String[] csvRows;
    		// -> https://javarevisited.blogspot.com/2012/07/read-file-line-by-line-java-example-scanner.html
    		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
				ArrayList<String> csvRowsList = new ArrayList<String>();
				String line = reader.readLine();
				while (line != null) {
                	csvRowsList.add(line);
                	line = reader.readLine();
            	}
            	csvRows = csvRowsList.toArray(new String[0]); // -> https://stackoverflow.com/questions/7876954/arraylist-toarray-not-converting-to-the-correct-type
			} catch (java.io.IOException ex) {
				return null; // IO error!
			}
			int numberOfEntriesPerRow = csvRows[0].split(",").length; // should be COLUMNS in our case!!
			String[][] answer = new String[csvRows.length][numberOfEntriesPerRow];
			for (int i = 0; i<answer.length; i++) {
				// If we wouldn't encode/decode the strings we could just do:
				// answer[i] = csvRows[i].split(",");
				String[] undecodedEntries = csvRows[i].split(",");
				for (int j = 0; j<undecodedEntries.length; j++) {
					answer[i][j] = decodeString(undecodedEntries[j]);
				}
			}
			return answer;
    	} else {
    		return null; // File f either is null, does not exist, is a directory or does not have the ".csv" suffix
    	}
	}

	// Deal with NULL values and Strings containing commas/linebreaks!!:
	// Would look ideally like this:
	// \0 means NULL (null)
	// \, means COMMA (',')
	// \n means line break ("\n")
	// \\ means BACKSLASH ('\')
	// !!!Here however we only care about the NULL thing!!!

	public static String encodeString(String str) {
		if (str == null) {
			return "NULL";
		} else {
			return str;
		}

	}

	public static String decodeString(String encodedStr) {
		if ("NULL".equals(encodedStr)) {
			return null;
		} else {
			return encodedStr;
		}
	}

}
