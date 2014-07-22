package com.rd.screencast;

public class RGB2YUVColorConverter {
	static {
		System.loadLibrary("fcolorconverter");
	}
	
	byte[] mYUVBuffer;
	
	public RGB2YUVColorConverter(int width, int height) {
		nativeInit(width, height, width, height);
		mYUVBuffer = new byte[width*height*3/2];
	}
	
	public void release(){
		nativeDeinit();
	}
	
	public byte [] Convert(byte [] rgb){
		nativeConvert(rgb, mYUVBuffer);
		return mYUVBuffer;
	}
	
	private native int nativeInit(int srcWidth, int srcHeight, int dstWidth, int dstHeight);
	private native int nativeDeinit();
	private native int nativeConvert(byte[] rgb, byte[] yuv);
	
}
