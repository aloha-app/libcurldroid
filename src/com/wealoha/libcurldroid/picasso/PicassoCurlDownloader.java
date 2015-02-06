package com.wealoha.libcurldroid.picasso;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import android.net.Uri;
import android.util.Log;

import com.squareup.picasso.Downloader;
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
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-04 22:31:12
 */
public class PicassoCurlDownloader implements Downloader {

	private static final Logger logger = Logger.getLogger(PicassoCurlDownloader.class);
	
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
	public Response load(Uri path, boolean localCacheOnly) throws IOException {
		String url = path.toString();
		logger.v("trying to load resources: url=%s, localCacheOnly=%s", url, localCacheOnly);

		if (cache != null) {
			logger.d("load from cache, url=%s", url);

			CacheFile cacheFile = cache.get(url);
			if (cacheFile == null) {
				logger.d("cache miss: url=%s", url);
			} else {
				// TODO check expire date
				Log.v(Constant.TAG, "get url from cache: " + url);
				if (cacheFile.getExpireTimeMillis() < System.currentTimeMillis()) {
					// file expired
					logger.d("seems file expired test lastModified: url=%s", url);
					if (cacheFile.getLastModifiedMillis() != null) {
						// check 304, if unmodified
						// If-Modified-Since
						Log.v(Constant.TAG, "see if file modified: " + url);
						String lastModified = formatHttpDate(new Date(cacheFile.getLastAccessMillis()));
						
						Result result = getUrlManualDealRedirect(url, lastModified);
						
						if (result.getStatus() == 304) {
							Log.v(Constant.TAG, "file not modified, return cached: " + url);
						} else if (result.getStatus() == 200) {
							Log.v(Constant.TAG, "file modified, replace cached: " + url);
							byte[] body = result.getDecodedBody();
							cacheResult(url, result);
							return new Response(new ByteArrayInputStream(body), false, body.length);
						}
					}
				}
				
				Log.d(Constant.TAG, "return cached url: " + url);
				InputStream is = cache.getInputStream(cacheFile);
				return new Response(is, true, cacheFile.getFileSize());
			}
		} // end if cache test
		
		logger.d("cache miss: url=%s", url);
		if (localCacheOnly) {
			return null;
		}
		
		// TODO init curl
		logger.d("get url: %s", url);
		Result result = getUrlManualDealRedirect(url, null);
		
		Log.v(Constant.TAG, "result: " + result.getStatusLine());
		if (result.getStatus() == 200) {
			byte[] body = result.getDecodedBody();
			cacheResult(url, result);
			return new Response(new ByteArrayInputStream(body), false, body.length);
		} else {
			throw new IOException("load url fail: " + url + " " + result.getStatusLine());
		}
	}
	
	private Result getUrlManualDealRedirect(String url, String headerIfModifideSince) throws IOException {
		boolean tryNext = false;
		
		Result result;
		do {
			CurlHttp curlHttp = CurlHttp.newInstance();
			// set curl
			if (callback != null) {
				callback.afterInit(curlHttp);
			}
			
			curlHttp.setFollowLocation(false);
			if (headerIfModifideSince != null) {
				curlHttp.addHeader("If-Modified-Since", headerIfModifideSince);
			}
			
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
	
	private void cacheResult(String url, Result result) throws IOException {
		logger.d("cache url: %s", url);
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
		if (cache != null && expireDate != null && expireDate.after(now)) {
			Log.v(Constant.TAG, "save date to cache: " + url);
			cache.set(url, body, lastModified, expireDate);
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
