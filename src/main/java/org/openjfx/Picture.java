package org.openjfx;

import java.io.File;
import java.util.Calendar; // conversion between formats: "YYYY:MM:DD HH:MM:SS", "dd.MM.yyyy HH:mm:ss", Epoch time
import java.util.Date; import java.time.format.DateTimeFormatter; import java.time.*; // both just for the printableDateTime() method
// Needed for handling CSV files:
import java.util.ArrayList;
import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream; // -> writeArrayToCSVFile()
import java.io.InputStreamReader;

public class Picture implements Comparable {
	public static final boolean DEBUG = false;
	public long timestamp; // number of milliseconds that have elapsed since January 1, 1970
	public double latitude; // parallels (to Equator): North/South (90°N North Pole - 0° Equator - 90°S South Pole)
	public double longitude; // West/East, converge at the North and South Poles (0° Greenwich, 180°W=180°E=Date Line)
	public String originalFileName = null; // !!!NEW!!! (needed for the new Regex filter feature)

	// Default Constructur:
	public Picture() {}

	// Constructor: not used, because not really necessary, for debugging/testing purposes only:
	public Picture(long timestamp, double latitude, double longitude, String originalFileName) {
		this.timestamp = timestamp;
		this.latitude = latitude;
		this.longitude = longitude;
		this.originalFileName = originalFileName; // !!!NEW!!! (needed for the new Regex filter feature)
	}

	/*
	* Compares two instances of Picture by their timestamp:
	**/
	@Override // -> http://javatricks.de/tricks/objekte-vergleichen-mit-comparable-und-comparator
    public int compareTo(Object o) { // Returns: a negative integer, zero, or a positive integer as this object is less than, equal to, or greater than the specified object.
        if (o == null) { // (every Picture instance) << NULL object
        	// non-images shall be sorted to the very right of the image array...:
        	return -1; // *****FIX***** (Integer.MIN_VALUE/2); // Integer.MIN_VALUE = -2147483648 ; sqrt(2147483648) = 46341 ????
        	// !!!! Very important not to use Integer.MIN_VALUE because then, Arrays.sort() will return rubbish !!!!
        }
        try {
			Picture other = (Picture) o;
			return Long.signum(this.timestamp - other.timestamp); // *****FIX*****
		} catch (ClassCastException ex) { // the Object compared to is not a Picture
        	return -1; // (see above)
		}
    }

    /**
    * Because we only need the equals() method for sorting-related purposes, two instances of Picture are considered to
    * be equal when their timestamps are equal (latitude and longitude values are completely ignored!)
    */
    @Override // -> https://www.geeksforgeeks.org/overriding-equals-method-in-java/
    public boolean equals(Object o) {
    	if (o == null) {return false;}
    	if (o == this) {return true;}
    	if (!(o instanceof Picture)) {return false;}
    	Picture p = (Picture) o; // Typecast
    	return (this.timestamp == p.timestamp);
    }

	/**
    * Used only at Main.removeOutliers() and for debugging
    */
    @Override
    public String toString(){ 
    	// !!! Dont't forget to multiply latitude by (-1) !!!
    	return "Picture taken on " + timestamp + " at lat:" + ((-1)*latitude) + " and long:" + longitude;
    }

    /**
    * @return this.timestamp in a human-readable format
    */
    public String printableDateTime(){
    	Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
    	cal.setTimeInMillis(this.timestamp);
    	Date d = cal.getTime();
    	Instant instant = d.toInstant(); // -> https://stackoverflow.com/questions/42210257/java-util-date-to-string-using-datetimeformatter
    	LocalDateTime ldt = instant.atOffset(ZoneOffset.UTC).toLocalDateTime();
    	DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm:ss");
    	return ldt.format(formatter);
    }

    /**
    * Necessary for the NEW FEATURE (drawing different days in different colors)
    * @return the day this Picture was taken (a value between 1 and 31)
    */
    public int getDayOfMonth() {
    	Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
    	cal.setTimeInMillis(this.timestamp);
    	return cal.get(Calendar.DAY_OF_MONTH); // DATE = synonym for DAY_OF_MONTH
    }

