package ru.zvonilka.prototype

import androidx.compose.animation.core.animate
import androidx.compose.foundation.*
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

@Composable fun CallSlideAction(label:String,color:Color,icon:ImageVector,onComplete:()->Unit) {
    val scope=rememberCoroutineScope()
    val view=LocalView.current
    val action by rememberUpdatedState(onComplete)
    var offset by remember{mutableFloatStateOf(0f)}
    var busy by remember{mutableStateOf(false)}
    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).clip(CircleShape).background(color.copy(alpha=.3f)).semantics {
        contentDescription=label+". Передвиньте ползунок вправо"
        customActions=listOf(CustomAccessibilityAction(label){if(!busy){action();true}else false})
    }) {
        val thumbPx=with(LocalDensity.current){64.dp.toPx()}
        val travel=(constraints.maxWidth-thumbPx-with(LocalDensity.current){8.dp.toPx()}).coerceAtLeast(1f)
        Text("$label  →",Modifier.align(Alignment.Center),color=Color.White,fontSize=18.sp)
        Box(Modifier.fillMaxSize().pointerInput(travel){
            var accepted=false
            detectHorizontalDragGestures(onDragStart={p->accepted=!busy && p.x<=thumbPx+16},onDragEnd={
                if(accepted) {
                    busy=true
                    scope.launch {
                        try {
                            if(offset>=travel*.9f) {
                                animate(offset,travel){v,_->offset=v}
                                view.performHapticFeedback(android.view.HapticFeedbackConstants.LONG_PRESS)
                                action();delay(500)
                            }
                            animate(offset,0f){v,_->offset=v}
                        } finally {busy=false}
                    }
                }
            },onDragCancel={if(accepted){busy=true;scope.launch{try{animate(offset,0f){v,_->offset=v}}finally{busy=false}}}}) { change,delta->
                if(accepted){change.consume();offset=(offset+delta).coerceIn(0f,travel)}
            }
        }) {
            Surface(Modifier.padding(4.dp).offset{IntOffset(offset.roundToInt(),0)}.size(64.dp),shape=CircleShape,color=color) {
                Box(contentAlignment=Alignment.Center){Icon(icon,null,Modifier.size(28.dp),tint=Color.White)}
            }
        }
    }
}
