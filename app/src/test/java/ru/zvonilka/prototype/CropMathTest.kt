package ru.zvonilka.prototype
import org.junit.Assert.*
import org.junit.Test
class CropMathTest {
    @Test fun allEdgesRemainInsideSource() {
        for((w,h) in listOf(1920 to 1080,1080 to 2400,96 to 96,1 to 1)) for(z in listOf(1f,2f,4f)) for(x in listOf(-1f,0f,1f)) for(y in listOf(-1f,0f,1f)) {
            val a=CropMath.area(w,h,z,x,y)
            assertTrue(a.left>=0 && a.top>=0 && a.width>0 && a.height>0)
            assertTrue(a.left+a.width<=w && a.top+a.height<=h)
        }
    }
    @Test fun zoomAndPanRevealExpectedAreas() {
        val center=CropMath.area(1600,1600,1f,0f,0f)
        assertEquals(720,center.width);assertEquals(440,center.left)
        assertEquals(0,CropMath.area(1600,1600,1f,1f,0f).left)
        assertEquals(880,CropMath.area(1600,1600,1f,-1f,0f).left)
        assertEquals(360,CropMath.area(1600,1600,2f,0f,0f).width)
    }
}