    /**
    * Necessary for the NEW FEATURE (drawing different days in different colors)
    * @return the day this Picture was taken (a value between 1 and 366?)
    */
    public int getDayOfYear() {
    	Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
    	cal.setTimeInMillis(this.timestamp);
    	return cal.get(Calendar.DAY_OF_YEAR);
    }

	/**
	 * Get the timestamp of this Picture as a Java Calendar object.
	 * For simplicity we set the TimeZone of this Calendar to UTC.
	 *
	 * @return the timestamp of this Picture as a Java Calendar object.
	 */
	public Calendar getCalendar() { //!!!!!NEW IN GUI VERSION (-> used in the "Rename Tool" feature)!!!!!
		Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
		cal.setTimeInMillis(this.timestamp);
		return cal;
	}

    /**
    * @return this instance as a CSV String ("timestamp,latitude,longitude"), can be read later by Picture.createFromCSV(str)
    */
    public String toCSV() {
    	return "" + this.timestamp + "," + this.latitude + "," + this.longitude;
    }

    // ===== Static functions: =====

	/**
	 * Read timestamp, latitude and longitude from the EXIF data of a JPEG/HEIF image and create a new Picture instance from that:
	 * @return null if File f is not a JPEG/HEIF image or EXIF parsing failed for some other reason
	 */
	public static Picture createFromFile(File f) { // (this "wrapper function" is !!!NEW!!!)
		Picture firstTry = Picture.createFromFile(f, -1); // use default numberOfBytesToRead
		if (firstTry != null) {
			return firstTry;
		} else { // Second try with (a lot) more numberOfBytesToRead (cf. PhotoCollectionSorter):
			final int MUCH_MORE_BYTES_TO_READ = 16_777_216; // = 2^24 (maximum found in L.A. holiday panorama pictures is 'Exif00MM' at 10.2 million bytes)
			return Picture.createFromFile(f, MUCH_MORE_BYTES_TO_READ);
		}
	}

    /**
    * Read timestamp, latitude and longitude from the EXIF data of a JPEG/HEIF image and create a new Picture instance from that:
    * @return null if File f is not a JPEG/HEIF image or EXIF parsing failed for some other reason
    */
    private static Picture createFromFile(File f, final int numberOfBytesToRead) {
    	if (f == null) {
    		if (DEBUG) {System.out.println("< Picture.createFromFile(): f==null />");}
    		return null; // cannot parse non-existant File
    	}
    	EXIFFile exifile = null; // !!SCOPE!!
    	try {
    		/* ******************** (Suffix & Magic Bytes Checking is now handled by the EXIFFile class) ********************
    		// --------- NEW: if suffix is not "jpg", "jpeg", "jpe", "jfif", "heif" or "heic" (case-insensitive), don't even open the file to check for magic bytes
    		// --------- (better overall performance when lots of non-EXIF-compatable files are in the folder)
    		if (EXIFFileInputStream.doesFileHaveEXIFcompatibleExtension(f)) {
    			// suffix is okay!
    		} else {
    			// DON'T print a "Warning:" message to console, to avoid spamming it
    			return null; // f is not a JPEG or HEIF/HEIC file!
    		}
    		// --------- --------- --------- --------- --------- --------- --------- --------- ---------
    		if (!EXIFFileInputStream.isFileEXIFcompatible(f)) {
    			// This will only happen if extension was "jpg", "jpeg", "jpe", "jfif" BUT not correct JPEG magic bytes (FF-D8-FF)
    			System.out.println("Warning: File \"" + f.getName() + "\" does not have correct JPEG magic bytes and is therefore excluded.");
    			return null; // cannot parse non-EXIF file
    		}
    		******************** (Suffix & Magic Bytes Checking is now handled by the EXIFFile class) ******************** */
    		Picture pic = new Picture(); // create a new instance of Picture (to return)
    		if (numberOfBytesToRead <= 0) { // !!NEW!! // negative argument (e.g. -1): use default numberOfBytesToRead
				exifile = new EXIFFile(f); // open the File f and read its beginning...
			} else {
				exifile = new EXIFFile(f, numberOfBytesToRead); // open the File f and read its beginning...
			}
    		// Now use the functions of the EXIFFile class to extract the EXIF date and write it into the new Picture instance:
    		pic.timestamp = exifile.readTimestamp();
    		// double[] EXIFFile.readEXIFRATIONAL(final int tagNumber, final int count) {...}
    		// ---- returns a double[] array containing {count} doubles
    		// === GPSLatitude: Tag = 2 (0x0002), Type = RATIONAL(=5), Count = 3, Default = none
    		// "The latitude is expressed as three RATIONAL values giving the degrees, minutes, and seconds, respectively."
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
    		exifile.close(); // !!!! (maybe) very important for the garbage collector to kick in...?! !!!
    		pic.originalFileName = f.getName(); // !!!NEW!!! (needed for the new Regex filter feature)
			return pic;
    	} catch (FormatNotSupportedException fnsex) {
    		if (exifile != null) {exifile.close();}
    		return null; // unsopported format, e.g. PNG -> continue quietly...
    	} catch (EXIFException eex) {
    		System.out.println("Warning: Reading EXIF data from File \"" + f.getName() + "\" failed: " + eex.getMessage());
    		if (exifile != null) {exifile.close();}
    		return null;
    	} catch (Exception ex) {
    		System.out.println("Warning: File \"" + f.getName() + "\" caused some internal Exception (" + ex.toString() +") and is therefore excluded.");
    		//if (DEBUG) {System.out.println("< Picture.createFromFile(): File " + f.getName() + " caused some exception: " + ex.toString() + " />");}
    		if (exifile != null) {exifile.close();}
    		return null;
    	}
    }

