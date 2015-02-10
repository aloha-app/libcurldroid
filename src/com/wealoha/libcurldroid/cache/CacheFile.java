package com.wealoha.libcurldroid.cache;

import java.util.Map;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015年2月5日 下午5:51:15
 */
public class CacheFile {

	private final String key;
	private final long fileSize;
	private long lastAccessMillis;
	private final long createTimeMillis;
	private final Map<String, String> meta;
	
	
	/**
	 * 
	 * @param key
	 * @param fileSize
	 * @param lastAccessMillis
	 * @param createTimeMillis
	 * @param meta
	 */
	public CacheFile(String key, long fileSize, long lastAccessMillis,
			long createTimeMillis, Map<String, String> meta) {
		super();
		this.key = key;
		this.fileSize = fileSize;
		this.lastAccessMillis = lastAccessMillis;
		this.createTimeMillis = createTimeMillis;
		this.meta = meta;
	}

	public String getKey() {
		return key;
	}
	
	public long getFileSize() {
		return fileSize;
	}
	
	public long getLastAccessMillis() {
		return lastAccessMillis;
	}
	
	public void setLastAccessMillis(long lastAccessMillis) {
		this.lastAccessMillis = lastAccessMillis;
	}
	
	public long getCreateTimeMillis() {
		return createTimeMillis;
	}

	public Map<String, String> getMeta() {
		return meta;
	}
	
	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result + ((key == null) ? 0 : key.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		CacheFile other = (CacheFile) obj;
		if (key == null) {
			if (other.key != null)
				return false;
		} else if (!key.equals(other.key))
			return false;
		return true;
	}
	
	
}
