package com.wealoha.libcurldroid.demo;

import android.app.Activity;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.wealoha.libcurldroid.Curl;
import com.wealoha.libcurldroid.Curl.WriteCallback;
import com.wealoha.libcurldroid.CurlCode;
import com.wealoha.libcurldroid.CurlConstant;
import com.wealoha.libcurldroid.CurlException;
import com.wealoha.libcurldroid.CurlOpt.OptFunctionPoint;
import com.wealoha.libcurldroid.CurlOpt.OptLong;
import com.wealoha.libcurldroid.CurlOpt.OptObjectPoint;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015-01-31 15:39:34
 */
public class MainActivity extends Activity {

    private static final String TAG = MainActivity.class.getSimpleName();
    
    private TextView textView;
    
	@Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        textView = (TextView) findViewById(R.id.text_view);
        textView.setMovementMethod(new ScrollingMovementMethod());
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    private void debug(String text) {
    	textView.append(text);
    }
    
    @Override
    protected void onResume() {
    	super.onResume();
    	
    	Curl curl = new Curl();
        CurlCode result = curl.curlGlobalInit(CurlConstant.CURL_GLOBAL_NOTHING);
        Log.i(TAG, "result: " + result);
        try {
			curl.curlEasyInit();
			curl.curlEasySetopt(OptLong.CURLOPT_CONNECTTIMEOUT_MS, 1000 * 15);
			curl.curlEasySetopt(OptLong.CURLOPT_TIMEOUT_MS, 1000 * 15);
			curl.curlEasySetopt(OptLong.CURLOPT_IPRESOLVE, CurlConstant.CURL_IPRESOLVE_V4);
			curl.curlEasySetopt(OptLong.CURLOPT_HTTPGET, 1);
			//curl.curlEasySetopt(OptLong.curlopt_, "114.114.114.114");
			curl.curlEasySetopt(OptFunctionPoint.CURLOPT_HEADERFUNCTION, new WriteCallback() {
				
				@Override
				public int readData(byte[] data) {
					String header = new String(data);
					// Log.d(TAG, "Header: " + header);
					debug("Header Field: " + header);
					return data.length;
				}
			});
			curl.curlEasySetopt(OptFunctionPoint.CURLOPT_WRITEFUNCTION, new WriteCallback() {
				
				@Override
				public int readData(byte[] data) {
					String body = new String(data);
					// Log.d(TAG, "Body data: " + body);
					debug("Body data: " + body);
					return data.length;
				}
			});
			/*curl.curlEasySetopt(OptFunctionPoint.CURLOPT_READFUNCTION, new ReadCallback() {
				@Override
				public int writeData(byte[] data) {
					return 0;
				}
			});*/
			// include header in write
			// curl.curlEasySetopt(OptLong.CURLOPT_HEADER, 1);
			// list!
			curl.curlEasySetopt(OptObjectPoint.CURLOPT_HTTPHEADER, new String[] {
				"Accept-Encoding: gzip, deflate, sdch",
				//"Accept-Type: */*"
			});
			
			curl.curlEasySetopt(OptObjectPoint.CURLOPT_URL, "http://www.baidu.com/");
			result = curl.curlEasyPerform();
			Log.i(TAG, "result: " + result);
			curl.curlEasyCleanup();
		} catch (CurlException e) {
			Log.w(TAG, "Exception", e);
		}
    }
}