    // ===== CSV-related functions (all static): =====

    /**
    * Read timestamp, latitude and longitude from a line in a CSV file ("timestamp,latitude,longitude") and create a new Picture instance from that:
    * @return null if line is in incorrect format (i.e. not "timestamp,latitude,longitude")
    */
    public static Picture createFromCSV(String csvString) {
    	String[] values = csvString.split(",");
    	if (values.length != 3) {
    		return null; // incorrect format, not exactly 3 values
    	} else {
    		try {
    			Picture answer = new Picture();
    			answer.timestamp = Long.parseLong(values[0], 10); // public static long parseLong(String s, int radix) throws NumberFormatException
    			answer.latitude = Double.parseDouble(values[1]); // public static double parseDouble(String s) throws NumberFormatException
    			answer.longitude = Double.parseDouble(values[2]);
    			return answer;
    		} catch (NumberFormatException nfex) {
    			System.out.println("Error: Reading the CSV line \"" + csvString + "\" caused a NumberFormatException!");
    			return null;
    		}
    	}
    }

    /**
    * @param f a CSV file, each line having the format "timestamp,latitude,longitude"
    * @return an array of Pictures, values read from the CSV lines
    */
    public static Picture[] arrayFromCSVFile(File f) {
    	if (f != null && f.exists() && !f.isDirectory() && f.getName().substring(f.getName().lastIndexOf(".")+1).toLowerCase().equals("csv")) {
    		// -> https://javarevisited.blogspot.com/2012/07/read-file-line-by-line-java-example-scanner.html
    		try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(f)))) {
				ArrayList<Picture> pics = new ArrayList<Picture>();
				String line = reader.readLine();
				while (line != null) {
                	pics.add(Picture.createFromCSV(line));
                	line = reader.readLine();
            	}
            	return pics.toArray(new Picture[0]); // -> https://stackoverflow.com/questions/7876954/arraylist-toarray-not-converting-to-the-correct-type
			} catch (java.io.IOException ex) {
				return null; // IO error!
			}
    	} else {
    		return null; // File f either is null, does not exist, is a directory or does not have the ".csv" suffix
    	}
    }

    /**
    * Writes the Picture[] Array
    * @param picArray into a newly created CSV file
    * @param f
    */
    public static void writeArrayToCSVFile(Picture[] picArray, File f) {
    	if (f != null && !f.exists()) { // f should NOT exist already!
    		try (FileOutputStream out = new FileOutputStream(f)) { // automatically creates file!
    			for (Picture pic : picArray) {
    				if (pic != null) { // just ignore/skip null elements in picArray
    					out.write(pic.toCSV().getBytes());
    					out.write(10); // new line: "\n".getBytes() --> byte[1] { 10 }
    				}
    			}
    		} catch (java.io.IOException ex) {
    			// IO error!
    			System.out.println("Error: IO error while writing Picture Array to CSV File: " + ex.toString());
    		}
    	}
    }

}