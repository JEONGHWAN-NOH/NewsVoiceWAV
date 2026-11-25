package com.newsvoice.tts;

import android.graphics.Bitmap;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLExt;
import android.opengl.EGLSurface;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.view.Surface;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

/** Renders 9:16 Android Bitmaps onto an H.264 encoder input surface with explicit PTS. */
final class EglFrameRenderer implements AutoCloseable {
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface window=EGL14.EGL_NO_SURFACE;
    private final Surface target;
    private final int program,texture,posSlot,texSlot,zoomSlot;
    private int textureWidth=-1,textureHeight=-1;
    private final FloatBuffer positions,texCoords;
    private static final String VERTEX="attribute vec4 aPos; attribute vec2 aTex; varying vec2 vTex; void main(){gl_Position=aPos;vTex=aTex;}";
    private static final String FRAGMENT="precision mediump float; varying vec2 vTex; uniform sampler2D uTex; uniform float uZoom;void main(){vec2 uv=(vTex-vec2(0.5))/uZoom+vec2(0.5);gl_FragColor=texture2D(uTex,uv);}";
    private static FloatBuffer floats(float[] v){FloatBuffer b=ByteBuffer.allocateDirect(v.length*4).order(ByteOrder.nativeOrder()).asFloatBuffer();b.put(v).position(0);return b;}
    EglFrameRenderer(Surface target,int width,int height)throws IOException {
        this.target=target;
        display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if(display==EGL14.EGL_NO_DISPLAY)throw new IOException("No EGL display");
        int[] versions=new int[2];
        if(!EGL14.eglInitialize(display,versions,0,versions,1))throw new IOException("EGL init failed");
        int[] attribs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,
                EGL14.EGL_ALPHA_SIZE,8,EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,
                0x3142,1,EGL14.EGL_NONE};
        EGLConfig[] configs=new EGLConfig[1];int[] count=new int[1];
        if(!EGL14.eglChooseConfig(display,attribs,0,configs,0,1,count,0)||count[0]<1)throw new IOException("No recordable EGL config");
        context=EGL14.eglCreateContext(display,configs[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
        window=EGL14.eglCreateWindowSurface(display,configs[0],target,new int[]{EGL14.EGL_NONE},0);
        if(context==EGL14.EGL_NO_CONTEXT||window==EGL14.EGL_NO_SURFACE||
                !EGL14.eglMakeCurrent(display,window,window,context))throw new IOException("EGL context/window setup failed");
        GLES20.glViewport(0,0,width,height);
        int v=shader(GLES20.GL_VERTEX_SHADER,VERTEX),f=shader(GLES20.GL_FRAGMENT_SHADER,FRAGMENT);
        program=GLES20.glCreateProgram();GLES20.glAttachShader(program,v);GLES20.glAttachShader(program,f);GLES20.glLinkProgram(program);
        int[] linked=new int[1];GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,linked,0);
        if(linked[0]==0)throw new IOException("GL program link: "+GLES20.glGetProgramInfoLog(program));
        GLES20.glDeleteShader(v);GLES20.glDeleteShader(f);
        posSlot=GLES20.glGetAttribLocation(program,"aPos");texSlot=GLES20.glGetAttribLocation(program,"aTex");
        zoomSlot=GLES20.glGetUniformLocation(program,"uZoom");
        int[] ids=new int[1];GLES20.glGenTextures(1,ids,0);texture=ids[0];
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MIN_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_MAG_FILTER,GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_S,GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D,GLES20.GL_TEXTURE_WRAP_T,GLES20.GL_CLAMP_TO_EDGE);
        positions=floats(new float[]{-1,-1,1,-1,-1,1,1,1});
        texCoords=floats(new float[]{0,1,1,1,0,0,1,0});
    }
    private static int shader(int type,String code)throws IOException {
        int sh=GLES20.glCreateShader(type);GLES20.glShaderSource(sh,code);GLES20.glCompileShader(sh);
        int[] status=new int[1];GLES20.glGetShaderiv(sh,GLES20.GL_COMPILE_STATUS,status,0);
        if(status[0]==0)throw new IOException("GL shader: "+GLES20.glGetShaderInfoLog(sh));return sh;
    }
    void upload(Bitmap image){
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        if(textureWidth==image.getWidth()&&textureHeight==image.getHeight()){
            GLUtils.texSubImage2D(GLES20.GL_TEXTURE_2D,0,0,0,image);
        } else {
            GLUtils.texImage2D(GLES20.GL_TEXTURE_2D,0,image,0);
            textureWidth=image.getWidth();textureHeight=image.getHeight();
        }
    }
    void draw(long timestampNs,float zoom)throws IOException {
        GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);GLES20.glBindTexture(GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1f(zoomSlot,zoom);
        GLES20.glEnableVertexAttribArray(posSlot);GLES20.glVertexAttribPointer(posSlot,2,GLES20.GL_FLOAT,false,0,positions);
        GLES20.glEnableVertexAttribArray(texSlot);GLES20.glVertexAttribPointer(texSlot,2,GLES20.GL_FLOAT,false,0,texCoords);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        GLES20.glDisableVertexAttribArray(posSlot);GLES20.glDisableVertexAttribArray(texSlot);
        EGLExt.eglPresentationTimeANDROID(display,window,timestampNs);
        if(!EGL14.eglSwapBuffers(display,window))throw new IOException("EGL swap failed: "+EGL14.eglGetError());
    }
    @Override public void close(){
        if(display!=EGL14.EGL_NO_DISPLAY){
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(window!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,window);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
        target.release();
    }
}
