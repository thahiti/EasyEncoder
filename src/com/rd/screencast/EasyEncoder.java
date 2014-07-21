package com.rd.screencast;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;

import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaFormat;
import android.util.Log;
import android.view.Surface;

public class EasyEncoder {
	final static String TAG = "EasyEncoder";

	private MediaCodec mMediaCodec;
	
	private int mWidth;
	private int mHeight;
	private Surface mSurface;
	
	private int mBitrate, mFramerate, mKeyFrameInterval;
	private byte[] mSps;
	private byte[] mPps;
	private ByteBuffer[] mOutputBuffers;
	
	boolean glInited=false;
	private GLColorConverter mColorConverter;
	
	private static boolean isIFrame(int hdr) {
		return 0x05 == (hdr & 0x1F);
	}
	
	long mFrameCount;
	
	public EasyEncoder(int width, int height, int framerate, int keyFrameInterval) {
		mWidth = width;
		mHeight = height;
		mFramerate = framerate;
		mKeyFrameInterval = keyFrameInterval;
		mFrameCount = 0;
		open();
	}
	
	interface EncodedFrameListener {
		public void frameReceived(byte[] data, long timestamp);
		public void avcParametersSetsEstablished(byte[] sps, byte[] pps);
	}

	private EncodedFrameListener mFrameListener;

	public void setEncodedFrameListener(EncodedFrameListener listener){
		mFrameListener = listener;
	}

	public void close(){
		mMediaCodec.stop();
		mMediaCodec.release();
		if(true == glInited)
		mColorConverter.release();
		glInited = false;
	}
	
	public void offerEncoder(byte[] input) {
		
		if(false == glInited){
			mColorConverter = new GLColorConverter(mSurface, mWidth, mHeight);
			mColorConverter.surfaceCreated(); 
			glInited = true;
		}
		
		mColorConverter.drawFrame(input,computePresentationTimeNsec(mFrameCount++));

		MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
		mOutputBuffers = mMediaCodec.getOutputBuffers();
		int outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);

		while (outputBufferIndex >= 0) {
			//Log.i(TAG, "get output buffer idx: "+outputBufferIndex+" offset: "+bufferInfo.offset+" ts: "+bufferInfo.presentationTimeUs+" flags: "+bufferInfo.flags);
			ByteBuffer outputBuffer = mOutputBuffers[outputBufferIndex];
			byte[] outData = new byte[bufferInfo.size];

			outputBuffer.get(outData);

			if (mSps != null && mPps != null) {
				printBufferHead(outData);
				
				//어떤 상황에서는 아래 주석처럼 nal start pattern 대신 사이즈를 적어야 함.
				//ByteBuffer frameBuffer = ByteBuffer.wrap(outData);
				//frameBuffer.putInt(bufferInfo.size - 4);

				if(null != mFrameListener){
					if(isIFrame(outData[4])){
						mFrameListener.frameReceived(mSps, 0);
						mFrameListener.frameReceived(mPps, 0);
					}
					mFrameListener.frameReceived(outData, outData.length);
				}
			} else {
				Log.i(TAG,"sps pps generated");
				printBufferHead(outData);
				separateAndStoreSpsPps(outData);


				if (null != mFrameListener) { 
					mFrameListener.avcParametersSetsEstablished(mSps, mPps);
				}

				printBufferHead(mSps);
				printBufferHead(mPps);
			}
			
			mMediaCodec.releaseOutputBuffer(outputBufferIndex, false);
			outputBufferIndex = mMediaCodec.dequeueOutputBuffer(bufferInfo, 0);
		}
	}
	
	private void open(){
		mBitrate = mWidth*mHeight*2*mFramerate*8/150;
		 
		mMediaCodec = MediaCodec.createEncoderByType("video/avc");
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFramerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mKeyFrameInterval);
		
		try{
			mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
			mSurface = mMediaCodec.createInputSurface();
			mMediaCodec.start();
		}catch(IllegalStateException e){
			e.printStackTrace();
		}
		
		mOutputBuffers = mMediaCodec.getOutputBuffers();

		Log.i(TAG,"Codec Started with bitrate: "+mBitrate+" framerate: "+mFramerate+" key frame interval: "+mKeyFrameInterval);
	}
	
	private boolean separateAndStoreSpsPps(byte [] buffer){
		int ppsIndex = 0;

		ByteBuffer spsPpsBuffer = ByteBuffer.wrap(buffer);
		
		//check nall start pattern
		if (spsPpsBuffer.getInt() == 0x00000001) {
		}else{
			return false;
		}
		
		//find pps start position
		try{
			while(!(spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x00 && spsPpsBuffer.get() == 0x01)) {}
		}catch(BufferUnderflowException e){
			return false;
		}
		
		ppsIndex = spsPpsBuffer.position()-4/*length of start pattern*/;
		
		mSps = new byte[ppsIndex];
		System.arraycopy(buffer, 0, mSps, 0, mSps.length);
		mPps = new byte[buffer.length - ppsIndex];
		System.arraycopy(buffer, ppsIndex, mPps, 0, mPps.length);
		
		return true;
	}
	
	private void printBufferHead(byte [] data){
		String s = new String("");
		for(int i=0; i<20 && i<data.length; ++i){
			s += String.format(" %02X", data[i]);
		}
		Log.i(TAG, "ecoded data: "+s+" size: "+ data.length);
	}

	private long computePresentationTimeNsec(long frameIndex) {
		final long ONE_BILLION = 1000000000;
		return frameIndex * ONE_BILLION / mFramerate;
	}


}