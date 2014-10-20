#include "NativeColorConverter.h"
#include "FScaler.h"
#include <android/log.h>
#include <errno.h>

#define LOGTAG "RGB2YUVColorConverter"

#define LOGD(...)	__android_log_print(ANDROID_LOG_DEBUG, LOGTAG, __VA_ARGS__);
#define LOGW(...)	__android_log_print(ANDROID_LOG_WARN, LOGTAG, __VA_ARGS__);
#define LOGE(...)	__android_log_print(ANDROID_LOG_ERROR, LOGTAG, __VA_ARGS__);

FScaler * fscaler;
int src_size;
int dst_size;
//FILE * dumpfile;
JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeInit(JNIEnv *env, jobject thiz, jint src_width, jint src_height, jint dst_width, jint dst_height){
    fscaler = new FScaler(src_width, src_height, AV_PIX_FMT_RGBA, dst_width, dst_height, AV_PIX_FMT_NV12);
    src_size = src_width*src_height*4;
    dst_size = dst_width*dst_height*3/2;

    //dumpfile = fopen("/mnt/sdcard/dump.bin", "wb");
    return 0;
}

JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeDeinit(JNIEnv *env, jobject thiz){
    //fclose(dumpfile);
    delete fscaler;
    return 0;
}

JNIEXPORT jint JNICALL Java_com_rd_screencast_RGB2YUVColorConverter_nativeConvert(JNIEnv *env, jobject thiz, jbyteArray rgb, jbyteArray yuv){
    int ret=0;

    jbyte * nativeRGB = (env)->GetByteArrayElements(rgb,0);  
    jbyte * nativeYUV = (env)->GetByteArrayElements(yuv,0);  
    unsigned char *buf;

    LOGD("start convert");
    fscaler->rgb2yuv((unsigned char*)nativeRGB, &buf);
    LOGD("convert done");

    memcpy(nativeYUV, buf, dst_size);
    //fwrite(nativeYUV, 1, dst_size, dumpfile);

    (env)->ReleaseByteArrayElements(rgb, nativeRGB ,JNI_ABORT);
    (env)->ReleaseByteArrayElements(yuv, nativeYUV ,JNI_ABORT);
    return ret;
}

