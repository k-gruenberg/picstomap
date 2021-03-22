package org.openjfx;

import java.io.*;
import java.nio.file.*;
import java.time.*;
import java.util.function.Consumer;

/**
* A tiny program that can rename your photo files / sort them into subfolders.
*
* @author Kendrick Gruenberg
*/
public final class PhotoCollectionSorter {

	/* //OLD CONSOLE VERSION:
	// This program does not need a CSV or similar database feature as it will only be executed once per folder (by definition)
	public static void main(String[] args) { // TO-DO
	 */

	/**
	 * Renames all EXIF-parsable files in the folder whose path is given by 'folderStr'
	 * according the the renaming scheme specified in 'renamePlaceholderStr'.
	 * The progress ist constantly updated to the 'progressConsumer' (as a Double between 0.0 and 1.0).
	 * Will return "" if no major error occurred, otherwise the error message is returned.
	 *
	 * @param folderStr the folder whose files are to rename
	 * @param renamePlaceholderStr format how to rename the files
	 * @param progressConsumer the progress (as a Double between 0.0 and 1.0) is constantly updated to this Consumer
	 * @return the error message, "" is no major error occurred
	 */
	public static String rename(final String folderStr, final String renamePlaceholderStr, Consumer<Double> progressConsumer) {
		File folder;
		try {
			folder = new File(folderStr); // may throw FileNotFoundException
		} catch (Exception ex) {
			return ex.toString();
		}
		if (!folder.exists()) {
			return "The given path does not point to an existing entity!";
		} else if (!folder.isDirectory()) {
			return "The given path does not point to a directory!";
		}

		/* // ----- OLD CONSOLE WAY OF DOING IT:
		// 0a) Basic user-input interpretation, copied from SlideshowCreator:
		File folder;
		if (args.length != 1) {
			System.out.println("Error: Expected absolute path to a folder with pictures as argument!");
			return; //exit
		} else {
			try {
				folder = new File(args[0]); // may throw FileNotFoundException
			} catch (Exception ex) {
				System.out.println("Error: An Exception occured interpreting your argument: " + ex.toString());
				return;
			}
		}
		if (folder == null || !folder.exists()) {
			System.out.println("Error: Your path does not point to an existing entity!");
			return;
		} else if (!folder.isDirectory()) {
			System.out.println("Error: You did not specify a directory!");
			return;
		}
		 */
		File[] allFilesWOmetadata = folder.listFiles(); // all Files in the folder specified by the user in cmd line
		// 0b) Turn File[] array into FileWithMetadata[] array:
		FileWithMetadata[] allFiles = new FileWithMetadata[allFilesWOmetadata.length];
		for (int i = 0; i<allFiles.length; i++) {
			allFiles[i] = new FileWithMetadata(allFilesWOmetadata[i]);
		}
		/*
		// 1) Tell user what PhotoCollectionSorter is capable of:
		System.out.println("Possible commands:");
		System.out.println("    rename [GENERIC_FILE_NAME_PLACEHOLDER]");
		System.out.println("        -> placeholder attributes available to use (! marks the most interesting ones):");
		System.out.println("           !{FILE_NAME} - the file name the picture file currently has, including suffix");
		System.out.println("            {FILE_NAME_WO_SUFFIX} - the file name the picture file currently has, excluding suffix");
		System.out.println("            {FILE_SUFFIX} - the suffix of the picture file, e.g. 'jpg' or 'HEIC' (without the '.')");
		System.out.println("           !{TIMESTAMP_INDEX} - (not yet supported) - 1 for the picture first taken, rest in ascending order by EXIF timestamp (2,3,4,..), every file will have a different index!");
		System.out.println("            {TIMESTAMP_UNIX} - the UNIX timestamp the picture was taken, number of seconds that have elapsed since 1 January 1970, might NOT be unique!");
		System.out.println("           !{DAY_INDEX} - (not yet supported) - 1 for first day (the day the first picture was taken), rest in ascending order (2,3,4,..), including days without pictures");
		System.out.println("           !{DAY_OF_MONTH} - the day the picture way taken, number between 1 and 31");
		System.out.println("            {DAY_OF_YEAR} - the day of YEAR the picture way taken, number between 1 and 365 (366 for leap years)");
		System.out.println("            {MONTH_NUMERIC} - the month the picture way taken, number between 1 and 12");
		System.out.println("            {MONTH_SHORT} - the month the picture way taken, 'JAN' to 'DEC'");
		System.out.println("            {MONTH_LONG} - the month the picture way taken, 'JANUARY' to 'DECEMBER'");
		System.out.println("            {YEAR} - the year the picture way taken (Gregorian calendar)");
		System.out.println("            {HOUR} - the hour the picture way taken (according to its timestamp)"); // NEW
		System.out.println("            {MINUTE} - the minute the picture way taken (according to its timestamp)"); // NEW
		System.out.println("            {SECOND} - the second the picture way taken (according to its timestamp)"); // NEW
		System.out.println("            {COUNTRY_LONG} - the country the picture was taken in, found using reverse geocoding (Nominatim API)");
		System.out.println("            {COUNTRY_SHORT} - the country the picture was taken in, 3-letter ISO code");
		System.out.println("            {CITY_LONG} - the city the picture was taken in, found using reverse geocoding (Nominatim API)");
		System.out.println("            {CITY_SHORT} - the city the picture was taken in, 3-letter IATA airport code");
		System.out.println("        -> use [] brackets instead of {} when you want a leading 0 (or leading zeros for DAY_OF_YEAR), e.g. '[MONTH_NUMERIC]' instead of '{MONTH_NUMERIC}' for '03' instead of '3'");
		System.out.println("        -> examples: rename {TIMESTAMP_INDEX}-{DAY_OF_MONTH}-{FILE_NAME}");
		System.out.println("        -> Note: Files where EXIF-parsing failed will NOT be renamed!");
		System.out.println("    moveIntoSubfoldersSortedByDay");
		System.out.println("    moveIntoSubfoldersSortedByLocation");
		System.out.println("    excludeByDateTime YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM");
		System.out.println("        -> all photos not taken within the specified time period will be moved into a subfolder called 'excluded-by-PhotoCollectionSorter'");
		System.out.println("    excludeByLocation [LATITUDE_IN_DEGREES] [LONGITUDE_IN_DEGREES] [RADIUS_IN_KM]"); // TODO
		System.out.println("        -> all photos not taken within [RADIUS_IN_KM] kilometers of the specified location will be moved into a subfolder called 'excluded-by-PhotoCollectionSorter'");
		System.out.println("    excludeByRoadType [COMMA_SEPERATED_LIST_OF_OSM_ROAD_TYPES]"); // TODO
		System.out.println("        -> all photos taken on a road whose type belongs to the ones specified will be moved into a subfolder called 'excluded-[OSM_ROAD_TYPE]'");
		System.out.println("        -> list of OSM road types (links included):"); // See: https://wiki.openstreetmap.org/wiki/DE:Key:highway
		System.out.println("           motorway, trunk, primary, secondary, tertiary, unclassified, residential, living_street, OTHER");
		// 2) Ask user what he wants PhotoCollectionSorter to do...:
		java.util.Scanner scanner = new java.util.Scanner(System.in);
		System.out.print("Enter your command: ");
		String userInput = scanner.nextLine().trim();
		// 3) Interpret user input and execute the command (if it is a valid one):
		if (userInput.startsWith("rename ")) {
		*/
		String placeholder = renamePlaceholderStr; //!!NEW!! //userInput.split(" ")[1]; // everything after "rename " -> "[GENERIC_FILE_NAME_PLACEHOLDER]"
		/* // We don't even tell the user about these placeholders in the GUI version anymore!:
			boolean timestampSortingNecessary
				= placeholder.contains("{TIMESTAMP_INDEX}") || placeholder.contains("{DAY_INDEX}");
			if (timestampSortingNecessary) {
				System.out.println("Sorry, the features {TIMESTAMP_INDEX} and {DAY_INDEX} are not yet implemented.");
				return;
			}
			boolean reverseGeocodingNecessary // will be false if user doesn't need the reverse Geo information, saving us the trouble of calling a Web API!!
				= placeholder.contains("{COUNTRY_LONG}") || placeholder.contains("{COUNTRY_SHORT}") || placeholder.contains("{CITY_LONG}") || placeholder.contains("{CITY_SHORT}");
			if (reverseGeocodingNecessary) {
				System.out.println("Sorry, the reverse geocoding features ({COUNTRY/CITY_LONG/SHORT}) are not yet implemented.");
				return;
			}
		 */
			// int errorCounter = 0; // In the GUI version we don't count the # of errors anymore.
			for (int i = 0; i < allFiles.length; i++) {
				File f = allFiles[i];
				// a) Determine the placeholder values:
				String placeholder_FILE_NAME = f.getName();
				String placeholder_FILE_NAME_WO_SUFFIX;
				if (f.getName().contains(".")) { // important check if we don't want a StringIndexOutOfBoundsException when there's no dot in the file name!!
					placeholder_FILE_NAME_WO_SUFFIX = f.getName().substring(0, f.getName().lastIndexOf("."));
				} else {
					placeholder_FILE_NAME_WO_SUFFIX = placeholder_FILE_NAME;
				}
				String placeholder_FILE_SUFFIX = f.getName().substring(f.getName().lastIndexOf(".")+1);
				EXIFFile exifile;
				long unixTimestampMillis;
				int[] yyyymmddhhmmssArray; // [year, month, day, hour, minute, second]
				try {
					exifile = new EXIFFile(f);
					unixTimestampMillis = exifile.readTimestamp();
					yyyymmddhhmmssArray = exifile.readFirstYYYYMMDDHHMMSSAsArray(); // [year, month, day, hour, minute, second]
				} catch (EXIFException exifex) {
					if (exifex.getMessage().contains("Header")) { // The problem is that the EXIF Header (Exif00MM) wasn't found:
						try { // Do a second try, this time reading A LOT more header bytes (experience has shown that this is necessary for HEIC panoramas for some reason):
							final int MUCH_MORE_BYTES_TO_READ = 16_777_216; // = 2^24 (maximum found in L.A. holiday panorama pictures is 'Exif00MM' at 10.2 million bytes)
							exifile = new EXIFFile(f, MUCH_MORE_BYTES_TO_READ);
							unixTimestampMillis = exifile.readTimestamp();
							yyyymmddhhmmssArray = exifile.readFirstYYYYMMDDHHMMSSAsArray(); // [year, month, day, hour, minute, second]
						} catch (Exception e) { // Nope, still doesn't work:
							//System.out.println("" + (i+1) + "/" + allFiles.length + ") ERROR: Could not find EXIF Header in '" + f.getName() + "' even after second try: " + e.toString());
							//errorCounter++;
							continue; // go on with the next file
						}
					} else {
						//System.out.println("" + (i+1) + "/" + allFiles.length + ") ERROR: Could not rename '" + f.getName() + "' because of an EXIF error: " + exifex.getMessage());
						//errorCounter++;
						continue; // go on with the next file
					}
				} catch (Exception ex) {
					//System.out.println("" + (i+1) + "/" + allFiles.length + ") ERROR: Could not rename '" + f.getName() + "' because of an unsupported format or an IO error: " + ex.toString());
					//errorCounter++;
					continue; // go on with the next file
				}
				int year = yyyymmddhhmmssArray[0];
				int month = yyyymmddhhmmssArray[1];
				int day = yyyymmddhhmmssArray[2];
				int hour = yyyymmddhhmmssArray[3];
				int minute = yyyymmddhhmmssArray[4];
				int second =  yyyymmddhhmmssArray[5];
				String placeholder_TIMESTAMP_UNIX = "" + (unixTimestampMillis/1000);
				String placeholder_DAY_OF_MONTH = "" + day;
				String placeholder_DAY_OF_YEAR = "" + Year.of(year).atMonth(month).atDay(day).getDayOfYear();
				String placeholder_MONTH_NUMERIC = "" + month;
				String placeholder_MONTH_SHORT = shortMonthName(Integer.parseInt(placeholder_MONTH_NUMERIC));
				String placeholder_MONTH_LONG = longMonthName(Integer.parseInt(placeholder_MONTH_NUMERIC));
				String placeholder_YEAR = "" + year;
				String placeholder_HOUR = "" + hour;
				String placeholder_MINUTE = "" + minute;
				String placeholder_SECOND = "" + second;
				// b) Determine the new file name:
				String newName = placeholder;
				newName = newName.replace("{FILE_NAME}", placeholder_FILE_NAME);
				newName = newName.replace("{FILE_NAME_WO_SUFFIX}", placeholder_FILE_NAME_WO_SUFFIX);
				newName = newName.replace("{FILE_SUFFIX}", placeholder_FILE_SUFFIX);
				//newName = newName.replace("{TIMESTAMP_INDEX}", );
				newName = newName.replace("{TIMESTAMP_UNIX}", placeholder_TIMESTAMP_UNIX);
				//newName = newName.replace("{DAY_INDEX}", );
				newName = newName.replace("{DAY_OF_MONTH}", placeholder_DAY_OF_MONTH);
				newName = newName.replace("[DAY_OF_MONTH]", leadingZeros(placeholder_DAY_OF_MONTH, 2));
				newName = newName.replace("{DAY_OF_YEAR}", placeholder_DAY_OF_YEAR);
				newName = newName.replace("[DAY_OF_YEAR]", leadingZeros(placeholder_DAY_OF_YEAR, 3));
				newName = newName.replace("{MONTH_NUMERIC}", placeholder_MONTH_NUMERIC);
				newName = newName.replace("[MONTH_NUMERIC]", leadingZeros(placeholder_MONTH_NUMERIC, 2));
				newName = newName.replace("{MONTH_SHORT}", placeholder_MONTH_SHORT);
				newName = newName.replace("{MONTH_LONG}", placeholder_MONTH_LONG);
				newName = newName.replace("{YEAR}", placeholder_YEAR);
				newName = newName.replace("{HOUR}", placeholder_HOUR);
				newName = newName.replace("[HOUR]", leadingZeros(placeholder_HOUR, 2));
				newName = newName.replace("{MINUTE}", placeholder_MINUTE);
				newName = newName.replace("[MINUTE]", leadingZeros(placeholder_MINUTE, 2));
				newName = newName.replace("{SECOND}", placeholder_SECOND);
				newName = newName.replace("[SECOND]", leadingZeros(placeholder_SECOND, 2));
				//newName = newName.replace("{COUNTRY_LONG}", );
				//newName = newName.replace("{COUNTRY_SHORT}", );
				//newName = newName.replace("{CITY_LONG}", );
				//newName = newName.replace("{CITY_SHORT}", );
				// c) Rename the file (print error message to console if that fails for some reason...):
				renameFile(f, newName); // In the GUI version we don't really track (the number of) errors anymore.
				progressConsumer.accept((double)(i+1)/allFiles.length); //!!!NEW way of showing progress in the GUI version!!!
				/*if (renameFile(f, newName)) {
					System.out.println("" + (i+1) + "/" + allFiles.length + ") Info: Renamed '" + f.getName() + "' to '" + newName + "'");
				} else {
					System.out.println("" + (i+1) + "/" + allFiles.length + ") ERROR: Could not rename '" + f.getName() + "' to '" + newName + "'");
					errorCounter++;
				}*/
			}
			/* // We don't need console output anymore. In the GUI version we also just don't care about this statistic anymore:
			if (errorCounter == 0) {
				System.out.println("Info: Finished renaming all " + allFiles.length + " files!");
			} else {
				System.out.println("Warning: Finished renaming files. However, " + errorCounter + " of " + allFiles.length + " files could not be renamed! (see error messages above)");
			}
			 */
			return ""; // no (major) error occurred.
		/*} else if (userInput.equals("moveIntoSubfoldersSortedByDay")) {
			// TO-DO
			System.out.println("Sorry, this feature is not yet implemented.");
		} else if (userInput.equals("moveIntoSubfoldersSortedByLocation")) {
			// TO-DO
			System.out.println("Sorry, this feature is not yet implemented.");
		} else if (userInput.startsWith("excludeByDateTime ")) {
			// TO-DO
			System.out.println("Sorry, this feature is not yet implemented.");
		} else if (userInput.startsWith("excludeByLocation ")) {
			// TO-DO
			System.out.println("Sorry, this feature is not yet implemented.");
		} else if (userInput.startsWith("excludeByRoadType ")) {
			// TO-DO
			System.out.println("Sorry, this feature is not yet implemented.");
		} else {
			System.out.println("Error: Invalid command!");
			return;
		}*/
	}

