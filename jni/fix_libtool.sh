#!/bin/sh
# patch curl libtool
#https://github.com/mevansam/cmoss/blob/master/build-droid/build-cURL.sh
# Fix libtool to not create versioned shared libraries
mv "libtool" "libtool~"
sed "s/library_names_spec=\".*\"/library_names_spec=\"~##~libname~##~{shared_ext}\"/" libtool~ > libtool~1
sed "s/soname_spec=\".*\"/soname_spec=\"~##~{libname}~##~{shared_ext}\"/" libtool~1 > libtool~2
sed "s/~##~/\\\\$/g" libtool~2 > libtool
chmod u+x libtool
