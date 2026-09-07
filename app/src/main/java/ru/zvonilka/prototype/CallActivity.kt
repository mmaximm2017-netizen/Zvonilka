package ru.zvonilka.prototype

import android.content.Intent
import android.os.Bundle
import android.os.PowerManager
import android.telecom.*
import android.view.KeyEvent
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.compose.foundation.*
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.semantics.*
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    private var appliedTheme="system"
    override fun attachBaseContext(base: android.content.Context) { super.attachBaseContext(ThemeSettings.wrap(base)) }
    override fun onResume() { super.onResume();if(appliedTheme!=ThemeSettings.mode(this)) recreate() }

    private var revision by mutableIntStateOf(0)
    private var selected by mutableStateOf<String?>(null)
    private var toneCall:Call?=null
    private var proximity:PowerManager.WakeLock?=null
    private val listener:()->Unit={revision++}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));setShowWhenLocked(true);setTurnScreenOn(true)
        appliedTheme=ThemeSettings.mode(this)
        CallDiagnostics.record(this, "screen_created")
        selected=savedInstanceState?.getString("selected") ?: intent.getStringExtra("call_id")
        val power=getSystemService(PowerManager::class.java)
        if(power.isWakeLockLevelSupported(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK)) proximity=power.newWakeLock(PowerManager.PROXIMITY_SCREEN_OFF_WAKE_LOCK,"Zvonilka:proximity")
        handleAnswer(intent)
        setContent { PhoneTheme { CallScreen() } }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);selected=intent.getStringExtra("call_id") ?: selected;handleAnswer(intent);revision++ }
    private fun handleAnswer(intent:Intent) { if(intent.action=="answer") CallStore.calls[intent.getStringExtra("call_id")]?.takeIf{it.state==Call.STATE_RINGING}?.answer(VideoProfile.STATE_AUDIO_ONLY) }
    override fun onStart() { super.onStart();CallDiagnostics.record(this,"screen_started");CallStore.listeners.add(listener);revision++ }
    override fun onStop() { CallStore.listeners.remove(listener);stopTone();releaseProximity();super.onStop() }
    override fun onSaveInstanceState(outState:Bundle) {outState.putString("selected",selected);super.onSaveInstanceState(outState)}
    private fun stopTone(){toneCall?.stopDtmfTone();toneCall=null}
    private fun releaseProximity(){if(proximity?.isHeld==true) proximity?.release()}
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable private fun CallScreen() {
        val tick=revision
        val live=CallStore.liveCalls()
        val key=if(selected in live) selected else live.entries.firstOrNull { it.value.state==Call.STATE_RINGING }?.key ?: live.keys.firstOrNull()
        val call=live[key]
        var lastPerson by remember { mutableStateOf<PersonRecord?>(null) }
        var lastNumber by remember { mutableStateOf("") }
        var seconds by remember(key) { mutableLongStateOf(0) }
        var keypad by remember(key) { mutableStateOf(false) }
        val person=if(call!=null) ContactCache.find(CallStore.label(call)) else lastPerson
        val audio=CallStore.service?.callAudioState
        LaunchedEffect(key,tick) {
            if(call!=null) { selected=key;lastPerson=ContactCache.find(CallStore.label(call));lastNumber=CallStore.label(call) }
            val use=call?.state==Call.STATE_ACTIVE && audio?.route==CallAudioState.ROUTE_EARPIECE
            if(use && proximity?.isHeld==false) proximity?.acquire(2*60*60*1000L) else if(!use) releaseProximity()
        }
        LaunchedEffect(key,call?.state) {
            if(call==null) { releaseProximity();delay(1000);finish() }
            else while(true) { val start=call.details.connectTimeMillis;if(start>0) seconds=((System.currentTimeMillis()-start)/1000).coerceAtLeast(0);delay(1000) }
        }
        val white=Color.White
        val secondary=white.copy(alpha=.72f)
        val ringing=call?.state==Call.STATE_RINGING
        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF34536B),Color(0xFF182A40),Color(0xFF101722))))) {
            if(person!=null) Photo(person,Modifier.fillMaxSize(),true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x70070D18),Color(0x20070D18),Color(0xD909101B)))))
            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {
                val compact=maxHeight<650.dp
                Column(Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min=maxHeight).padding(horizontal=28.dp,vertical=20.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.SpaceBetween) {
                    Column(Modifier.fillMaxWidth().padding(top=if(compact)12.dp else 38.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Text(call?.details?.accountHandle?.let{Dialing.label(this@CallActivity,it)} ?: "",color=secondary,fontSize=14.sp,textAlign=TextAlign.Center)
                        Spacer(Modifier.height(14.dp))
                        Text(person?.name ?: NumberTools.display(call?.let{CallStore.label(it)} ?: lastNumber).ifBlank{"Неизвестный номер"},modifier=Modifier.fillMaxWidth(),fontSize=34.sp,lineHeight=39.sp,fontWeight=FontWeight.Normal,color=white,textAlign=TextAlign.Center)
                        val status=when {
                            call==null -> "Вызов завершён"
                            call.state==Call.STATE_ACTIVE -> NumberTools.duration(seconds)
                            ringing -> "Входящий вызов"
                            else -> CallStore.state(call)
                        }
                        Text(status,Modifier.padding(top=10.dp),fontSize=20.sp,color=secondary,textAlign=TextAlign.Center)
                        if(person==null) CallerId.text(this@CallActivity,key)?.let { Text(it,Modifier.padding(top=10.dp),fontSize=16.sp,color=white,textAlign=TextAlign.Center) }
                    }
                    Column(Modifier.fillMaxWidth().widthIn(max=420.dp).padding(top=28.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        live.forEach { (id,other)->if(id!=key) TextButton(onClick={selected=id}){Text("${ContactCache.find(CallStore.label(other))?.name ?: CallStore.label(other)} · ${CallStore.state(other)}",color=white,textAlign=TextAlign.Center)} }
                        if(ringing && call!=null) {
                            Control("Отклонить",Icons.Default.CallEnd,color=Color(0xFFFF453A)){if(call.state==Call.STATE_RINGING)call.reject(false,null)}
                            Spacer(Modifier.height(if(compact)24.dp else 38.dp))
                            key(key) { CallSlideAction("Ответить",Color(0xFF34C759),Icons.Default.Call){if(call.state==Call.STATE_RINGING)call.answer(VideoProfile.STATE_AUDIO_ONLY)} }
                            Spacer(Modifier.height(if(compact)12.dp else 32.dp))
                        } else if(call!=null) {
                            if(call.state==Call.STATE_SELECT_PHONE_ACCOUNT) {
                                @Suppress("DEPRECATION")
                                val accounts=call.details.extras?.getParcelableArrayList<PhoneAccountSuggestion>(Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS)?.map{it.phoneAccountHandle}.orEmpty()
                                accounts.forEach { a->Button(onClick={call.phoneAccountSelected(a,false)}){Text(Dialing.label(this@CallActivity,a))} }
                            }
                            if(keypad && call.state==Call.STATE_ACTIVE) {
                                Dialpad(call)
                                TextButton(onClick={stopTone();keypad=false}){Text("Скрыть клавиатуру",color=white)}
                            } else {
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                    Control("Микрофон",Icons.Default.MicOff,audio?.isMuted==true){CallStore.service?.setMuted(audio?.isMuted!=true)}
                                    Control("Клавиши",Icons.Default.Dialpad,enabled=call.state==Call.STATE_ACTIVE){keypad=true}
                                    Control("Динамик",Icons.Default.VolumeUp,audio?.route==CallAudioState.ROUTE_SPEAKER){CallStore.service?.setAudioRoute(if(audio?.route==CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)}
                                }
                                Spacer(Modifier.height(24.dp))
                                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                                    Control("Добавить",Icons.Default.Add){startActivity(Intent(this@CallActivity,MainActivity::class.java).setAction(Intent.ACTION_DIAL))}
                                    Control("Удержание",Icons.Default.Pause,call.state==Call.STATE_HOLDING,enabled=call.details.can(Call.Details.CAPABILITY_HOLD)){if(call.state==Call.STATE_HOLDING)call.unhold() else call.hold()}
                                    Control("Аудио",Icons.Default.Bluetooth,audio?.route==CallAudioState.ROUTE_BLUETOOTH){
                                        val routes=listOf(CallAudioState.ROUTE_EARPIECE to "Телефон",CallAudioState.ROUTE_SPEAKER to "Динамик",CallAudioState.ROUTE_BLUETOOTH to "Bluetooth",CallAudioState.ROUTE_WIRED_HEADSET to "Гарнитура").filter{(audio?.supportedRouteMask ?: 0) and it.first != 0}
                                        android.app.AlertDialog.Builder(this@CallActivity).setTitle("Аудиовыход").setItems(routes.map{it.second}.toTypedArray()){_,i->CallStore.service?.setAudioRoute(routes[i].first)}.show()
                                    }
                                }
                            }
                            Spacer(Modifier.height(if(compact || keypad)24.dp else 42.dp))
                            FilledIconButton(onClick={call.disconnect()},modifier=Modifier.size(80.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFFFF453A),contentColor=white)){Icon(Icons.Default.CallEnd,"Завершить вызов",Modifier.size(36.dp))}
                            Spacer(Modifier.height(if(compact)8.dp else 24.dp))
                        }
                    }
                }
            }
        }
    }
    @Composable private fun Control(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean=false,enabled:Boolean=true,color:Color=Color.White.copy(alpha=.18f),action:()->Unit) {
        Column(Modifier.width(84.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            FilledIconButton(onClick=action,enabled=enabled,modifier=Modifier.size(72.dp).semantics{if(enabled && label in listOf("Микрофон","Динамик","Удержание"))stateDescription=if(active) "Включено" else "Выключено"},colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(active)Color.White else color,contentColor=if(active)Color(0xFF18202A) else Color.White,disabledContainerColor=Color.White.copy(alpha=.08f),disabledContentColor=Color.White.copy(alpha=.3f))){Icon(icon,label,Modifier.size(30.dp))}
            Text(label,color=Color.White.copy(alpha=if(enabled)1f else .4f),fontSize=13.sp,fontWeight=FontWeight.Normal,modifier=Modifier.padding(top=8.dp),textAlign=TextAlign.Center)
        }
    }
    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
    @Composable private fun Dialpad(call:Call) {
        val letters=listOf("","ABC","DEF","GHI","JKL","MNO","PQRS","TUV","WXYZ","","+","")
        DisposableEffect(call) { onDispose { stopTone() } }
        listOf("123","456","789","*0#").forEachIndexed { row,line ->
            Row(Modifier.fillMaxWidth().padding(vertical=5.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                line.forEachIndexed { column,ch ->
                    Box(Modifier.size(68.dp).clip(CircleShape).background(Color.White.copy(alpha=.18f)).semantics {
                        contentDescription=ch.toString()
                        onClick { stopTone();toneCall=call;call.playDtmfTone(ch);android.os.Handler(mainLooper).postDelayed({if(toneCall===call)stopTone()},150);true }
                    }.pointerInteropFilter { event->when(event.action){android.view.MotionEvent.ACTION_DOWN->{stopTone();toneCall=call;call.playDtmfTone(ch)};android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL->stopTone()};true },contentAlignment=Alignment.Center) {
                        Column(horizontalAlignment=Alignment.CenterHorizontally) { Text(ch.toString(),fontSize=29.sp,color=Color.White);val sub=letters[row*3+column];if(sub.isNotEmpty())Text(sub,fontSize=9.sp,letterSpacing=1.sp,color=Color.White) }
                    }
                }
            }
        }
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_UP||keyCode==KeyEvent.KEYCODE_VOLUME_DOWN) {
            CallStore.calls.values.firstOrNull{it.state==Call.STATE_RINGING}?.let{it.reject(false,null);return true}
        }
        return super.onKeyDown(keyCode,event)
    }
}
