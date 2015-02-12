libcurldroid
=============

Android Java JNI Wrapper for cURL

Delegate all HTTP transport to [libcurl](http://curl.haxx.se/libcurl/), through [Picasso](https://github.com/square/picasso) to [Retrofit](https://github.com/square/retrofit), or using our simple fluent style api. Bypassing Android java HTTP bugs.

* [x] easy of use
* [x] type safe Java interfaces
* [ ] HTTP and HTTPS request
* [ ] proxy using http, socks5
* [ ] Integration for Picasso (Downloader) and Retrofit (Client)
* [x] native prebuild .so libs for arch arm, armv7, and x86
* [x] custom build instructions
* [x] minSdkVersion 8
* [x] jdk 1.6


API (may be change before 1.0.0 formal release)
----

```java
Result result = CurlHttp.newInstance() //
    .setIpResolveV4() //
    .addHeader("Accept-Encoding", "gzip, deflate, sdch") //
    .setHttpProxy("10.0.1.2", 8888) //
    .addParam("hello", "World!") //
    // passing array like jQuery
    .addParam("foo[]", Arrays.asList("Bar!", "Bar!")) //
    // multipart form(post only)
    .addMultiPartPostParam("multi_a", null, null, bytes) //
    .addMultiPartPostParam("multi_b", null, "text/html", bytes) //
    .addMultiPartPostParam("multi_c", "c.html", null, bytes) //
    .addMultiPartPostParam("multi_d", "d.html", "text/plain", bytes) //
    .postUrl("http://your-host/cgi-bin/t.cgi") // or .getUrl(String url)
    .perform();
    
int status = result.getStatus();
String statusLine = result.getStatusLine();
String body = result.getBodyAsString();
byte[] binaryData = result.getBody();
byte[] binaryDecodedDate = result.getDecodedBody(); // if gzipped
String header = result.getHeader("ContentType"); // ignore header name case
Map<String, String> headers : result.getHeaders();


// get with custom params
Result result = CurlHttp.newInstance() //
    .addParam("hello", "World!") //
    .addParam("hello", "GitHub!") //
    .addParam("foo", "Bar!") //
    .getUrl("http://your-host/cgi-bin/t.cgi") //
```

Retrofit
---------

```java
RetrofitCurlClient client = new RetrofitCurlClient() //
    .curlCalback(new CurlHttpCallback() {
        
        @Override
        public void afterInit(CurlHttp curlHttp, String url) {
            curlHttp.setTimeoutMillis(1000 * 180);
            curlHttp.setConnectionTimeoutMillis(1000 * 10);
            curlHttp.addHeader("Accept-Encoding", "gzip");
        }
    });

RestAdapter adapter = new RestAdapter.Builder() //
    .setClient(client) //
    .setEndpoint("http://api.xxx.com/") //
    .setRequestInterceptor(requestInterceptor) //
    .setLogLevel(RestAdapter.LogLevel.BASIC) //
    .setErrorHandler(errorHandler) //
    .setConverter(gsonConvertor).build();
```

Picasso
---------

```java
File cacheDir = new File(Environment.getExternalStorageDirectory(), "libcurldroid"); 
// external storage status needed to check

// disk based LRU cache
DiskCache cache = Builder.newInstance() //
    .cachePath(new File(parentPath, "cache")) //
    .accessTimeSyncMillis(1000 * 30) // 30 secs
    .evictIntervalMillis(1000 * 180) // 3 mins
    .evictFactor(0.8f) // 80%
    .maxCacheSizeInBytes(256 * 1024 * 1024) // 256m
    .build();
    
PicassoCurlDownloader downloader = new PicassoCurlDownloader() //
    .cache(cache) //
    .curlCalback(new CurlHttpCallback() {
        
        @Override
        public void afterInit(CurlHttp curlHttp, String url) {
            curlHttp.setTimeoutMillis(1000 * 180);
            curlHttp.setConnectionTimeoutMillis(1000 * 10);
        }
    });

LruCache memoryCache = new LruCache((int) Runtime.getRuntime().maxMemory() / 5);
Picasso picasso = new Picasso.Builder(this) //
    .indicatorsEnabled(true) //
    .loggingEnabled(true) //
    .downloader(downloader) //
    .memoryCache(memoryCache) //
    .build();
```

Build
------

Provided binary libcares and libcurl comes with default configure options.
You can build with your custom configure options.

Full build instructions here: https://github.com/aloha-app/libcurldroid/blob/master/jni/BUILD.md , may be the shortest(and easiest) document on the net:] 

ProGuard
---------

```
-keepclasseswithmembernames class * {
    native <methods>;
}
-keep class com.wealoha.libcurldroid.Curl$WriteCallback { *; }
-keep class com.wealoha.libcurldroid.Curl$ReadCallback { *; }
-keep class com.wealoha.libcurldroid.easy.MultiPart { *; }
```
FAQ
-----

1. If you use Eclipse, reference libcurldroid as library, you may encounter this:

    [2015-02-05 16:34:30 - Dex Loader] Unable to execute dex: Multiple dex files define Lcom/squareup/picasso/Action$RequestWeakReference;
    [2015-02-05 16:34:30 - your-project] Conversion to Dalvik format failed: Unable to execute dex: Multiple dex files define Lcom/squareup/picasso/Action$RequestWeakReference;
    
Right click libcurldroid -> Properties -> Java Build Path -> Order and Export -> uncheck Android Private Libraries

In the end
-----
Great thanks to curl-android project: https://github.com/liudongmiao/curl-android

Daniel Stenberg and his [cURL](http://curl.haxx.se/libcurl/), without cURL this project will never exist!