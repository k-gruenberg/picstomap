package org.openjfx;

import java.time.LocalDate; // (NEW FOR GUI VERSION!!)
import java.util.Calendar;
/**
* Needed when the user supplies "filterDate", "filterDates", "filterDateTime", "filterDatesTime" or "filterMillisecondRange" as a commandline argument
*/
public class PictureFilter {

	public boolean filterAtAll = true;
	private long minAcceptedTimestamp;
	private long maxAcceptedTimestamp;
	private String stringInfo;

	// Constructor -1: filterMillisecondRange mmm...mmm mmm...mmm {PATH_TO_FOLDER_WITH_PICTURES}
	public PictureFilter(long min, long max) {
		this.minAcceptedTimestamp = min;
		this.maxAcceptedTimestamp = max;
		this.stringInfo = "-" + min + "ms-" + max + "ms";
	}

	// Constructor 0: don't filter at all
	public PictureFilter() {
		this.filterAtAll = false;
	}

	// Constructor 1: filterDate YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}
	public PictureFilter(String dateStr) throws IllegalArgumentException, NumberFormatException {
		this(dateStr, dateStr);
	}

	// Constructor 2: filterDates YYYY/MM/DD YYYY/MM/DD {PATH_TO_FOLDER_WITH_PICTURES}
	// ---> filter all pictures from beginning of date1 until end of date2 <---
	public PictureFilter(String date1Str, String date2Str) throws IllegalArgumentException, NumberFormatException {
		if ((date1Str.length() != 10) || (date2Str.length() != 10)) {
			throw new IllegalArgumentException("Format YYYY/MM/DD expected!");
		} else {
			int year1 = Integer.parseInt(date1Str.substring(0,4));
			int month1 = Integer.parseInt(date1Str.substring(5,7));
			int day1 = Integer.parseInt(date1Str.substring(8,10));
			int year2 = Integer.parseInt(date2Str.substring(0,4));
			int month2 = Integer.parseInt(date2Str.substring(5,7));
			int day2 = Integer.parseInt(date2Str.substring(8,10));
			
			this.minAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year1,month1,day1,0,0,0);
			this.maxAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year2,month2,day2,23,59,59);
			if (date1Str.equals(date2Str)) {
				this.stringInfo = "-" + date1Str.replace("/","");
			} else {
				this.stringInfo = "-" + date1Str.replace("/","") + "-" + date2Str.replace("/","");
			}
		}
	}

	// Constructor 3: filterDateTime YYYY/MM/DD HH:MM HH:MM {PATH_TO_FOLDER_WITH_PICTURES}
	public PictureFilter(String dateStr, String time1Str, String time2Str)  throws IllegalArgumentException, NumberFormatException {
		if ((dateStr.length() != 10) || (time1Str.length() != 5) || (time2Str.length() != 5)) {
			throw new IllegalArgumentException("Format YYYY/MM/DD HH:MM HH:MM expected!");
		} else {
			int year = Integer.parseInt(dateStr.substring(0,4));
			int month = Integer.parseInt(dateStr.substring(5,7));
			int day = Integer.parseInt(dateStr.substring(8,10));
			int hour1 = Integer.parseInt(time1Str.substring(0,2));
			int minute1 = Integer.parseInt(time1Str.substring(3,5));
			int hour2 = Integer.parseInt(time2Str.substring(0,2));
			int minute2 = Integer.parseInt(time2Str.substring(3,5));
			
			this.minAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year,month,day,hour1,minute1,0);
			this.maxAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year,month,day,hour2,minute2,59);
			this.stringInfo = "-" + dateStr.replace("/","") + "-" + time1Str.replace(":","") + "-" + time2Str.replace(":","");
		}
	}

	// Constructor 4: filterDatesTime YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM {PATH_TO_FOLDER_WITH_PICTURES}
	public PictureFilter(String date1Str, String time1Str, String date2Str, String time2Str)  throws IllegalArgumentException, NumberFormatException {
		if ((date1Str.length() != 10) || (date2Str.length() != 10) || (time1Str.length() != 5) || (time2Str.length() != 5)) {
			throw new IllegalArgumentException("Format YYYY/MM/DD HH:MM YYYY/MM/DD HH:MM expected!");
		} else {
			int year1 = Integer.parseInt(date1Str.substring(0,4));
			int month1 = Integer.parseInt(date1Str.substring(5,7));
			int day1 = Integer.parseInt(date1Str.substring(8,10));
			int year2 = Integer.parseInt(date2Str.substring(0,4));
			int month2 = Integer.parseInt(date2Str.substring(5,7));
			int day2 = Integer.parseInt(date2Str.substring(8,10));

			int hour1 = Integer.parseInt(time1Str.substring(0,2));
			int minute1 = Integer.parseInt(time1Str.substring(3,5));
			int hour2 = Integer.parseInt(time2Str.substring(0,2));
			int minute2 = Integer.parseInt(time2Str.substring(3,5));
			
			this.minAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year1,month1,day1,hour1,minute1,0);
			this.maxAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(year2,month2,day2,hour2,minute2,59);
			this.stringInfo = "-" + date1Str.replace("/","") + "-" + time1Str.replace(":","") + "-" + date2Str.replace("/","") + "-" + time2Str.replace(":","");
		}
	}

	// !!! BRAND NEW CONSTRUCTOR FOR THE GUI VERSION: !!!
	public PictureFilter(boolean ignoreBefore, LocalDate beforeDate, int beforeHour, int beforeMinute,
						 boolean ignoreAfter, LocalDate afterDate, int afterHour, int afterMinute) {
		if (ignoreBefore == false && ignoreAfter == false) {
			this.filterAtAll = false;
			return;
		}
		// ----- Before Settings: -----
		if (ignoreBefore) {
			this.minAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(
					beforeDate.getYear(), beforeDate.getMonthValue(), beforeDate.getDayOfMonth(), beforeHour, beforeMinute, 0);
		} else {
			this.minAcceptedTimestamp = Long.MIN_VALUE;
		}
		// ----- After Settings: -----
		if (ignoreAfter) {
			this.maxAcceptedTimestamp = timestampFromYearMonthDayHourMinuteSecond(
					afterDate.getYear(), afterDate.getMonthValue(), afterDate.getDayOfMonth(), afterHour, afterMinute, 59);
		} else {
			this.maxAcceptedTimestamp = Long.MAX_VALUE;
		}
	}

	/**
	* @return null if pic is null or if pic.timestamp is outside the filtered timeframe, pic if pic.timestamp is inside the timeframe
	*/
	public Picture filter(Picture pic) {
		if (!this.filterAtAll) { // this PictureFilter isn't actually supposed to filter at all! 
			return pic;
		} else if (pic == null) {
			return null;
		} else if ((this.minAcceptedTimestamp <= pic.timestamp) && (this.maxAcceptedTimestamp >= pic.timestamp)) {
			return pic; // inside timeframe
		} else {
			return null; // outside timeframe
		}
	}

	// !!! ALSO BRAND NEW !!!
	public boolean isFiltered(Picture pic) {
		if (!this.filterAtAll) { // this PictureFilter isn't actually supposed to filter at all!
			return false;
		} else if (pic == null) {
			return true; // (filter out nulls)
		} else if ((this.minAcceptedTimestamp <= pic.timestamp) && (this.maxAcceptedTimestamp >= pic.timestamp)) {
			return false; // inside timeframe
		} else {
			return true; // outside timeframe
		}
	}

	/**
	* To include the filter specification in the filename (see main() method)
	*/
	public String shortStringInfo() {
		if (!this.filterAtAll) {
			return "";
		} else {
			return this.stringInfo;
		}
	}

	// ===== ===== Static functions: ===== =====
	/**
	* Compare to: EXIFTool.readFirstYYYYMMDDHHMMSSfromFile()
	* Note: made public instead of private, so it can be used in the SlideshowCreator class!!!
	*/
	public static long timestampFromYearMonthDayHourMinuteSecond(int year, int month, int day, int hour, int minute, int second) {
		Calendar cal = Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
    	cal.set(year, month-1, day, hour, minute, second); // 0-based month!!! (-> https://stackoverflow.com/questions/29029549/calendar-method-gettimeinmillis-returning-wrong-value)
    	cal.set(Calendar.MILLISECOND, 0); // otherwise the actual current millisecond counter will be in here - always returning odd values!
    	return cal.getTimeInMillis(); // "Returns this Calendar's time value in milliseconds." (type: long)
	}

}
