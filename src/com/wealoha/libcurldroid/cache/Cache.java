package com.wealoha.libcurldroid.cache;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * <h1>Cache</h1>
 * 
 * Cached item contains meta(CacheFile) and data(InputStream)
 * 
 * <pre>
 * Cache cache;
 * String key;
 * Map<String, Object> metaMap;
 * 
 * cache.set(key, data, metaMap);
 * 
 * CacheFile cacheFile = cache.get(key);
 * InputStream is = cache.getInputStream(cacheFile);
 * 
 * cache.remove(key);
 * 
 * 
 * </pre> 
 * 
 * @author javamonk
 * @createTime 2015-02-06 08:40:10
 */
public interface Cache {

	/**
	 * return meta information about the cached file
	 * 
	 * @param key
	 * @return null if not exist
	 * @throws IOException
	 */
	public CacheFile get(String key) throws IOException;
	
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
	 * @param metaMap
	 * @throws IOException
	 */
	public void set(String key, byte[] data, Map<String, String> metaMap) throws IOException;

	/**
	 * remove file
	 * 
	 * @param key
	 * @throws IOException
	 */
	public void remove(String key) throws IOException;
}
