#include <jni.h>
#include <string>
#include <list>
#include <android/log.h>
#include "curl/curl.h"
#include "curldroid.h"

#define TAG "CURLDROID"
#define LOGD(...) (__android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__))

static JavaVM *cached_jvm;
static jmethodID MID_CB_write; // write is really read
static jmethodID MID_CB_read;  // and read is really write
                               // don't be confused

JNIEXPORT jint JNICALL
   JNI_OnLoad(JavaVM *jvm, void *reserved) {
	JNIEnv *env;
	jclass cls;
	cached_jvm = jvm;  /* cache the JavaVM pointer */
	if (jvm->GetEnv((void **)&env, JNI_VERSION_1_6)) {
	   return JNI_ERR; /* JNI version not supported */
	}
	cls = env->FindClass("com/wealoha/libcurldroid/Curl$WriteCallback");
	if (cls == NULL) {
	   return JNI_ERR;
	}
	/* Compute and cache the method ID */
	MID_CB_write = env->GetMethodID(cls, "readData", "([B)I");
	if (MID_CB_write == NULL) {
	   return JNI_ERR;
	}

	cls = env->FindClass("com/wealoha/libcurldroid/Curl$ReadCallback");
	if (cls == NULL) {
	   return JNI_ERR;
	}
	/* Compute and cache the method ID */
	MID_CB_read = env->GetMethodID(cls, "writeData", "([B)I");
	if (MID_CB_read == NULL) {
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
	std::list<struct curl_slist*> m_slists;

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

	void cleanSlists() {
		LOGD("clean slists");
		while (!m_slists.empty()) {
			struct curl_slist* slist = m_slists.front();
			LOGD(".");
			curl_slist_free_all(slist);
			m_slists.pop_front();
		}
	}

public:
	Holder(CURL* curl) {
		mCurl = curl;
	}

	~Holder() {
		// clear all GlobalRefs avoid memory leak
		cleanGlobalRefs();
		// clear all slists
		cleanSlists();
	}

	CURL* getCurl() {
		return mCurl;
	}

	// hold GlobalRef
	void addGlobalRefs(jobject obj) {
		m_j_global_refs.push_back(obj);
	}

	void addCurlSlist(struct curl_slist *slist) {
		m_slists.push_back(slist);
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
	JNIEnv *env;
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
	result = env->CallIntMethod(object, MID_CB_write, array);
	env->DeleteLocalRef(array);

	return result;
}

size_t read_callback(char *buffer, size_t size, size_t nitems, void *instream) {
	JNIEnv *env;
	jbyteArray array;
	jint length = size * nitems;
	jobject obj = (jobject) instream;
	if (length == 0) {
		return 0;
	}

	env = JNU_GetEnv();

	// write up to array length
	array = env->NewByteArray(length);
	int write_len = env->CallIntMethod(obj, MID_CB_read, array);
	if (write_len > length) {
		// bad citizen!
		return CURL_READFUNC_ABORT;
	}
	env->GetByteArrayRegion(array, 0, write_len, (jbyte *)buffer);
	env->DeleteLocalRef(array);
	return write_len;
}




JNIEXPORT int JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasySetoptFunctionNative
  (JNIEnv * env, jobject obj, jlong handle, jint opt, jobject cb) {
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();
	jobject cb_ref = 0;
	switch (opt) {
	case CURLOPT_HEADERFUNCTION:
		LOGD("CURLOPT_HEADERFUNCTION");
		curl_easy_setopt(curl, (CURLoption) opt, &write_callback);
		cb_ref = env->NewGlobalRef(cb);
		holder->addGlobalRefs(cb_ref);
		curl_easy_setopt(curl, CURLOPT_HEADERDATA, (void *)cb_ref);
		break;
	case CURLOPT_WRITEFUNCTION:
		// see http://curl.haxx.se/libcurl/c/CURLOPT_WRITEFUNCTION.html
		curl_easy_setopt(curl, (CURLoption) opt, &write_callback);
		cb_ref = env->NewGlobalRef(cb);
		holder->addGlobalRefs(cb_ref);
		curl_easy_setopt(curl, CURLOPT_WRITEDATA, (void *)cb_ref);
		break;
	case CURLOPT_READFUNCTION:
		curl_easy_setopt(curl, (CURLoption) opt, &read_callback);
		cb_ref = env->NewGlobalRef(cb);
		holder->addGlobalRefs(cb_ref);
		curl_easy_setopt(curl, CURLOPT_READDATA, (void *)cb_ref);
		break;
	default:
		// no-op
		;
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

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasySetoptObjectPointArrayNative
  (JNIEnv *env, jobject obj, jlong handle, jint opt, jobjectArray values) {
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();

	const char *str;
	struct curl_slist *slist = 0;
	int nargs = env->GetArrayLength(values);
	for (int i = 0; i < nargs; i++) {
		jstring value = (jstring) env->GetObjectArrayElement(values, i);
		str = env->GetStringUTFChars(value, 0);
		if (str == 0) {
			LOGD("break");
			return 0;
		}
		LOGD("append slist");
		slist = curl_slist_append(slist, str);
		env->ReleaseStringUTFChars(value, str);
	}
	holder->addCurlSlist(slist);
	LOGD("set slist");
	return curl_easy_setopt(curl, (CURLoption) opt, slist);
}

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasyPerformNavite
  (JNIEnv * env, jobject obj, jlong handle) {
	Holder* holder = (Holder*) handle;
	CURL * curl = holder->getCurl();
	return (int) curl_easy_perform(curl);
}
