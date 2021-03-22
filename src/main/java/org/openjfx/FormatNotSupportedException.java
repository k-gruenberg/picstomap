package org.openjfx;

/**
* Thrown when a file is in an unsupported format (e.g. PNG)
* ---> QUIET! No Warning message will be printed to console! (see Picture.createFromFile())
* !!! Throw an EXIFException whenever something that is supposed to be EXIF data cannot be interpreted correctly! !!!
*/
public class FormatNotSupportedException extends Exception {
	public FormatNotSupportedException () {}
	public FormatNotSupportedException (String message) {super(message);}
	public FormatNotSupportedException (Throwable cause) {super(cause);}
	public FormatNotSupportedException (String message, Throwable cause) {super(message, cause);}
	// See: https://stackoverflow.com/questions/8423700/how-to-create-a-custom-exception-type-in-java
}
