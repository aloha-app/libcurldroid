LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)

LOCAL_MODULE    := curldroid
LOCAL_SRC_FILES := curldroid.cpp
LOCAL_LDLIBS    := -lm -llog # -ljnigraphics
# shared library
LOCAL_C_INCLUDES := $(LOCAL_PATH) $(LOCAL_PATH)/shared/curl/include
LOCAL_SHARED_LIBRARIES := curl cares
include $(BUILD_SHARED_LIBRARY)

#LOCAL_STATIC_LIBRARIES := curl
#include $(BUILD_SHARED_LIBRARY)


# Add prebuilt libcurl
include $(CLEAR_VARS)

LOCAL_MODULE := curl
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libcurl.so

include $(PREBUILT_SHARED_LIBRARY)

# Add prebuilt libcurl
include $(CLEAR_VARS)

LOCAL_MODULE := cares
LOCAL_SRC_FILES := $(TARGET_ARCH_ABI)/libcares.so

include $(PREBUILT_SHARED_LIBRARY)