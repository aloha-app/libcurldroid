#include <jni.h>
#include <cstddef>
#include <string>
#include <list>
#include <android/log.h>
#include "curl/curl.h"
#include "curldroid.h"

#define TAG "CURLDROID"
#define LOGD(...) (__android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__))

static JavaVM *cached_jvm;

// read/write callback
static jmethodID MID_CB_write; // write is really read
static jmethodID MID_CB_read;  // and read is really write
                               // don't be confused

// form Pojo
static jmethodID MID_MultiPart_get_name;
static jmethodID MID_MultiPart_get_filename;
static jmethodID MID_MultiPart_get_content_type;
static jmethodID MID_MultiPart_get_content;

jmethodID findMethod(JNIEnv* env, const char* class_name, const char* method_name, const char* method_signature) {
    jclass cls = env->FindClass(class_name);
    if (cls == NULL) {
       return NULL;
    }

    return env->GetMethodID(cls, method_name, method_signature);
}

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
    // To interactive vs java
    MID_CB_write = env->GetMethodID(cls, "readData", "([B)I");
    if (MID_CB_write == NULL) {
       return JNI_ERR;
    }
    cls = env->FindClass("com/wealoha/libcurldroid/Curl$ReadCallback");
    if (cls == NULL) {
       return JNI_ERR;
    }
    MID_CB_read = env->GetMethodID(cls, "writeData", "([B)I");
    if (MID_CB_read == NULL) {
       return JNI_ERR;
    }
    const char* multipart = "com/wealoha/libcurldroid/easy/MultiPart";
    MID_MultiPart_get_name = findMethod(env, multipart, "getName", "()Ljava/lang/String;");
    if (MID_MultiPart_get_name == NULL) {
        return JNI_ERR;
    }
    MID_MultiPart_get_filename = findMethod(env, multipart, "getFilename", "()Ljava/lang/String;");
    if (MID_MultiPart_get_filename == NULL) {
		return JNI_ERR;
	}
    MID_MultiPart_get_content_type = findMethod(env, multipart, "getContentType", "()Ljava/lang/String;");
    if (MID_MultiPart_get_content_type == NULL) {
    	return JNI_ERR;
	}
    MID_MultiPart_get_content = findMethod(env, multipart, "getContent", "()[B");
    if (MID_MultiPart_get_content == NULL) {
		return JNI_ERR;
	}
    return JNI_VERSION_1_6;
}

JNIEnv* JNU_GetEnv() {
   JNIEnv* env;
   cached_jvm->GetEnv((void **)&env, JNI_VERSION_1_6);
   return env;
}

// keep string reference by libcurl, release after perform
typedef struct {
    jobject obj;
    void* str;
} jobject_str_t;

class Holder {
    CURL* mCurl;
    struct curl_httppost* m_post;
    std::list<jobject> m_j_global_refs;
    std::list<struct curl_slist*> m_slists;
    std::list<jobject_str_t*> m_string_refs;
    std::list<jobject_str_t*> m_byte_array_refs;

