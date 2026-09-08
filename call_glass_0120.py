from pathlib import Path

# --- CallActivity ---
p = Path('app/src/main/java/ru/zvonilka/prototype/CallActivity.kt')
s = p.read_text()

imports = '''import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape\nimport com.kyant.backdrop.Backdrop\nimport com.kyant.backdrop.backdrops.layerBackdrop\nimport com.kyant.backdrop.backdrops.rememberLayerBackdrop\nimport com.kyant.backdrop.drawBackdrop\nimport com.kyant.backdrop.effects.blur\nimport com.kyant.backdrop.effects.lens\nimport com.kyant.backdrop.effects.vibrancy\n'''
if 'import com.kyant.backdrop.Backdrop' not in s:
    s = s.replace('import androidx.compose.foundation.shape.CircleShape\n', 'import androidx.compose.foundation.shape.CircleShape\n' + imports, 1)

s = s.replace('''        var keypad by remember(key) { mutableStateOf(false) }\n        val person=if(call!=null) ContactCache.find(CallStore.label(call)) else lastPerson\n''','''        var keypad by remember(key) { mutableStateOf(false) }\n        var audioPicker by remember(key) { mutableStateOf(false) }\n        val glassBackdrop=rememberLayerBackdrop()\n        val person=if(call!=null) ContactCache.find(CallStore.label(call)) else lastPerson\n''',1)

old_root='''        Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFF34536B),Color(0xFF182A40),Color(0xFF101722))))) {\n            if(person!=null) Photo(person,Modifier.fillMaxSize(),true)\n            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x70070D18),Color(0x20070D18),Color(0xD909101B)))))\n            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {\n'''
new_root='''        Box(Modifier.fillMaxSize()) {\n            Box(Modifier.fillMaxSize().layerBackdrop(glassBackdrop).background(Brush.verticalGradient(listOf(Color(0xFF34536B),Color(0xFF182A40),Color(0xFF101722))))) {\n                if(person!=null) Photo(person,Modifier.fillMaxSize(),true)\n                Box(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0x70070D18),Color(0x20070D18),Color(0xD909101B)))))\n            }\n            BoxWithConstraints(Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.systemBars)) {\n'''
if old_root not in s:
    raise SystemExit('call screen root pattern not found')
s=s.replace(old_root,new_root,1)

s=s.replace('''key(key) { CallSlideAction("Ответить",Color(0xFF34C759),Icons.Default.Call){if(call.state==Call.STATE_RINGING)call.answer(VideoProfile.STATE_AUDIO_ONLY)} }''','''key(key) { CallSlideAction("Ответить",Color(0xFF34C759),Icons.Default.Call,glassBackdrop){if(call.state==Call.STATE_RINGING)call.answer(VideoProfile.STATE_AUDIO_ONLY)} }''',1)

s=s.replace('''accounts.forEach { a->Button(onClick={call.phoneAccountSelected(a,false)}){Text(Dialing.label(this@CallActivity,a))} }''','''accounts.forEach { a->GlassChoiceButton(glassBackdrop,Dialing.label(this@CallActivity,a)){call.phoneAccountSelected(a,false)} }''',1)

# Dialpad gets the same backdrop.
s=s.replace('''                                        Dialpad(call)''','''                                        Dialpad(call,glassBackdrop)''',1)

