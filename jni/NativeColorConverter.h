#include <jni.h>
#ifndef __com_rd_screencast_ColorConverter__
#define __com_rd_screencast_ColorConverter__
#ifdef __cplusplus
extern "C" {
#endif

JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeInit(JNIEnv *, jobject, jint, jint, jint, jint);
JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeDeinit(JNIEnv *, jobject);
JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeConvert(JNIEnv *, jobject, jbyteArray, jbyteArray);

#ifdef __cplusplus
}
#endif
#endif

