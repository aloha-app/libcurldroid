package com.wealoha.libcurldroid.cache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.Date;

import android.support.v4.util.LruCache;
import android.util.Log;

import com.wealoha.libcurldroid.Constant;
import com.wealoha.libcurldroid.util.StringUtils;

/**
 * <h1>Disk based LRU cache</h1>
 * 
 * Two layer:
 * 
 * <ol>
 *   <li>memory(LruCache)</li>
 *   <li>disk(file)</li>
 * </ol>
 * 
 * A file cached with key(url), data and meta on disk.<br/>
 * When getting a file by key, first we get meta from memory, if not hit, warm meta from disk.
 * Then read InputStream by meta separately.
 * 
 * @author javamonk
 * @createTime 2015-02-05 17:48:18
 */
public class DiskCache implements Cache {

	// key: urlMd5
	private LruCache<String, CacheFile> fileCache;
	private File path;
	private int maxCacheSizeInBytes;
	
	/**
	 * 
	 * @param path
	 */
	public DiskCache(File path) {
		this.path = path;
		fileCache = new LruCache<String, CacheFile>(2000);
	}
	
	/**
	 * set max cache size in bytes
	 * 
	 * @param size
	 * @return
	 */
	public DiskCache  maxCacheSizeInBytes(int size) {
		this.maxCacheSizeInBytes = size;
		return this;
	}
	
	public DiskCache autoEvictEachIntervalMillis(long millis) {
		// TODO rename
		return this;
	}
	
	
	/**
	 * Get
	 * 
	 * @param url
	 * @return null if miss
	 */
	@Override
	public CacheFile get(String url) {
		String urlMd5 = StringUtils.md5(url);
		
		CacheFile item = getCacheFile(urlMd5);
		
		if (item == null) {
			return null;
		}
		
		while (!url.equals(item.getUrl())) {
			// md5 conflict, different url with same md5
			Log.w(Constant.TAG, "md5 conflict url=" + url + " cacheUrl=" + item.getUrl() + ", md5=" + urlMd5);
		
			// recalculate a new md5
			// same algorithm with set
			urlMd5 = StringUtils.md5(urlMd5 + url);
			
			item = getCacheFile(urlMd5);
			
			if (item == null) {
				return null;
			}
		}
		
		return item;
	}
	
	/**
	 * 
	 * @param file
	 * @return null or a new InputStream you must call close()
	 * @throws IOException
	 */
	@Override
	public InputStream getInputStream(CacheFile file) throws IOException {
		// TODO
		if (file == null) {
			return null;
		}
		
		String filePath = getFilePath(file.getUrlMd5());
		File fileFile = new File(filePath);
		if (fileFile.exists()) {
			return new FileInputStream(fileFile);
		} else {
			// file not exist
			Log.w(Constant.TAG, "meta file not exist");
			// delete from memory cache
			fileCache.remove(file.getUrlMd5());
			// delete meta file
			File metaFile = new File(getMetaFilePath(file.getUrlMd5()));
			if (metaFile.exists()) {
				metaFile.delete();
			}
		}
		
		return null;
	}
	
	/**
	 * set data to cache
	 * 
	 * if key exist, clean previous key
	 * 
	 * @param url
	 * @param data
	 * @param expireDate
	 */
	@Override
	public void set(String url, byte[] data, Date lastModifiedDate, Date expireDate) throws IOException {
		// check exist?
		String urlMd5 = StringUtils.md5(url);
		
		CacheFile cacheFile = getCacheFile(urlMd5);
		if (cacheFile != null) {
			while (urlMd5.equals(cacheFile.getUrlMd5()) 
					&& !url.equals(cacheFile.getUrl())) {
				// same md5, different url, key conflict
				// same algorithm with set
				urlMd5 = StringUtils.md5(urlMd5 + url);
				cacheFile = getCacheFile(urlMd5);
				if (cacheFile == null) {
					break;
				}
			}
		}
		
		synchronized (urlMd5.intern()) {
			// only one can write a same name file
			long now = System.currentTimeMillis();
			Long lastModifiedTime = lastModifiedDate == null ? null : lastModifiedDate.getTime();
			if (cacheFile == null) {
				// cache
				cacheFile = new CacheFile(url, urlMd5, data.length, now, lastModifiedTime, expireDate.getTime(), now);	
				Log.v(Constant.TAG, "cache new file: url=" + url);
			} else {
				// update
				cacheFile = new CacheFile(url, urlMd5, data.length, now, lastModifiedTime, expireDate.getTime(), cacheFile.getCreateTimeMillis());
				Log.v(Constant.TAG, "cache replace file: url=" + url);
				fileCache.remove(urlMd5);
			}
			
			File targetDir = new File(getFileDir(urlMd5));
			if (!targetDir.exists()) {
				targetDir.mkdirs();
			}
			File dataFile = new File(getFilePath(urlMd5));
			File metaFile = new File(getMetaFilePath(urlMd5));
			
			
			// save data
			FileOutputStream dataOs = new FileOutputStream(dataFile);
			try {
				dataOs.write(data);
				dataOs.flush();
			} finally {
				dataOs.close();
			}
			
			FileOutputStream metaOs = new FileOutputStream(metaFile);
			try {
				metaOs.write(encodeMeta(cacheFile).getBytes());
				metaOs.flush();
			} finally {
				metaOs.close();
			}
			
		}
	}
	