# All call controls get true glass.
s=s.replace('''Control("Микрофон",Icons.Default.MicOff,audio?.isMuted==true){CallStore.service?.setMuted(audio?.isMuted!=true)}''','''Control(glassBackdrop,"Микрофон",Icons.Default.MicOff,audio?.isMuted==true){CallStore.service?.setMuted(audio?.isMuted!=true)}''',1)
s=s.replace('''Control("Клавиши",Icons.Default.Dialpad,enabled=call.state==Call.STATE_ACTIVE){keypad=true}''','''Control(glassBackdrop,"Клавиши",Icons.Default.Dialpad,enabled=call.state==Call.STATE_ACTIVE){keypad=true}''',1)
s=s.replace('''Control("Динамик",Icons.Default.VolumeUp,audio?.route==CallAudioState.ROUTE_SPEAKER){CallStore.service?.setAudioRoute(if(audio?.route==CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)}''','''Control(glassBackdrop,"Динамик",Icons.Default.VolumeUp,audio?.route==CallAudioState.ROUTE_SPEAKER){CallStore.service?.setAudioRoute(if(audio?.route==CallAudioState.ROUTE_SPEAKER) CallAudioState.ROUTE_WIRED_OR_EARPIECE else CallAudioState.ROUTE_SPEAKER)}''',1)
s=s.replace('''Control("Добавить",Icons.Default.Add){startActivity(Intent(this@CallActivity,MainActivity::class.java).setAction(Intent.ACTION_DIAL))}''','''Control(glassBackdrop,"Добавить",Icons.Default.Add){startActivity(Intent(this@CallActivity,MainActivity::class.java).setAction(Intent.ACTION_DIAL))}''',1)
s=s.replace('''Control("Удержание",Icons.Default.Pause,call.state==Call.STATE_HOLDING,enabled=call.details.can(Call.Details.CAPABILITY_HOLD)){if(call.state==Call.STATE_HOLDING)call.unhold() else call.hold()}''','''Control(glassBackdrop,"Удержание",Icons.Default.Pause,call.state==Call.STATE_HOLDING,enabled=call.details.can(Call.Details.CAPABILITY_HOLD)){if(call.state==Call.STATE_HOLDING)call.unhold() else call.hold()}''',1)
old_audio='''                                            Control("Аудио",Icons.Default.Bluetooth,audio?.route==CallAudioState.ROUTE_BLUETOOTH){\n                                                val routes=listOf(CallAudioState.ROUTE_EARPIECE to "Телефон",CallAudioState.ROUTE_SPEAKER to "Динамик",CallAudioState.ROUTE_BLUETOOTH to "Bluetooth",CallAudioState.ROUTE_WIRED_HEADSET to "Гарнитура").filter{(audio?.supportedRouteMask ?: 0) and it.first != 0}\n                                                android.app.AlertDialog.Builder(this@CallActivity).setTitle("Аудиовыход").setItems(routes.map{it.second}.toTypedArray()){_,i->CallStore.service?.setAudioRoute(routes[i].first)}.show()\n                                            }'''
new_audio='''                                            Control(glassBackdrop,"Аудио",Icons.Default.Bluetooth,audio?.route==CallAudioState.ROUTE_BLUETOOTH){audioPicker=true}'''
if old_audio not in s:
    raise SystemExit('audio control pattern not found')
s=s.replace(old_audio,new_audio,1)

# Red hangup stays unmistakably red, but sits on a real glass halo.
old_hang='''                            FilledIconButton(onClick={call.disconnect()},interactionSource=hangupInteraction,modifier=Modifier.size(80.dp).graphicsLayer{scaleX=hangupScale;scaleY=hangupScale},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFFFF453A),contentColor=white)){Icon(Icons.Default.CallEnd,"Завершить вызов",Modifier.size(36.dp))}'''
new_hang='''                            Box(Modifier.size(94.dp).drawBackdrop(\n                                backdrop=glassBackdrop,\n                                shape={AbsoluteRoundedCornerShape(47.dp)},\n                                effects={vibrancy();blur(8.dp.toPx());lens(15.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                                onDrawSurface={drawRect(Color.White.copy(alpha=.10f))}\n                            ),contentAlignment=Alignment.Center) {\n                                FilledIconButton(onClick={call.disconnect()},interactionSource=hangupInteraction,modifier=Modifier.size(80.dp).graphicsLayer{scaleX=hangupScale;scaleY=hangupScale},colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color(0xFFFF453A),contentColor=white)){Icon(Icons.Default.CallEnd,"Завершить вызов",Modifier.size(36.dp))}\n                            }'''
if old_hang not in s:
    raise SystemExit('hangup pattern not found')
s=s.replace(old_hang,new_hang,1)

