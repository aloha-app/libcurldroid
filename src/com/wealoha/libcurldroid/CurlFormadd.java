// Auto generated from 'curl/curl.h', DO NOT EDIT!!!
package com.wealoha.libcurldroid;
import android.util.SparseArray;

public enum CurlFormadd {    
    CURL_FORMADD_OK(0), //
    CURL_FORMADD_MEMORY(1), //
    CURL_FORMADD_OPTION_TWICE(2), //
    CURL_FORMADD_NULL(3), //
    CURL_FORMADD_UNKNOWN_OPTION(4), //
    CURL_FORMADD_INCOMPLETE(5), //
    CURL_FORMADD_ILLEGAL_ARRAY(6), //
    CURL_FORMADD_DISABLED(7), //
    ;

	
	private final int value;

	private static SparseArray<CurlFormadd> valuesMap = new SparseArray<CurlFormadd>();

	static {
		for (CurlFormadd e : values()) {
			valuesMap.put(e.getValue(), e);
		}
	}

	private CurlFormadd(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
	
	public static CurlFormadd fromValue(int value) {
		return valuesMap.get(value);
	}
}