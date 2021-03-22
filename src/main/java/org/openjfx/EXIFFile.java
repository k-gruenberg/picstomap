package org.openjfx;

/*
* ------ https://en.wikipedia.org/wiki/List_of_file_signatures ------
* -> all .jpg/.jpeg files begin with FF D8 FF
* -> JFIF: FF D8 FF E0
* -> Exif: FF D8 FF E1
*
* ------ PDF: EXIF-Header in JPEG-Dateien (von Thomas Schmidt), Seite 5 ------
* -> Damit beginnt eine JPEG-Datei, die einen EXIF-Datenblock enthält also immer mit folgender Bytefolge:
* -> Intel-Format    ("II" 49-49): FF-D8-FF-E1-HL-LL-45-78-69-66-00-00-49-49-2A-00-08-00-00-00
* -> Motorola-Format ("MM" 4D-4D): FF-D8-FF-E1-HL-LL-45-78-69-66-00-00-4D-4D-00-2A-00-00-00-08
* -> Wobei HL-LL die Länge des EXIF-Datenblocks inklusive der Längenangabe, des EXIF-Headers und des TIFF-Headers darstellt.
* -> 45-78-69-66-00-00 Buchstaben „Exif“ gefolgt von zwei Nullbytes. Sie macht die Verwendung des Markers FF-E1 eindeutig. Schließlich könnten ja
* die Programmierer anderer Anwendungen auch auf die Idee gekommen sein, den anwendungsspezifischen Marker FF-E1 für eigene Zwecke zu verwenden.
*********************************************
* ---- Added JFIF support ----
* Idea:
* All(!) EXIF files begin with "......Exif00" (FF-D8-FF-E1-HL-LL-45-78-69-66-00-00) before the "MM" or "II"
* A JFIF file (of the type that we can handle) begins with "......JFIF.....H.H......Exif00" before the "MM" or "II"
* -> just chop off the first 18 bytes: "......JFIF.....H.H"
*********************************************
* ---- Added HEIF/HEIC (High Efficiency Image) support ----
* Initial Idea: - find first occurence of "Exif00MM" or "Exif00II"
*               - replace all the bytes before that with FF-D8-FF-E1-HL-LL (choose FF-FF for HL-LL i.e. maximum header length)
*               - interpret it as a normal JPEG-EXIF
*/

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
/**
* Can extract the EXIF data out of any file containing an EXIF block
* (JPEG/JFIF/HEIC)
*/
public class EXIFFile {

	private int[] bytes; // the bytes at the beginning of the file, containing (among possibly other stuff) the EXIF block (a -1 in the bytes[] array signifies the end of the stream)
	private String bytesAsString;
	private String fileSuffix; // LOWERCASE!, EXIF-compatible extensions are: "jpg", "jpeg", "jpe", "jfif"; "heif", "heic"
	private int positionOfEXIFheader = -1; // first position of "Exif00MM" or "Exif00II" (-1 means not yet determined)
	private boolean bigEndian = true; // "MM" Motorola format (Big-Endian) or "II" Intel format (Little-Endian)

	// Constructur: Read the File f and write its beginning bytes into the bytes[] array (see above)
	public EXIFFile(File f) throws IOException, FormatNotSupportedException, EXIFException {
		this(f, 131072); // 131072 = 2^17 = DEFAULT_NUMBER_OF_BYTES (max EXIF block length is 2^16, in HEIC files however the EXIF block is not at the file beginning)
	}

	// Constructur: Read the File f and write its beginning bytes (excatly how many specified by argument) into the bytes[] array (see above)
	public EXIFFile(File f, final int numberOfBytesToRead) throws IOException, FormatNotSupportedException, EXIFException {
		if ((f == null) || (!f.exists()) || (f.isDirectory())) {
			throw new FormatNotSupportedException("EXIFFile Constructor: File is either null, nonexistent or a directory!");
		} else {
			this.fileSuffix = f.getName().substring(f.getName().lastIndexOf(".")+1).toLowerCase(); // (stackoverflow.com)
			if (this.hasEXIFcompatibleSuffix()) {
				// "The try-with-resources Statement" ---> https://docs.oracle.com/javase/tutorial/essential/exceptions/tryResourceClose.html
				try (FileInputStream inputStream = new FileInputStream(f)) {
					this.bytes = new int[numberOfBytesToRead];
					for (int i = 0; i < numberOfBytesToRead; i++) {
						bytes[i] = inputStream.read(); // JavaDoc: "Reads the next byte of data from the input stream. The value byte is returned as an int in the range 0 to 255."
						if (bytes[i] == -1) { // "the end of the stream is reached"
							break; // (a -1 in the bytes[] array signifies the end of the stream)
						}
						if ((i == 2) && (!this.isValidEXIF())) { // the third byte has just been read: check the JPEG magic bytes (FF-D8-FF), if its a JPEG file
							this.close();
							throw new EXIFException("EXIFFile Constructor: Invalid JPEG magic bytes!");
						}
					}
					this.determinePositionOfEXIFheaderAndEndianness();
					this.markEndOfEXIFHeaderInJPEGs(); // inserts a (-1) after the end of the EXIF-Header iff the file is in JPEG/JFIF and NOT in HEIF/HEIC format!!!
					this.writeBytesAsString(); // stops when a (-1) is reached
					//this.determineEndianness();
				} catch (IOException ex) {
					this.close();
					throw ex; //rethrow the IOException
				}
			} else {
				this.close();
				throw new FormatNotSupportedException("EXIFFile Constructor: File doesn't have EXIF compatible suffix!"); //e.g. PNG
			}
		}
	}

