package ru.zvonilka.prototype

import kotlin.math.roundToInt

data class CropArea(val left:Int,val top:Int,val width:Int,val height:Int)
object CropMath {
    const val ASPECT=0.45f
    fun area(width:Int,height:Int,zoom:Float,x:Float,y:Float):CropArea {
        require(width>0 && height>0)
        val baseWidth=minOf(width.toFloat(),height*ASPECT)
        val cropWidth=(baseWidth/zoom.coerceIn(1f,4f)).roundToInt().coerceIn(1,width)
        val cropHeight=(cropWidth/ASPECT).roundToInt().coerceIn(1,height)
        val left=((width-cropWidth)*(1-x.coerceIn(-1f,1f))/2).roundToInt().coerceIn(0,width-cropWidth)
        val top=((height-cropHeight)*(1-y.coerceIn(-1f,1f))/2).roundToInt().coerceIn(0,height-cropHeight)
        return CropArea(left,top,cropWidth,cropHeight)
    }
}
