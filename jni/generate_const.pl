#!/usr/bin/perl
# read curl.h and generate constatns


my $file = 'shared/curl/include/curl/curl.h';
my $file_java = '../src/com/wealoha/libcurldroid/CurlConstant.java';
my $file_code = '../src/com/wealoha/libcurldroid/CurlCode.java';
my $file_opt = '../src/com/wealoha/libcurldroid/CurlOpt.java';
my $file_formadd = '../src/com/wealoha/libcurldroid/CurlFormadd.java';


open FH, $file or die "File not found: $file $!";
open OUT, '>', $file_java or die $!;
open OUT_CODE, '>', $file_code or die $!;
open OUT_OPT, '>', $file_opt or die $!;
open OUT_FORM, '>', $file_formadd or die $!;

print OUT qq{// Auto generated from 'curl/curl.h', DO NOT EDIT!!!
package com.wealoha.libcurldroid;
import java.util.HashSet;
import java.util.Set;

public abstract class CurlConstant \{

    private CurlConstant() {}

	private static Set<Integer> LONG_OPT_SET = new HashSet<Integer>();
	
	private static Set<Integer> OBJECT_POINT_OPT_SET = new HashSet<Integer>();
	
	private static Set<Integer> FUNCTION_POINT_OPT_SET = new HashSet<Integer>();
	
};

print OUT_CODE qq(// Auto generated from 'curl/curl.h', DO NOT EDIT!!!
package com.wealoha.libcurldroid;
import android.util.SparseArray;

public enum CurlCode {    
);

print OUT_OPT qq(// Auto generated from 'curl/curl.h', DO NOT EDIT!!!
package com.wealoha.libcurldroid;
import android.util.SparseArray;

public interface CurlOpt {    
);

print OUT_FORM qq(// Auto generated from 'curl/curl.h', DO NOT EDIT!!!
package com.wealoha.libcurldroid;
import android.util.SparseArray;

public enum CurlFormadd {    
);


my $init = "";

# CurlCode.java
my $curl_code_block = 0;

# CurlOpt.java
my $curl_opts = {};

# CurlFormadd.java
my $curl_form_block = 0;

while (<FH>) {
    
    if (m{\#define\s+(CURLOPTTYPE_\w+?)\s+(\d+)}xmsi) {
	    # #define CURLOPTTYPE_LONG          0
	    my ($type, $value) = ($1, $2);
        print OUT "    public static int $type = $value;\n\n";
    } elsif (m{CINIT\((\w+)\,\s+(\w+)\,\s+(\d+)\)}xmsi) {
    	# CINIT(WRITEDATA, OBJECTPOINT, 1);
    	my ($name, $type, $number) = ($1, $2, $3);
    	print OUT "    public static int CURLOPT_$name = CURLOPTTYPE_$type + $number;\n\n";
    	if ("OBJECTPOINT" eq $type) {
    		$init .= "        OBJECT_POINT_OPT_SET.add(CURLOPT_$name);\n";
    	} elsif ("LONG" eq $type) {
    		$init .= "        LONG_OPT_SET.add(CURLOPT_$name);\n";
    	} elsif ("FUNCTIONPOINT" eq $type) {
    		$init .= "        FUNCTION_POINT_OPT_SET.add(CURLOPT_$name);\n";
    	}
    	$curl_opts{$type} .= "    CURLOPT_$name(CurlConstant.CURLOPT_$name), //\n";
    } elsif (m{(CURL_IPRESOLVE_\w+)\s+(\d+)}xmsi) {
    	# #define CURL_IPRESOLVE_WHATEVER 0
    	my ($type, $value) = ($1, $2);
    	print OUT "    public static int $type = $value;\n\n";
    } elsif (m{(CURLE_OK)\s+=\s+(\d+)}xmsi) {
    	# CURLE_OK = 0,
    	my ($name) = $1;
    	$curl_code_block = 0;
    	print OUT_CODE "    $name($curl_code_block), //\n";
    	$curl_code_block++;
    } elsif ($curl_code_block && m{^\s{0,8}(CURLE_\w+?),}xmsi) {
    	my ($name) = $1;
    	print OUT_CODE "    $name($curl_code_block), //\n";
    	$curl_code_block++;
    } elsif ($curl_code_block && m{CURL_LAST}xmsi) {
    	$curl_code_block = 0;
    } elsif (m{^\s{0,8}(CURL_FORMADD_OK),}xmsi) {
        # CURL_FORMADD_OK = 0,
        my ($name) = $1;
        $curl_form_block = 0;
        print OUT_FORM "    $name($curl_form_block), //\n";
        $curl_form_block++;
    } elsif ($curl_form_block && m{^\s{0,8}(CURL_FORMADD\w+?),}xmsi) {
        my ($name) = $1;
        print OUT_FORM "    $name($curl_form_block), //\n";
        $curl_form_block++;
    } elsif ($curl_form_block && m{CURL_FOMRADD_LAST}xmsi) {
        $curl_form_block = 0;
    } elsif (m{\#define\s+(CURL_GLOBAL_\w+)\s+\(?([^\)]+)\)?}xmsi) {
    	# #define CURL_GLOBAL_SSL (1<<0)
    	my ($name, $value) = ($1, $2);
    	print OUT "    public static int $name = $value;\n\n";
    }
}

print OUT qq(
    public static boolean isLongOpt(int opt) {
    	return LONG_OPT_SET.contains(opt);
    }
    
    static {
$init
    }
}
);

print OUT_CODE "    ;\n";
print OUT_CODE get_enum_stuff('CurlCode');
print OUT_CODE "}";

print OUT_FORM "    ;\n";
print OUT_FORM get_enum_stuff('CurlFormadd');
print OUT_FORM "}";

my $enum_body_object_point = get_enum_stuff('OptObjectPoint');
my $enum_body_long = get_enum_stuff('OptLong');
my $enum_body_function_point = get_enum_stuff('OptFunctionPoint');

print OUT_OPT qq(
	public enum OptObjectPoint {
		$curl_opts{OBJECTPOINT}
		;
		$enum_body_object_point
	}
	
	public enum OptLong {
		$curl_opts{LONG}
		;
		$enum_body_long
	}
	
	public enum OptFunctionPoint {
		$curl_opts{FUNCTIONPOINT}
		;
		$enum_body_function_point
	}
}
);

close FH;
close OUT;
close OUT_CODE;
close OUT_OPT;


sub get_enum_stuff {
	my $name = shift;

	return qq(
	
	private final int value;

	private static SparseArray<$name> valuesMap = new SparseArray<$name>();

	static {
		for ($name e : values()) {
			valuesMap.put(e.getValue(), e);
		}
	}

	private $name(int value) {
		this.value = value;
	}
	
	public int getValue() {
		return value;
	}
	
	public static $name fromValue(int value) {
		return valuesMap.get(value);
	}
);
}