	public void close() {
		this.bytes = null;
		this.bytesAsString = null;
		this.fileSuffix = null;
		// now, the garbage collector can do its job...
	}

	// just to be executed once by the constructor: reads bytes[] and writes them into a String (casting them to chars)
	// compare to: EXIFTool.readFirstYYYYMMDDHHMMSSfromFile()
	private void writeBytesAsString() {
		int len = this.bytes.length;
		StringBuilder sBuilder = new StringBuilder(len); // Javadoc: StringBuilder(int capacity) - Constructs a string builder with no characters in it and an initial capacity specified by the capacity argument.
		for (int i = 0; i < len; i++) {
			if (bytes[i] != -1) {
				sBuilder.append((char)bytes[i]);
			} else { // (a -1 in the bytes[] array signifies the end of the stream/header)
				break; // stop creating the String, end of bytes[] reached!
			}
		}
		this.bytesAsString = sBuilder.toString(); // StringBuilder->String
	}

	// just to be executed once by the constructor:
	// needed in the getStartOfTIFFHeader() function, so do NOT use that here!!!
	// also: use this.bytes and NOT this.bytesAsString !!!!
	private void determinePositionOfEXIFheaderAndEndianness() throws EXIFException { // (first position of "Exif00MM" or "Exif00II")
		int[] searchArrayMM = new int[]{0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x4D, 0x4D};
		int[] searchArrayII = new int[]{0x45, 0x78, 0x69, 0x66, 0x00, 0x00, 0x49, 0x49};
		int posMM = findArrayInArray(this.bytes, searchArrayMM); // first: look for first instance of "Exif00MM" in entire dataset
		if (posMM == -1) { // "Exif00MM" not found at all!
			int posII = findArrayInArray(this.bytes, searchArrayII); // look for first instance of "Exif00II" in entire dataset
			if (posII == -1) { // neither "Exif00MM" nor "Exif00II" found in entire dataset!!
				this.close();
				throw new EXIFException("EXIF Header (Exif00MM or Exif00II) not found!");
			} else { // "Exif00MM" not found, but "Exif00II" found
				this.bigEndian = false;
				this.positionOfEXIFheader = posII;
			}
		} else { // "Exif00MM" found
			int posII = findArrayInArray(this.bytes, searchArrayII, posMM); // see whether an instance of "Exif00II" occurs before first "Exif00MM"
			if (posII == -1) { // no instance of "Exif00II" occurs before first "Exif00MM"
				this.bigEndian = true;
				this.positionOfEXIFheader = posMM;
			} else { // there actually is an instance of "Exif00II" BEFORE(!) first "Exif00MM"
				this.bigEndian = false;
				this.positionOfEXIFheader = posII;
			}
		}
		assert positionOfEXIFheader != -1;
	}

