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
	private final byte[] content;

	/**
	 * 
	 * @param name required
	 * @param filename
	 * @param contentType
	 * @param content required
	 */
	public MultiPart(String name, String filename, String contentType, byte[] content) {
		super();
		this.name = name;
		this.filename = filename;
		this.contentType = contentType;
		this.content = content;
	}

	public String getName() {
		return name;
	}
	
	/**
	 * 
	 * @return may be null
	 */
	public String getFilename() {
		return filename;
	}
	
	/**
	 * 
	 * @return may be null
	 */
	public String getContentType() {
		return contentType;
	}
	
	public byte[] getContent() {
		return content;
	}
}
