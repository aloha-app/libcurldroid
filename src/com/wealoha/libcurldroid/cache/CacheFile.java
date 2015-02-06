package com.wealoha.libcurldroid.cache;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015年2月5日 下午5:51:15
 */
public class CacheFile {

	private final String url;
	private final String urlMd5;
	private final long fileSize;
	private final long lastAccessMillis;
	private final Long lastModifiedMillis;
	private final long expireTimeMillis;
	private final long createTimeMillis;
	
	public CacheFile(String url, String urlMd5, long fileSize, long lastAccessMillis, Long lastModifiedMillis, long expireTimeMillis,
			long createTimeMillis) {
		super();
		this.url = url;
		this.urlMd5 = urlMd5;
		this.fileSize = fileSize;
		this.lastAccessMillis = lastAccessMillis;
		this.expireTimeMillis = expireTimeMillis;
		this.createTimeMillis = createTimeMillis;
		this.lastModifiedMillis = lastModifiedMillis;
	}

	public String getUrl() {
		return url;
	}
	
	public String getUrlMd5() {
		return urlMd5;
	}
	
	public long getFileSize() {
		return fileSize;
	}
	
	public long getLastAccessMillis() {
		return lastAccessMillis;
	}
	
	public long getExpireTimeMillis() {
		return expireTimeMillis;
	}
	
	public long getCreateTimeMillis() {
		return createTimeMillis;
	}
	
	public Long getLastModifiedMillis() {
		return lastModifiedMillis;
	}
}