	/**
	* @param f File to rename
	* @param newName
	* @return whether the renaming was successful
	*/
	public static boolean renameFile(File f, String newName) {
		try {
			Path p = f.toPath();
			Files.move(p, p.resolveSibling(newName));
			return true;
		} catch (Exception ex) { // including FileAlreadyExistsException
			return false;
		}
	}

	/*
	* @param files Files to move
	* @param newFolder where to move the Files, is created if it doesn't exist already
	* @return whether the moving operation was successful
	*
	public static boolean moveFilesIntoNewFolder(File[] files, File newFolder) {
		boolean success = true;
		for (File f : files) {
			success = moveFileIntoNewFolder(f, newFolder) & success;
		}
		return success;
	}*/

	/*
	* @param files File to move
	* @param newFolder where to move the File, is created if it doesn't exist already
	* @return whether the moving operation was successful
	*
	public static boolean moveFileIntoNewFolder(File f, File newFolder) {
		// TODO
		throw new UnsupportedOperationException("moveFileIntoNewFolder() is not yet implemented.");
	}*/

	private static String shortMonthName(int month) {
		switch (month) {
			case 1: return "JAN";
			case 2: return "FEB";
			case 3: return "MAR";
			case 4: return "APR";
			case 5: return "MAY";
			case 6: return "JUN";
			case 7: return "JUL";
			case 8: return "AUG";
			case 9: return "SEP";
			case 10: return "OCT";
			case 11: return "NOV";
			case 12: return "DEC";
			default: return "???";
		}
	}

	private static String longMonthName(int month) {
		switch (month) {
			case 1: return "JANUARY";
			case 2: return "FEBRUARY";
			case 3: return "MARCH";
			case 4: return "APRIL";
			case 5: return "MAY";
			case 6: return "JUNE";
			case 7: return "JULY";
			case 8: return "AUGUST";
			case 9: return "SEPTEMBER";
			case 10: return "OCTOBER";
			case 11: return "NOVEMBER";
			case 12: return "DECEMBER";
			default: return "?MONTH?";
		}
	}

	private static String leadingZeros(String str, int desiredLength) {
		if (str.length() >= desiredLength) { // str is already long enough:
			return str;
		} else { // add leading zero(s):
			return "0".repeat(desiredLength - str.length()) + str;
		}
	}

}
