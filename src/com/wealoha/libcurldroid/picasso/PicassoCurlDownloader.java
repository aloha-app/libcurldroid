package com.wealoha.libcurldroid.picasso;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Downloader;
import com.squareup.picasso.NetworkPolicy;
import com.wealoha.libcurldroid.Constant;
import com.wealoha.libcurldroid.CurlHttp;
import com.wealoha.libcurldroid.Result;
import com.wealoha.libcurldroid.cache.Cache;
import com.wealoha.libcurldroid.cache.CacheFile;
import com.wealoha.libcurldroid.cache.DiskCache;
import com.wealoha.libcurldroid.third.CurlHttpCallback;
import com.wealoha.libcurldroid.util.Logger;
import com.wealoha.libcurldroid.util.StringUtils;

/**
 * This downloader handle 301 or 302 redirect manually, 
 * pass 'If-Modified-Since' through all redirect, that avoid 
 * 'java.io.IOException: Received response with 0 content-length header'
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-04 22:31:12
 */
public class PicassoCurlDownloader implements Downloader {

	private static final Logger logger = Logger.getLogger(PicassoCurlDownloader.class);

	private static final String META_LAST_MODIFIED_MILLIS = "lastModified";

	private static final String META_EXPIRE_MILLIS = "expire";

	private static final String META_URL = "url";
	
	private Cache cache;
	
	private CurlHttpCallback callback;
	
	
	/**
	 * Default downloader, if you need cache call setCache
	 * @param for custom curl params
	 */
	public PicassoCurlDownloader() {
	}
	
	/**
	 * set curl init callback
	 * 
	 * @param callback
	 * @return
	 */
	public PicassoCurlDownloader curlCalback(CurlHttpCallback callback) {
		this.callback = callback;
		return this;
	}
	
	/**
	 * set cache, you can use {@link DiskCache}
	 * 
	 * @param cache
	 * @return
	 */
	public PicassoCurlDownloader cache(Cache cache) {
		this.cache = cache;
		return this;
	}
	
	@Override
	public Response load(Uri uri, int networkPolicy) throws IOException {
		String url = uri.toString();
		boolean localCacheOnly = NetworkPolicy.isOfflineOnly(networkPolicy);
		logger.v("trying to load resources: url=%s, networkPolicy=%s", url, networkPolicy);

		if (cache != null) {
			logger.d("trying load from cache, url=%s", url);

			CacheFile cacheFile = getCacheUrl(url);
			if (cacheFile == null) {
				logger.d("cache miss: url=%s", url);
			} else {
				logger.d("cache hit: url=%s ()", url, cacheFile.getFileSize());
				
				Long expireMillis = parseMillis(cacheFile.getMeta(), META_EXPIRE_MILLIS);
				
				if (expireMillis != null && expireMillis < System.currentTimeMillis()) {
					// file expired
					// check 304, if unmodified
					logger.d("seems file expired, test lastModified: url=%s", url);
					Date checkTime = null;
					Long lastModifiedMillis = parseMillis(cacheFile.getMeta(), META_LAST_MODIFIED_MILLIS);
					if (lastModifiedMillis != null) {
						checkTime = new Date(lastModifiedMillis);
					} else {
						checkTime = new Date(cacheFile.getCreateTimeMillis());
					}
					String lastModified = formatHttpDate(checkTime);
					
					Result result = getUrlManualDealRedirect(url, lastModified);
					
					if (result.getStatus() == 304) {
						logger.d("file not modified, return cached: %s", url);
						// FIXME update expire time
					} else if (result.getStatus() == 200) {
						logger.d("file modified, replace cached: %s ", url);
						byte[] body = result.getDecodedBody();
						cacheResult(url, result);
						return new Response(new ByteArrayInputStream(body), false, body.length);
					}
				}
				
				InputStream is = cache.getInputStream(cacheFile);
				return new Response(is, true, cacheFile.getFileSize());
			}
		} // end if cache test
		
		if (localCacheOnly) {
			return null;
		}

		logger.v("download url: %s", url);
		Result result = getUrlManualDealRedirect(url, null);
		
		if (result.getStatus() == 200) {
			byte[] body = result.getDecodedBody();
			cacheResult(url, result);
			return new Response(new ByteArrayInputStream(body), false, body.length);
		} else {
			throw new IOException("load url fail: " + url + " " + result.getStatusLine());
		}
	}
	
	private Long parseMillis(Map<String, String> metaMap, String key) {
		String str = metaMap.get(key);
		if (StringUtils.isBlank(str)) {
			return null;
		}
		return Long.valueOf(str);
	}
	