	// just to be executed once by the constructor:
	// iff the file is in JPEG/JFIF and NOT in HEIF/HEIC format: insert a (-1) after the end of the EXIF-Header
	// --> increases efficiency & accuracy in read() functions, as they stop searching as soon as they encounter a (-1), writeBytesAsString() and findArrayinArray(!) as well
	/*
	* -> JPEG/EXIF in Intel-Format    ("II" 49-49): FF-D8-FF-E1-HL-LL-45-78-69-66-00-00-49-49-2A-00-08-00-00-00
	* -> JPEG/EXIF in Motorola-Format ("MM" 4D-4D): FF-D8-FF-E1-HL-LL-45-78-69-66-00-00-4D-4D-00-2A-00-00-00-08
	* -> Wobei HL-LL die Länge des EXIF-Datenblocks inklusive der Längenangabe, des EXIF-Headers und des TIFF-Headers darstellt.
	* All(!) EXIF files begin with "......Exif00" (FF-D8-FF-E1-HL-LL-45-78-69-66-00-00) before the "MM" or "II"
	* A JFIF file (of the type that we can handle) begins with "......JFIF.....H.H......Exif00" before the "MM" or "II"
	* -> just chop off the first 18 bytes: "......JFIF.....H.H"
	*/
	private void markEndOfEXIFHeaderInJPEGs() throws EXIFException {
		if (!this.hasHEIFHEICsuffix() && this.hasJPEGmagicbytes()) {
			final int JFIF_OFFSET = 18;
			int posOfHLLL;
			if (this.hasJPEGEXIFmagicbytes()) {
				posOfHLLL = 4;
			} else if (this.hasJPEGJFIFmagicbytes()) {
				posOfHLLL = JFIF_OFFSET+4;
			} else {
				this.close();
				throw new EXIFException("markEndOfEXIFHeaderInJPEGs(): File has JPEG magic bytes but is neither in EXIF nor in JFIF format!");
			}
			int hl = this.bytes[posOfHLLL];
			int ll = this.bytes[posOfHLLL+1];
			int exifBlockLength = 256*hl + ll;
			// The first byte not containing any useful information for us anymore is posOfHLLL + exifBlockLength
			this.bytes[posOfHLLL + exifBlockLength] = -1; // insert a (-1) after the end of the EXIF-Header
		}
	}

	/* XXXXXXX
	// just to be executed once by the constructor:
	private void determineEndianness() throws IllegalStateException, EXIFException { // "MM" Motorola format (Big-Endian) or "II" Intel format (Little-Endian)
		if (this.positionOfEXIFheader == -1) { // for some reason not yet determined!
			this.close();
			throw new IllegalStateException("determineEndianness(): positionOfEXIFheader not yet determined!");
		} else {
			String mmiiString; // should ideally be either "MM" or "II"
			// !!! TODO !!!
			switch (mmiiString) {
				case "MM":
					this.bigEndian = true;
					break;
				case "II":
					this.bigEndian = false; // little endian
					break;
				default:
					this.close();
					throw new EXIFException("Endianness could not be determined, neither Motorola nor Intel format!");
			}
		}
	}
	XXXXX */

	public boolean isValidEXIF() {
		return ( hasHEIFHEICsuffix() || (hasJPEGSuffix() && hasJPEGmagicbytes()) );
	}

	public boolean hasEXIFcompatibleSuffix() {
		return (this.hasJPEGSuffix() || this.hasHEIFHEICsuffix());
	}

	public boolean hasJPEGSuffix() {
		return ("jpg".equals(this.fileSuffix) || "jpeg".equals(this.fileSuffix) || "jpe".equals(this.fileSuffix) || "jfif".equals(this.fileSuffix));
	}

	public boolean hasHEIFHEICsuffix() {
		return ("heif".equals(this.fileSuffix) || "heic".equals(this.fileSuffix));
	}

	public boolean hasJPEGmagicbytes() {
		return (this.bytes[0] == 0xFF) && (this.bytes[1] == 0xD8) && (this.bytes[2] == 0xFF);
	}

	public boolean hasJPEGEXIFmagicbytes() {
		return (this.bytes[0] == 0xFF) && (this.bytes[1] == 0xD8) && (this.bytes[2] == 0xFF) && (this.bytes[3] == 0xE1);
	}

	public boolean hasJPEGJFIFmagicbytes() {
		return (this.bytes[0] == 0xFF) && (this.bytes[1] == 0xD8) && (this.bytes[2] == 0xFF) && (this.bytes[3] == 0xE0);
	}

	/**
	* The TIFF header's position is important as the EXIF Value Offsets are calculated from the start of the TIFF header!
	* The TIFF header (is always exactly 8 bytes long and) looks like this:
	* -> Intel-Form (Little-Endian):  49-49-2A-00-08-00-00-00
	* -> Motorola-Form (Big-Endian):  4D-4D-00-2A-00-00-00-08
	*/
	public int getStartOfTIFFHeader() throws IllegalStateException {
		final int START_OF_TIFF_HEADER_JPEGEXIF = 12; // FF-D8-FF-E1-HL-LL-45-78-69-66-00-00 -> then TIFF-Header, either in Intel or Motorola form (see PDF by T. Schmidt)
		final int JFIF_OFFSET = 18; // a JPEG-JFIF looks like a JPEG-EXIF but with "......JFIF.....H.H" in front (18 bytes)
		if (this.hasJPEGEXIFmagicbytes()) {
			return START_OF_TIFF_HEADER_JPEGEXIF;
			// example: FF D8 FF E1 2F FE 45 78 69 66 00 00 4D 4D 00 2A 00 00 00 08 ("......Exif00MM.*....")
		} else if (this.hasJPEGJFIFmagicbytes()) {
			return JFIF_OFFSET+START_OF_TIFF_HEADER_JPEGEXIF;
			// example: FF D8 FF E0 00 10 4A 46 49 46 00 01 01 00 00 48 00 48 00 00 FF E1 08 4A 45 78 69 66 00 00 4D 4D 00 2A 00 00 00 08
			//          "......JFIF.....H.H......Exif00MM.*...."
		} else {
			// positionOfEXIFheader --> first position of "Exif00MM" or "Exif00II" (-1 means not yet determined)
			// here we shall return the first position of "MM"/"II"
			// "Exif00" is 6 bytes, so just add 6
			if (this.positionOfEXIFheader == -1) { // for some reason not yet determined!
				this.close();
				throw new IllegalStateException("getStartOfTIFFHeader(): positionOfEXIFheader not yet determined!");
			}
			return this.positionOfEXIFheader+6;
		}
	}

