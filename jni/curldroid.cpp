#include <jni.h>
#include <string>
#include <list>
#include <android/log.h>
#include "curl/curl.h"
#include "curldroid.h"

#define TAG "CURLDROID"
#define LOGD(...) (__android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__))

static JavaVM *cached_jvm;
static jweak Class_CB;
static jmethodID MID_CB_cb;

JNIEXPORT jint JNICALL
   JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *env;
	jclass cls;
	cached_jvm = jvm;  /* cache the JavaVM pointer */
	if (jvm->GetEnv((void **)&env, JNI_VERSION_1_6)) {
	   return JNI_ERR; /* JNI version not supported */
	}
	cls = env->FindClass("com/wealoha/libcurldroid/Curl$Callback");
	if (cls == NULL) {
	   return JNI_ERR;
	}
	/* Use weak global ref to allow C class to be unloaded */
	Class_CB = env->NewWeakGlobalRef(cls);
	if (Class_CB == NULL) {
	   return JNI_ERR;
	}
	/* Compute and cache the method ID */
	MID_CB_cb = env->GetMethodID(cls, "callback", "([B)I");
	if (MID_CB_cb == NULL) {
	   return JNI_ERR;
	}
	return JNI_VERSION_1_6;
}

JNIEnv *JNU_GetEnv() {
   JNIEnv *env;
   cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
   return env;
}

class Holder {
	CURL* mCurl;
	std::list<jobject> m_j_global_refs;

	void cleanGlobalRefs() {
		JNIEnv * env = JNU_GetEnv();
		LOGD("clean ref");
		while (!m_j_global_refs.empty()) {
		    jobject ref = m_j_global_refs.front();
		    LOGD(".");
		    env->DeleteGlobalRef(ref);
		    m_j_global_refs.pop_front();
		}
	}

public:
	Holder(CURL* curl) {
		mCurl = curl;
	}

	~Holder() {
		// clear all GlobalRefs avoid memory leak
		cleanGlobalRefs();
	}

	CURL* getCurl() {
		return mCurl;
	}

	// hold GlobalRef
	void addGlobalRefs(jobject obj) {
		m_j_global_refs.push_back(obj);
	}

};

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlGlobalInitNative
  (JNIEnv * env, jobject obj, jint flag) {
	curl_global_init((int) flag);
}

JNIEXPORT void JNICALL Java_com_wealoha_libcurldroid_Curl_curlGlobalCleanup
  (JNIEnv * env, jobject obj) {
	curl_global_cleanup();
}

JNIEXPORT jlong JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasyInitNative
  (JNIEnv * env, jobject obj) {
	CURL* curl = curl_easy_init();
	if (curl != 0) {
		Holder* holder = new Holder(curl);
		return (long) holder;
	}
	return 0;
}

JNIEXPORT void JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasyCleanupNative
  (JNIEnv * env, jobject obj, jlong handle) {
	if (handle != 0) {
		Holder* holder = (Holder*) handle;
		curl_easy_cleanup(holder->getCurl());
		delete holder;
		holder = 0;
	}
}

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasySetoptLongNative
  (JNIEnv *env, jobject obj, jlong handle, jint opt, jlong value) {
	Holder* holder = (Holder*) handle;
	return (int) curl_easy_setopt(holder->getCurl(), (CURLoption) opt, (long) value);
}

size_t write_callback(char *ptr, size_t size, size_t nmemb, void *userdata) {
	JNIEnv * env;
	jint result;
	jbyteArray array;
	jint length = size * nmemb;
	jobject object = (jobject)userdata;

	if (length == 0) {
		return 0;
	}

	env = JNU_GetEnv();
	array = env->NewByteArray(length);
	if (!array) {
		return 0;
	}
	env->SetByteArrayRegion(array, 0, length, (jbyte *)ptr);
	result = env->CallIntMethod(object, MID_CB_cb, array);
	env->DeleteLocalRef(array);

	return result;
}



JNIEXPORT int JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasySetoptFunctionNative
  (JNIEnv * env, jobject obj, jlong handle, jint opt, jobject cb) {
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();
	switch (opt) {
	case CURLOPT_WRITEFUNCTION:
		// see http://curl.haxx.se/libcurl/c/CURLOPT_WRITEFUNCTION.html
		curl_easy_setopt(curl, (CURLoption) opt, &write_callback);
		jobject cb_ref = env->NewGlobalRef(cb);
		holder->addGlobalRefs(cb_ref);
		curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)cb_ref);
		break;
	}
	return (int) CURLE_OK;
}

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasySetoptObjectPointNative
  (JNIEnv * env, jobject obj, jlong handle, jint opt, jstring value) {
	const char *str;
	int result;
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();
	str = env->GetStringUTFChars(value, 0);
	if (str == 0) {
	   return 0;
    }
	result = (int) curl_easy_setopt(curl, (CURLoption) opt, str);
	env->ReleaseStringUTFChars(value, str);
	return result;
}

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasyPerformNavite
  (JNIEnv * env, jobject obj, jlong handle) {
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();
	return (int) curl_easy_perform(curl);
}
