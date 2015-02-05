package com.wealoha.libcurldroid.third;

import com.wealoha.libcurldroid.CurlHttp;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-05 16:48:31
 */
public interface CurlHttpCustomizeCallback {

	/**
	 * for set curl options
	 * 
	 * timeout, headers, redirects, proxies ...
	 * 
	 * @param curlHttp
	 */
	public void setCurlOptions(CurlHttp curlHttp);
}
