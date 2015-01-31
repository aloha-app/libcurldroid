package com.wealoha.libcurldroid;

import java.util.HashMap;
import java.util.Map;

import com.wealoha.libcurldroid.CurlOpt.OptLong;
import com.wealoha.libcurldroid.CurlOpt.OptObjectPoint;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-01-30 8:30:17
 */
public class CurlEasy {

	private Curl curl;
	private Map<String, String> headerMap;
	private Map<String, String> postFieldMap;
	private Boolean get;
	
	private CurlEasy() {
		curl = new Curl();
	}
	
	public static CurlEasy newInstance() {
		return new CurlEasy();
	}
	
	public CurlEasy addHeader(String name, String value) {
		if (headerMap == null) {
			headerMap = new HashMap<String, String>() ;
		}
		headerMap.put(name, value);
		return this;
	}
	
	public CurlEasy setConnectionTimeoutMillis(long millis) {
		curl.curlEasySetopt(OptLong.CURLOPT_CONNECTTIMEOUT_MS, millis);
		return this;
	}
	
	public CurlEasy setTimeoutMillis(long millis) {
		curl.curlEasySetopt(OptLong.CURLOPT_TIMEOUT_MS, millis);
		return this;
	}
	
	public CurlEasy setIpResolveV4() {
		curl.curlEasySetopt(OptLong.CURLOPT_IPRESOLVE, CurlConstant.CURL_IPRESOLVE_V4);
		return this;
	}
	
	public CurlEasy setIpResolveV6() {
		curl.curlEasySetopt(OptLong.CURLOPT_IPRESOLVE, CurlConstant.CURL_IPRESOLVE_V6);
		return this;
	}
	
	public CurlEasy setIpResolveWhatever() {
		curl.curlEasySetopt(OptLong.CURLOPT_IPRESOLVE, CurlConstant.CURL_IPRESOLVE_WHATEVER);
		return this;
	}
	
	public CurlEasy getUrl(String url) {
		if (get != null && !get) {
			throw new IllegalArgumentException("A post url already set!");
		}
		get = true;
		
		curl.curlEasySetopt(OptLong.CURLOPT_HTTPGET, 1);
		curl.curlEasySetopt(OptObjectPoint.CURLOPT_URL, url);
		return this;
	}
	
	public CurlEasy postUrl(String url) {
		if (get != null && get) {
			throw new IllegalArgumentException("A get url already set!");
		}
		get = false;
		
		curl.curlEasySetopt(OptLong.CURLOPT_POST, 1);
		curl.curlEasySetopt(OptObjectPoint.CURLOPT_URL, url);
		return this;
	}
	
	public CurlEasy postParam(String key, String value) {
		if (postFieldMap == null) {
			postFieldMap = new HashMap<String, String>();
		}
		postFieldMap.put(key, value);
		return this;
	}
	
	public Result perform() {
		// TODO
		// - populate headers
		// - do request
		// - post data (if needed)
		// - read response
		
		return null;
	}
}
