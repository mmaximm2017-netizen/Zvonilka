package ru.zvonilka.prototype

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Paint
import android.graphics.Rect
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable fun PhotoCropEditor(source:ByteArray,name:String,onSave:(ByteArray)->Unit,onDismiss:()->Unit) {
    val bitmap=remember(source){BitmapFactory.decodeByteArray(source,0,source.size)}
    var zoom by remember(source){mutableFloatStateOf(1f)}
    var panX by remember(source){mutableFloatStateOf(0f)}
    var panY by remember(source){mutableFloatStateOf(0f)}
    var saving by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf<String?>(null)}
    val scope=rememberCoroutineScope()
    val paint=remember{Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)}
    Dialog(onDismissRequest={if(!saving)onDismiss()},properties=DialogProperties(usePlatformDefaultWidth=false)) {
        Surface(Modifier.fillMaxSize(),color=MaterialTheme.colorScheme.background) {
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(16.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    TextButton(enabled=!saving,onClick=onDismiss){Text("Отмена")}
                    Text("Фото звонка",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium)
                    TextButton(enabled=bitmap!=null && !saving,onClick={
                        val input=bitmap ?: return@TextButton
                        val area=CropMath.area(input.width,input.height,zoom,panX,panY)
                        saving=true
                        scope.launch {
                            runCatching { withContext(Dispatchers.Default) {
                                val output=Bitmap.createBitmap(720,1600,Bitmap.Config.ARGB_8888)
                                try {
                                    android.graphics.Canvas(output).drawBitmap(input,Rect(area.left,area.top,area.left+area.width,area.top+area.height),Rect(0,0,720,1600),Paint(Paint.FILTER_BITMAP_FLAG))
                                    var quality=92
                                    var bytes:ByteArray
                                    do { bytes=java.io.ByteArrayOutputStream().use { out->output.compress(Bitmap.CompressFormat.JPEG,quality,out);out.toByteArray() };quality-=10 } while(bytes.size>650000 && quality>=12)
                                    require(bytes.size<=650000){"Фото слишком большое. Выберите другой кадр"}
                                    bytes
                                } finally { output.recycle() }
                            } }.onSuccess(onSave).onFailure{error=it.message ?: "Не удалось обработать фото"}
                            saving=false
                        }
                    }){Text(if(saving) "Сохраняю…" else "Готово")}
                }
                BoxWithConstraints(Modifier.weight(1f).fillMaxWidth(),contentAlignment=Alignment.Center) {
                    val width=minOf(maxWidth,maxHeight*CropMath.ASPECT)
                    Box(Modifier.width(width).aspectRatio(CropMath.ASPECT).clip(MaterialTheme.shapes.large).background(Color.Black)) {
                        if(bitmap!=null) Canvas(Modifier.fillMaxSize().pointerInput(bitmap,saving){
                            if(saving)return@pointerInput
                            detectTransformGestures { _,pan,scale,_ ->
                                zoom=(zoom*scale).coerceIn(1f,4f)
                                panX=(panX+pan.x/size.width.coerceAtLeast(1)*2/zoom).coerceIn(-1f,1f)
                                panY=(panY+pan.y/size.height.coerceAtLeast(1)*2/zoom).coerceIn(-1f,1f)
                            }
                        }) {
                            val area=CropMath.area(bitmap.width,bitmap.height,zoom,panX,panY)
                            drawContext.canvas.nativeCanvas.drawBitmap(bitmap,Rect(area.left,area.top,area.left+area.width,area.top+area.height),android.graphics.RectF(0f,0f,size.width,size.height),paint)
                        }
                        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x66071A31),Color.Transparent,Color(0xAA071A31)))))
                        Column(Modifier.align(Alignment.TopCenter).padding(top=28.dp),horizontalAlignment=Alignment.CenterHorizontally) { Text(name.ifBlank{"Имя контакта"},fontSize=18.sp,color=Color.White);Text("SIM · Входящий вызов",fontSize=11.sp,color=Color.White) }
                        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
                            listOf("Ответить →" to Color.White.copy(alpha=.3f)).forEach { (label,color) ->
                                Box(Modifier.fillMaxWidth().height(36.dp).clip(CircleShape).background(color.copy(alpha=.65f)),contentAlignment=Alignment.Center) { Text(label,fontSize=12.sp,color=Color.White) }
                            }
                        }
                    }
                }
                Text("Передвигайте фото и увеличивайте двумя пальцами",Modifier.padding(top=12.dp),style=MaterialTheme.typography.bodySmall)
                Slider(value=zoom,onValueChange={zoom=it},valueRange=1f..4f,enabled=!saving)
                TextButton(enabled=!saving,onClick={zoom=1f;panX=0f;panY=0f}){Text("Сбросить кадр")}
                error?.let{Text(it,color=MaterialTheme.colorScheme.error)}
            }
        }
    }
}
