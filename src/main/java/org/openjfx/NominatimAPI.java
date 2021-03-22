package org.openjfx;

// Utility class:
public final class NominatimAPI {

	public static final boolean DEBUG = false;

	private NominatimAPI() {} // private constructor to prevent instantiation (Utility class)

	// Previously in Main.java:
	/**
	* "Reverse geocoding generates an address from a latitude and longitude or from an OSM object."
	* -> https://nominatim.org/release-docs/develop/api/Reverse/
	* -> https://wiki.openstreetmap.org/wiki/Nominatim
	*/
	public static String reverseGeocode(double latitude, double longitude) {
		final String placeholderURL = "https://nominatim.openstreetmap.org/reverse?format=xml&lat={lat}&lon={long}&zoom=5&addressdetails=0";
		// For replacing & RegEx see: OSMTile.downloadImage() method
		String urlString = placeholderURL.replaceFirst("\\{lat\\}", ""+latitude);
		urlString = urlString.replaceFirst("\\{long\\}", ""+longitude);
		final String userAgent = "Mozilla/5.0 (Java HttpURLConnection; PicsToMap and SlideshowCreator Applications)";
		String xmlServerResponse = HTTPTool.getRequest(urlString, userAgent);
		if (xmlServerResponse == null) { // HTTPTool.getRequest didn't work:
			return "{error!}";
		}
		// Extract whats in the result-tag: <result [...]>[!!!]</result>
		int indexStartTag = xmlServerResponse.indexOf("<result ");
		int endOfStartTag = xmlServerResponse.indexOf(">", indexStartTag);
		int indexEndTag = xmlServerResponse.indexOf("</result>");
		// "the index of the first occurrence of the specified substring, or -1 if there is no such occurrence."
		// -> example: "abc</result>".indexOf("</result>") ==> 3
		if ((indexStartTag == -1) || (endOfStartTag == -1) || (indexEndTag == -1)) {
			if (xmlServerResponse.contains("<error>Unable to geocode</error>")) {
				return "{error: nominatim was unable to geocode}";
			} else {
				return "{error: XML extraction failed; indexStartTag=" + indexStartTag + "; endOfStartTag=" + endOfStartTag + "; indexEndTag=" + indexEndTag + "}"; //"; XML: " + xmlSeverResponse.replace("\n", "");
			}
		} else {
			return xmlServerResponse.substring(endOfStartTag+1, indexEndTag).trim();
		}
	}

	// New: A Slideshow of a Los Angeles Trip will include mostly pictures taken on the highway...
	// Goes into a bit more complexity than reverseGeocode()
	// Also see: -> https://nominatim.org/release-docs/develop/api/Reverse/
	/**
	* https://wiki.openstreetmap.org/wiki/Key:highway
	* "The key highway=* is the main key used for identifying any kind of road, street or path.
	* The value of the key helps indicate the importance of the highway within the road network as a whole."
	*
	* @return a score between 0 and 10 (invented by myself and not OSM!!)
	*			0 not a road at all
	*			1 path for pedestrians (an maybe bicycles), no cars
	*			2 low-level, residential road
	*			3 medium-level road
	*			4 high-level road (highway/motorway), no pedestrians
	*/
	public static int roadType(double latitude, double longitude) {
		// TODO??
		return -1; // feature not implemented yet!
	}

