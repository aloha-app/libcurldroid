package com.wealoha.libcurldroid.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-06 08:40:10
 */
public interface Cache {

	/**
	 * return meta information about the cached file
	 * 
	 * @param url
	 * @return null if not exist
	 * @throws IOException
	 */
	public CacheFile get(String url) throws IOException;
	
	/**
	 * return the real {@link InputStream}
	 * 
	 * @param file
	 * @return
	 * @throws IOException
	 */
	public InputStream getInputStream(CacheFile file) throws IOException;

	/**
	 * save data to cache.
	 * 
	 * if key exist, clean previous key
	 * 
	 * @param url
	 * @param data
	 * @param lastModifiedDate may be null
	 * @param expireDate
	 * @throws IOException
	 */
	public void set(String url, byte[] data, Date lastModifiedDate, Date expireDate) throws IOException;

	/**
	 * remove file
	 * 
	 * @param url
	 * @throws IOException
	 */
	public void remove(String url) throws IOException;
}
