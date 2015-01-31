Build curl
===========

INSTALL document comes with curl and c-ares sources is helpful!

To build jni wrapper code, two libs must pre builded.

- c-ares (DNS lib, can skip if you doesn't need DNS functions)
- curl

Get a recent version ndk.
Get curl, c-ares from http://curl.haxx.se/ and http://c-ares.haxx.se/ .

1. Prepare toolchain

Run

    $NDK_HOME/build/tools/make-standalone-toolchain.sh

If make-standalone-toolchain.sh encounter this:

    Unable to auto-config arch from toolchain

Run:

    $NDK_HOME/build/tools/make-standalone-toolchain.sh  —toolchain=arm-linux-androideabi-4.9

Where "arm-linux-androideabi-4.9" can be found in $NDK_HOME/toolchinas

Untar file generate at /tmp to some where(refer as $TOOLCHAIN later)


2. Build c-ares

Build c-ares-1.10.0(and install to somewhere for ex: curl-7.40.0/cares/)

Set build environment:

    export TOOLCHAIN=/path/to/arm-linux-androideabi-4.9
    export PATH=$TOOLCHAIN/bin:$PATH
    ./configure --host=arm-linux-androideabi --enable-shared --disable-static --prefix=/data/build/curl-7.40.0/cares/ CFLAGS="-march=armv5"
    sh path/to/fix_libtool.sh
    make
    make install
    copy .libs/libcares.so to jni/<arch>/

--prefix will install c-ares to a none standard path (Since is's cross compiled, you can't use it on current host expect it's arm :] )

3. Build curl

    ./configure --host=arm-linux-androideabi --enable-ares=/abs_path_to/c-ares/install/path --enable-shared --disable-static CFLAGS="-march=armv5" [your configure options here]
    make
	sh path/to/fix_libtool.sh
	make
	copy lib/.libs/libcares.so to jni/<arch>/

4. Repeat steps 2~3 to build other arch

host: arm-linux-androideabi,arm-linux-androideabi,i686-linux-android, mipsel-linux-android
arch: armv5, armv7-a, x86, mips

Important: fix_libtool.sh is a REQUIRED step. Android has problems with soname like libcurl.so, libcurl.so.5, if you miss this, when you run, a exception like this will thrown:

    01-29 19:06:45.310: E/AndroidRuntime(17532): java.lang.UnsatisfiedLinkError: dlopen failed: could not load library "libcurl.so.5" needed by "libcurldroid.so"; caused by library "libcurl.so.5" not found

5. build wrapper

    build_jni.sh

Reference
=========

- http://studygolang.com/articles/2281 (toolchain)
- https://github.com/mevansam/cmoss/blob/master/build-droid/build-cURL.sh (fix soname)
