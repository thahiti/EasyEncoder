//
//Book:      OpenGL(R) ES 2.0 Programming Guide
//Authors:   Aaftab Munshi, Dan Ginsburg, Dave Shreiner
//ISBN-10:   0321502795
//ISBN-13:   9780321502797
//Publisher: Addison-Wesley Professional
//URLs:      http://safari.informit.com/9780321563835
//         http://www.opengles-book.com
//

//Simple_Texture2D
//
// This is a simple example that draws a quad with a 2D
// texture image. The purpose of this example is to demonstrate 
// the basics of 2D texturing
//
package com.rd.screencast;


import java.io.DataInputStream;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;
import java.util.Arrays;

import android.content.Context;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.util.Log;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLSurfaceView;


public class SimpleTexture2DRenderer implements GLSurfaceView.Renderer
{
	private final String TAG = "2d renderer";

	private int mTextureWidth, mTextureHeight, mWidth, mHeight;
	
	public SimpleTexture2DRenderer(Context context, int width, int height)
	{
		mTextureWidth = 512;
		mTextureHeight = 512;
		mWidth = width;
		mHeight = height;
		
		textureBuffer = new byte[mTextureWidth*mTextureHeight*4];
		pixels = new byte[mWidth*mHeight*4];
		
		mVertices = ByteBuffer.allocateDirect(mVerticesData.length * 4)
				.order(ByteOrder.nativeOrder()).asFloatBuffer();
		mVertices.put(mVerticesData).position(0);
		mIndices = ByteBuffer.allocateDirect(mIndicesData.length * 2)
				.order(ByteOrder.nativeOrder()).asShortBuffer();
		mIndices.put(mIndicesData).position(0);
	}

	private void copyImage(byte[] src, byte[] dst){
		//write byte array first
//		Arrays.fill(dst, (byte) 111);
//		int offsetX=mTextureWidth-mWidth;
//		int offsetY=mTextureHeight-mHeight;
		
		int offsetX = 0;
		int offsetY = 0;
		for(int i=0; i<mHeight; ++i){
			System.arraycopy(src, i*mWidth*4, dst, ((i+offsetY)*mTextureWidth*4)+(offsetX*4), mWidth*4);
//			Log.i(TAG,"copy "+i+"th line");
		}
		
	}
	
	//
	// Create a simple 2x2 texture image with four different colors
	//
	byte [] textureBuffer;
	byte [] pixels;
	ByteBuffer pixelBuffer;

	
	private int createSimpleTexture2D(byte [] pixels )
	{
		// Texture object handle
		int[] textureId = new int[1];

		// 2x2 Image, 3 bytes per pixel (R, G, B)
		//		int width=2;
		//		int height=2;
		//		int frameSize = 4*4;
		//		byte[] pixels = 
		//			{  
		//				127,   0,   0, 127, // Red
		//				0, 127,   0, 127,// Green
		
		Log.i(TAG, "pixel size: "+mWidth+"X"+mHeight+" texture size: "+mTextureWidth+"X"+mTextureHeight);
		
		Arrays.fill(textureBuffer, (byte) 0);
		
		pixelBuffer = ByteBuffer.allocateDirect(mTextureWidth*mTextureHeight*4);
		pixelBuffer.put(textureBuffer).position(0);

		// Use tightly packed data
		GLES20.glPixelStorei ( GLES20.GL_UNPACK_ALIGNMENT, 1 );

		//  Generate a texture object
		GLES20.glGenTextures ( 1, textureId, 0 );

		// Bind the texture object
		GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, textureId[0] );

