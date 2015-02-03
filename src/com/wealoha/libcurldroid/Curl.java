package com.wealoha.libcurldroid;

import java.util.List;

import android.util.Log;

import com.wealoha.libcurldroid.CurlOpt.OptFunctionPoint;
import com.wealoha.libcurldroid.CurlOpt.OptLong;
import com.wealoha.libcurldroid.CurlOpt.OptObjectPoint;
import com.wealoha.libcurldroid.easy.MultiPart;

/**
 * Curl Jni Wrapper</br>
 * 
 * Usage: See MainActivity in demo project<br/>
 * 
 * 
 * Curl object isn't thread safe, DO NOT share one Curl instant through multiple threads.
 * 
 * @author javamonk
 * @createTime 2015-01-29 12:39:39
 * @see http://curl.haxx.se/libcurl/c/
 */
public class Curl {
	
	private static final String TAG = Curl.class.getSimpleName();
	
	private long handle;
	
	public interface Callback {}
	
	private static Object CLEANUP = new Object() {
		
		@Override
		protected void finalize() throws Throwable {
			if (INIT) {
				Log.i(TAG, "curlGlobalCleanup");
				curlGlobalCleanupNative();
			}
		}
		
	};
	
	private static boolean INIT = false;
	
	public interface WriteCallback extends Callback {
		/**
		 * Called when data received from peer (for example: header, body)
		 * 
		 * @param data 
		 * @return the number of bytes actually taken care of.
		 * @see http://curl.haxx.se/libcurl/c/CURLOPT_WRITEFUNCTION.html
		 */
		public int readData(byte[] data);
	}
	
	public interface ReadCallback extends Callback {
		/**
		 * Called when data need send to peer (for example: header, form)
		 * 
		 * @param data the buffer to fill
		 * @return the actual number of bytes that it stored in that memory area.
		 * @see http://curl.haxx.se/libcurl/c/CURLOPT_READFUNCTION.html
		 */
		public int writeData(byte[] data);
	}
	
	
	public Curl() {
		if (!INIT) {
			curlGlobalInit(CurlConstant.CURL_GLOBAL_DEFAULT);
		}
	}
	
	public static void curlGlobalInit(int flags) {
		if (INIT) {
			return;
		}
		
		CurlCode code = CurlCode.fromValue(curlGlobalInitNative(flags));
		if (code != CurlCode.CURLE_OK) {
			throw new IllegalStateException("curlGlobalInit fail: " + code);
		}
		INIT = true;
	}
	
	private native static int curlGlobalInitNative(int flags);
	
	private native static void curlGlobalCleanupNative();
	
	public void curlEasyInit() throws CurlException  {
		Log.v(TAG, "curlEastInit");
		handle = curlEasyInitNative();
		if (handle == 0) {
			throw new CurlException();
		}
	}
	
	private native long curlEasyInitNative();
	
	public void curlEasyCleanup() {
		Log.v(TAG, "curlEastCleanup: " + handle);
		if (handle != 0) {
			curlEasyCleanupNative(handle);
		}
		handle = 0;
	}
	
	private native void curlEasyCleanupNative(long handle);
	
	/**
	 * 
	 * @param opt {@link OptLong}
	 * @param value
	 * @return
	 */
	public CurlCode curlEasySetopt(OptLong opt, long value) {
		Log.v(TAG, "curlEastSetopt: " + opt + "=" + value);
		return CurlCode.fromValue(curlEasySetoptLongNative(handle, opt.getValue(), value));
	}
	
	private native int curlEasySetoptLongNative(long handle, int opt, long value); 
	
	public CurlCode curlEasySetopt(OptFunctionPoint opt, WriteCallback callback) {
		Log.v(TAG, "curlEastSetopt: " + opt + "=" + callback);
		return CurlCode.fromValue(curlEasySetoptFunctionNative(handle, opt.getValue(), callback));
	}
	
	private native int curlEasySetoptFunctionNative(long handle, int opt, Callback callback);
	
	public CurlCode curlEasySetopt(OptObjectPoint opt, String value) {
		Log.v(TAG, "curlEastSetopt: " + opt + "=" + value);
		return CurlCode.fromValue(curlEasySetoptObjectPointNative(handle, opt.getValue(), value));
	}
	
	private native int curlEasySetoptObjectPointNative(long handle, int opt, String value);
	
	public CurlCode curlEasySetopt(OptObjectPoint opt, String[] values) {
		Log.v(TAG, "curlEastSetopt: " + opt + "=" + values);
		return CurlCode.fromValue(curlEasySetoptObjectPointArrayNative(handle, opt.getValue(), values));
	}
	private native int curlEasySetoptObjectPointArrayNative(long handle, int opt, String[] value);
	
	/**
	 * if set multiple times, previous form will be cleared!
	 * 
	 * @param multiParts
	 * @return
	 */
	public CurlFormadd setFormdata(List<MultiPart> multiParts) {
		if (multiParts != null && multiParts.size() > 0) {
			return CurlFormadd.fromValue(setFormdataNative(handle, multiParts.toArray(new MultiPart[multiParts.size()])));
		} else {
			return CurlFormadd.CURL_FORMADD_NULL;
		}
	}
	
	private native int setFormdataNative(long handle, MultiPart[] multiArray);
	
	public CurlCode curlEasyPerform() {
		Log.v(TAG, "curlEasyPerform");
		return CurlCode.fromValue(curlEasyPerformNavite(handle));
	}
	
	private native int curlEasyPerformNavite(long handle);
	
	static {
		System.loadLibrary("curldroid");
	}
}