	// Previously in SlideshowCreator.java:
	/**
	* See also: String Main.reverseGeocode(double latitude, double longitude) function
	*
	* -> https://wiki.openstreetmap.org/wiki/Nominatim
	* Usage Policy: https://operations.osmfoundation.org/policies/nominatim/
	*     No heavy uses (an absolute maximum of 1 request per second).
    *     Provide a valid HTTP Referer or User-Agent identifying the application (stock User-Agents as set by http libraries will not do).
    *     Clearly display attribution as suitable for your medium.
    *     Data is provided under the ODbL license which requires to share alike (although small extractions are likely to be covered by fair usage / fair dealing).
	*
	* -> https://nominatim.org/release-docs/develop/api/Overview/
	* API: /search - search OSM objects by name or type
	*      /reverse - search OSM object by their location
	*      /lookup - look up address details for OSM objects by their ID
	*
	* !!!!! https://nominatim.org/release-docs/develop/api/Search/ !!!!!
	* 
	* @param queryString textual description of a location, something like "Los Angeles" or "Switzerland"
	* @return latitude & longitude of that location, found by the nominatim API!!!
	*         null whenever API search fails!
	*/
	public static LatitudeLongitudePair search(String queryString) {
		// First: Determine Query URL:
		final String placeholderURL = "https://nominatim.openstreetmap.org/search/{QUERY}?format=xml&limit=1";
		// -> https://stackoverflow.com/questions/4737841/urlencoder-not-able-to-translate-space-character
		// -> https://stackoverflow.com/questions/1634271/url-encoding-the-space-character-or-20
		//        --> "This boils down to: You should have %20 before the ? and + after.""
		// WARNING: @Deprecated public static String encode(String s) ; use the encode(String,String) method to specify the encoding
		//        --> Solution: https://stackoverflow.com/questions/213506/java-net-urlencoder-encodestring-is-deprecated-what-should-i-use-instead
		String urlEncodedQueryString;
		try {
			urlEncodedQueryString = java.net.URLEncoder.encode(queryString, java.nio.charset.StandardCharsets.UTF_8.toString()).replace("+", "%20"); // "This boils down to: You should have %20 before the ? and + after."
		} catch (java.io.UnsupportedEncodingException unencex) {
			return null; // error, he does not know UTF-8...
		}
		String queryURL = placeholderURL.replace("{QUERY}", urlEncodedQueryString);
		// Second: Query that URL: -> https://www.baeldung.com/java-http-request
		final String userAgent = "Mozilla/5.0 (Java HttpURLConnection; PicsToMap and SlideshowCreator Applications)";
		String xmlServerResponse = HTTPTool.getRequest(queryURL, userAgent);
		if (xmlServerResponse == null) { // HTTPTool.getRequest didn't work:
			return null;
		}
		// Third: Interpret the XML result:
		// Format: <searchresults ...><place ... lat="34.0536909" lon="-118.2427666" ... /></searchresults>
		int latLocation = xmlServerResponse.indexOf("lat="); // JavaDoc: Returns: the index of the first occurrence of the specified substring, or -1 if there is no such occurrence.
		int lonLocation = xmlServerResponse.indexOf("lon=");
		if (latLocation == -1 || lonLocation == -1) {
			return null; // error, no lat/lon values in XML response
		}
		// JavaDoc: public String substring(int beginIndex)
		//          Returns a new string that is a substring of this string. The substring begins with the character at the specified index and extends to the end of this string. 
		String latString = xmlServerResponse.substring(latLocation+5); //.split("\"")[0];
		String lonString = xmlServerResponse.substring(lonLocation+5); //.split("\"")[0];
		if (DEBUG) {
			System.out.println("DEBUG: latLocation = " + latLocation);
			System.out.println("DEBUG: lonLocation = " + lonLocation);
			System.out.println("DEBUG: latString = " + latString);
			System.out.println("DEBUG: lonString = " + lonString);
		}
		// Last: Return the anser:
		double latitude = parseDoubleBeginning(latString);
		double longitude = parseDoubleBeginning(lonString);
		return new LatitudeLongitudePair(latitude, longitude);
	}

	// ??? when I call the Nominatim API from Firefox I get " quotes, yet here I get ' quotes
	/**
	* @param str "123.456'xyzbla"
	* @return (double)123.456
	*/
	private static double parseDoubleBeginning(String str) {
		if (DEBUG) {System.out.println("DEBUG: Function call: parseDoubleBeginning(" + str + ")");}
		try {
			return Double.parseDouble(str);
		} catch (NumberFormatException nfex) {
			final String allowedCharacters = "0123456789.+-"; // DON'T FORGET THE MINUS SIGN!!
			int indexOfFirstIllegalCharacter = str.length();
			for (int i = 0; i < str.length() ; i++) {
				if (allowedCharacters.indexOf((int)str.charAt(i)) == -1) { // str.charAt(i) is illegal character
					indexOfFirstIllegalCharacter = i;
					break;
				}
			}
			return Double.parseDouble(str.substring(0, indexOfFirstIllegalCharacter));
		}
	}

}
