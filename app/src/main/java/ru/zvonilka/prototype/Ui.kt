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
import androidx.compose.material.icons.outlined.Person
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.FilterQuality

val Green=Color(0xFF28AC67)
val Red=Color(0xFFE34E59)
val Blue=Color(0xFF3987DF)
val Ocean=Color(0xFF1765D1)
val Sky=Color(0xFFE8F3FF)
@Composable fun PhoneTheme(content:@Composable ()->Unit) {
    val colors=if(isSystemInDarkTheme()) darkColorScheme(
        primary=Color(0xFF9ACBFF),onPrimary=Color(0xFF003263),primaryContainer=Color(0xFF153E68),onPrimaryContainer=Color(0xFFDCEEFF),
        secondary=Color(0xFF8CD5EB),onSecondary=Color(0xFF003641),secondaryContainer=Color(0xFF193E51),onSecondaryContainer=Color(0xFFC7F1FF),
        background=Color(0xFF0B1625),onBackground=Color(0xFFEAF3FF),surface=Color(0xFF122338),onSurface=Color(0xFFEAF3FF),
        surfaceVariant=Color(0xFF1B3048),onSurfaceVariant=Color(0xFFACC1D8),outline=Color(0xFF718AA4),outlineVariant=Color(0xFF29425B)
    ) else lightColorScheme(
        primary=Ocean,onPrimary=Color.White,primaryContainer=Color(0xFFDCEEFF),onPrimaryContainer=Color(0xFF123D70),
        secondary=Color(0xFF13718F),onSecondary=Color.White,secondaryContainer=Color(0xFFDDF5FF),onSecondaryContainer=Color(0xFF16475D),
        background=Color(0xFFF2F7FD),onBackground=Color(0xFF142D49),surface=Color.White,onSurface=Color(0xFF142D49),
        surfaceVariant=Sky,onSurfaceVariant=Color(0xFF536D87),outline=Color(0xFF7992AB),outlineVariant=Color(0xFFD6E5F3)
    )
    MaterialTheme(colorScheme=colors,shapes=Shapes(small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(20.dp),large=RoundedCornerShape(28.dp)),
        typography=Typography(
            headlineMedium=androidx.compose.ui.text.TextStyle(fontWeight=FontWeight.Bold,fontSize=28.sp,lineHeight=34.sp),
            titleLarge=androidx.compose.ui.text.TextStyle(fontWeight=FontWeight.Bold,fontSize=22.sp,lineHeight=28.sp),
            titleMedium=androidx.compose.ui.text.TextStyle(fontWeight=FontWeight.Medium,fontSize=16.sp,lineHeight=22.sp),
            bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=20.sp),
            labelLarge=androidx.compose.ui.text.TextStyle(fontWeight=FontWeight.SemiBold,fontSize=14.sp,lineHeight=20.sp)
        ),content=content)
}
@Composable fun historyColor(type:Int):Color = when(type) {
    android.provider.CallLog.Calls.MISSED_TYPE -> if(isSystemInDarkTheme()) Color(0xFFFF9BA9) else Color(0xFFB83245)
    android.provider.CallLog.Calls.OUTGOING_TYPE -> if(isSystemInDarkTheme()) Color(0xFF7BDAAE) else Color(0xFF187544)
    else -> MaterialTheme.colorScheme.primary
}
fun listRowShape(first:Boolean,last:Boolean)=RoundedCornerShape(
    topStart=if(first) 20.dp else 0.dp,topEnd=if(first) 20.dp else 0.dp,
    bottomStart=if(last) 20.dp else 0.dp,bottomEnd=if(last) 20.dp else 0.dp)
@Composable fun SectionLabel(text:String) {
    Text(text,Modifier.padding(start=4.dp,top=10.dp,bottom=4.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
}
@Composable fun EmptySection(title:String,subtitle:String) {
    Column(Modifier.fillMaxWidth().padding(horizontal=24.dp,vertical=56.dp),horizontalAlignment=Alignment.CenterHorizontally) {
        Surface(shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer) { Icon(Icons.Default.Call,null,Modifier.padding(22.dp).size(30.dp),tint=MaterialTheme.colorScheme.primary) }
        Spacer(Modifier.height(20.dp));Text(title,style=MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp));Text(subtitle,color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodyMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
    }
}
@Composable fun SettingsGroup(content:@Composable ColumnScope.()->Unit) {
    Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable fun Photo(person:PersonRecord?,modifier:Modifier=Modifier,full:Boolean=false) {
    val thumbnail=remember(person?.id,person?.photo) { person?.photo?.let { BitmapFactory.decodeByteArray(it,0,it.size)?.asImageBitmap() } }
    val context=LocalContext.current.applicationContext
    // Key the state as well as the coroutine so a different caller never sees the old photo.
    val large = key(person?.id,person?.photo,full) {
        val loaded by produceState<ImageBitmap?>(initialValue=null) {
            if(full && person!=null) value=withContext(Dispatchers.IO) {
                PhoneData(context).displayPhoto(person.id)?.asImageBitmap()
            }
        }
        loaded
    }
    val bitmap=large ?: thumbnail
    Box(modifier.background(Brush.linearGradient(if(full) listOf(Color(0xFF123D70),Color(0xFF245F97)) else listOf(MaterialTheme.colorScheme.primaryContainer,MaterialTheme.colorScheme.secondaryContainer))),contentAlignment=Alignment.Center) {
        if(bitmap!=null) Image(bitmap,null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop,filterQuality=FilterQuality.High)
        else if(person==null) Icon(Icons.Outlined.Person,"Нет фото",Modifier.size(if(full) 96.dp else 28.dp),tint=if(full) Color.White else MaterialTheme.colorScheme.primary)
        else Text(NumberTools.initials(person.name),fontSize=if(full) 72.sp else 19.sp,color=if(full) Color.White else MaterialTheme.colorScheme.onPrimaryContainer)
    }
}
@OptIn(ExperimentalFoundationApi::class)
@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(20.dp),containerColor:Color=MaterialTheme.colorScheme.surface,content:@Composable ()->Unit) {
    val offset=remember { Animatable(0f) }; val scope=rememberCoroutineScope(); var width by remember { mutableIntStateOf(1) }
    val view=LocalView.current
    val call by rememberUpdatedState(onCall)
    Box(modifier.clip(shape).onSizeChanged { width=it.width }) {
        // The action background must follow content size, never impose its own height.
        Box(Modifier.matchParentSize().background(if(offset.value>0f) Ocean else containerColor)) {
            if(offset.value>0f) Icon(Icons.Default.Call,null,Modifier.align(Alignment.CenterStart).padding(start=24.dp),tint=Color.White)
        }
        Box(Modifier.offset { IntOffset(offset.value.toInt(),0) }.fillMaxWidth().background(containerColor)
            .pointerInput(width,enabled) {
                if(!enabled) return@pointerInput
                detectHorizontalDragGestures(onDragEnd={
                    val complete=offset.value>=width*0.82f
                    scope.launch {
                        if(complete) { offset.animateTo(width.toFloat());view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS);call() }
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