	// ========== ========== Actually important functions, previously in the EXIFTool class: ========== ==========

	/**
	* Abstracted function: get the DateTime timestamp
	* by regexing the patterm "YYYY:MM:DD HH:MM:SS" or (maybe later) somehow reading the actual EXIF tags...:
	* – 7 (0x7) GPSTimeStamp, Type=RATIONAL, Count=3: Indicates the time as UTC (Coordinated Universal Time). 3 RATIONAL values giving hour, minute & second
	* – 29 (0x1D) GPSDateStamp, Type=ASCII, Count=11: date and time relative to UTC, format is "YYYY:MM:DD.", length is 11 bytes including NULL
	* – 306 (0x132) DateTime, Type=ASCII, Count=20: image creation. when the file was changed. format is "YYYY:MM:DD HH:MM:SS" (time in 24-hour format).
	* – 36867 (0x9003) DateTimeOriginal, Type=ASCII, Count=20: when the original image data was generated. format is "YYYY:MM:DD HH:MM:SS" (time in 24-hour format).
	* – 36868 (0x9004) DateTimeDigitized, Type=ASCII, Count=20: when the image was stored as digital data. format is "YYYY:MM:DD HH:MM:SS" (time in 24-hour format).
	* !!! Since EXIF Version 2.3(!!) there are also the timezone-related tags "OffsetTime", "OffsetTimeOriginal", "OffsetTimeDigitized" !!!
	*/
	public long readTimestamp() throws EXIFException, NullPointerException {
		return this.readFirstYYYYMMDDHHMMSS();
	}

	/**
	* 1) Takes the entire EXIF-Header as a String (this.bytesAsString)
	* 2) Uses Regex to find first instance of pattern "YYYY:MM:DD HH:MM:SS"
	* 3) Uses Javas standard library (Calendar class) to turn that into Epoch time (the long value returned)
	* Throws EXIFException if Regex isn't found!
	* Throws NullPointerException if this EXIFFile is closed!
	*/
	public long readFirstYYYYMMDDHHMMSS() throws EXIFException, NullPointerException {
		if (this.bytesAsString == null) {
			this.close();
			throw new NullPointerException("readFirstYYYYMMDDHHMMSS(): bytesAsString == null");
		} else {
			try {
				// -------------------- 2) --------------------
				int startIndex = findRegExIndex(this.bytesAsString, "[1-2][0-9][0-9][0-9]:[0-1][0-9]:[0-3][0-9]\\s[0-2][0-9]:[0-5][0-9]:[0-5][0-9]"); // theres actually also always a "NULL for termination" (see PDF, page 22)
    			int year = Integer.parseInt(this.bytesAsString.substring(startIndex, startIndex+4));
    			int month = Integer.parseInt(this.bytesAsString.substring(startIndex+5, startIndex+7));
    			int day = Integer.parseInt(this.bytesAsString.substring(startIndex+8, startIndex+10));
    			int hour = Integer.parseInt(this.bytesAsString.substring(startIndex+11, startIndex+13));
    			int minute = Integer.parseInt(this.bytesAsString.substring(startIndex+14, startIndex+16));
    			int second = Integer.parseInt(this.bytesAsString.substring(startIndex+17, startIndex+19));
    			// -------------------- 3) --------------------
    			java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")); // Constructor is protected / class is abstract (due to locale-sensitivity)
    			cal.set(year, month-1, day, hour, minute, second); // 0-based month!!! (-> https://stackoverflow.com/questions/29029549/calendar-method-gettimeinmillis-returning-wrong-value)
    			cal.set(java.util.Calendar.MILLISECOND, 0); // otherwise the actual current millisecond counter will be in here - always returning odd values!
    			return cal.getTimeInMillis(); // "Returns this Calendar's time value in milliseconds." (type: long)
    		} catch (IllegalArgumentException ex) { // (Regex not found)
    			this.close();
    			throw new EXIFException(ex.getMessage()); // rethrow the IllegalArgumentException as an EXIFException!
    		}
		}
	}