	private Result getUrlManualDealRedirect(String url, String headerIfModifideSince) throws IOException {
		boolean tryNext = false;
		
		Result result;
		do {
			CurlHttp curlHttp = CurlHttp.newInstance();
			// set curl
			if (callback != null) {
				callback.afterInit(curlHttp, url);
			}
			
			curlHttp.setFollowLocation(false);
			if (headerIfModifideSince != null) {
				curlHttp.addHeader("If-Modified-Since", headerIfModifideSince);
			}
			
			logger.v("trying download data from url: %s", url);
			result = curlHttp.getUrl(url).perform();
			
			if (result.getStatus() == 301 || result.getStatus() == 302) {
				String nextUrl = result.getHeader("Location");
				logger.v("redirect for url: %s -> %s", url, nextUrl);
				if (url.equals(nextUrl)) {
					throw new IOException("redirect loop: url=" + url + ", Location=" + nextUrl);
				}
				if (StringUtils.isBlank(nextUrl)) {
					throw new IOException("redirect response without Location header, url=" + nextUrl);
				}
				url = nextUrl;
				tryNext = true;
			} else {
				tryNext = false;
			}
		} while (tryNext == true);
		
		return result;
	}
	
	private CacheFile getCacheUrl(String url) throws IOException {
		if (cache == null) {
			return null;
		}
		
		String key = StringUtils.md5(url);
		CacheFile cacheFile = cache.get(key);
		
		if (cacheFile != null) {
			String existUrl = cacheFile.getMeta().get(META_URL);
			while (!url.equals(existUrl)) {
				logger.w("key conflict in read: %s exist: %s", url, existUrl);
				
				// same as cacheResult
				key = StringUtils.md5(url + key);
				cacheFile = cache.get(key);
				
				if (cacheFile == null) {
					break;
				}
				existUrl = cacheFile.getMeta().get(META_URL);
			}
		}
		
		return cacheFile;
	}
	
	private void cacheResult(String url, Result result) throws IOException {
		if (cache == null) {
			return;
		}
		
		String key = StringUtils.md5(url);
		
		CacheFile existItem = cache.get(key);
		if (existItem != null) {
			String existUrl = existItem.getMeta().get(META_URL);
			while (!url.equals(existUrl)) {
				logger.w("key conflict for write: %s exist: %s", url, existUrl);
				// same as getCacheUrl
				key = StringUtils.md5(url + key);
				existItem = cache.get(key);
				
				if (existItem == null) {
					break;
				}
				existUrl = existItem.getMeta().get(META_URL);
			}
		}
		
		logger.d("cache url data: %s (%s)", url, key);
		byte[] body = result.getBody();
		// "If a response includes both an Expires header and a max-age directive, the max-age directive overrides the Expires header, even if the Expires header is more restrictive."
		Date expireDate = parseHttpDate(result.getHeader("Expires"));
		Long maxAgeSeconds = parseMaxAge(result.getHeader("Cache-Control"));
		Date date = parseHttpDate(result.getHeader("Date")); // server time
		Date lastModified = parseHttpDate(result.getHeader("Last-Modified")); //
		
		Date now = new Date();
		if (date == null) {
			date = now;
		}
		if (maxAgeSeconds != null) {
			expireDate = new Date(now.getTime() + maxAgeSeconds * 1000);
		}
		
		logger.v("expire date: url=%s, date=%s", url, expireDate);
		if (expireDate != null && expireDate.after(now)) {
			logger.v("save data to cache: %s (%s)", url, key);
			Map<String, String> metaMap = new HashMap<String, String>();
			if (lastModified != null) {
				metaMap.put(META_LAST_MODIFIED_MILLIS, lastModified.getTime() + "");
			}
			metaMap.put(META_URL, url);
			metaMap.put(META_EXPIRE_MILLIS, expireDate.getTime() + "");
			cache.set(key, body, metaMap);
		}
	}
	
	
	
	private static final Pattern PATTERN_MAX_AGE = Pattern.compile("max-age=(\\d+)", Pattern.CASE_INSENSITIVE);
	
	/**
	 * 
	 * @param cacheControl
	 * @return seconds
	 */
	private Long parseMaxAge(String cacheControl) {
		// Cache-Control:max-age=31536000, public
		if (StringUtils.isBlank(cacheControl)) {
			return null;
		}
		
		Matcher m = PATTERN_MAX_AGE.matcher(cacheControl);
		if (m.find()) {
			return Long.valueOf(m.group(1));
		}
		
		return null;

	}
	
	private Date parseHttpDate(String value) {
		// Last-Modified:Wed, 04 Feb 2015 17:06:09 GMT
		// Expires:Thu, 04 Feb 2016 17:06:45 GMT
		if (StringUtils.isBlank(value)) {
			return null;
		}
		try {
			SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
			return df.parse(value.trim());
		} catch (ParseException e) {
			Log.w(Constant.TAG, "parse time fail: " + value);
		}
		return null;
	}
	
	private String formatHttpDate(Date date) {
		SimpleDateFormat df = new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.ENGLISH);
		df.setTimeZone(TimeZone.getTimeZone("UTC"));
		return df.format(date);
	}

	@Override
	public void shutdown() {
	}

}
