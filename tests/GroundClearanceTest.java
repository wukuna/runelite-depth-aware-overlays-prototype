package net.runelite.client.plugins.gpu;

import org.junit.Test;
import static org.junit.Assert.*;

public class GroundClearanceTest
{
    private float[] projection()
    {
        float[] m = Mat4.identity(); m[5] = -1; m[15] = 100; return m;
    }
    @Test public void clearsBottomEdgeAndLocalSlope()
    {
        float[] m = projection();
        assertEquals(12, GroundClearance.lift(m,0,-1,0,new float[]{0,0,0},200,22),.02f);
        assertEquals(20, GroundClearance.lift(m,0,-1,0,new float[]{0,0,0,24,-8,24},200,22),.02f);
        assertEquals(0, GroundClearance.lift(m,0,-30,0,new float[]{0,0,0},200,22),0);
    }
    @Test public void boundedAndOptOut()
    {
        assertEquals(64, GroundClearance.lift(projection(),0,0,0,new float[]{0,-100,0},200,22),0);
        assertEquals(0, GroundClearance.lift(projection(),0,0,0,null,200,22),0);
        assertEquals(0, GroundClearance.lift(projection(),0,0,0,new float[]{0,0,0},0,22),0);
    }
    @Test public void cameraAndInvalidProjectionDoNotCauseJump()
    {
        float[] m = projection(); m[15]=49;
        assertEquals(0,GroundClearance.lift(m,0,0,0,new float[]{0,0,0},200,22),0);
        m[15]=100; m[5]=0;
        assertEquals(0,GroundClearance.lift(m,0,0,0,new float[]{0,0,0},200,22),0);
        m[5]=Float.NaN;
        assertEquals(0,GroundClearance.lift(m,0,0,0,new float[]{0,0,0},200,22),0);
    }
    @Test public void usesCurrentViewportAndCameraProjection()
    {
        float a=GroundClearance.lift(projection(),0,-1,0,new float[]{0,0,0},200,22);
        float b=GroundClearance.lift(projection(),0,-1,0,new float[]{0,0,0},400,22);
        assertTrue(b<a);
    }
}