	/**
	* NEWLY ADDED ON SEPT 15 2020.
	* Does essentially the same as readFirstYYYYMMDDHHMMSS() but will return the result as an int array:
	* [year, month, day, hour, minute, second]
	* (will return it as it is literally written in the file, no nasty UTC conversions or similar!!!)
	*/
	public int[] readFirstYYYYMMDDHHMMSSAsArray() throws EXIFException, NullPointerException {
		if (this.bytesAsString == null) {
			this.close();
			throw new NullPointerException("readFirstYYYYMMDDHHMMSS(): bytesAsString == null");
		} else {
			try {
				// -------------------- 2) --------------------
				int startIndex = findRegExIndex(this.bytesAsString, "[1-2][0-9][0-9][0-9]:[0-1][0-9]:[0-3][0-9]\\s[0-2][0-9]:[0-5][0-9]:[0-5][0-9]"); // theres actually also always a "NULL for termination" (see PDF, page 22)
    			int year = Integer.parseInt(this.bytesAsString.substring(startIndex, startIndex+4));
    			int month = Integer.parseInt(this.bytesAsString.substring(startIndex+5, startIndex+7));
    			int day = Integer.parseInt(this.bytesAsString.substring(startIndex+8, startIndex+10));
    			int hour = Integer.parseInt(this.bytesAsString.substring(startIndex+11, startIndex+13));
    			int minute = Integer.parseInt(this.bytesAsString.substring(startIndex+14, startIndex+16));
    			int second = Integer.parseInt(this.bytesAsString.substring(startIndex+17, startIndex+19));
    			// -------------------- 3) --------------------
    			return new int[]{year, month, day, hour, minute, second};
    		} catch (IllegalArgumentException ex) { // (Regex not found)
    			this.close();
    			throw new EXIFException(ex.getMessage()); // rethrow the IllegalArgumentException as an EXIFException!
    		}
		}
	}

	/**
	* A generalization of the readEXIFRATIONAL and readEXIFASCIIsingle functions
	* @return
	* a) the 4 bytes in the Value Offset field, if {count}*{length of type} <= 4
	* b) the {count}*{length of type} bytes located at the ValueOffset
	*/
	public int[] readEXIFTag(final int tagNumber, final int type, final int count) throws EXIFException, NullPointerException, IllegalArgumentException {
		int typeLength;
		switch (type) {
			case 1: // 1 = BYTE An 8-bit unsigned integer.,
				typeLength = 1;
				break;
			case 2: // 2 = ASCII An 8-bit byte containing one 7-bit ASCII code. The final byte is terminated with NULL.
				typeLength = 1;
				break;
			case 3: // 3 = SHORT A 16-bit (2-byte) unsigned integer
				typeLength = 2;
				break;
			case 4: // 4 = LONG A 32-bit (4-byte) unsigned integer
				typeLength = 4;
				break;
			case 5: // 5 = RATIONAL Two LONGs. The first LONG is the numerator and the second LONG expresses the denominator
				typeLength = 8;
				break;
			case 7: // 7 = UNDEFINED An 8-bit byte that can take any value depending on the field definition
				typeLength = 1;
				break;
			case 9: // 9 = SLONG A 32-bit (4-byte) signed integer (2's complement notation)
				typeLength = 4;
				break;
			case 10: // 10 = SRATIONAL Two SLONGs. The first SLONG is the numerator and the second SLONG is the denominator.
				typeLength = 8;
				break;
			default:
				throw new EXIFException("There is no corresponding EXIF data type for the number " + type);
		}
		int valueLength = count*typeLength;
		int[] valueOffset = this.readEXIFTagValueOffset(tagNumber, type, count);
		assert valueOffset.length == 4;
		if (valueLength <= 4) {
			return valueOffset;
		} else { // go to Value Offset and read the valueLength={count}*{length of type} bytes located there
			if (this.bytes == null) {
				this.close();
				throw new NullPointerException("readEXIFTag(): bytes == null");
			} else {
				int start_of_tiff_header = this.getStartOfTIFFHeader();
				int offset = java.lang.Math.toIntExact(readFourByteNumberWithEndianness(this.bigEndian, valueOffset[0], valueOffset[1], valueOffset[2], valueOffset[3]));
				int[] answer = new int[valueLength];
				for (int i = 0; i < valueLength; i++) { // fill the answer array with the values of this.bytes, at the (value) offset
					answer[i] = this.bytes[start_of_tiff_header+offset+i];
				}
				return answer;
			}
		}
	}