		//  Load the texture
		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer );

		// Set the filtering mode
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_NEAREST );
		GLES20.glTexParameteri ( GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_NEAREST );


		return textureId[0];        
	}

	///
	// Initialize the shader and program object
	//

	public static int loadShader(int type, String shaderCode){

		// create a vertex shader type (GLES20.GL_VERTEX_SHADER)
		// or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
		int shader = GLES20.glCreateShader(type);

		// add the source code to the shader and compile it
		GLES20.glShaderSource(shader, shaderCode);
		GLES20.glCompileShader(shader);

		return shader;
	}
	
	DataInputStream in;

	/**
	 * Store the model matrix. This matrix is used to move models from object space (where each model can be thought
	 * of being located at the center of the universe) to world space.
	 */
	private float[] mModelMatrix = new float[16];

	/**
	 * Store the view matrix. This can be thought of as our camera. This matrix transforms world space to eye space;
	 * it positions things relative to our eye.
	 */
	private float[] mViewMatrix = new float[16];

	/** Store the projection matrix. This is used to project the scene onto a 2D viewport. */
	private float[] mProjectionMatrix = new float[16];
	
	/** Allocate storage for the final combined matrix. This will be passed into the shader program. */
	private float[] mMVPMatrix = new float[16];
	
	public void onSurfaceCreated(GL10 glUnused, EGLConfig config)
	{
		String filename = "/mnt/sdcard/dump.rgb";
		try {
			in = new DataInputStream(new FileInputStream(filename));
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

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

		String fShaderStr = 
				"precision mediump float;                            \n"
						+ "varying vec2 v_texCoord;                            \n"
						+ "uniform sampler2D s_texture;                        \n"
						+ "uniform vec4 vColor;								   \n"
						+ "void main()                                         \n"
						+ "{                                                   \n"
						+ "  gl_FragColor = texture2D( s_texture, v_texCoord );\n"
						+ "}                                                   \n";

		// Load the shaders and get a linked program object
		//     mProgramObject = ESShader.loadProgram(vShaderStr, fShaderStr);

		int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vShaderStr);
		checkGlError("load vertex shader");
		int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fShaderStr);
		checkGlError("load fragment shader");
		mProgramObject = GLES20.glCreateProgram();     
		checkGlError("create program");// create empty OpenGL ES Program
		Log.i(TAG, "mProgram: "+mProgramObject+" v shader: "+vertexShader+" f shader: "+fragmentShader);

		Log.i(TAG,"attach and link shaders");
		GLES20.glAttachShader(mProgramObject, vertexShader);   // add the vertex shader to program
		checkGlError("attach vertex shader");// create empty OpenGL ES Program
		GLES20.glAttachShader(mProgramObject, fragmentShader); // add the fragment shader to program
		checkGlError("attach fragment shader");// create empty OpenGL ES Program
		GLES20.glLinkProgram(mProgramObject);                  // creates OpenGL ES program executables
		checkGlError("link program");// create empty OpenGL ES Program

		// Get the attribute locations
		mPositionLoc = GLES20.glGetAttribLocation(mProgramObject, "a_position");
		mTexCoordLoc = GLES20.glGetAttribLocation(mProgramObject, "a_texCoord" );

		// Get the sampler location
		mSamplerLoc = GLES20.glGetUniformLocation (mProgramObject, "s_texture" );
		mMVPMatrixHandle = GLES20.glGetUniformLocation(mProgramObject, "u_MVPMatrix");
		// Load the texture
		mTextureId = createSimpleTexture2D(pixels);

		mColorHandle = GLES20.glGetUniformLocation(mProgramObject, "vColor");

		//set model matrix
		Matrix.setIdentityM(mModelMatrix, 0);
		Matrix.translateM(mModelMatrix, 0, 0.0f, 0.0f, 0.0f);

		// Position the eye behind the origin.
		final float eyeX = 0.0f;
		final float eyeY = 0.0f;
		final float eyeZ = 3f;

		// We are looking toward the distance
		final float lookX = 0.0f;
		final float lookY = 0.0f;
		final float lookZ = -1.0f;

		// Set our up vector. This is where our head would be pointing were we holding the camera.
		final float upX = 0.0f;
		final float upY = 1.0f;
		final float upZ = 0.0f;
		//set view matrix
		Matrix.setLookAtM(mViewMatrix, 0, eyeX, eyeY, eyeZ, lookX, lookY, lookZ, upX, upY, upZ);
       
		float left = -1f;
		float right = (mWidth - mTextureWidth/2)/mTextureWidth/2;
		float bottom = -(mHeight - mTextureHeight/2)/mTextureHeight/2;
		float top = 1f;
		float near = 3f;
		float far = 7f;
		
		//set projection matrix
		float ratio = (float) mWidth / mHeight;
