# gen header files
javah -o curldroid.h -cp ../bin/classes/ com.wealoha.libcurldroid.Curl
# build
ndk-build