	/**
	* A generalization of the readEXIFRATIONAL and readEXIFASCIIsingle functions
	* Generate & find fingerprint -> read the Value Offset field
	* @return the 4 bytes in the Value Offset field (i.e. an integer array with 4 integers in the 0-255 range)
	*/
	private int[] readEXIFTagValueOffset(final int tagNumber, final int type, final int count) throws EXIFException, NullPointerException, IllegalArgumentException {
		if (type<1 || type>10 || type==6 || type==8) {throw new IllegalArgumentException("readEXIFTagValueOffset(): invalid EXIF type: " + type);}
		if (tagNumber>255) {throw new IllegalArgumentException("readEXIFTagValueOffset(): tagNumber>255");} // makes our life easier!
		if (count>255) {throw new IllegalArgumentException("readEXIFTagValueOffset(): count>255");} // makes our life easier!
		if (this.bytes == null) {
			this.close();
			throw new NullPointerException("readEXIFTagValueOffset(): bytes == null");
		} else {
			// ----- 1) Generate fingerprint: -----
			int[] fingerprint = new int[8];
			if (this.bigEndian) {
				//-> {Bytes 0-1 Tag: unique 2-byte number to identify the field}
				fingerprint[0] = 0x00;
				fingerprint[1] = tagNumber;
        		//-> {Bytes 2-3 Type: 2 = ASCII, etc...}
        		fingerprint[2] = 0x00;
				fingerprint[3] = type;
        		//-> {Bytes 4-7 Count: number of values!}
       		 	fingerprint[4] = 0x00;
				fingerprint[5] = 0x00;
				fingerprint[6] = 0x00;
				fingerprint[7] = count;
			} else { // little endian:
				//-> {Bytes 0-1 Tag: unique 2-byte number to identify the field}
				fingerprint[0] = tagNumber;
				fingerprint[1] = 0x00;
      		  	//-> {Bytes 2-3 Type: 2 = ASCII, etc...}
      		  	fingerprint[2] = type;
				fingerprint[3] = 0x00;
      		  	//-> {Bytes 4-7 Count: number of values!}
      		  	fingerprint[4] = count;
				fingerprint[5] = 0x00;
				fingerprint[6] = 0x00;
				fingerprint[7] = 0x00;
			}
			// ----- 2) Find fingerprint: -----
			int firstIndex = this.getStartOfTIFFHeader(); // do not search BEFORE the start of the TIFF Header -> prevent accidental matches, especially in HEIF/HEIC files
			int index = findArrayInArray(this.bytes, fingerprint, firstIndex); // int findArrayInArray(int[] sourceArray, int[] findArray, int firstIndex)
			if (index == -1) { // fingerprint not found!!
				throw new EXIFException("Fingerprint not found for Tag=" + tagNumber + ", Type=" + type + ", Count=" + count + ", bigEndian=" + this.bigEndian);
			}
			// ----- 3) Read the Value Offset field & return it: ----
			int[] answer = new int[4];
			answer[0] = this.bytes[index+fingerprint.length+0];
			answer[1] = this.bytes[index+fingerprint.length+1];
			answer[2] = this.bytes[index+fingerprint.length+2];
			answer[3] = this.bytes[index+fingerprint.length+3];
			return answer;
		}
	}

	/**
	* 1) Use the infos that we seek a TYPE=RATIONAL(=5) and tagNumber and count to generate the 8-byte fingerprint we are looking for
	*    -> See PDF by Technical Standardization Committee on AV & IT Storage Systems and Equipment, Pages 11-14.
    *    -> Pages 13&14: 4.6.2 IFD Structure:
    *    -> {Bytes 0-1 Tag: unique 2-byte number to identify the field}
    *    -> {Bytes 2-3 Type: 5 = RATIONAL}
    *    -> {Bytes 4-7 Count: number of values!}
    *   --> {Bytes 8-11 Value Offset: from the start of the TIFF header}
	* 2) find that fingerprint, read the next 4 bytes i.e. the Value Offset (from the start of the TIFF header)
	* 3) go to that offset and read the next {count}*8 bytes (each RATIONAL is 8 bytes long)
	*    -> PDF by T.S.C., page 14: RATIONAL = Two LONGs (each a 32-bit/4-byte unsigned integer), 1st numerator, 2nd denominator
	* 4) Create a new double[] array with {count} entries & fill them in
	*
	* @return a double[] array containing {count} doubles
	*/
	public double[] readEXIFRATIONAL(final int tagNumber, final int count) throws EXIFException, NullPointerException, IllegalArgumentException {
		final int TYPE = 5; // 5 = RATIONAL. Two LONGs (each 32-bit/4-byte, unsigned). The first LONG is the numerator and the second LONG expresses the denominator.
		int[] rationals = this.readEXIFTag(tagNumber, TYPE, count);

		if (rationals.length != count*8) { // we should have {count}*8 bytes (each RATIONAL is 8 bytes long)
			throw new EXIFException("readEXIFRATIONAL(): rationals.length = " + rationals.length + " != " + count*8);
		}

		// Now transforming the values into Java doubles:
		double[] answer = new double[count];
		for (int i = 0; i < answer.length; i++) {
			answer[i] = exifRATIONALtoDouble(this.bigEndian, rationals[8*i], rationals[8*i+1], rationals[8*i+2], rationals[8*i+3], rationals[8*i+4], rationals[8*i+5], rationals[8*i+6], rationals[8*i+7]);
		}
		return answer;
	}

