package com.wealoha.libcurldroid;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.util.Log;

import com.wealoha.libcurldroid.Curl.ReadCallback;
import com.wealoha.libcurldroid.Curl.WriteCallback;
import com.wealoha.libcurldroid.CurlOpt.OptFunctionPoint;
import com.wealoha.libcurldroid.CurlOpt.OptLong;
import com.wealoha.libcurldroid.CurlOpt.OptObjectPoint;
import com.wealoha.libcurldroid.util.CaseInsensitiveMap;
import com.wealoha.libcurldroid.util.StringUtils;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-01-30 8:30:17
 */
public class CurlEasy {

	private static final String TAG = CurlEasy.class.getSimpleName();
	
	private Curl curl;
	private Map<String, String> headerMap;
	private Map<String, String> postFieldMap;
	private Boolean get;
	
	static {
		Curl curl = new Curl();
		Log.i(TAG, "do curlGlobalInit");
		curl.curlGlobalInit(CurlConstant.CURL_GLOBAL_NOTHING);
	}
	
	private CurlEasy() {
		curl = new Curl();
	}
	
	@Override
	protected void finalize() throws Throwable {
		curl.curlEasyCleanup();
		super.finalize();
	}
	
	public static CurlEasy newInstance() throws CurlException {
		CurlEasy curlEasy = new CurlEasy();
		curlEasy.curl.curlEasyInit();
		curlEasy.headerMap = new HashMap<String, String>();
		curlEasy.headerMap.put("User-Agent", "libcurldroid/0.1.0 libcurl/7.40.0 libcares/1.10.0"); // TODO get curl and cares version from jni
		return curlEasy;
	}
	
	/**
	 * 
	 * @param name
	 * @param value pass a null value clear previous set(or curl default) header
	 * @return
	 */
	public CurlEasy addHeader(String name, String value) {
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
	
	/**
	 * 
	 * @param proxy [scheme]://
	 * @return
	 * @see http://curl.haxx.se/libcurl/c/CURLOPT_PROXY.html
	 */
	public CurlEasy setProxy(String proxy) {
		curl.curlEasySetopt(OptObjectPoint.CURLOPT_PROXY, proxy);
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
	
	private void setHeaderCallback(final Map<String, String> resultMap) {
		curl.curlEasySetopt(OptFunctionPoint.CURLOPT_HEADERFUNCTION, new WriteCallback() {
			
			@Override
			public int readData(byte[] data) {
				String header = new String(data);
				String[] nameAndValue = StringUtils.split(header, ":", 2);
				if (nameAndValue.length == 2) {
					resultMap.put(nameAndValue[0].trim(), nameAndValue[1].trim());
				}
				return data.length;
			}
		});
	}
	
	private void setBodyCallback(final OutputStream os) {
		curl.curlEasySetopt(OptFunctionPoint.CURLOPT_WRITEFUNCTION, new WriteCallback() {
			
			@Override
			public int readData(byte[] data) {
				if (data != null && data.length == 0) {
					return 0;
				}
				try {
					os.write(data);
				} catch (IOException e) {
					Log.w(TAG, "write fail", e);
					return 0;
				}
				return data.length;
			}
		});
	}
	
	public Result perform() throws CurlException {
		// TODO
		// - populate headers
		List<String> headers = new ArrayList<String>(headerMap.size());
		for (Entry<String, String> entry : headerMap.entrySet()) {
			String value = entry.getValue();
			if (value == null) {
				value = "";
			}
			headers.add(entry.getKey() + ": " + value);
		}
		Log.d(TAG, "add hreader: " + headers.size());
		curl.curlEasySetopt(OptObjectPoint.CURLOPT_HTTPHEADER, headers.toArray(new String[headerMap.size()]));
		
		// - do request
		@SuppressWarnings("unchecked")
		Map<String, String> resultHeaderMap = new CaseInsensitiveMap<String, String>();
		setHeaderCallback(resultHeaderMap);
		ByteArrayOutputStream bodyOs = new ByteArrayOutputStream();
		setBodyCallback(bodyOs);
		
		try {
			CurlCode code = curl.curlEasyPerform();
			if (code != CurlCode.CURLE_OK) {
				throw new CurlException(code);
			}
			
			for (Entry<String, String> entry : resultHeaderMap.entrySet()) {
				Log.d(TAG, "Header: " + entry.getKey() + ": " + entry.getValue()) ;
			}
			
			// - post data (if needed)
			// - read response
		
			return new Result(200, resultHeaderMap, bodyOs.toByteArray());
		} finally {
			curl.curlEasyCleanup();
		}
	}
}
