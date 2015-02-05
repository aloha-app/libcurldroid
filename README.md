libcurldroid
=============

Android Java JNI Wrapper for libcurl

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
    // multipart form not support array
    .addMultiPartPostParam("multi_a", null, null, bytes) //
    .addMultiPartPostParam("multi_b", null, "text/html", bytes) //
    .addMultiPartPostParam("multi_c", "c.html", null, bytes) //
    .addMultiPartPostParam("multi_d", "d.html", "text/plain", bytes) //
    .postUrl("http://some-url/cgi-bin/t.cgi") // or .getUrl(String url)
    .perform();
    
int status = result.getStatus();
String statusLine = result.getStatusLine();
String body = result.getBodyAsString();
byte[] binaryData = result.getBody();
String header = result.getHeader("ContentType"); // ignore header name case
Map<String, String> headers : result.getHeaders();
```

Retrofit
---------

```java
    RestAdapter adapter = new RestAdapter.Builder() //
        .setClient(new RetrofitCurlClient(new CurlHttpCustomizeCallback() {

            @Override
            public void setCurlOptions(CurlHttp curlHttp) {
                curlHttp.setTimeoutMillis(1000 * 180);
                curlHttp.setConnectionTimeoutMillis(1000 * 10);
                curlHttp.addHeader("Accept-Encoding", "gzip");
            }
        })) //
        .setEndpoint("http://api.xxx.com/") //
        .setRequestInterceptor(requestInterceptor) //
        .setLogLevel(RestAdapter.LogLevel.BASIC) //
        .setErrorHandler(errorHandler) //
        .setConverter(gsonConvertor).build();
```

Build
------

Provided binary libcares and libcurl comes with default configure options.
You can build with your custom configure options.

Full build instructions here: https://github.com/aloha-app/libcurldroid/blob/master/jni/BUILD.md , may be the shortest(and easiest) document on the net:] 

FAQ
-----

1. If you use Eclipse, reference libcurldroid as library, you may encounter this:

    [2015-02-05 16:34:30 - Dex Loader] Unable to execute dex: Multiple dex files define Lcom/squareup/picasso/Action$RequestWeakReference;
    [2015-02-05 16:34:30 - your-project] Conversion to Dalvik format failed: Unable to execute dex: Multiple dex files define Lcom/squareup/picasso/Action$RequestWeakReference;
    
Right click libcurldroid -> Properties -> Java Build Path -> Order and Export -> uncheck Android Private Libraries

In the end
-----
Great thanks to curl-android project: https://github.com/liudongmiao/curl-android