# Add audio glass sheet over the controls before closing the outer root Box.
needle='''            }\n        }\n    }\n    @Composable private fun Control('''
audio_sheet='''            }\n            if(audioPicker && call!=null) {\n                val routes=listOf(\n                    CallAudioState.ROUTE_EARPIECE to "Телефон",\n                    CallAudioState.ROUTE_SPEAKER to "Динамик",\n                    CallAudioState.ROUTE_BLUETOOTH to "Bluetooth",\n                    CallAudioState.ROUTE_WIRED_HEADSET to "Гарнитура"\n                ).filter{(audio?.supportedRouteMask ?: 0) and it.first != 0}\n                Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha=.24f)).clickable{audioPicker=false}) {\n                    Column(\n                        Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(16.dp)\n                            .drawBackdrop(\n                                backdrop=glassBackdrop,\n                                shape={AbsoluteRoundedCornerShape(30.dp)},\n                                effects={vibrancy();blur(14.dp.toPx());lens(18.dp.toPx(),13.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                                onDrawSurface={drawRect(Color(0xFF0A1320).copy(alpha=.30f))}\n                            )\n                            .border(1.dp,Color.White.copy(alpha=.20f),AbsoluteRoundedCornerShape(30.dp))\n                            .padding(18.dp)\n                            .clickable(enabled=false){},\n                        horizontalAlignment=Alignment.CenterHorizontally\n                    ) {\n                        Text("Аудиовыход",color=Color.White,fontSize=20.sp,fontWeight=FontWeight.SemiBold)\n                        Spacer(Modifier.height(10.dp))\n                        routes.forEach { (route,name)->\n                            val active=audio?.route==route\n                            TextButton(\n                                onClick={CallStore.service?.setAudioRoute(route);audioPicker=false},\n                                modifier=Modifier.fillMaxWidth().height(50.dp)\n                            ){\n                                Text(name,color=if(active)Color.White else Color.White.copy(alpha=.82f),fontWeight=if(active)FontWeight.SemiBold else FontWeight.Normal)\n                            }\n                        }\n                    }\n                }\n            }\n        }\n    }\n    @Composable private fun Control('''
if needle not in s:
    raise SystemExit('control insertion pattern not found')
s=s.replace(needle,audio_sheet,1)

# Replace control implementation.
start=s.index('    @Composable private fun Control(')
end=s.index('    @OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)\n    @Composable private fun Dialpad',start)
control='''    @Composable private fun Control(backdrop:Backdrop,label:String,icon:androidx.compose.ui.graphics.vector.ImageVector,active:Boolean=false,enabled:Boolean=true,action:()->Unit) {\n        val interaction=remember { MutableInteractionSource() }\n        val pressed by interaction.collectIsPressedAsState()\n        val scale by animateFloatAsState(if(pressed && enabled).97f else 1f,tween(120),label="controlPress")\n        val shape=AbsoluteRoundedCornerShape(36.dp)\n        Column(Modifier.width(84.dp),horizontalAlignment=Alignment.CenterHorizontally) {\n            FilledIconButton(\n                onClick=action,enabled=enabled,interactionSource=interaction,\n                modifier=Modifier.size(72.dp).drawBackdrop(\n                    backdrop=backdrop,shape={shape},\n                    effects={vibrancy();blur(9.dp.toPx());lens(15.dp.toPx(),11.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                    onDrawSurface={drawRect(if(active)Color.White.copy(alpha=.72f) else Color.White.copy(alpha=.12f))}\n                ).border(1.dp,Color.White.copy(alpha=if(active).72f else .18f),shape).graphicsLayer{scaleX=scale;scaleY=scale}.semantics{if(enabled && label in listOf("Микрофон","Динамик","Удержание"))stateDescription=if(active) "Включено" else "Выключено"},\n                colors=IconButtonDefaults.filledIconButtonColors(containerColor=Color.Transparent,contentColor=if(active)Color(0xFF18202A) else Color.White,disabledContainerColor=Color.Transparent,disabledContentColor=Color.White.copy(alpha=.3f))\n            ){Icon(icon,label,Modifier.size(30.dp))}\n            Text(label,color=Color.White.copy(alpha=if(enabled)1f else .4f),fontSize=13.sp,fontWeight=FontWeight.Normal,modifier=Modifier.padding(top=8.dp),textAlign=TextAlign.Center)\n        }\n    }\n\n    @Composable private fun GlassChoiceButton(backdrop:Backdrop,label:String,onClick:()->Unit) {\n        val shape=AbsoluteRoundedCornerShape(24.dp)\n        TextButton(\n            onClick=onClick,\n            modifier=Modifier.fillMaxWidth().padding(vertical=4.dp).height(52.dp).drawBackdrop(\n                backdrop=backdrop,shape={shape},\n                effects={vibrancy();blur(10.dp.toPx());lens(13.dp.toPx(),9.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color.White.copy(alpha=.12f))}\n            ).border(1.dp,Color.White.copy(alpha=.18f),shape)\n        ){Text(label,color=Color.White,fontSize=16.sp)}\n    }\n\n'''
s=s[:start]+control+s[end:]

