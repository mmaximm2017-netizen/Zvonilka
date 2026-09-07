package ru.zvonilka.prototype

import android.graphics.BitmapFactory
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Call
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch

val Green=Color(0xFF28AC67)
val Red=Color(0xFFE34E59)
val Blue=Color(0xFF3987DF)
@Composable fun PhoneTheme(content:@Composable ()->Unit) {
    MaterialTheme(colorScheme=if(isSystemInDarkTheme()) darkColorScheme(primary=Green,background=Color(0xFF101114),surface=Color(0xFF191B20)) else lightColorScheme(primary=Color(0xFF16884B),background=Color(0xFFF6F7FA),surface=Color.White),content=content)
}
@Composable fun Photo(person:PersonRecord?,modifier:Modifier=Modifier,full:Boolean=false) {
    val bitmap=remember(person?.id,person?.photo) { person?.photo?.let { BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap() } }
    Box(modifier.background(Color(0xFF435968)),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
        else Text(NumberTools.initials(person?.name ?: "?"),fontSize=if(full) 72.sp else 19.sp,color=Color.White)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},content:@Composable ()->Unit) {
    val offset=remember { Animatable(0f) }; val scope=rememberCoroutineScope(); var width by remember { mutableIntStateOf(1) }
    val view=LocalView.current
    val call by rememberUpdatedState(onCall)
    Box(modifier.clip(RoundedCornerShape(16.dp)).background(Green).onSizeChanged { width=it.width }) {
        Icon(Icons.Default.Call,"Позвонить",Modifier.align(Alignment.CenterStart).padding(24.dp),tint=Color.White)
        Box(Modifier.offset { IntOffset(offset.value.toInt(),0) }.fillMaxWidth().background(MaterialTheme.colorScheme.surface)
            .pointerInput(width) {
                detectHorizontalDragGestures(onDragEnd={
                    val complete=offset.value>=width*0.82f
                    scope.launch {
                        if(complete) { offset.animateTo(width.toFloat());view.performHapticFeedback(android.view.HapticFeedbackConstants.CONFIRM);call() }
                        offset.animateTo(0f)
                    }
                },onDragCancel={scope.launch { offset.animateTo(0f) }}) { change,amount ->
                    change.consume();scope.launch { offset.snapTo((offset.value+amount).coerceIn(0f,width.toFloat())) }
                }
            }.combinedClickable(onClick=onTap,onLongClick=onLong)) { content() }
    }
}
@Composable fun Confirm(title:String,onDismiss:()->Unit,onConfirm:()->Unit) {
    AlertDialog(onDismissRequest=onDismiss,title={Text(title)},confirmButton={TextButton(onClick=onConfirm){Text("Подтвердить",color=Red)}},dismissButton={TextButton(onClick=onDismiss){Text("Отмена")}})
}
