package com.wealoha.libcurldroid.util;

import com.wealoha.libcurldroid.Constant;

import android.util.Log;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015年2月6日 下午1:49:24
 */
public class Logger {
	
	public static void v(String template) {
		Log.v(Constant.TAG, template);
	}
	
	public static void v(String template, Object arg1) {
		Log.v(Constant.TAG, String.format(template, arg1));
	}
	
	public static void v(String template, Object arg1, String arg2) {
		Log.v(Constant.TAG, String.format(template, arg1, arg2));
	}
	
	public static void v(String template, Object... args) {
		Log.v(Constant.TAG, String.format(template, args));
	}
	
	public static void d(String template) {
		Log.d(Constant.TAG, template);
	}
	
	public static void d(String template, Object arg1) {
		Log.d(Constant.TAG, String.format(template, arg1));
	}
	
	public static void d(String template, Object arg1, String arg2) {
		Log.d(Constant.TAG, String.format(template, arg1, arg2));
	}
	
	public static void d(String template, Object... args) {
		Log.d(Constant.TAG, String.format(template, args));
	}
	
	public static void i(String template) {
		Log.i(Constant.TAG, template);
	}
	
	public static void i(String template, Object arg1) {
		Log.i(Constant.TAG, String.format(template, arg1));
	}
	
	public static void i(String template, Object arg1, String arg2) {
		Log.i(Constant.TAG, String.format(template, arg1, arg2));
	}
	
	public static void i(String template, Object... args) {
		Log.i(Constant.TAG, String.format(template, args));
	}
	
	public static void w(String template) {
		Log.w(Constant.TAG, template);
	}
	
	public static void w(String template, Object arg1) {
		Log.w(Constant.TAG, String.format(template, arg1));
	}
	
	public static void w(String template, Object arg1, String arg2) {
		Log.w(Constant.TAG, String.format(template, arg1, arg2));
	}
	
	public static void w(String template, Object... args) {
		Log.w(Constant.TAG, String.format(template, args));
	}
}