    void cleanGlobalRefs() {
        JNIEnv * env = JNU_GetEnv();
        LOGD("clean ref");
        while (!m_j_global_refs.empty()) {
            jobject ref = m_j_global_refs.front();
            LOGD(".");
            env->DeleteGlobalRef(ref);
            m_j_global_refs.pop_front();
        }

        LOGD("clean ref string");
        while (!m_string_refs.empty()) {
            jobject_str_t *ref = m_string_refs.front();
            LOGD(".");
            env->ReleaseStringUTFChars((jstring) ref->obj, (const char*)ref->str);
            env->DeleteGlobalRef(ref->obj);
            free(ref);
            m_string_refs.pop_front();
        }

        LOGD("clean ref byteArray");
		while (!m_byte_array_refs.empty()) {
			jobject_str_t *ref = m_byte_array_refs.front();
			LOGD(".");
			env->ReleaseByteArrayElements((jbyteArray) ref->obj, (jbyte *)ref->str, 0);
			env->DeleteGlobalRef(ref->obj);
			free(ref);
			m_byte_array_refs.pop_front();
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
        m_post = NULL;
    }

    ~Holder() {
        // clear all GlobalRefs avoid memory leak
        cleanGlobalRefs();
        // clear all slists
        cleanSlists();

		if (m_post != NULL) {
			LOGD("clean post form");
			curl_formfree(m_post);
			m_post = NULL;
		}
    }

    CURL* getCurl() {
        return mCurl;
    }

    struct curl_httppost* getPost() {
        return m_post;
    }

    void setPost(struct curl_httppost* post) {
        this->m_post = post;
    }

    // hold GlobalRef
    void addGlobalRefs(jobject obj) {
        m_j_global_refs.push_back(obj);
    }

    void addStringGlobalRefs(jstring string, const char *str) {
        jobject_str_t* ref;
        ref = (jobject_str_t *) malloc(sizeof(jobject_str_t));
        ref->obj = string;
        ref->str = (void*) str;
        m_string_refs.push_back(ref);
    }

    void addByteArrayGlobalRefs(jobject array, const char* str) {
    	jobject_str_t* ref;
    	ref = (jobject_str_t *) malloc(sizeof(jobject_str_t));
		ref->obj = array;
		ref->str = (void*) str;
		m_byte_array_refs.push_back(ref);
    }

    void addCurlSlist(struct curl_slist *slist) {
        m_slists.push_back(slist);
    }

};

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlGlobalInitNative
  (JNIEnv * env, jclass cls, jint flag) {
    curl_global_init((int) flag);
}

JNIEXPORT void JNICALL Java_com_wealoha_libcurldroid_Curl_curlGlobalCleanupNative
  (JNIEnv * env, jclass cls) {
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
    jstring value_ref;
    str = env->GetStringUTFChars(value, 0);
    if (str == 0) {
       return 0;
    }

    result = (int) curl_easy_setopt(curl, (CURLoption) opt, str);
    switch(opt) {
    case CURLOPT_POSTFIELDS:
        // this field not copy data
        // see http://curl.haxx.se/libcurl/c/CURLOPT_POSTFIELDS.html
        value_ref = (jstring) env->NewGlobalRef(value);
        holder->addStringGlobalRefs(value_ref, str);
        break;
    default:
        // free
        env->ReleaseStringUTFChars(value, str);
    }

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

JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_setFormdataNative
  (JNIEnv* env, jobject obj, jlong handle, jobjectArray multi_array) {
    Holder* holder = (Holder*) handle;
    if (holder == NULL) {
        return 0;
    }
    CURL* curl = holder->getCurl();

    struct curl_httppost* post = holder->getPost();;
    struct curl_httppost* last = NULL;
    // clear all
    if (post != NULL) {
        LOGD("clear previous form.");
        curl_formfree(post);
        post = NULL;
    }

    if (multi_array != NULL) {
        LOGD("set name/parts");
        CURLFORMcode code;
        int len = env->GetArrayLength(multi_array);
        for (int i = 0; i < len; i++) {
            LOGD(".");
            jobject part = env->GetObjectArrayElement(multi_array, i);
            jstring name = (jstring) env->CallObjectMethod(part, MID_MultiPart_get_name);
            jstring filename = (jstring) env->CallObjectMethod(part, MID_MultiPart_get_filename);
            jstring content_type = (jstring) env->CallObjectMethod(part, MID_MultiPart_get_content_type);
            jbyteArray content = (jbyteArray) env->CallObjectMethod(part, MID_MultiPart_get_content);
            jbyte* bytes = env->GetByteArrayElements(content, 0);
            int content_length = env->GetArrayLength(content);
            holder->addGlobalRefs(env->NewGlobalRef(content)); // release after perform

            const char* name_str = env->GetStringUTFChars(name, 0);

            // content_type and filename may be null
            if (content_type == NULL && filename == NULL) {
            	code = curl_formadd(&post, &last,
            						CURLFORM_COPYNAME, name_str,
            						CURLFORM_BUFFER, "file.dat",
            						CURLFORM_BUFFERPTR, bytes,
            						CURLFORM_BUFFERLENGTH, content_length,
								    CURLFORM_END);
            } else if (content_type == NULL) {
            	const char* filename_str = env->GetStringUTFChars(filename, 0);
            	code = curl_formadd(&post, &last,
									CURLFORM_COPYNAME, name_str,
									CURLFORM_BUFFER, filename_str,
									CURLFORM_BUFFERPTR, bytes,
									CURLFORM_BUFFERLENGTH, content_length,
									CURLFORM_END);
            	env->ReleaseStringUTFChars(filename, filename_str);
            } else if (filename == NULL) {
            	const char* content_type_str = env->GetStringUTFChars(content_type, 0);
            	code = curl_formadd(&post, &last,
									CURLFORM_COPYNAME, name_str,
									CURLFORM_BUFFER, "file.dat",
									CURLFORM_CONTENTTYPE, content_type_str,
									CURLFORM_BUFFERPTR, bytes,
									CURLFORM_BUFFERLENGTH, content_length,
									CURLFORM_END);
				env->ReleaseStringUTFChars(content_type, content_type_str);
            } else {
            	const char* filename_str = env->GetStringUTFChars(filename, 0);
            	const char* content_type_str = env->GetStringUTFChars(content_type, 0);
            	code = curl_formadd(&post, &last,
									CURLFORM_COPYNAME, name_str,
									CURLFORM_BUFFER, filename_str,
									CURLFORM_CONTENTTYPE, content_type_str,
									CURLFORM_BUFFERPTR, bytes,
									CURLFORM_BUFFERLENGTH, content_length,
									CURLFORM_END);
            	env->ReleaseStringUTFChars(filename, filename_str);
            	env->ReleaseStringUTFChars(content_type, content_type_str);
            }

            env->ReleaseStringUTFChars(name, name_str);
        }

        if (code != CURL_FORMADD_OK) {
        	curl_formfree(post);
        	return (int) code;
        }
    }

    if (post != NULL) {
		curl_easy_setopt(curl, CURLOPT_HTTPPOST, post);
		holder->setPost(post);
    }
}


JNIEXPORT jint JNICALL Java_com_wealoha_libcurldroid_Curl_curlEasyPerformNavite
  (JNIEnv *env, jobject obj, jlong handle) {
    Holder* holder = (Holder*) handle;
    CURL * curl = holder->getCurl();
    return (int) curl_easy_perform(curl);
}
