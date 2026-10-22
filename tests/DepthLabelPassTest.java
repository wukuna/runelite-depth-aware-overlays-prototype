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

public class DepthLabelPassTest
{
    @Test
    public void realGpuDepthOcclusionStateAndLifecycle()
    {
        assertTrue("GLFW initialization", glfwInit());
        glfwWindowHint(GLFW_VISIBLE, GLFW_FALSE);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3);
        glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3);
        glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE);
        long window = glfwCreateWindow(128, 128, "Isolated depth-label graphics test", 0, 0);
        assertNotEquals("Hidden GPU test context", 0, window);
        DepthLabelPass pass = new DepthLabelPass();
        try
        {
            glfwMakeContextCurrent(window); GL.createCapabilities();
            glViewport(0, 0, 128, 128);
            glClearColor(1, 0, 0, 1); glClearDepth(0);
            glClear(GL_COLOR_BUFFER_BIT | GL_DEPTH_BUFFER_BIT);
            // A nearer object on the left half, empty space on the right.
            glEnable(GL_SCISSOR_TEST); glScissor(0, 0, 64, 128);
            glClearDepth(.9); glClear(GL_DEPTH_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
            float[] before = depths();
            // Non-default caller state, including an unpack buffer that must not leak into texture upload.
            int pbo = glGenBuffers(); glBindBuffer(GL_PIXEL_UNPACK_BUFFER, pbo);
            glBufferData(GL_PIXEL_UNPACK_BUFFER, 32, GL_STREAM_DRAW);
            glActiveTexture(GL_TEXTURE3); glPixelStorei(GL_UNPACK_ROW_LENGTH, 7);
            glPixelStorei(GL_UNPACK_SKIP_PIXELS, 2); glPixelStorei(GL_UNPACK_SKIP_ROWS, 3);
            glDisable(GL_DEPTH_TEST); glEnable(GL_CULL_FACE); glDisable(GL_BLEND);
            glDepthFunc(GL_LESS); glBlendEquationSeparate(GL_FUNC_REVERSE_SUBTRACT, GL_FUNC_SUBTRACT);
            pass.render(Mat4.identity(), 128, 128,
                Collections.singletonList(new DepthLabelPass.Label("TEST OCCLUSION", 0, 0, 0)));
            assertArrayEquals("Label pass never writes scene depth", before, depths(), 0f);
            assertFalse(glIsEnabled(GL_DEPTH_TEST)); assertTrue(glIsEnabled(GL_CULL_FACE));
            assertFalse(glIsEnabled(GL_BLEND)); assertTrue(glGetBoolean(GL_DEPTH_WRITEMASK));
            assertEquals(GL_LESS, glGetInteger(GL_DEPTH_FUNC));
            assertEquals(GL_TEXTURE3, glGetInteger(GL_ACTIVE_TEXTURE));
            assertEquals(pbo, glGetInteger(GL_PIXEL_UNPACK_BUFFER_BINDING));
            assertEquals(7, glGetInteger(GL_UNPACK_ROW_LENGTH));
            assertEquals(2, glGetInteger(GL_UNPACK_SKIP_PIXELS));
            assertEquals(3, glGetInteger(GL_UNPACK_SKIP_ROWS));
            assertEquals(GL_FUNC_REVERSE_SUBTRACT, glGetInteger(GL_BLEND_EQUATION_RGB));
            assertEquals(GL_FUNC_SUBTRACT, glGetInteger(GL_BLEND_EQUATION_ALPHA));
            assertEquals(0, glGetInteger(GL_CURRENT_PROGRAM));
            assertEquals(0, glGetInteger(GL_VERTEX_ARRAY_BINDING));
            assertArrayEquals("Nearer scene geometry hides the label", new int[]{255,0,0}, pixel(40,64));
            assertFalse("Label is visible against distant depth", java.util.Arrays.equals(new int[]{255,0,0}, pixel(85,64)));
            assertArrayEquals("Unrelated pixels remain untouched", new int[]{255,0,0}, pixel(10,10));
            // A later normal UI/overlay pass still draws in front of labels.
            glEnable(GL_SCISSOR_TEST); glScissor(80, 60, 10, 10);
            glClearColor(0,1,0,1); glClear(GL_COLOR_BUFFER_BIT); glDisable(GL_SCISSOR_TEST);
            assertArrayEquals(new int[]{0,255,0}, pixel(85,64));
            pass.close(); pass.close();
            pass.render(Mat4.identity(), 128, 128,
                Collections.singletonList(new DepthLabelPass.Label("RE-ENABLE", 0,0,0)));
            assertEquals("No GL errors after teardown and recreation", GL_NO_ERROR, glGetError());
            glBindBuffer(GL_PIXEL_UNPACK_BUFFER, 0); glDeleteBuffers(pbo);
        }
        finally
        {
            pass.close(); GL.setCapabilities(null); glfwDestroyWindow(window); glfwTerminate();
        }
    }

    private static int[] pixel(int x, int y)
    {
        ByteBuffer b = BufferUtils.createByteBuffer(4);
        glReadPixels(x,y,1,1,GL_RGBA,GL_UNSIGNED_BYTE,b);
        return new int[]{b.get(0)&255,b.get(1)&255,b.get(2)&255};
    }
    private static float[] depths()
    {
        FloatBuffer buffer = BufferUtils.createFloatBuffer(128*128);
        glReadPixels(0,0,128,128,GL_DEPTH_COMPONENT,GL_FLOAT,buffer);
        float[] result = new float[128*128]; buffer.get(result); return result;
    }
}
