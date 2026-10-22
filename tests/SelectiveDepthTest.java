package net.runelite.client.plugins.gpu;

import java.nio.ByteBuffer;
import java.nio.FloatBuffer;
import java.util.Collections;
import org.junit.Test;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.junit.Assert.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;

public class SelectiveDepthTest
{
    @Test public void separateDepthPreservesSceneAndCompositesLabelsWithAndWithoutMsaa()
    {
        assertTrue(glfwInit());
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
        glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
        long window=glfwCreateWindow(128,128,"Selective TEST graphics",0,0);
        assertNotEquals(0,window);
        try
        {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            for (int samples : new int[]{0, Math.min(4,glGetInteger(GL_MAX_SAMPLES))}) { exercise(samples); }
        }
        finally { GL.setCapabilities(null); glfwDestroyWindow(window); glfwTerminate(); }
    }

    private void exercise(int samples)
    {
        int[] scene=fbo(samples), resolved=fbo(0);
        SelectiveDepth selective=new SelectiveDepth();
        DepthLabelPass labels=new DepthLabelPass();
        int shader=program(), vao=glGenVertexArrays();
        try
        {
            glBindFramebuffer(GL_FRAMEBUFFER,scene[0]); glViewport(0,0,128,128);
            glDisable(GL_SCISSOR_TEST); glDepthMask(true); glColorMask(true,true,true,true);
            glClearDepth(0); glClearColor(.2f,.2f,.2f,1); glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);
            // Normal scene: left landscape is green; middle prop is red; right player is blue.
            patch(0,20,.95,0,1,0); patch(20,60,.9,1,0,0); patch(80,25,.8,0,0,1);
            resolve(scene[0],resolved[0]); float[] originalDepth=depths();
            glBindFramebuffer(GL_FRAMEBUFFER,scene[0]);
            glUseProgram(shader); glEnable(GL_DEPTH_TEST); glDepthFunc(GL_GREATER);
            selective.begin(true);
            glDepthMask(false);
            try { selective.replayScene(() -> { throw new IllegalStateException("probe"); }, false); fail(); }
            catch (IllegalStateException expected) { assertEquals("probe", expected.getMessage()); }
            assertFalse(glGetBoolean(GL_DEPTH_WRITEMASK));
            assertEquals(scene[0], glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            ByteBuffer mask = BufferUtils.createByteBuffer(4); glGetBooleanv(GL_COLOR_WRITEMASK, mask);
            for (int i=0;i<4;i++) { assertNotEquals(0,mask.get(i)); }
            assertEquals(0, glGetUniformi(shader, glGetUniformLocation(shader,"testLabelDepth")));
            glDepthMask(true);
            assertEquals(scene[0],glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            glUseProgram(shader); glBindVertexArray(vao);
            glEnable(GL_SCISSOR_TEST);
            // Depth-only landscape replay. The prop is deliberately absent.
            glScissor(0,0,20,128); glUniform1f(glGetUniformLocation(shader,"z"),.9f);
            selective.replayScene(() -> glDrawArrays(GL_TRIANGLES,0,3), true);
            // Player behind landscape must never be redrawn into scene color.
            glScissor(0,0,20,128); glUniform1f(glGetUniformLocation(shader,"z"),.6f);
            selective.replayScene(() -> glDrawArrays(GL_TRIANGLES,0,3), true);
            glScissor(80,0,25,128);
            selective.replayScene(() -> glDrawArrays(GL_TRIANGLES,0,3), true);
            glDisable(GL_SCISSOR_TEST);
            resolve(scene[0],resolved[0]);
            assertArrayEquals(new int[]{0,255,0},pixel(10,64));
            assertArrayEquals(new int[]{255,0,0},pixel(40,64));
            assertArrayEquals(new int[]{0,0,255},pixel(90,64));
            assertArrayEquals(originalDepth,depths(),0f);
            glBindFramebuffer(GL_FRAMEBUFFER,scene[0]);
            float[] placement = Mat4.identity(); placement[15] = 100; placement[5] = -1;
            selective.labels(() -> labels.render(placement,128,128,
                Collections.singletonList(new DepthLabelPass.Label("TEST SELECTIVE LANDSCAPE AND PLAYER",0,0,0,
                    new float[]{0,0,0}))));
            assertEquals(scene[0],glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING));
            resolve(scene[0],resolved[0]);
            assertArrayEquals("Landscape stays above raised label and player",new int[]{0,255,0},pixel(10,77));
            assertFalse("Raised label renders over ignored prop",java.util.Arrays.equals(new int[]{255,0,0},pixel(40,77)));
            assertArrayEquals("Visible player stays above raised label",new int[]{0,0,255},pixel(90,77));
            assertArrayEquals("Scene depth is never altered",originalDepth,depths(),0f);
            assertArrayEquals("Outside labels remains unchanged",new int[]{255,0,0},pixel(40,10));
            // A later UI overlay can still cover all scene content.
            glBindFramebuffer(GL_FRAMEBUFFER,scene[0]); patch(0,128,.2,1,1,0);
            resolve(scene[0],resolved[0]); assertArrayEquals(new int[]{255,255,0},pixel(10,64));
            glBindFramebuffer(GL_FRAMEBUFFER,scene[0]); selective.begin(false);
            assertFalse(selective.active);
            assertEquals(GL_NO_ERROR,glGetError());
        }
        finally
        {
            labels.close(); selective.close(); selective.close();
            glUseProgram(0); glBindVertexArray(0); glDeleteProgram(shader); glDeleteVertexArrays(vao);
            glBindFramebuffer(GL_FRAMEBUFFER,0);
            for (int[] f : new int[][]{scene,resolved}) { glDeleteFramebuffers(f[0]);glDeleteRenderbuffers(f[1]);glDeleteRenderbuffers(f[2]); }
        }
    }
    private static int[] fbo(int samples)
    {
        int[] f={glGenFramebuffers(),glGenRenderbuffers(),glGenRenderbuffers()};
        glBindFramebuffer(GL_FRAMEBUFFER,f[0]);
        for (int i=1;i<=2;i++)
        {
            glBindRenderbuffer(GL_RENDERBUFFER,f[i]); int format=i==1?GL_RGBA8:GL_DEPTH_COMPONENT32F;
            if(samples>0)glRenderbufferStorageMultisample(GL_RENDERBUFFER,samples,format,128,128);
            else glRenderbufferStorage(GL_RENDERBUFFER,format,128,128);
            glFramebufferRenderbuffer(GL_FRAMEBUFFER,i==1?GL_COLOR_ATTACHMENT0:GL_DEPTH_ATTACHMENT,GL_RENDERBUFFER,f[i]);
        }
        assertEquals(GL_FRAMEBUFFER_COMPLETE,glCheckFramebufferStatus(GL_FRAMEBUFFER));return f;
    }
    private static void patch(int x,int width,double depth,float r,float g,float b)
    {
        glEnable(GL_SCISSOR_TEST);glScissor(x,0,width,128);glClearDepth(depth);glClearColor(r,g,b,1);
        glDepthMask(true);glClear(GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT);glDisable(GL_SCISSOR_TEST);
    }
    private static void resolve(int scene,int target)
    {
        glBindFramebuffer(GL_READ_FRAMEBUFFER,scene);glBindFramebuffer(GL_DRAW_FRAMEBUFFER,target);
        glBlitFramebuffer(0,0,128,128,0,0,128,128,GL_COLOR_BUFFER_BIT|GL_DEPTH_BUFFER_BIT,GL_NEAREST);
        glBindFramebuffer(GL_READ_FRAMEBUFFER,target);
    }
    private static int[] pixel(int x,int y)
    {
        ByteBuffer b=BufferUtils.createByteBuffer(4);glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);
        return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255};
    }
    private static float[] depths()
    {
        FloatBuffer b=BufferUtils.createFloatBuffer(128*128);glReadPixels(0,0,128,128,GL_DEPTH_COMPONENT,GL_FLOAT,b);
        float[] data=new float[128*128];b.get(data);return data;
    }
    private static int program()
    {
        int v=glCreateShader(GL_VERTEX_SHADER),f=glCreateShader(GL_FRAGMENT_SHADER),p=glCreateProgram();
        glShaderSource(v,"#version 330\nuniform float z;void main(){vec2 p[3]=vec2[3](vec2(-1,-1),vec2(3,-1),vec2(-1,3));gl_Position=vec4(p[gl_VertexID],z,1);}");
        glShaderSource(f,"#version 330\nuniform int testLabelDepth;out vec4 color;void main(){color=vec4(1,0,1,1);if(testLabelDepth!=0) color.r=0.5;}");
        glCompileShader(v);glCompileShader(f);glAttachShader(p,v);glAttachShader(p,f);glLinkProgram(p);
        assertEquals(glGetProgramInfoLog(p),GL_TRUE,glGetProgrami(p,GL_LINK_STATUS));
        glDeleteShader(v);glDeleteShader(f);return p;
    }
}