# Replace dialpad signature and visual circles.
s=s.replace('''    @Composable private fun Dialpad(call:Call) {''','''    @Composable private fun Dialpad(call:Call,backdrop:Backdrop) {''',1)
old_digit='''                    Box(Modifier.size(68.dp).clip(CircleShape).background(Color.White.copy(alpha=.18f)).semantics {'''
new_digit='''                    val keyShape=AbsoluteRoundedCornerShape(34.dp)\n                    Box(Modifier.size(68.dp).drawBackdrop(\n                        backdrop=backdrop,shape={keyShape},\n                        effects={vibrancy();blur(8.dp.toPx());lens(13.dp.toPx(),9.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                        onDrawSurface={drawRect(Color.White.copy(alpha=.12f))}\n                    ).border(1.dp,Color.White.copy(alpha=.16f),keyShape).semantics {'''
if old_digit not in s:
    raise SystemExit('dialpad key pattern not found')
s=s.replace(old_digit,new_digit,1)

p.write_text(s)

# --- CallSlideAction ---
p=Path('app/src/main/java/ru/zvonilka/prototype/CallSlideAction.kt')
s=p.read_text()
extra='''import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape\nimport com.kyant.backdrop.Backdrop\nimport com.kyant.backdrop.drawBackdrop\nimport com.kyant.backdrop.effects.blur\nimport com.kyant.backdrop.effects.lens\nimport com.kyant.backdrop.effects.vibrancy\n'''
if 'import com.kyant.backdrop.Backdrop' not in s:
    s=s.replace('import androidx.compose.foundation.shape.CircleShape\n','import androidx.compose.foundation.shape.CircleShape\n'+extra,1)
s=s.replace('''@Composable fun CallSlideAction(label:String,color:Color,icon:ImageVector,onComplete:()->Unit) {''','''@Composable fun CallSlideAction(label:String,color:Color,icon:ImageVector,backdrop:Backdrop,onComplete:()->Unit) {''',1)
old_track='''    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).clip(CircleShape).background(Color.White.copy(alpha=.18f)).border(1.dp,Color.White.copy(alpha=.12f),CircleShape).semantics {'''
new_track='''    val trackShape=AbsoluteRoundedCornerShape(36.dp)\n    BoxWithConstraints(Modifier.fillMaxWidth().height(72.dp).drawBackdrop(\n        backdrop=backdrop,shape={trackShape},\n        effects={vibrancy();blur(12.dp.toPx());lens(17.dp.toPx(),12.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n        onDrawSurface={drawRect(Color.White.copy(alpha=.10f))}\n    ).border(1.dp,Color.White.copy(alpha=.18f),trackShape).semantics {'''
if old_track not in s:
    raise SystemExit('slider track pattern not found')
s=s.replace(old_track,new_track,1)
old_thumb='''        Surface(\n            Modifier.padding(4.dp).offset{IntOffset(offset.roundToInt(),0)}.size(64.dp).draggable('''
new_thumb='''        val thumbShape=AbsoluteRoundedCornerShape(32.dp)\n        Surface(\n            Modifier.padding(4.dp).offset{IntOffset(offset.roundToInt(),0)}.size(64.dp).drawBackdrop(\n                backdrop=backdrop,shape={thumbShape},\n                effects={vibrancy();blur(6.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(color.copy(alpha=.78f))}\n            ).border(1.dp,Color.White.copy(alpha=.24f),thumbShape).draggable('''
if old_thumb not in s:
    raise SystemExit('slider thumb pattern not found')
s=s.replace(old_thumb,new_thumb,1)
s=s.replace('''            ),shape=CircleShape,color=color\n''','''            ),shape=thumbShape,color=Color.Transparent\n''',1)
p.write_text(s)

# --- version ---
p=Path('app/build.gradle.kts')
s=p.read_text().replace('versionCode = 27','versionCode = 28').replace('versionName = "0.11.9"','versionName = "0.12.0"')
p.write_text(s)
