package com.wealoha.libcurldroid.util;

import android.util.Log;

/**
 * 
 * 
 * @author javamonk
 * @createTime 2015年2月6日 下午1:49:24
 */
public class Logger {
	
	private final String tag;
	
	private Logger(String tag) {
		this.tag = tag;
	}
	
	@SuppressWarnings("rawtypes")
	public static Logger getLogger(Class clazz) {
		return new Logger("libcurldroid." + clazz.getSimpleName());
	}
	
	public void v(String template) {
		Log.v(tag, template);
	}
	
	public void v(String template, Object arg1) {
		Log.v(tag, String.format(template, arg1));
	}
	
	public void v(String template, Object arg1, String arg2) {
		Log.v(tag, String.format(template, arg1, arg2));
	}
	
	public void v(String template, Object... args) {
		Log.v(tag, String.format(template, args));
	}
	
	public void d(String template) {
		Log.d(tag, template);
	}
	
	public void d(String template, Object arg1) {
		Log.d(tag, String.format(template, arg1));
	}
	
	public void d(String template, Object arg1, String arg2) {
		Log.d(tag, String.format(template, arg1, arg2));
	}
	
	public void d(String template, Object... args) {
		Log.d(tag, String.format(template, args));
	}
	
	public void i(String template) {
		Log.i(tag, template);
	}
	
	public void i(String template, Object arg1) {
		Log.i(tag, String.format(template, arg1));
	}
	
	public void i(String template, Object arg1, String arg2) {
		Log.i(tag, String.format(template, arg1, arg2));
	}
	
	public void i(String template, Object... args) {
		Log.i(tag, String.format(template, args));
	}
	
	public void w(String template) {
		Log.w(tag, template);
	}
	
	public void w(String template, Object arg1) {
		Log.w(tag, String.format(template, arg1));
	}
	
	public void w(String template, Object arg1, String arg2) {
		Log.w(tag, String.format(template, arg1, arg2));
	}
	
	public void w(String template, Object... args) {
		Log.w(tag, String.format(template, args));
	}
	
	public void w(String template, Throwable t) {
		Log.w(tag, template, t);
	}
	
	public void w(String template, Object arg1, Exception e) {
		Log.w(tag, String.format(template, arg1), e);
	}
	
	public void w(String template, Object arg1, Object arg2, Exception e) {
		Log.w(tag, String.format(template, arg1, arg2), e);
	}
}
