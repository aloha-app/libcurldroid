package com.wealoha.libcurldroid.retrofit;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import retrofit.client.Client;
import retrofit.client.Header;
import retrofit.client.Request;
import retrofit.client.Response;
import retrofit.mime.TypedByteArray;
import retrofit.mime.TypedInput;
import retrofit.mime.TypedOutput;
import android.util.Log;

import com.wealoha.libcurldroid.CurlHttp;
import com.wealoha.libcurldroid.Result;
import com.wealoha.libcurldroid.third.CurlHttpCallback;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-02-04 22:34:46
 */
public class RetrofitCurlClient implements Client {

	private static final String TAG = RetrofitCurlClient.class.getSimpleName();
	
	private CurlHttpCallback callback;
		
	public RetrofitCurlClient() {
	}
	
	public RetrofitCurlClient curlCalback(CurlHttpCallback callback) {
		this.callback = callback;
		return this;
	}
	
	@Override
	public Response execute(Request request) throws IOException {
		List<Header> headers = request.getHeaders();
		
		CurlHttp curlHttp = CurlHttp.newInstance();
		
		if (callback != null) {
			callback.afterInit(curlHttp, request.getUrl());
		}
		
		if (headers != null && headers.size() > 0) {
			for (Header header : headers) {
				Log.v(TAG, "add header: " + header.getName() + " " + header.getValue());
				curlHttp.addHeader(header.getName(), header.getValue());
			}
		}
		
		if ("get".equalsIgnoreCase(request.getMethod())) {
			// get
			curlHttp.getUrl(request.getUrl());
		} else {
			// post
			TypedOutput body = request.getBody();
			if (body != null) {
				Log.v(TAG, "set request body");
				ByteArrayOutputStream os = new ByteArrayOutputStream((int) body.length());
				body.writeTo(os);
				curlHttp.setBody(body.mimeType(), os.toByteArray());
			}
			curlHttp.postUrl(request.getUrl());
		}
		return convertResult(request, curlHttp.perform());
	}
	
	private Response convertResult(Request request, Result result) throws IOException {
		Map<String, String> headerMap = result.getHeaders();
		TypedInput input = new TypedByteArray(headerMap.get("Content-Type"), result.getDecodedBody());
		List<Header> headers = new ArrayList<Header>(headerMap.size());
		for (Entry<String, String> entry : headerMap.entrySet()) {
			headers.add(new Header(entry.getKey(), entry.getValue()));
		}
		return new Response(request.getUrl(), result.getStatus(), result.getStatusLine(), headers, input);
	}

}