	/**
	* Needed to read the signs for latitude & longitude:
	* GPSLatitudeRef: Tag = 1 (0x0001), Type = ASCII(=2), Count = 2, Default = none ('N'/'S')
    * GPSLongitudeRef: Tag = 3 (0x0003), Type = ASCII(=2), Count = 2, Default = none ('E'/'W')
    * NEW: GPSSpeedRef (Tag = 12, only used in SlideshowCreator, not in PicsToMap)
    * "Note: When the tag Type is ASCII, it shall be terminated with NULL. It shall be noted carefully that since the value count includes the terminator NULL,
    *     the total count is the number of data+1. For example, GPSLatitudeRef cannot have any values other than Type ASCII 'N' or 'S'; but because the terminator NULL is added, the value of N is 2."
	*
	* 1) Use the infos that we seek a TYPE=ASCII(=2) and tagNumber and count=2 to generate the 8-byte fingerprint
	*    -> {Bytes 0-1 Tag} {Bytes 2-3 Type: 2 = ASCII} {Bytes 4-7 Count: =2}
	* 2) find that fingerprint and read the ASCII character (from the next 4 bytes). DONE!
	*
	* Unlike readEXIFRATIONAL(), here we don't have to bother with:
	* - the Value Offset & reading the file again from the beginning, as here the value is directly stored after the "fingerprint"
	* - endianness of the stored value or the Value Offset
	*   (we do however have to deal with the endianness of the fingerprint)
	*/
	public char readEXIFASCIIsingle(final int tagNumber) throws EXIFException, NullPointerException, IllegalArgumentException {
		final int TYPE = 2; // 2 = ASCII. An 8-bit byte containing one 7-bit ASCII code. The final byte is terminated with NULL.
		final int COUNT = 2; // we're just reading a single character here, terminated with NULL: 1+1=2
		int[] valueOffset = this.readEXIFTag(tagNumber, TYPE, COUNT);
		if (valueOffset[1] != 0) { // there's no NULL byte after the character as we would expect
			throw new IllegalArgumentException("readEXIFASCIIsingle(): not NULL-terminated!");
		} else {
			return (char)valueOffset[0]; // 'N', 'E', 'S' or 'W' (when reading GPSLatitudeRef/GPSLongitudeRef which is what we do)
		}
	}



	// ========== ========== Helper functions: ========== ==========

	/**
	* @return the index of the first instance of regex in String str (not found: throws Exception)
    */
    private static int findRegExIndex(String str, String regex) throws IllegalArgumentException {
    	java.util.regex.Pattern p = java.util.regex.Pattern.compile(regex);
    	java.util.regex.Matcher m = p.matcher(str);
    	if (m.find()) {
    		return m.start();
    	} else {
    		//throw new IllegalArgumentException("RegEx not found!"); // error
    		throw new IllegalArgumentException("RegEx not found: " + regex); //+ " in " + str); // !!DEBUG!!
    		// ---> later rethrown as an EXIFException
    	}
    }

    /**
    * PDF by Technical Standardization Committee, page 14:
    * 4 = LONG:     A 32-bit (4-byte) unsigned integer
    * 5 = RATIONAL: Two LONGs. The first LONG is the numerator and the second LONG expresses the denominator.,
    */
    private static double exifRATIONALtoDouble(boolean bigEndian, int num1, int num2, int num3, int num4, int den1, int den2, int den3, int den4) {
    	long numerator = readFourByteNumberWithEndianness(bigEndian, num1, num2, num3, num4);
    	long denominator = readFourByteNumberWithEndianness(bigEndian, den1, den2, den3, den4);
    	return (double)numerator/denominator;
    }

