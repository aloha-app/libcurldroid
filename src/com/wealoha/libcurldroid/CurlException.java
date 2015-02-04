package com.wealoha.libcurldroid;
/**
 * 
 * 
 * @author javamonk
 * @createTime 2015年1月30日 下午3:20:19
 */
public class CurlException extends RuntimeException {

	private static final long serialVersionUID = -5532332305546682790L;

	private final CurlCode curlCode;

	public CurlException() {
		super();
		this.curlCode = null;
	}
	
	public CurlException(String detailMessage) {
		super(detailMessage);
		curlCode = null;
	}

	public CurlException(CurlCode curlCode) {
		super("curlCode: " + curlCode);
		this.curlCode = curlCode;
	}
	
	/**
	 * 
	 * @return may be null
	 */
	public CurlCode getCurlCode() {
		return curlCode;
	}
	
	
}
