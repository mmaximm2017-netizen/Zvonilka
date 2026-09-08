package ru.zvonilka.prototype

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable fun CallSlideAction(label:String,color:Color,icon:ImageVector,onComplete:()->Unit) {
    val scope=rememberCoroutineScope()
    val view=LocalView.current
    val action by rememberUpdatedState(onComplete)
    var offset by remember{mutableFloatStateOf(0f)}
    var settleJob by remember{mutableStateOf<Job?>(null)}
    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).clip(CircleShape).background(Color.White.copy(alpha=.18f)).border(1.dp,Color.White.copy(alpha=.12f),CircleShape).semantics {
        contentDescription=label+". Передвиньте ползунок вправо"
        customActions=listOf(CustomAccessibilityAction(label){action();true})
    }) {
        val thumbPx=with(LocalDensity.current){64.dp.toPx()}
        val travel=(constraints.maxWidth-thumbPx-with(LocalDensity.current){8.dp.toPx()}).coerceAtLeast(1f)
        Text(label,Modifier.align(Alignment.Center).padding(start=44.dp),color=Color.White.copy(alpha=(1f-offset/travel).coerceIn(0f,1f)),fontSize=20.sp)
        val dragState=rememberDraggableState { delta->offset=(offset+delta).coerceIn(0f,travel) }
        Surface(
            Modifier.padding(4.dp).offset{IntOffset(offset.roundToInt(),0)}.size(64.dp).draggable(
                state=dragState,orientation=Orientation.Horizontal,
                onDragStarted={settleJob?.cancel()},
                onDragStopped={velocity->
                    settleJob=scope.launch {
                        val complete=GesturePolicy.shouldComplete(offset,travel,velocity,.72f,.25f,1200f)
                        val target=if(complete) travel else 0f
                        val anim=Animatable(offset)
                        anim.animateTo(target,spring(dampingRatio=Spring.DampingRatioNoBouncy,stiffness=Spring.StiffnessMediumLow),initialVelocity=velocity) { offset=value }
                        if(complete) {
                            val haptic=if(Build.VERSION.SDK_INT>=30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY
                            view.performHapticFeedback(haptic)
                            action()
                        }
                    }
                }
            ),shape=CircleShape,color=color
        ) {
            Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(28.dp),tint=Color.White)}
        }
    }
}
