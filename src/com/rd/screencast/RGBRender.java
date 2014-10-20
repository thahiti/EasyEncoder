package com.rd.screencast;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;


public class RGBRender implements GLSurfaceView.Renderer
{
	private Object lock;
	private static final String TAG = "rgb renderer";
	private static final int MIN_TEXTURE_SIZE = 256; 
	private int mTextureWidth, mTextureHeight, mSourceWidth, mSourceHeight, mSurfaceWidth, mSurfaceHeight;

	//Matrix for world->view->projection transform.
	private float[] mRotationMatrix = new float[16];
	private float[] mModelMatrix = new float[16];
	private float[] mViewMatrix = new float[16];
	private float[] mProjectionMatrix = new float[16];
	private float[] mMVPMatrix = new float[16];

	//Handle to a program, attributes, texture, mvp matrix
	private int mProgramObject;
	private int mPositionLoc;
	private int mTexCoordLoc;
	
	//buffer for texture
	ByteBuffer pixelBuffer;
	private int mSamplerLoc;
	private int mTextureId;
	int[] textureId = new int[1];

	private int mMVPMatrixHandle;

	private FloatBuffer mVertices;
	private ShortBuffer mIndices;

	private boolean textureCreated, needTextureCreation, pictureUpdated;

	private int vertexShader;
	private int fragmentShader;

//	private int mColorHandle;
	float color[] = { 0.2f, 0.709803922f, 0.898039216f, 0.0f };
	
	private final float[] mVerticesData ={ 
			-1f, 1f, 0.0f, // Position 0
			0.0f, 0.0f, // TexCoord 0
			-1f, -1f, 0.0f, // Position 1
			0.0f, 1.0f, // TexCoord 1
			1f, -1f, 0.0f, // Position 2
			1.0f, 1.0f, // TexCoord 2
			1f, 1f, 0.0f, // Position 3
			1.0f, 0.0f // TexCoord 3
	};

	private final short[] mIndicesData ={ 0, 1, 2, 0, 2, 3 };
	public RGBRender(Context context)
	{
		mSourceWidth = 0;
		mSourceHeight = 0;


		mVertices = ByteBuffer.allocateDirect(mVerticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mVertices.put(mVerticesData).position(0);

		mIndices = ByteBuffer.allocateDirect(mIndicesData.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
		mIndices.put(mIndicesData).position(0);

		lock = new Object();
		textureCreated = false;
		needTextureCreation = false;
		pictureUpdated = false;
	}

	public void setSourceSize(int width, int height){
		synchronized (lock) {

			Log.i(TAG, String.format("source size set: %d x %d", width, height));
			mSourceWidth = width;
			mSourceHeight = height;

			mTextureWidth = decideTextureSize();
			mTextureHeight = decideTextureSize();
//			pixelBuffer = ByteBuffer.allocateDirect(mTextureWidth*mTextureHeight*4);
			pixelBuffer = ByteBuffer.allocateDirect(mSourceWidth*mSourceHeight*4);
			needTextureCreation = true;
		}
	}
	
	public void release(){
//		deleteTexture();
		GLES20.glDeleteShader(vertexShader);
		GLES20.glDeleteShader(fragmentShader);
		GLES20.glDeleteProgram(mProgramObject);
	}

	public static int loadShader(int type, String shaderCode){
		int shader = GLES20.glCreateShader(type);
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);
		return shader;
	}

	public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
	{
		String vShaderStr =
				"attribute vec4 a_position;   				\n"
						+ "uniform mat4 u_MVPMatrix;    				\n"
						+ "attribute vec2 a_texCoord;   				\n"
						+ "varying vec2 v_texCoord;     				\n"
						+ "void main()                 					\n"
						+ "{                            				\n"
						+ "   gl_Position = u_MVPMatrix * a_position; 	\n"
						+ "   v_texCoord = a_texCoord;  				\n"
						+ "}                            				\n";

		//Fragment Shader
		String fShaderStr = 
				"precision mediump float;                            		\n"
						+ "varying vec2 v_texCoord;                            		\n"
						+ "uniform sampler2D s_texture;                        		\n"
						+ "uniform vec4 vColor;		 						    	\n" 		
						+ "void main()                                         		\n"
						+ "{                                                   		\n"
//						+ "  gl_FragColor = vColor;\n"
						+ "  gl_FragColor = texture2D(s_texture, v_texCoord);\n"
						+ "}                                                   		\n";

		mProgramObject = GLES20.glCreateProgram();     
		checkGlError("create program");
		
		vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vShaderStr);
		checkGlError("load vertex shader");
		fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fShaderStr);
		checkGlError("load fragment shader");

		Log.i(TAG, "mProgram: "+mProgramObject+" v shader: "+vertexShader+" f shader: "+fragmentShader);

		Log.i(TAG,"attach and link shaders");
		GLES20.glAttachShader(mProgramObject, vertexShader);   
		checkGlError("attach vertex shader");
		GLES20.glAttachShader(mProgramObject, fragmentShader); 
		checkGlError("attach fragment shader");
		GLES20.glLinkProgram(mProgramObject);  
		checkGlError("link program");

		// Get the attribute locations
		mPositionLoc = GLES20.glGetAttribLocation(mProgramObject, "a_position");
		mTexCoordLoc = GLES20.glGetAttribLocation(mProgramObject, "a_texCoord" );

