LOCAL_PATH := $(call my-dir)

include $(CLEAR_VARS)
LOCAL_MODULE            := libavutil
LOCAL_SRC_FILES         := ffmpeg/libavutil/libavutil-52.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libswscale
LOCAL_SRC_FILES         := ffmpeg/libswscale/libswscale-2.so
include $(PREBUILT_SHARED_LIBRARY)

include $(CLEAR_VARS)
LOCAL_MODULE            := libx264
LOCAL_SRC_FILES         := x264/libx264.so
include $(PREBUILT_SHARED_LIBRARY)
	
include $(CLEAR_VARS)

LOCAL_SRC_FILES += NativeColorConverter.cpp\
				   FScaler.cpp

LOCAL_C_INCLUDES := $(LOCAL_PATH)/ffmpeg

LOCAL_SHARED_LIBRARIES := libavutil\
						  libswscale\
						  libx264

LOCAL_CFLAGS = -D__STDC_CONSTANT_MACROS 
LOCAL_MODULE_TAGS := eng 
LOCAL_PRELINK_MODULE:=false
LOCAL_LDLIBS := -llog -ljnigraphics -lz -landroid
LOCAL_MODULE    := fcolorconverter

include $(BUILD_SHARED_LIBRARY)
