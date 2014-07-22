LOCAL_PATH := $(call my-dir)
FFMPEG := ../../ffmpeg
X264 := ../../x264

include $(CLEAR_VARS)
LOCAL_MODULE            := libavutil
LOCAL_SRC_FILES         := $(FFMPEG)/output/libavutil-52.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libswscale
LOCAL_SRC_FILES         := $(FFMPEG)/output/libswscale-2.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libx264
LOCAL_SRC_FILES         := $(X264)/output/libx264.so
include $(PREBUILT_SHARED_LIBRARY)
	
include $(CLEAR_VARS)

LOCAL_SRC_FILES += NativeColorConverter.cpp\
				   FScaler.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/$(FFMPEG)

LOCAL_SHARED_LIBRARIES := libavutil\
						  libswscale\
						  libx264

LOCAL_CFLAGS = -D__STDC_CONSTANT_MACROS 
LOCAL_MODULE_TAGS := eng 
LOCAL_PRELINK_MODULE:=false
LOCAL_LDLIBS := -llog -ljnigraphics -lz -landroid
LOCAL_MODULE    := fcolorconverter

include $(BUILD_SHARED_LIBRARY)
