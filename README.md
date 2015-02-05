libcurldroid
=============

Java JNI Wrapper for libcurl

* [x] easy of use
* [x] type safe Java interfaces
* [ ] HTTP and HTTPS request
* [ ] proxy using http, socks5
* [ ] Integration for Picasso (Downloader) and Retrofit (Client)
* [x] native prebuild .so libs for arch arm, armv7, and x86
* [x] custom build instructions
* [x] minSdkVersion 8
* [x] jdk 1.6


API
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

In the end
-----
Great thanks to curl-android project: https://github.com/liudongmiao/curl-android