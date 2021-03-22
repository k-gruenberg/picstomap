package org.openjfx;

// Tiny Helper Class for the SlideshowCreator (NominatimAPI and OverpassAPI)
public final class LatitudeLongitudePair {
	
	public double latitude; // parallels (to Equator): North/South (90°N North Pole - 0° Equator - 90°S South Pole)
	public double longitude; // West/East, converge at the North and South Poles (0° Greenwich, 180°W=180°E=Date Line)

	// Constructor:
	public LatitudeLongitudePair(double latitude, double longitude) {
		this.latitude = latitude;
		this.longitude = longitude;
	}
}