//		Matrix.frustumM(mProjectionMatrix, 0, -ratio, ratio, -1, 1, 3, 7);
		Matrix.frustumM(mProjectionMatrix, 0, left, right, bottom, top, near, far);
        
		// This multiplies the view matrix by the model matrix, and stores the result in the MVP matrix
        // (which currently contains model * view).
        Matrix.multiplyMM(mMVPMatrix, 0, mViewMatrix, 0, mModelMatrix, 0);
        
        // This multiplies the modelview matrix by the projection matrix, and stores the result in the MVP matrix
        // (which now contains model * view * projection).
        Matrix.multiplyMM(mMVPMatrix, 0, mProjectionMatrix, 0, mMVPMatrix, 0);
		GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
	}

	// /
	// Draw a triangle using the shader pair created in onSurfaceCreated()
	//
	public void onDrawFrame(GL10 glUnused)
	{
		try {
			int len = in.read(pixels);
			Log.i(TAG, len+ " read for texture");
		}catch(Exception e){}
		
		updateTexture(pixels); 

		// Use the program object
		GLES20.glUseProgram(mProgramObject);
		// Set the viewport
		GLES20.glViewport(0, 0, mWidth, mHeight);
	
		// Clear the color buffer
		GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

		// Load the vertex position
		mVertices.position(0);
		GLES20.glVertexAttribPointer ( mPositionLoc, 3, GLES20.GL_FLOAT, 
				false, 
				5 * 4, mVertices );
		// Load the texture coordinate
		mVertices.position(3);
		GLES20.glVertexAttribPointer ( mTexCoordLoc, 2, GLES20.GL_FLOAT,
				false, 
				5 * 4, 
				mVertices );

		GLES20.glEnableVertexAttribArray ( mPositionLoc );
		GLES20.glEnableVertexAttribArray ( mTexCoordLoc );

		// Bind the texture
		GLES20.glActiveTexture ( GLES20.GL_TEXTURE0 );
		GLES20.glBindTexture ( GLES20.GL_TEXTURE_2D, mTextureId );

		// Set the sampler texture unit to 0
		GLES20.glUniform1i ( mSamplerLoc, 0 );
        GLES20.glUniformMatrix4fv(mMVPMatrixHandle, 1, false, mMVPMatrix, 0);

		GLES20.glDrawElements ( GLES20.GL_TRIANGLES, 6, GLES20.GL_UNSIGNED_SHORT, mIndices );
	}

	/// 
	// Handle surface changes
	//
	public void onSurfaceChanged(GL10 glUnused, int width, int height)
	{
//		mWidth = width;
//		mHeight = height;
	}


	// Handle to a program object
	private int mProgramObject;

	// Attribute locations
	private int mPositionLoc;
	private int mTexCoordLoc;

	// Sampler location
	private int mSamplerLoc;
	private int mMVPMatrixHandle;
	// Texture handle
	private int mTextureId;

	private int mColorHandle;
	
	private FloatBuffer mVertices;
	private ShortBuffer mIndices;

	private final float[] mVerticesData =
		{ 
			-1f, 1f, 0.0f, // Position 0
			0.0f, 0.0f, // TexCoord 0
			-1f, -1f, 0.0f, // Position 1
			0.0f, 1.0f, // TexCoord 1
			1f, -1f, 0.0f, // Position 2
			1.0f, 1.0f, // TexCoord 2
			1f, 1f, 0.0f, // Position 3
			1.0f, 0.0f // TexCoord 3
		};

	private final short[] mIndicesData =
		{ 
			0, 1, 2, 0, 2, 3 
		};

	public static void checkGlError(String glOperation) {
		int error;
		while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
			throw new RuntimeException(glOperation + ": glError " + error);
		}
	}

	public int updateTexture(byte[] pixels){
		// Texture object handle

		pixelBuffer.clear();
		copyImage(pixels, textureBuffer);
		pixelBuffer.put(textureBuffer).position(0);

		//  Load the texture
		GLES20.glTexImage2D ( GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA, mTextureWidth, mTextureHeight, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, pixelBuffer );

		//
		return mTextureId;
	}
}