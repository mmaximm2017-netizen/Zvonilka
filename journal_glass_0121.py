from pathlib import Path

# Ui.kt: optional true-glass reveal for SwipeCall, green-tinted for journal.
p=Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
s=p.read_text()
imports='''import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape\nimport com.kyant.backdrop.Backdrop\nimport com.kyant.backdrop.drawBackdrop\nimport com.kyant.backdrop.effects.blur\nimport com.kyant.backdrop.effects.lens\nimport com.kyant.backdrop.effects.vibrancy\n'''
if 'import com.kyant.backdrop.Backdrop' not in s:
    s=s.replace('import androidx.compose.foundation.shape.RoundedCornerShape\n','import androidx.compose.foundation.shape.RoundedCornerShape\n'+imports,1)
old_sig='''@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(20.dp),containerColor:Color=MaterialTheme.colorScheme.surface,content:@Composable ()->Unit) {'''
new_sig='''@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(20.dp),containerColor:Color=MaterialTheme.colorScheme.surface,glassBackdrop:Backdrop?=null,greenGlass:Boolean=false,content:@Composable ()->Unit) {'''
if old_sig not in s: raise SystemExit('SwipeCall signature not found')
s=s.replace(old_sig,new_sig,1)
old_bg='''        Box(Modifier.matchParentSize().background(if(offset>0f) Ocean else containerColor)) {\n            if(offset>0f) Icon(Icons.Default.Call,null,Modifier.align(Alignment.CenterStart).padding(start=24.dp),tint=Color.White)\n        }'''
new_bg='''        val reveal=if(offset>0f) (offset/width.toFloat()).coerceIn(0f,1f) else 0f\n        val glassShape=AbsoluteRoundedCornerShape(18.dp)\n        val revealModifier=if(glassBackdrop!=null && greenGlass) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Ocean else containerColor)\n        Box(revealModifier) {\n            if(offset>0f) {\n                Icon(Icons.Default.Call,null,Modifier.align(Alignment.CenterStart).padding(start=24.dp),tint=Color.White)\n                if(greenGlass) Text("Вызов",Modifier.align(Alignment.CenterStart).padding(start=58.dp),color=Color.White.copy(alpha=(.45f+.55f*reveal).coerceAtMost(1f)),fontWeight=FontWeight.SemiBold,fontSize=13.sp)\n            }\n        }'''
if old_bg not in s: raise SystemExit('SwipeCall background not found')
s=s.replace(old_bg,new_bg,1)
p.write_text(s)

# MainActivity.kt: give only journal swipes access to the app backdrop.
p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()
s=s.replace('''tab==0 -> HistoryList(history)''','''tab==0 -> HistoryList(history,glassBackdrop)''',1)
s=s.replace('''    @Composable private fun HistoryList(rows:List<HistoryRecord>) {''','''    @Composable private fun HistoryList(rows:List<HistoryRecord>,glassBackdrop:Backdrop) {''',1)
old='''SwipeCall(shape=RoundedCornerShape(0.dp),containerColor=MaterialTheme.colorScheme.background,enabled=!editMode,onCall={dial(h.number)},onTap={if(editMode) selectRow() else expanded=if(expanded==h.id) null else h.id},onLong={contextCall=h}) {'''
new='''SwipeCall(shape=RoundedCornerShape(0.dp),containerColor=MaterialTheme.colorScheme.background,glassBackdrop=glassBackdrop,greenGlass=true,enabled=!editMode,onCall={dial(h.number)},onTap={if(editMode) selectRow() else expanded=if(expanded==h.id) null else h.id},onLong={contextCall=h}) {'''
if old not in s: raise SystemExit('history SwipeCall not found')
s=s.replace(old,new,1)
p.write_text(s)

# version bump
p=Path('app/build.gradle.kts')
s=p.read_text().replace('versionCode = 28','versionCode = 29').replace('versionName = "0.12.0"','versionName = "0.12.1"')
p.write_text(s)
