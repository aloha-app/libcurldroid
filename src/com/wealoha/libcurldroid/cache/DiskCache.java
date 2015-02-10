package com.wealoha.libcurldroid.cache;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;

import com.wealoha.libcurldroid.Constant;
import com.wealoha.libcurldroid.util.Logger;
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

	// key: urlMd5, order by lastAccessTime desc
	private final SortedMap<String, CacheFile> fileMap;
	private final Map<String, Long> lastAccessTimeMap;
	// for sync lastAccessTime to disk
	private final LinkedBlockingQueue<CacheFile> accessTimeUpdateQueue;
	private final File path;
	private final int maxCacheSizeInBytes;
	private final long evictIntervalMillis;
	private final long accessTimeSyncMillis;
	private final Timer accessTimeUpdateTimer;
	
	private long lastWrite;
	private long lastEvict;
	
	
	private static final Logger logger = Logger.getLogger(DiskCache.class);
	
	public static class Builder {
		
		private File path;
		private int maxCacheSizeInBytes;
		private long evictIntervalMillis = 120 * 1000;
		private long accessTimeSyncMillis = 120 * 1000;

		public static Builder newInstance() {
			return new Builder();
		}
		
		/**
		 * Set cache path
		 * 
		 * @param path
		 * @return
		 */
		public Builder cachePath(File path) {
			this.path = path;
			return this;
		}
		
		/**
		 * set max cache size in bytes
		 * 
		 * @param size
		 * @return
		 */
		public Builder maxCacheSizeInBytes(int size) {
			this.maxCacheSizeInBytes = size;
			return this;
		}
		
		/**
		 * Cache evict interval millis.
		 * 
		 * If too large, cache size may exceed limitations. 
		 * If too small, will consume more IO resources.
		 * 
		 * @param millis default 120s
		 * @return
		 */
		public Builder evictIntervalMillis(long millis) {
			this.evictIntervalMillis = millis;
			return this;
		}
		
		/**
		 * Last access time flush to disk interval millis.
		 * 
		 * @param millis default 120s
		 * @return
		 */
		public Builder accessTimeSyncMillis(long millis) {
			this.accessTimeSyncMillis = millis;
			return this;
		}
		
		public DiskCache build() {
			if (path == null) {
				throw new IllegalStateException("cachePath not set");
			}
			return new DiskCache(path, maxCacheSizeInBytes, accessTimeSyncMillis, evictIntervalMillis);
		}
	}
	
	/**
	 * 
	 * @param path
	 * @param accces
	 */
	public DiskCache(File path, int maxCacheSizeInBytes, long accessTimeSyncMillis, long evictIntervalMillis) {
		this.path = path;
		this.maxCacheSizeInBytes = maxCacheSizeInBytes;
		this.accessTimeSyncMillis = accessTimeSyncMillis;
		this.evictIntervalMillis = evictIntervalMillis;
		
		
		// meta memory map
		lastAccessTimeMap = new ConcurrentHashMap<String, Long>();
		fileMap = Collections.synchronizedSortedMap(new TreeMap<String, CacheFile>(new Comparator<String>() {
			@Override
			public int compare(String a, String b) {
				Long t1 = lastAccessTimeMap.get(a);
				Long t2 = lastAccessTimeMap.get(b);
				return compare(t2 == null ? 0 : t2, t1 == null ? 0 : t1);
			}
			
			private int compare(long x, long y) {
				return (x < y) ? -1 : 1;
			}
		}));
		accessTimeUpdateQueue = new LinkedBlockingQueue<CacheFile>();
		accessTimeUpdateTimer = new Timer();
		accessTimeUpdateTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				if (accessTimeUpdateQueue.isEmpty()) {
					return;
				}
				
				List<CacheFile> fails = new ArrayList<CacheFile>();
				int c = 0;
				long t = System.currentTimeMillis();
				while (!accessTimeUpdateQueue.isEmpty()) {
					try {					
						CacheFile cacheFile = accessTimeUpdateQueue.take();
						try {
							writeMeta(cacheFile);
							c++;
						} catch (IOException e) {
							logger.w("flush meta file to disk fail %s", cacheFile.getUrlMd5());
							fails.add(cacheFile);
						}
					} catch (InterruptedException e) {
					}
				}
				
				logger.i("flush meta task done: count=%d, time=%d", c, (System.currentTimeMillis() - t));
				
				if (fails.size() > 0) {
					// re send to queue
					logger.i("flush meta fail, try reflush next time: %d", fails.size());
					accessTimeUpdateQueue.addAll(fails);
				}
			}
		}, accessTimeSyncMillis, accessTimeSyncMillis);
	}
	

	/**
	 * Get
	 * 
	 * @param url
	 * @return null if miss
	 */
	@Override
	public CacheFile get(String url) throws IOException {
		String urlMd5 = StringUtils.md5(url);
		
		CacheFile item = getCacheFile(urlMd5);
		
		if (item == null) {
			return null;
		}
		
		while (!url.equals(item.getUrl())) {
			// md5 conflict, different url with same md5
			logger.w("md5 conflict url=%s cacheUrl=%s, md5=%s/%s", 
					url, item.getUrl(), urlMd5, item.getUrlMd5());
		
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
		boolean purge = false;
		if (fileFile.exists()) {
			if (fileFile.length() == file.getFileSize()) {
				logger.v("read file as stream: %s", fileFile.getAbsolutePath());
				return new FileInputStream(fileFile);
			} else {
				purge = true;
			}
		} else {
			purge = true;
		}
		if (purge) {
			// file not exist
			logger.w("corrupted file: %s", filePath);
			// delete meta file
			File metaFile = new File(getMetaFilePath(file.getUrlMd5()));
			if (metaFile.exists()) {
				metaFile.delete();
			}
			// delete from memory cache
			fileMap.remove(file.getUrlMd5());
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
				// same algorithm with get
				urlMd5 = StringUtils.md5(urlMd5 + url);
				cacheFile = getCacheFile(urlMd5);
				if (cacheFile == null) {
					// okay we get the correct md5!
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
			}
			
			File targetDir = new File(getFileDir(urlMd5));
			if (!targetDir.exists()) {
				targetDir.mkdirs();
			}
			File dataFile = new File(getFilePath(urlMd5));
			
			// save data
			FileOutputStream dataOs = new FileOutputStream(dataFile);
			try {
				dataOs.write(data);
				dataOs.flush();
			} finally {
				dataOs.close();
			}
			
			// savemeta
			writeMeta(cacheFile);
		
			// save to memory map
			fileMap.put(urlMd5, cacheFile);
		}
	}
	
	
	
	private void writeMeta(CacheFile cacheFile) throws IOException {
		File targetDir = new File(getFileDir(cacheFile.getUrlMd5()));
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		
		synchronized (cacheFile.getUrlMd5().intern()) {
			File metaFile = new File(getMetaFilePath(cacheFile.getUrlMd5()));
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
	
	private CacheFile getCacheFile(String urlMd5) throws IOException {
		// -read from cache
		CacheFile cacheFile = fileMap.get(urlMd5);
		if (cacheFile == null) {
			// -fail-back to disk
			logger.d("trying load meta from disk: %s", urlMd5);
			String metaFilePath = getMetaFilePath(urlMd5);
			File metaFile = new File(metaFilePath);

			cacheFile = decodeMeta(metaFile);
			
			if (cacheFile != null) {
				logger.d("disk hit: %s", urlMd5);
				fileMap.put(urlMd5, cacheFile);
				lastAccessTimeMap.put(urlMd5, cacheFile.getLastAccessMillis());
			}
		}
		
		if (cacheFile != null) {
			updateLastAccess(cacheFile);			
		}
		return cacheFile;
	}
	
	public void updateLastAccess(CacheFile cacheFile) {
		logger.d("add last access time task to queue: " + cacheFile.getUrl());
		accessTimeUpdateQueue.remove(cacheFile);
		long now = System.currentTimeMillis();
		cacheFile.setLastAccessMillis(now);
		lastAccessTimeMap.put(cacheFile.getUrlMd5(), now);
		accessTimeUpdateQueue.add(cacheFile);
	}
	
	/**
	 * meta and data' perent path
	 * 
	 * @param urlMd5
	 * @return
	 */
	private String getFileDir(String urlMd5) {
		return String.format("%s/%s/%s/",
				path.getAbsolutePath(), 
				urlMd5.substring(0, 1),
				urlMd5.substring(1, 2));
	}
	
	/**
	 * data file full path
	 * 
	 * @param urlMd5
	 * @return
	 */
	private String getFilePath(String urlMd5) {
		return String.format("%s%s",
				getFileDir(urlMd5),
				urlMd5);
	}
	
	/**
	 * meta file full path
	 * 
	 * @param urlMd5
	 * @return
	 */
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
		if (!metaFile.exists()) {
			return null;
		}
		
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
