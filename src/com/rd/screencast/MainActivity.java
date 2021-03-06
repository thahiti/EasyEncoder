package com.rd.screencast;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.Fragment;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Looper;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

public class MainActivity extends Activity {

	private final String TAG = "Encoding tester";
	private GLSurfaceView mGLView;

	private String avcDumpFilePath = "/dump.h264";
	private String rgbInputFilePath = "/maroon5_small_short.rgb";
	private int width = 320;
	private int height = 240;

	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mGLView = new MyGLSurfaceView(this);
		setContentView(mGLView);
	}  

	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(R.layout.fragment_main, container,
					false);
			return rootView;
		}
	}

	@Override
	protected void onStart() {

		videoFrameHandlerThread = new HandlerThread("Video frame thread");
		videoFrameHandlerThread.start();
		videoFrameHandler = new VideoFrameHandler(videoFrameHandlerThread.getLooper());

		videoFrameHandler.post(new Runnable() {
			public void run() {
				startTest();
			}
		});

		// TODO Auto-generated method stub
		super.onStart();
	}

	private static class VideoFrameHandler extends Handler{
		public VideoFrameHandler(Looper looper){
			super(looper);
		}
	}

	private HandlerThread videoFrameHandlerThread;
	private VideoFrameHandler videoFrameHandler;

	FileOutputStream outputStream;
	DataInputStream inputStream;
	int frameSize;
	byte[] buf;
	
	void startTest(){
		Log.i(TAG, "start test");
		frameSize = width*height*4;
		buf = new byte[frameSize];
		int len=0;
		if(null == outputStream){
			try {
				avcDumpFilePath = Environment.getExternalStorageDirectory().getPath() + avcDumpFilePath;
				rgbInputFilePath = Environment.getExternalStorageDirectory().getPath() + rgbInputFilePath;
				
				Log.i(TAG, String.format("encoding start for input: %s output: %s", rgbInputFilePath, avcDumpFilePath));
				
				outputStream = new FileOutputStream(new File(avcDumpFilePath));
				inputStream = new DataInputStream(new FileInputStream(rgbInputFilePath));

			} catch (FileNotFoundException e) {
				e.printStackTrace();
				Log.i(TAG, "File not found exit test");
				return;
			}
		}
  
//		EasyRGBEncoder easyEncoder = new EasyRGBEncoder(width,height,60,2);
//		easyEncoder.setEncodedFrameListener(new EasyRGBEncoder.EncodedFrameListener() { 
//			public void writeFrame(byte[] data){
//				try{  
//					outputStream.write(data);
//				}catch( Exception e){  
//					e.printStackTrace();
//				} 
//			}
//			
//			public void frameReceived(byte[] data, long timestamp) {
//				writeFrame(data); 
//			}
//
//			public void avcParametersSetsEstablished(byte[] sps, byte[] pps) {
//			}
//		});
		
		RGB2YUVColorConverter converter = new RGB2YUVColorConverter(width,  height);
		EasyYUVEncoder easyEncoder = new EasyYUVEncoder(width,height,60,2);
		easyEncoder.setEncodedFrameListener(new EasyYUVEncoder.EncodedFrameListener() {
			public void writeFrame(byte[] data){ 
				try{
					outputStream.write(data);  
				}catch( Exception e){  
					e.printStackTrace();
				}
			}
			
			public void frameReceived(byte[] data, long timestamp) {
				writeFrame(data);  
			}  

			public void avcParametersSetsEstablished(byte[] sps, byte[] pps) {
			}  
		});
		
		((MyGLSurfaceView)mGLView).setSourceSize(width, height);
		while(true){ 
			try {
				len = inputStream.read(buf);
				((MyGLSurfaceView)mGLView).updatePicture(buf);
				runOnUiThread(new Runnable(){
					public void run() {
						mGLView.requestRender();
					}
				});
				
			}catch(Exception e){}  
			
			if(len > 0){
				byte[] yuv = converter.Convert(buf);
				easyEncoder.offerEncoder(yuv);
			}else{
				break;
			}
		}
 
		try {
			outputStream.close();
			inputStream.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
		
		Log.i(TAG, "test done");
	}
}
