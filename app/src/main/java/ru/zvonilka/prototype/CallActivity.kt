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
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.unit.*
import kotlinx.coroutines.delay

class CallActivity : ComponentActivity() {
    private var revision by mutableIntStateOf(0)
    private var selected by mutableStateOf<String?>(null)
    private var toneCall:Call?=null
    private var proximity:PowerManager.WakeLock?=null
    private val listener:()->Unit={revision++}
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState);enableEdgeToEdge(statusBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),navigationBarStyle=SystemBarStyle.dark(android.graphics.Color.TRANSPARENT));setShowWhenLocked(true);setTurnScreenOn(true)
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
        var seconds by remember { mutableLongStateOf(0) }
        var keypad by remember { mutableStateOf(false) }
        val person=call?.let { ContactCache.find(CallStore.label(it)) } ?: lastPerson
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
        Box(Modifier.fillMaxSize().background(Color(0xFF102B4C))) {
            Photo(person,Modifier.fillMaxSize(),true)
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xA6071A31),Color(0x660B2442),Color(0xE6071A31)))))
            Column(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars).padding(24.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                Spacer(Modifier.height(36.dp))
                Text(person?.name ?: NumberTools.display(call?.let{CallStore.label(it)} ?: lastNumber).ifBlank{"Неизвестный номер"},fontSize=32.sp,fontWeight=FontWeight.Bold,color=Color.White)
                Spacer(Modifier.height(12.dp))
                Text(call?.details?.accountHandle?.let{Dialing.label(this@CallActivity,it)} ?: "",color=Color.White.copy(alpha=.8f))
                Text(if(call==null) "Разговор ${NumberTools.duration(seconds)}" else CallStore.state(call),Modifier.padding(top=12.dp),fontSize=22.sp,color=Color.White)
                if(call?.state==Call.STATE_ACTIVE) Text(NumberTools.duration(seconds),color=Color.White,fontSize=22.sp)
                Spacer(Modifier.height(64.dp))
                live.forEach { (id,other)->if(id!=key) TextButton(onClick={selected=id}){Text("${ContactCache.find(CallStore.label(other))?.name ?: CallStore.label(other)} · ${CallStore.state(other)}",color=Color.White)} }
                if(call?.state==Call.STATE_RINGING) {
                    Text("Проведите почти до конца вправо",color=Color.White.copy(alpha=.75f),fontSize=14.sp)
                    Spacer(Modifier.height(16.dp))
                    SwipeCall(onCall={call.answer(VideoProfile.STATE_AUDIO_ONLY)},onTap={}) { Box(Modifier.fillMaxWidth().background(Green).padding(20.dp),contentAlignment=Alignment.Center){Text("→  Ответить",color=Color.White,fontSize=22.sp)} }
                    Spacer(Modifier.height(16.dp))
                    SwipeCall(onCall={call.reject(false,null)},onTap={}) { Box(Modifier.fillMaxWidth().background(Red).padding(20.dp),contentAlignment=Alignment.Center){Text("→  Отклонить",color=Color.White,fontSize=22.sp)} }
                } else if(call!=null) {
                    if(call.state==Call.STATE_SELECT_PHONE_ACCOUNT) {
                        @Suppress("DEPRECATION")
                        val accounts=call.details.extras?.getParcelableArrayList<PhoneAccountSuggestion>(Call.EXTRA_SUGGESTED_PHONE_ACCOUNTS)?.map{it.phoneAccountHandle}.orEmpty()
                        accounts.forEach { a->Button(onClick={call.phoneAccountSelected(a,false)}){Text(Dialing.label(this@CallActivity,a))} }
                    }
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        Control("Микрофон",if(audio?.isMuted==true) Icons.Default.MicOff else Icons.Default.Mic,audio?.isMuted==true){CallStore.service?.setMuted(audio?.isMuted!=true)}
                        Control("Динамик",Icons.Default.VolumeUp,audio?.route==CallAudioState.ROUTE_SPEAKER){CallStore.service?.setAudioRoute(if(audio?.route==CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)}
                        Control("Аудиовыход",Icons.Default.Bluetooth){
                            val routes=listOf(CallAudioState.ROUTE_EARPIECE to "Телефон",CallAudioState.ROUTE_SPEAKER to "Динамик",CallAudioState.ROUTE_BLUETOOTH to "Bluetooth",CallAudioState.ROUTE_WIRED_HEADSET to "Гарнитура").filter{(audio?.supportedRouteMask ?: 0) and it.first != 0}
                            android.app.AlertDialog.Builder(this@CallActivity).setTitle("Аудиовыход").setItems(routes.map{it.second}.toTypedArray()){_,i->CallStore.service?.setAudioRoute(routes[i].first)}.show()
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                        Control("Клавиатура",Icons.Default.Dialpad,keypad){keypad=!keypad}
                        if(call.details.can(Call.Details.CAPABILITY_HOLD)) Control("Удержание",Icons.Default.Pause,call.state==Call.STATE_HOLDING){if(call.state==Call.STATE_HOLDING)call.unhold() else call.hold()}
                        Control("Добавить",Icons.Default.Add){
                            // MainActivity is not showWhenLocked: leaving the call requires unlock.
                            startActivity(Intent(this@CallActivity,MainActivity::class.java).setAction(Intent.ACTION_DIAL))
                        }
                    }
                    if(keypad && call.state==Call.STATE_ACTIVE) listOf("123","456","789","*0#").forEach{line->Row {
                        line.forEach{ch->Box(Modifier.weight(1f).height(60.dp).pointerInteropFilter { event->when(event.action){android.view.MotionEvent.ACTION_DOWN->{stopTone();toneCall=call;call.playDtmfTone(ch)};android.view.MotionEvent.ACTION_UP,android.view.MotionEvent.ACTION_CANCEL->stopTone()};true },contentAlignment=Alignment.Center){Text(ch.toString(),fontSize=28.sp,color=Color.White)}}
                    }}
                    Spacer(Modifier.height(44.dp))
                    FilledIconButton(onClick={call.disconnect()},modifier=Modifier.size(80.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=Red,contentColor=Color.White)){Icon(Icons.Default.CallEnd,"Завершить",Modifier.size(34.dp))}
                }
            }
        }
    }
    @Composable private fun Control(label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean=false,action:()->Unit) {
        Column(horizontalAlignment=Alignment.CenterHorizontally) {
            FilledIconButton(onClick=action,modifier=Modifier.size(64.dp),colors=IconButtonDefaults.filledIconButtonColors(containerColor=if(active)Color(0xFFDCEEFF) else Color(0xCC153E68),contentColor=if(active)Color(0xFF123D70) else Color.White)){Icon(icon,label)}
            Text(label,color=Color.White,fontSize=12.sp,modifier=Modifier.padding(top=6.dp))
        }
    }
    override fun onKeyDown(keyCode:Int,event:KeyEvent):Boolean {
        if(keyCode==KeyEvent.KEYCODE_VOLUME_UP||keyCode==KeyEvent.KEYCODE_VOLUME_DOWN) {
            CallStore.calls.values.firstOrNull{it.state==Call.STATE_RINGING}?.let{it.reject(false,null);return true}
        }
        return super.onKeyDown(keyCode,event)
    }
}