    private static long readFourByteNumberWithEndianness(boolean bigEndian, int byte1, int byte2, int byte3, int byte4) {
    	if (bigEndian) { // e.g. 00-00-00-08
    		return byte4 + (byte3 << 8) + (byte2 << 16) + (byte1 << 24);
    	} else { // little endian: e.g. 08-00-00-00
    		return byte1 + (byte2 << 8) + (byte3 << 16) + (byte4 << 24);
    	}
    }

    /** !!!!!NEW!!!!
    * Goes through
    * @param sourceArray until it finds the
    * @param findArray and then
    * @return the index; or -1 if not found
    * Throws an IllegalArgumentException if the length of findArray is larger than that of the sourceArray!
    */
    private static int findArrayInArray(int[] sourceArray, int[] findArray) throws IllegalArgumentException {
    	return findArrayInArray(sourceArray, findArray, 0);
    	/*
    	if (findArray.length > sourceArray.length) {
    		throw new IllegalArgumentException("findArrayInArray(): findArray is longer than sourceArray!");
    	}
    	boolean found;
    	for (int i = 0; i <= (sourceArray.length-findArray.length); i++) {
    		found = true;
    		for (int j = 0; j < findArray.length; j++) {
    			if (findArray[0+j] != sourceArray[i+j]) { // mismatch:
    				found = false;
    				break;
    			}
    		}
    		if (found) { // findArray found at index i:
    			return i;
    		}
    	}
    	return -1; // not found!
    	*/
    }

    /**
    * Goes through
    * @param sourceArray starting at
    * @param firstIndex until it finds the
    * @param findArray and then
    * @return the index where it found the findArray; or -1 if not found
    * Throws an IllegalArgumentException if the length of findArray is larger than that of the sourceArray!
    */
    private static int findArrayInArray(int[] sourceArray, int[] findArray, int firstIndex) throws IllegalArgumentException {
    	return findArrayInArray(sourceArray, findArray, firstIndex, (sourceArray.length-findArray.length));
    }

    /**
    * Goes through
    * @param sourceArray starting at
    * @param firstIndex until it finds the
    * @param findArray or it reaches the 
    * @param lastIndex (= the last index in sourceArray at which to look for findArray)
    * OR when a (-1), indicating the end of the stream/header is reached and then
    * @return the index where it found the findArray; or -1 if not found
    * Throws an IllegalArgumentException if the length of findArray is larger than that of the sourceArray!
    */
    private static int findArrayInArray(int[] sourceArray, int[] findArray, int firstIndex, int lastIndex) throws IllegalArgumentException {
    	if ((findArray.length > sourceArray.length) || (firstIndex > (sourceArray.length-findArray.length)) || (lastIndex > (sourceArray.length-findArray.length))) {
    		throw new IllegalArgumentException("findArrayInArray(): findArray is longer than sourceArray or firstIndex/lastIndex exceeds sourceArray.length-findArray.length!");
    	}
    	boolean found;
    	for (int i = firstIndex; i <= lastIndex; i++) {
    		if (sourceArray[i] == -1) { // end of the stream/header is reached!!
    			return -1; // not found!
    		}
    		found = true;
    		for (int j = 0; j < findArray.length; j++) {
    			if (findArray[0+j] != sourceArray[i+j]) { // mismatch:
    				found = false;
    				break;
    			}
    		}
    		if (found) { // findArray found at index i:
    			return i;
    		}
    	}
    	return -1; // not found!
    }

    /*
    * Just like findArrayInArray(int[] sourceArray, int[] findArray) but it stops the search when
    * @param searchDepth (= the last index in sourceArray at which to look for findArray) is reached
    * OR when a (-1), indicating the end of the stream/header is reached.
    * @return the index; -1 if not found or searchDepth is reached!
    *
    private static int findArrayInArray(int[] sourceArray, int[] findArray, int searchDepth) throws IllegalArgumentException {
    	if ((findArray.length > sourceArray.length) || (searchDepth > (sourceArray.length-findArray.length))) {
    		throw new IllegalArgumentException("findArrayInArray(): findArray is longer than sourceArray or searchDepth exceeds sourceArray.length-findArray.length!");
    	}
    	boolean found;
    	for (int i = 0; i <= searchDepth; i++) {
    		if (sourceArray[i] == -1) { // end of the stream/header is reached!!
    			return -1; // not found!
    		}
    		found = true;
    		for (int j = 0; j < findArray.length; j++) {
    			if (findArray[0+j] != sourceArray[i+j]) { // mismatch:
    				found = false;
    				break;
    			}
    		}
    		if (found) { // findArray found at index i:
    			return i;
    		}
    	}
    	return -1; // not found!
    }*/

}
