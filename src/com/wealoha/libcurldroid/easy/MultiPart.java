package com.wealoha.libcurldroid.easy;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-01 14:18:59
 */
public class MultiPart {
	
	private final String name;
	private final String filename;
	private final String contentType;
	private final byte[] data;

	public MultiPart(String name, String filename, String contentType, byte[] data) {
		super();
		this.name = name;
		this.filename = filename;
		this.contentType = contentType;
		this.data = data;
	}

	public String getName() {
		return name;
	}
	public String getFilename() {
		return filename;
	}
	public String getContentType() {
		return contentType;
	}
	public byte[] getData() {
		return data;
	}
}
