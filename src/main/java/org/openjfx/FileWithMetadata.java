package org.openjfx;

import java.io.File;
import java.util.HashMap;

public class FileWithMetadata extends java.io.File { //implements Comparable<FileWithMetadata> { // !!!! CANNOT BE DONE, because File already implements Comparable<File> !!!!

	// !!! Casting (FileWithMetadata)File leads to java.lang.ClassCastException !!!
	public FileWithMetadata(File f) {
		super(f.toURI());
		/* ----- JavaDoc: public File(URI uri) -----
		For a given abstract pathname f it is guaranteed that
		new File( f.toURI()).equals( f.getAbsoluteFile()) 
		so long as the original abstract pathname, the URI, and the new abstract pathname are all created in (possibly different invocations of) the same Java virtual machine. 
		*/
	}

	/*
	JavaDoc: java.io.File: Constructor Summary
	File(File parent, String child)
		Creates a new File instance from a parent abstract pathname and a child pathname string.
	File(String pathname)
		Creates a new File instance by converting the given pathname string into an abstract pathname.
	File(String parent, String child)
		Creates a new File instance from a parent pathname string and a child pathname string.
	File(URI uri)
		Creates a new File instance by converting the given file: URI into an abstract pathname.
	*/
	// See also: ->  https://stackoverflow.com/questions/1644317/java-constructor-inheritance

	public FileWithMetadata(File parent, String child) {
		super(parent, child);
	}

	public FileWithMetadata(String pathname) {
		super(pathname);
	}

	public FileWithMetadata(String parent, String child) {
		super(parent, child);
	}

	public FileWithMetadata(java.net.URI uri) {
		super(uri);
	}

	// -> https://stackoverflow.com/questions/10948348/does-java-have-a-data-structure-that-stores-key-value-pairs-equivalent-to-idict
	// -> https://docs.oracle.com/javase/7/docs/api/java/util/Map.html
	// -> https://docs.oracle.com/javase/6/docs/api/java/util/HashMap.html
	// -> https://en.wikipedia.org/wiki/Hash_table
	private java.util.HashMap<String, Comparable> metadataMap = new HashMap<String, Comparable>();

	public void setMetadata(String key, Comparable value) {
		metadataMap.put(key, value);
		// JavaDoc: "If the map previously contained a mapping for the key, the old value is replaced by the specified value."
	}

	/**
	* @return null if there is no value stored for the key name specified
	*/
	public Comparable getMetadata(String key) {
		return metadataMap.get(key);
		// JavaDoc: "Returns the value to which the specified key is mapped, or null if this map contains no mapping for the key."
	}

	// ----- For java.util.Arrays.sort() only !!! -----

	/*
	public String comparisonKey = null; // The key value to compare by

	@Override
	public int compareTo(Object o) {
        FileWithMetadata other = (FileWithMetadata) o;
        return this.getMetadata(comparisonKey).compareTo(other.getMetadata(comparisonKey));
    }

    // -> http://www.angelikalanger.com/Articles/EffectiveJava/01.Equals-Part1/01.Equals1.html
    @Override
    public boolean equals(Object other) {
		if (this == other) {return true;}
		if (other == null) {return false;}
		if (other.getClass() != getClass()) {return false;}
		return (this.compareTo(other) == 0);
   }
   */

}
