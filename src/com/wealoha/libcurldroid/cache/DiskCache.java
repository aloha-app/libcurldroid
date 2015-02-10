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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.SortedMap;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.wealoha.libcurldroid.util.Logger;
import com.wealoha.libcurldroid.util.StringUtils;

/**
 * <h1>Disk based LRU cache</h1>
 * 
 * Two layer:
 * 
 * <ol>
 *   <li>memory(Map)</li>
 *   <li>disk(file)</li>
 * </ol>
 * 
 * A file cached with key, data and meta on disk.<br/>
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
	private final Timer accessTimeUpdateTimer;
	private final Timer evictTimer;
	
	private long lastWrite;
	private long lastEvict;
	
	
	private static final Logger logger = Logger.getLogger(DiskCache.class);
	
	public static class Builder {
		
		private File path;
		private int maxCacheSizeInBytes;
		private long evictIntervalMillis = 120 * 1000;
		private long accessTimeSyncMillis = 120 * 1000;
		private float evictFactor = 0.75f;

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
		 * @param size 0 means no limit, default 0
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
		 * Cache evict if usedBytes > {@link #maxCacheSizeInBytes(int)} * factor
		 * 
		 * @param factor
		 * @return
		 */
		public Builder eviceFactor(float factor) {
			this.evictFactor = factor;
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
			return new DiskCache(path, maxCacheSizeInBytes, accessTimeSyncMillis, evictIntervalMillis, evictFactor);
		}
	}
	
	/**
	 * 
	 * @param path
	 * @param accces
	 */
	public DiskCache(File path, int maxCacheSizeInBytes, long accessTimeSyncMillis, long evictIntervalMillis, float evictFactor) {
		this.path = path;
		this.maxCacheSizeInBytes = maxCacheSizeInBytes;
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
		
		// last access time sync timer 
		accessTimeUpdateQueue = new LinkedBlockingQueue<CacheFile>();
		accessTimeUpdateTimer = new Timer();
		initAccessTimeSyncTimer(accessTimeSyncMillis);
		
		// evict
		if (maxCacheSizeInBytes > 0) {
			evictTimer = new Timer();
			initEvictTimer(evictIntervalMillis, evictFactor);
		} else {
			evictTimer = null;
		}
	}

	private void initEvictTimer(long evictIntervalMillis, final float evictFactor) {
		logger.i("init evict task timer");
		evictTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				try {
					if (lastEvict > lastWrite) {
						logger.d("no write since last evict, skip");
						return;
					}
					
					long t = System.currentTimeMillis();
					// key, lastAccessTime
					final Map<String, Long> accessTimeMap = new HashMap<String, Long>();
					// all files, order by lastAccessTime asc
					// key, filesize (meta and data)
					TreeMap<String, Long> fileMap = new TreeMap<String, Long>(new Comparator<String>() {
						@Override
						public int compare(String a, String b) {
							Long t1 = accessTimeMap.get(a);
							Long t2 = accessTimeMap.get(b);
							return compare(t1 == null ? 0 : t1, t2 == null ? 0 : t2);
						}
						
						private int compare(long x, long y) {
							return (x < y) ? -1 : 1;
						}
					});
					
					AtomicLong allBytes = new AtomicLong();
					getTotalBytes(path, allBytes, accessTimeMap, fileMap);
					long currentBytes = allBytes.get();
					logger.v("calculate total bytes, time: %d, size=%d", (System.currentTimeMillis() - t), currentBytes);
					
					
					long expectSize = (long) (maxCacheSizeInBytes * evictFactor);
					if (currentBytes < expectSize) {
						logger.d("cache size safe, just return: current=%d, limit=%d", currentBytes, maxCacheSizeInBytes);
					}
					
					long finalBytes = currentBytes;
					int keys = 0;
					for (Entry<String, Long> keyAndSize : fileMap.entrySet()) {
						if (finalBytes < expectSize) {
							break;
						}
						logger.v("evict item: key=%s, size=%d, lastAccess=%d", keyAndSize.getKey(), keyAndSize.getValue(), accessTimeMap.get(keyAndSize.getKey()));
						
						// delete
						try {
							remove(keyAndSize.getKey());
							finalBytes -= keyAndSize.getValue();
							keys++;
						} catch (IOException e) {
							logger.w("evict key fail: %s", keyAndSize.getKey(), e);
						}
					}
					
					logger.i("evict done: expect=%d, before=%d, after=%d, time=%d, keys=%d",
							expectSize, currentBytes, finalBytes, (System.currentTimeMillis() - t), keys);
					
					// updat elast evict timestamp
					lastEvict = System.currentTimeMillis();
				} catch (Throwable t) {
					logger.w("evict running fail", t);
				}
			}
		}, evictIntervalMillis, evictIntervalMillis);
	}
	
	private final static Pattern PATTERN_FILE = Pattern.compile("(\\w+)(?:\\.meta){0,1}$");
	
	private void getTotalBytes(File path, AtomicLong currentTotal, Map<String, Long> accessTimeMap, SortedMap<String, Long> fileSet) {
		if (path.isDirectory()) {
			File[] files = path.listFiles();
			for (File file : files) {
				if (file.isFile()) {
					String fileName = file.getName();
					Matcher m = PATTERN_FILE.matcher(fileName);
					if (m.find()) {
						String key = m.group(1);
						if (fileName.endsWith(".meta")) {
							// meta file
							Long lastAccess = lastAccessTimeMap.get(key);
							if (lastAccess == null) {
								// last access sync will rewrite meta file, assume lastModified time is lastAccess
								logger.v("last access time not found, use last mofied: %s", key);
								lastAccess = file.lastModified();
							}
							accessTimeMap.put(key, lastAccess);
						} else {
							// data file
							Long lastAccess = lastAccessTimeMap.get(key);
							if (lastAccess != null) {
								accessTimeMap.put(key, lastAccess);
							} else if (accessTimeMap.get(key) == null) {
								// need access time to sort
								try {
									CacheFile meta = decodeMeta(new File(file.getAbsolutePath() + ".meta"));
									if (meta != null) {
										accessTimeMap.put(key, meta.getLastAccessMillis());
									}
								} catch (IOException e) {
									logger.w("read meta fail: " + fileName, e);
								}
							}
						}
						
						Long currentSize = fileSet.get(key);
						if (currentSize == null) {
							currentSize = 0l;
						}
						currentSize += file.length();
						fileSet.put(key, currentSize);
						// logger.d("file: %s, %d", file.getName(), file.length());
						currentTotal.addAndGet(file.length());
					}
				} else {
					getTotalBytes(file, currentTotal, accessTimeMap, fileSet);
				}
			}
		}
	}

	private void initAccessTimeSyncTimer(long accessTimeSyncMillis) {
		logger.i("init last access time sync task timer");
		accessTimeUpdateTimer.scheduleAtFixedRate(new TimerTask() {
			
			@Override
			public void run() {
				try {
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
								logger.w("flush meta file to disk fail %s", cacheFile.getKey(), e);
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
				} catch (Throwable e) {
					logger.w("flush meta fail", e);
				}
			}
		}, accessTimeSyncMillis, accessTimeSyncMillis);
	}
	

	/**
	 * Get
	 * 
	 * @param key
	 * @return null if miss
	 */
	@Override
	public CacheFile get(String key) throws IOException {
		CacheFile item = getCacheFile(key);
		
		if (item == null) {
			return null;
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
		if (file == null) {
			return null;
		}
		
		String filePath = getFilePath(file.getKey());
		File fileFile = new File(filePath);
		boolean purge = false;
		if (fileFile.exists()) {
			if (fileFile.length() == file.getFileSize()) {
				logger.d("read file as stream: %s %s", file.getKey(), fileFile.getAbsolutePath());
				return new FileInputStream(fileFile);
			} else {
				// file size
				purge = true;
			}
		} else {
			purge = true;
		}
		if (purge) {
			// file not exist
			logger.w("destory corrupted file: %s, %s", file.getKey(), filePath);
			
			remove(file.getKey());
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
	 * @param metaMap
	 */
	@Override
	public void set(String key, byte[] data, java.util.Map<String, String> metaMap) throws IOException {
		synchronized (key.intern()) {
			CacheFile cacheFile = new CacheFile(key, data.length, System.currentTimeMillis(), System.currentTimeMillis(), metaMap);
			logger.d("cache file: %s", key);
			
			File targetDir = new File(getFileDir(key));
			if (!targetDir.exists()) {
				targetDir.mkdirs();
			}
			File dataFile = new File(getFilePath(key));
			
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
			fileMap.put(key, cacheFile);
			
			// update last write timestamp (using by evict)
			lastWrite = System.currentTimeMillis();
		}
	}
	
	
	
	private void writeMeta(CacheFile cacheFile) throws IOException {
		String key = cacheFile.getKey();
		File targetDir = new File(getFileDir(key));
		if (!targetDir.exists()) {
			targetDir.mkdirs();
		}
		
		synchronized (key.intern()) {
			File metaFile = new File(getMetaFilePath(key));
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
	public void remove(String key) throws IOException {
		synchronized (key.intern()) {
			logger.d("delete file: %s", key);
			
			File dataFile = new File(getFilePath(key));
			File metaFile = new File(getMetaFilePath(key));
			if (metaFile.exists()) {
				metaFile.delete();
			}
			if (dataFile.exists()) {
				dataFile.delete();
			}
			fileMap.remove(key);
			lastAccessTimeMap.remove(key);
		}
		
	}
	
	private CacheFile getCacheFile(String key) throws IOException {
		// -read from memory
		CacheFile cacheFile = fileMap.get(key);
		if (cacheFile != null) {
			logger.d("memory hit: %s", key);
		} else {
			// -fail-back to disk
			logger.d("trying load meta from disk: %s", key);
			String metaFilePath = getMetaFilePath(key);
			File metaFile = new File(metaFilePath);

			cacheFile = decodeMeta(metaFile);
			
			if (cacheFile != null) {
				logger.d("disk hit: %s", key);
				lastAccessTimeMap.put(key, System.currentTimeMillis());
				fileMap.put(key, cacheFile);
			}
		}
		
		if (cacheFile != null) {
			updateLastAccess(cacheFile);			
		}
		return cacheFile;
	}
	
	public void updateLastAccess(CacheFile cacheFile) {
		logger.v("enqueue lastAccess task: %s", cacheFile.getKey());
		accessTimeUpdateQueue.remove(cacheFile);
		long now = System.currentTimeMillis();
		cacheFile.setLastAccessMillis(now);
		lastAccessTimeMap.put(cacheFile.getKey(), now);
		accessTimeUpdateQueue.add(cacheFile);
	}
	
	/**
	 * meta and data' perent path
	 * 
	 * @param key
	 * @return
	 */
	private String getFileDir(String key) {
		return String.format("%s/%s/%s/",
				path.getAbsolutePath(), 
				key.substring(0, 1),
				key.substring(1, 2));
	}
	
	/**
	 * data file full path
	 * 
	 * @param key
	 * @return
	 */
	private String getFilePath(String key) {
		return String.format("%s%s",
				getFileDir(key),
				key);
	}
	
	/**
	 * meta file full path
	 * 
	 * @param key
	 * @return
	 */
	private String getMetaFilePath(String key) {
		return getFilePath(key) + ".meta";
	}
	
	private String encodeMeta(CacheFile file) {
		StringBuilder sb = new StringBuilder() //
			.append("key=" + file.getKey() + "\n") //
			.append("fileSize=" + file.getFileSize() + "\n") //
			.append("lastAccess=" + file.getLastAccessMillis() + "\n") //
			.append("createTime=" + file.getCreateTimeMillis() + "\n");
		Map<String, String> meta = file.getMeta();
		if (meta != null && meta.size() > 0) {
			for (Entry<String, String> e : meta.entrySet()) {
				sb.append("meta." + e.getKey() + "=" + e.getValue() + "\n");
			}
		}
		return sb.toString();
	}
	
	private CacheFile decodeMeta(File metaFile) throws IOException {
		if (!metaFile.exists()) {
			return null;
		}
		
		BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(metaFile)));
		
		String key = null;
		long lastAccess = 0;
		long createTime = 0;
		long fileSize = 0;
		Map<String, String> meta = new HashMap<String, String>();
		
		try {
			String line;
			while ((line = reader.readLine()) != null) {
				String[] parts = StringUtils.split(line, "=", 2);
				if (parts != null && parts.length == 2) {
					String theKey = parts[0];
					String theValue = parts[1];
					if ("key".equals(theKey)) {
						key = theValue;
					} else if ("fileSize".equals(theKey)) {
						fileSize = Long.parseLong(theValue);
					} else if ("lastAccess".equals(theKey)) {
						lastAccess = Long.parseLong(theValue);
					} else if ("createTime".equals(theKey)) {
						createTime = Long.parseLong(theValue);
					} else if (theKey.startsWith("meta.")) {
						String[] split = StringUtils.split(theKey, ".", 2);
						if (split != null && split.length == 2) {
							meta.put(split[1], theValue);
						}
					}
				}
			}
			
			if (key != null) {
				return new CacheFile(key, fileSize, lastAccess, createTime, meta);
			} else {
				logger.w("invalid meta file: %s, delete!", metaFile.getAbsolutePath());
				metaFile.delete();
			}
		} finally {
			reader.close();
		}
		
		return null;
	}
}
