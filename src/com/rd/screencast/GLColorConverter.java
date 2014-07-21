package com.rd.screencast;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

import android.opengl.GLES20;
import android.util.Log;
import android.opengl.Matrix;
import android.view.Surface;

public class GLColorConverter{ 
	private static final String TAG = "GLColorConverter";
	private static final int MIN_TEXTURE_SIZE = 256; 
	private int mTextureWidth, mTextureHeight, mWidth, mHeight;
	
	private EGLController eglController;
	
	//buffer for texture
	ByteBuffer pixelBuffer;

	//Matrix for world->view->projection transform.
	private float[] mModelMatrix = new float[16];
	private float[] mViewMatrix = new float[16];
	private float[] mProjectionMatrix = new float[16];
	private float[] mMVPMatrix = new float[16];

	//Handle to a program, attributes, texture, mvp matrix
	private int mProgramObject;
	private int mPositionLoc;
	private int mTexCoordLoc;
	private int mSamplerLoc;
	private int mTextureId;
	private int mMVPMatrixHandle;

	private FloatBuffer mVertices;
	private ShortBuffer mIndices;

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
	
	public GLColorConverter(Surface surface, int width, int height) {
		if (surface == null) {
			throw new NullPointerException();
		}

		eglController = new EGLController(surface);

		mWidth = width;
		mHeight = height;

		mTextureWidth = decideTextureSize();
		mTextureHeight = decideTextureSize();
		
		mVertices = ByteBuffer.allocateDirect(mVerticesData.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
		mVertices.put(mVerticesData).position(0);
		
		mIndices = ByteBuffer.allocateDirect(mIndicesData.length * 2).order(ByteOrder.nativeOrder()).asShortBuffer();
		mIndices.put(mIndicesData).position(0);
	}

	public void release(){
		eglController.release();
	}
	
	//create texture object.
	private int createTextureObject()
	{
		Log.i(TAG, "pixel size: "+mWidth+"X"+mHeight+" texture size: "+mTextureWidth+"X"+mTextureHeight);

		int[] textureId = new int[1];
		pixelBuffer = ByteBuffer.allocateDirect(mTextureWidth*mTextureHeight*4);

		GLES20.glPixelStorei ( GLES20.GL_UNPACK_ALIGNMENT, 1 );
		GLES20.glGenTextures ( 1, textureId, 0 );
		GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, textureId[0] );
		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer );
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST );
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST );
		Log.i(TAG,"Texture ID: "+textureId[0]);
		return textureId[0];        
	}

	public static int loadShader(int type, String shaderCode){
		int shader = GLES20.glCreateShader(type); 
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);
		return shader;
	}

	public void surfaceCreated()
	{
		eglController.makeCurrent();
		
		//Vertex Shader.
		String vShaderStr =
				"attribute vec4 a_position;   \n"
						+ "uniform mat4 u_MVPMatrix;    \n"
						+ "attribute vec2 a_texCoord;   \n"
						+ "varying vec2 v_texCoord;     \n"
						+ "void main()                  \n"
						+ "{                            \n"
						+ "   gl_Position = u_MVPMatrix * a_position; \n"
						+ "   v_texCoord = a_texCoord;  \n"
						+ "}                            \n";
		//Fragment Shader
		String fShaderStr = 
				"precision mediump float;                            \n"
						+ "varying vec2 v_texCoord;                            \n"
						+ "uniform sampler2D s_texture;                        \n"						
						+ "void main()                                         \n"
						+ "{                                                   \n"
						+ "  gl_FragColor = texture2D( s_texture, v_texCoord );\n"
						+ "}                                                   \n";

		
		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vShaderStr);
		checkGlError("load vertex shader");
		int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fShaderStr);
		checkGlError("load fragment shader");
		
		mProgramObject = GLES20.glCreateProgram();     
		checkGlError("create program");
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
		mSamplerLoc = GLES20.glGetUniformLocation (mProgramObject, "s_texture" );
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramObject, "u_MVPMatrix");
		
		//create the texture
		mTextureId = createTextureObject();

		//set model matrix
		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);

		//Prepare view transform matrix
		final float eyeX = 0.0f, eyeY = 0.0f, eyeZ = 3f;
		final float lookX = 0.0f, lookY = 0.0f, lookZ = -1.0f;
		final float upX = 0.0f, upY = 1.0f, upZ = 0.0f;
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);

		//Prepare projection transform matrix
		float left = -1f;
		float right = -((float)mTextureWidth/2-mWidth)/((float)mTextureWidth/2);
		float bottom = ((float)mTextureHeight/2-mHeight)/((float)mTextureHeight/2);
		float top = 1f;
		float near = 1f;
		float far = 10f;		
		Matrix.orthoM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
        
		//Prepare Model x View x Projection transform matrix.
		Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	}

	public void drawFrame(byte [] input, long timestamp)
	{
		eglController.makeCurrent();
		eglController.setPresentationTime(timestamp);
		
		updateTexture(input);  

		GLES20.glUseProgram(mProgramObject);
		GLES20.glViewport(0, 0, mWidth, mHeight);
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		mVertices.position(0);
		GLES20.glVertexAttribPointer ( mPositionLoc, 3, GLES20.GL_FLOAT, false, 5 * 4, mVertices );
		mVertices.position(3);
		GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT, false, 5 * 4, mVertices );

		GLES20.glEnableVertexAttribArray ( mPositionLoc );
		GLES20.glEnableVertexAttribArray ( mTexCoordLoc );
 
		// Bind the texture
		GLES20.glActiveTexture ( GLES20.GL_TEXTURE0 );
		GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, mTextureId );

		// Set the sampler texture unit to 0
		GLES20.glUniform1i ( mSamplerLoc, 0 );
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

		GLES20.glDrawElements ( GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, mIndices );
		
		eglController.swapBuffers();
	}


	public static void checkGlError(String glOperation) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			Log.e(TAG, glOperation + ": glError " + error);
			throw new RuntimeException(glOperation + ": glError " + error);
		}
	}

	public int updateTexture(byte[] rgbFrame){
		pixelBuffer.clear();
		for(int i=0; i<mHeight; ++i){
			pixelBuffer.put(rgbFrame, i*mWidth*4, mWidth*4);
			pixelBuffer.position(i*mTextureWidth*4);
		}
	    pixelBuffer.position(0);  
		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer );
		return mTextureId;
	}
	
	private int decideTextureSize(){
		int size = MIN_TEXTURE_SIZE;
		int biggerNum = (mWidth > mHeight) ? mWidth : mHeight;
		while(biggerNum>size){size*=2;}
		return size;
	}
}