		// Get the sampler location
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramObject, "u_MVPMatrix");

//		mColorHandle = GLES20.glGetUniformLocation(mProgramObject, "vColor");
		
		textureCreated = false;
		Log.i(TAG, "surface created");
	}

	//create texture object.
	private void createTextureObject()
	{
		Log.i(TAG, "create texture pixel size: "+mSourceWidth+"X"+mSourceHeight+" texture size: "+mTextureWidth+"X"+mTextureHeight);



		GLES20.glPixelStorei ( GLES20.GL_UNPACK_ALIGNMENT, 1 );
		GLES20.glGenTextures ( 1, textureId, 0 );
		mSamplerLoc = GLES20.glGetUniformLocation (mProgramObject, "s_texture" );
		mTextureId = textureId[0];
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, textureId[0] );
		GLES20.glUniform1i ( mSamplerLoc, 0 ); 
		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null );
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST );
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST );


		textureCreated = true;
		needTextureCreation = false;

		Log.i(TAG, "create texture is done");
	}

	private void prepareModelViewProjectionMatrix(){
		//set model matrix 
		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);
		//		Matrix.setRotateM(mRotationMatrix, 0, -90f, 0, 0, 1.0f);
		Matrix.setRotateM(mRotationMatrix, 0, 0f, 0, 0, 1.0f);

		//Prepare view transform matrix
		final float eyeX = 0.0f, eyeY = 0.0f, eyeZ = 3f;
		final float lookX = 0.0f, lookY = 0.0f, lookZ = -1.0f;
		final float upX = 0.0f, upY = 1.0f, upZ = 0.0f;
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);


		//Prepare projection transform matrix
		float left = -1f;
		float right = -((float)mTextureWidth/2-mSourceWidth)/((float)mTextureWidth/2);
		float top = 1f;
		float bottom = ((float)mTextureHeight/2-mSourceHeight)/((float)mTextureHeight/2);

		/*		float rotate_left =  bottom+(float)0.005;
		float rotate_right = 1f;
		float rotate_top = 1f;
		float rotate_bottom = -right;*/

		float near = 1f;
		float far = 10f;

		//		Matrix.orthoM(mProjectionMatrix, 0, rotate_left, rotate_right, rotate_bottom, rotate_top, near, far);
		Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, near, far);


		//Prepare Model x View x Projection transform matrix.
		Matrix.multiplyMM(mModelMatrix, 0, mRotationMatrix, 0, mModelMatrix, 0);
		Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
		Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	}

	private void deleteTexture(){
		GLES20.glDeleteTextures(3, textureId, 0);
	}

	private void applyTexture(){
		pixelBuffer.position(0);  
		GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
		GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, mTextureId);
		GLES20.glUniform1i (mSamplerLoc, 0);
//		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer );
		GLES20.glTexSubImage2D(GLES20.GL_TEXTURE_2D, 0, 0, 0, mSourceWidth, mSourceHeight, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer);
	}

	public void onDrawFrame(GL10 glUnused)
	{
		Log.i(TAG, "on draw invoked");
		synchronized (lock) {
            GLES20.glUseProgram(mProgramObject); 
			if(needTextureCreation){
				Log.i(TAG,"texture initialize needed");
				if(textureCreated){
					Log.i(TAG,"reboot texture unit");
					deleteTexture();
					textureCreated = false;
				}
				createTextureObject();
				prepareModelViewProjectionMatrix();
			}

			if(textureCreated & pictureUpdated){
				Log.i(TAG,"draw by texture");
				GLES20.glClearColor(0.0f, 0.0f, 0.0f, 1.0f);

				//following cause GLES error code, 502
				GLES20.glViewport(0, 0, mSurfaceWidth, mSurfaceHeight);
				GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

				mVertices.position(0);
				GLES20.glVertexAttribPointer ( mPositionLoc, 3, GLES20.GL_FLOAT, false, 5 * 4, mVertices );
				mVertices.position(3);
				GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT, false, 5 * 4, mVertices );

				GLES20.glEnableVertexAttribArray ( mPositionLoc );
				GLES20.glEnableVertexAttribArray ( mTexCoordLoc );

				applyTexture();

				GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);
				GLES20.glDrawElements ( GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, mIndices );
				pictureUpdated = false;
			}
		}
	}

	public void onSurfaceChanged(GL10 glUnused, int width, int height)
	{
		mSurfaceWidth = width;
		mSurfaceHeight = height; 
	}

	public static void checkGlError(String glOperation) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, glOperation + ": glError " + error);
		}
	}

	public void updatePicture(byte[] rgbFrame){
		synchronized (lock) {
			if(textureCreated){
//				pixelBuffer.clear();
//				for(int i=0; i<mSourceHeight; ++i){
//					pixelBuffer.put(rgbFrame, i*mSourceWidth*4, mSourceWidth*4);
//					pixelBuffer.position(i*mTextureWidth*4);
//				}
//				pixelBuffer.position(0);
				pixelBuffer.position(0);
				pixelBuffer.put(rgbFrame);
				pictureUpdated = true;
			}
		}
	}
	
	private int decideTextureSize(){
		int size = MIN_TEXTURE_SIZE;
		int biggerNum = (mSourceWidth > mSourceHeight) ? mSourceWidth : mSourceHeight;
		while(biggerNum>size){size*=2;}
		return size;
	}
}