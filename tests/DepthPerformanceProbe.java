package net.runelite.client.plugins.gpu;
import java.util.*;
import java.nio.*;
import org.lwjgl.BufferUtils;
import org.lwjgl.opengl.GL;
import static org.junit.Assert.*;
import static org.lwjgl.glfw.GLFW.*;
import static org.lwjgl.opengl.GL33C.*;
public class DepthPerformanceProbe {
 public static void main(String[] args) throws Exception {
  assertTrue(glfwInit()); glfwWindowHint(GLFW_VISIBLE,GLFW_FALSE);
  glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR,3); glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR,3);
  glfwWindowHint(GLFW_OPENGL_PROFILE,GLFW_OPENGL_CORE_PROFILE);
  long window=glfwCreateWindow(128,128,"Isolated performance probe",0,0);
  assertNotEquals(0,window); glfwMakeContextCurrent(window); GL.createCapabilities();
  try (java.io.PrintWriter out=new java.io.PrintWriter(args[0])) {
   for(int i=0;i<3;i++) run(new java.io.PrintWriter(new java.io.StringWriter()),0,100,64,false);
   out.println("# GPU: "+glGetString(GL_RENDERER));
   out.println("# replay coverage: "+(Boolean.getBoolean("probe.small")?"128x128 scissor":"1920x1080 fullscreen"));
   out.println("samples,submitted_labels,replay_calls,cache,cpu_median_ms,cpu_p95_ms,gpu_median_ms,gpu_p95_ms");
   for(int ms:new int[]{0,4}) for(int replay:new int[]{0,100,500}) for(int count:new int[]{0,16,64,256}) run(out,ms,replay,count,false);
   run(out,0,100,64,true);
  } finally {GL.setCapabilities(null);glfwDestroyWindow(window);glfwTerminate();}
 }
 static void run(java.io.PrintWriter out,int samples,int replays,int count,boolean churn) {
  int[] frame=fbo(samples); int shader=program(),vao=glGenVertexArrays(),query=glGenQueries();
  DepthLabelPass labels=new DepthLabelPass(); SelectiveDepth selective=new SelectiveDepth();
  List<DepthLabelPass.Label> commands=new ArrayList<>();
  float[] projection=Mat4.identity();projection[15]=100;projection[5]=-1;
  IntBuffer offsets=BufferUtils.createIntBuffer(Math.max(1,replays)), counts=BufferUtils.createIntBuffer(Math.max(1,replays));
  for(int i=0;i<replays;i++){offsets.put(0);counts.put(3);} offsets.flip();counts.flip();
  int warm=churn?5:30,n=churn?30:80;
  double[] cpu=new double[n],gpu=new double[n];
  try {
   for(int frameIndex=-warm;frameIndex<n;frameIndex++) {
    if(commands.isEmpty() || churn) {
     commands.clear();
     for(int i=0;i<count;i++) commands.add(new DepthLabelPass.Label("TEST item "+i+(churn?" frame "+frameIndex:""),-80+(i%8)*20,-40+(i/8)*10,0,new float[]{-80+(i%8)*20,-35+(i/8)*10,0}));
    }
    glBindFramebuffer(GL_FRAMEBUFFER,frame[0]); glViewport(0,0,1920,1080);
    glUseProgram(shader);glBindVertexArray(vao);glUniform1f(glGetUniformLocation(shader,"z"),-.5f);
    glEnable(GL_DEPTH_TEST);glDepthFunc(GL_GREATER);glDepthMask(true);
    glClearDepth(0);glClear(GL_DEPTH_BUFFER_BIT);glFinish();
    glBeginQuery(GL_TIME_ELAPSED,query);long start=System.nanoTime();
    selective.begin(!commands.isEmpty());
    if(Boolean.getBoolean("probe.small")){glEnable(GL_SCISSOR_TEST);glScissor(896,476,128,128);}
    if(Boolean.getBoolean("probe.batch") && replays>0) {
     selective.replayScene(() -> glMultiDrawArrays(GL_TRIANGLES,offsets,counts),true);
    } else for(int i=0;i<replays;i++) selective.replayScene(() -> glDrawArrays(GL_TRIANGLES,0,3), true);
    glDisable(GL_SCISSOR_TEST);
    selective.labels(() -> labels.render(projection,1920,1080,commands));
    glEndQuery(GL_TIME_ELAPSED);double elapsed=(System.nanoTime()-start)/1e6;
    double gpuElapsed=glGetQueryObjectui64(query,GL_QUERY_RESULT)/1e6;
    if(frameIndex>=0){cpu[frameIndex]=elapsed;gpu[frameIndex]=gpuElapsed;}
   }
   Arrays.sort(cpu);Arrays.sort(gpu);
   out.printf(Locale.ROOT,"%d,%d,%d,%s,%.4f,%.4f,%.4f,%.4f%n",samples,count,replays,churn?"churn":"warm",cpu[n/2],cpu[(int)(n*.95)],gpu[n/2],gpu[(int)(n*.95)]);out.flush();
   assertEquals(GL_NO_ERROR,glGetError());
  } finally {
   labels.close();selective.close();glUseProgram(0);glBindVertexArray(0);
   glDeleteProgram(shader);glDeleteVertexArrays(vao);glDeleteQueries(query);
   glBindFramebuffer(GL_FRAMEBUFFER,0);glDeleteFramebuffers(frame[0]);glDeleteRenderbuffers(frame[1]);glDeleteRenderbuffers(frame[2]);
  }
 }
    private static int[] fbo(int samples)
    {
        int[] f={glGenFramebuffers(),glGenRenderbuffers(),glGenRenderbuffers()};
        glBindFramebuffer(GL_FRAMEBUFFER,f[0]);
        for (int i=1;i<=2;i++)
        {
            glBindRenderbuffer(GL_RENDERBUFFER,f[i]); int format=i==1?GL_RGBA8:GL_DEPTH_COMPONENT32F;
            if(samples>0)glRenderbufferStorageMultisample(GL_RENDERBUFFER,samples,format,1920,1080);
            else glRenderbufferStorage(GL_RENDERBUFFER,format,1920,1080);
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

