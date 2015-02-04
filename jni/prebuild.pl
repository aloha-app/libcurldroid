#!/usr/bin/perl
use strict;
use warnings;
use Cwd;

my $ndk_home = "/Users/javamonk/adt-bundle-mac-x86_64-20140702/ndk/";
my $toolchains = "/Users/javamonk/adt-bundle-mac-x86_64-20140702/toolchains/";
my $curl_src = "shared/curl"; # relative to current path (ln -s)
my $cares_src = "shared/c-ares"; # relative to current path (ln -s)
my $pwd = getcwd;

# names compiler need
my @arches = qw(armv5te armv7-a i686); # mipsel

my %toolchains = qw(
    armv5te arm-linux-androideabi-4.9
    armv7-a arm-linux-androideabi-4.9
    mipsel  mipsel-linux-android-4.9
    i686    x86-4.9
);

my %hosts = qw(
    armv5te arm-linux-androideabi
    armv7-a arm-linux-androideabi
    i686    i686-android-linux
    mipsel  mipsel-android-linux
);

# android really called
my %libs = qw(
    armv5te armeabi
    armv7-a armeabi-v7a
    mipsel  mips
    i686    x86
);

my $shell = "";
my $old_path = $ENV{PATH};
foreach my $arch(@arches) {
	my $toolchain = $toolchains{$arch};
	my $host = $hosts{$arch};
	
	$ENV{PATH} = "$toolchains/$toolchain/bin:$old_path";
	print "PATH: $ENV{PATH}\n";
	# c-ares
	$shell .= "export PATH=$toolchains/$toolchain/bin:$old_path\n";
	$shell .= "cd $cares_src;\n";
	$shell .= "make clean\n";
	$shell .= "CC=mipsel-linux-android-gcc " if $arch eq 'mipsel';
    $shell .= "CC=i686-linux-android-gcc " if $arch eq 'i686';
    #$shell .= "SYSROOT=$toolchains/$toolchain/ ";
    $shell .= qq{CFLAGS="-march=$arch" } if $arch =~ /arm/;
	$shell .= qq{./configure --host=$host --enable-shared --disable-static --prefix=$pwd/$curl_src/cares_$arch;\n};
	$shell .= "sh $pwd/fix_libtool.sh\n";
	$shell .= "make -j 4\n";
	$shell .= "make install\n";
	$shell .= "cd $pwd;\n";
    $shell .= "cp $pwd/$cares_src/.libs/libcares.so $pwd/$libs{$arch}/\n";
    
    
    # curl
    $shell .= "cd $curl_src;\n";
    $shell .= "make clean\n";
    # https://code.google.com/p/android/issues/detail?id=64266
    # $shell .= qq{LDFLAGS="-Wl,-rpath-link=$ndk_home/platforms/android-9/arch-mips/usr/lib" } if $arch eq 'mipsel';
    #$shell .= "LD_LIBRARY_PATH=$pwd/$curl_src/cares_$arch/lib ";
    $shell .= "CC=mipsel-linux-android-gcc " if $arch eq 'mipsel';
    $shell .= "CC=i686-linux-android-gcc " if $arch eq 'i686';
    $shell .= qq{CFLAGS="-march=$arch" } if $arch =~ /arm/;
    $shell .= qq{./configure --host=$host --enable-ares=$pwd/$curl_src/cares_$arch --enable-shared --disable-static;\n};
    $shell .= "sh $pwd/fix_libtool.sh\n";
    $shell .= "make -j 4\n";
    #$shell .= "make install\n";
    $shell .= "cd $pwd;\n";
    $shell .= "cp $pwd/$curl_src/lib/.libs/libcurl.so $pwd/$libs{$arch}/\n";
    
    print $shell;
    
    
    sleep(5);
    if (system($shell) != 0) {
    	die "compile fail " . $@;
    }
}