	@Override
	public void remove(String url) throws IOException {
		// TODO Auto-generated method stub
		
	}
	
	private CacheFile getCacheFile(String urlMd5) {
		// -read from cache
		CacheFile cacheFile = fileCache.get(urlMd5);
		if (cacheFile == null) {
			// -fail-back to disk
		
			String metaFilePath = getMetaFilePath(urlMd5);
			File metaFile = new File(metaFilePath);
			try {
				cacheFile = decodeMeta(metaFile);
				
				if (cacheFile != null) {
					fileCache.put(urlMd5, cacheFile);
					fileCache.get(urlMd5); // read one time
				}
			} catch (IOException e) {
				// TODO throw exception
			}
		}
		
		return cacheFile;
	}
	
	private String getFileDir(String urlMd5) {
		return String.format("%s/%s/%s/",
				path.getAbsolutePath(), 
				urlMd5.substring(0, 1),
				urlMd5.substring(1, 2));
	}
	
	@Deprecated
	private String getFilePath(String urlMd5) {
		return String.format("%s/%s/%s/%s",
				path.getAbsolutePath(), 
				urlMd5.substring(0, 1),
				urlMd5.substring(1, 2),
				urlMd5);
	}
	
	@Deprecated
	private String getMetaFilePath(String urlMd5) {
		return getFilePath(urlMd5) + ".meta";
	}
	
	private String encodeMeta(CacheFile file) {
		StringBuilder sb = new StringBuilder() //
			.append("url=" + file.getUrl() + "\n") //
			.append("urlMd5=" + file.getUrlMd5() + "\n") //
			.append("fileSize=" + file.getFileSize() + "\n") //
			.append("lastAccess=" + file.getLastAccessMillis() + "\n") //
			.append("expireTime=" + file.getExpireTimeMillis() + "\n") //
			.append("createTime=" + file.getCreateTimeMillis() + "\n");
		if (file.getLastModifiedMillis() != null) {
			sb.append("lastModifiedTime=" + file.getLastModifiedMillis() + "\n");
		}
		return sb.toString();
	}
	
	private CacheFile decodeMeta(File metaFile) throws IOException {
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(metaFile)));
		
		String url = null;
		String urlMd5 = null;
		long lastAccess = 0;
		long expireTime = 0;
		long createTime = 0;
		long fileSize = 0;
		Long lastModified = null;
		
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = StringUtils.split(line, "=", 2);
				if (parts != null && parts.length == 2) {
					if ("url".equals(parts[0])) {
						url = parts[1];
					} else if ("urlMd5".equals(parts[0])) {
						urlMd5 = parts[1];
					} else if ("fileSize".equals(parts[0])) {
						fileSize = Long.parseLong(parts[1]);
					} else if ("lastAccess".equals(parts[0])) {
						lastAccess = Long.parseLong(parts[1]);
					} else if ("expireTime".equals(parts[0])) {
						expireTime = Long.parseLong(parts[1]);
					} else if ("createTime".equals(parts[0])) {
						createTime = Long.parseLong(parts[1]);
					} else if ("lastModifiedTime".equals(parts[0])) {
						lastModified = Long.parseLong(parts[1]);
					}
				}
			}
			
			if (url != null && urlMd5 != null) {
				return new CacheFile(url, urlMd5, fileSize, lastAccess, lastModified, expireTime, createTime);
			} else {
				Log.w(Constant.TAG, "invalid meta file: " + metaFile.getAbsolutePath() + " delete!");
				metaFile.delete();
			}
		} finally {
			reader.close();
		}
		
		return null;
	}
}
