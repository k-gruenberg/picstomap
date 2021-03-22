package org.openjfx;

/**
* A type of Exception that is thrown whenever something that is supposed to be EXIF data cannot be interpreted correctly!
* ---> Warning message will be printed to console (see Picture.createFromFile())
* !!! When a file is in an unsupported format (e.g. PNG), throw a FormatNotSupportedException !!!
*/
public class EXIFException extends Exception {
	public EXIFException () {}
	public EXIFException (String message) {super(message);}
	public EXIFException (Throwable cause) {super(cause);}
	public EXIFException (String message, Throwable cause) {super(message, cause);}
	// See: https://stackoverflow.com/questions/8423700/how-to-create-a-custom-exception-type-in-java
}
