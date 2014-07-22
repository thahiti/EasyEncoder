package com.rd.screencast;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaCodecList;
import android.media.MediaFormat;
import android.util.Log;

public class EasyYUVEncoder {
	final static String TAG = "EasyYUVEncoder";

	private MediaCodec mMediaCodec;
	
	private int mWidth;
	private int mHeight;
	
	private int mBitrate, mFramerate, mKeyFrameInterval;
	private byte[] mSps;
	private byte[] mPps;
	
	private ByteBuffer[] mInputBuffers;
	private ByteBuffer[] mOutputBuffers;
	
	private long ts=0;
	
	private static boolean isIFrame(int hdr) {
		return 0x05 == (hdr & 0x1F);
	}
	
	long mFrameCount;
	
	public EasyYUVEncoder(int width, int height, int framerate, int keyFrameInterval) {
		mWidth = width;
		mHeight = height;
		mFramerate = framerate;
		mKeyFrameInterval = keyFrameInterval;
		mFrameCount = 0;
		open();
	}
	
	public interface EncodedFrameListener {
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
	}
	
	public void offerEncoder(byte[] input) {
		mInputBuffers = mMediaCodec.getInputBuffers();
		int inputBufferIndex = mMediaCodec.dequeueInputBuffer(-1);
		 
		if (inputBufferIndex >= 0) {
			ByteBuffer inputBuffer = mInputBuffers[inputBufferIndex];
//			Log.i(TAG, "buffer index: "+inputBufferIndex+" size: "+inputBuffer.capacity()+ " input size: "+input.length);
			inputBuffer.clear();
			inputBuffer.put(input);
			mMediaCodec.queueInputBuffer(inputBufferIndex, 0, input.length, ts+=300000, 0);
		}
		
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
		MediaCodecInfo codecInfo = selectCodec("video/avc");
		int colorFormat = selectColorFormat(codecInfo, "video/avc");
//		int colorFormat = MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar;

		mBitrate = mWidth*mHeight*2*mFramerate*8/150;
		mMediaCodec = MediaCodec.createEncoderByType("video/avc");
		MediaFormat mediaFormat = MediaFormat.createVideoFormat("video/avc", mWidth, mHeight);
		mediaFormat.setInteger(MediaFormat.KEY_BIT_RATE, mBitrate);
		mediaFormat.setInteger(MediaFormat.KEY_FRAME_RATE, mFramerate);
		mediaFormat.setInteger(MediaFormat.KEY_COLOR_FORMAT, colorFormat);
		mediaFormat.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, mKeyFrameInterval);

		
		try{
			mMediaCodec.configure(mediaFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
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

//	private long computePresentationTimeNsec(long frameIndex) {
//		final long ONE_BILLION = 1000000000;
//		return frameIndex * ONE_BILLION / mFramerate;
//	}
	
    private static MediaCodecInfo selectCodec(String mimeType) {
        int numCodecs = MediaCodecList.getCodecCount();
        for (int i = 0; i < numCodecs; i++) {
            MediaCodecInfo codecInfo = MediaCodecList.getCodecInfoAt(i);
            if (!codecInfo.isEncoder()) {
                continue;
            }
            String[] types = codecInfo.getSupportedTypes();
            for (int j = 0; j < types.length; j++) {
                if (types[j].equalsIgnoreCase(mimeType)) {
                    return codecInfo;
                }
            }
        }
        return null;
    }
    
    private static boolean isRecognizedFormat(int colorFormat) {
        switch (colorFormat) {
            // these are the formats we know how to handle for this test
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Planar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420SemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420PackedSemiPlanar:
            case MediaCodecInfo.CodecCapabilities.COLOR_TI_FormatYUV420PackedSemiPlanar:
            	
                return true;
            default:
                return false;
        }
    }
    
    private static int selectColorFormat(MediaCodecInfo codecInfo, String mimeType) {
        MediaCodecInfo.CodecCapabilities capabilities = codecInfo.getCapabilitiesForType(mimeType);
        for (int i = 0; i < capabilities.colorFormats.length; i++) {
            int colorFormat = capabilities.colorFormats[i];
            if (isRecognizedFormat(colorFormat)) {
                return colorFormat;
            }
        }
        Log.e(TAG, "couldn't find a good color format for " + codecInfo.getName() + " / " + mimeType);
        return 0;   // not reached
    }



}