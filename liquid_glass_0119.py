from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()

s=s.replace('import androidx.compose.foundation.shape.RoundedCornerShape\n','import androidx.compose.foundation.shape.RoundedCornerShape\nimport androidx.compose.foundation.shape.AbsoluteRoundedCornerShape\n',1)
anchor='import androidx.compose.ui.unit.*\n'
imports='''import com.kyant.backdrop.Backdrop\nimport com.kyant.backdrop.backdrops.layerBackdrop\nimport com.kyant.backdrop.backdrops.rememberLayerBackdrop\nimport com.kyant.backdrop.drawBackdrop\nimport com.kyant.backdrop.effects.blur\nimport com.kyant.backdrop.effects.lens\nimport com.kyant.backdrop.effects.vibrancy\n'''
if imports not in s:
    s=s.replace(anchor,anchor+imports,1)

s=s.replace('''        val scope=rememberCoroutineScope()\n        BackHandler(settings || selected!=null) { settings=false;selected=null }\n        Scaffold(\n''','''        val scope=rememberCoroutineScope()\n        val glassBackdrop=rememberLayerBackdrop()\n        val showGlassBar=!settings && selected==null\n        BackHandler(settings || selected!=null) { settings=false;selected=null }\n        Box(Modifier.fillMaxSize()) {\n        Scaffold(\n''',1)
s=s.replace('''            bottomBar={ if(!settings && selected==null) GlassBottomBar() }\n''','''            bottomBar={}\n''',1)
s=s.replace('''            Column(Modifier.padding(padding).fillMaxSize()) {\n''','''            Column(Modifier.padding(padding).fillMaxSize().layerBackdrop(glassBackdrop)) {\n''',1)
s=s.replace('''        }\n        if(editing) Editor()\n''','''        }\n        if(showGlassBar) GlassBottomBar(glassBackdrop,Modifier.align(Alignment.BottomCenter))\n        }\n        if(editing) Editor()\n''',1)

start=s.index('    @Composable private fun GlassBottomBar() {')
end=s.index('    @Composable private fun SimBadge',start)
bar='''    @Composable private fun GlassBottomBar(backdrop:Backdrop,modifier:Modifier=Modifier) {\n        val dark=isSystemInDarkTheme()\n        val labels=listOf("Недавние","Контакты","Клавиши")\n        val icons=listOf(Icons.Outlined.History,Icons.Outlined.Contacts,Icons.Outlined.Dialpad)\n        Box(\n            modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal=12.dp,vertical=8.dp),\n            contentAlignment=Alignment.Center\n        ) {\n            Row(\n                Modifier\n                    .fillMaxWidth()\n                    .height(70.dp)\n                    .drawBackdrop(\n                        backdrop=backdrop,\n                        shape={ AbsoluteRoundedCornerShape(30.dp) },\n                        effects={\n                            vibrancy()\n                            blur(12.dp.toPx())\n                            lens(18.dp.toPx(),14.dp.toPx(),depthEffect=true,chromaticAberration=true)\n                        },\n                        onDrawSurface={\n                            drawRect(if(dark) Color(0xFF07111F).copy(alpha=.28f) else Color.White.copy(alpha=.24f))\n                        }\n                    )\n                    .border(\n                        BorderStroke(1.dp,if(dark) Color.White.copy(alpha=.18f) else Color.White.copy(alpha=.72f)),\n                        AbsoluteRoundedCornerShape(30.dp)\n                    )\n                    .padding(5.dp),\n                verticalAlignment=Alignment.CenterVertically\n            ) {\n                icons.forEachIndexed { i,icon->\n                    val active=tab==i\n                    Box(\n                        Modifier.weight(1f).fillMaxHeight().clip(RoundedCornerShape(24.dp)).background(\n                            if(active) MaterialTheme.colorScheme.primaryContainer.copy(alpha=if(dark).42f else .38f) else Color.Transparent\n                        ).clickable {\n                            tab=i\n                            if(i==0) MissedCalls.clear(this@MainActivity)\n                        },\n                        contentAlignment=Alignment.Center\n                    ) {\n                        Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {\n                            BadgedBox(badge={if(i==0 && missedCount>0) Badge { Text(if(missedCount>99)"99+" else missedCount.toString()) }}) {\n                                Icon(\n                                    icon,labels[i],Modifier.size(if(active)23.dp else 22.dp),\n                                    tint=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.84f)\n                                )\n                            }\n                            Spacer(Modifier.height(3.dp))\n                            Text(\n                                labels[i],fontSize=10.sp,\n                                fontWeight=if(active) FontWeight.SemiBold else FontWeight.Normal,\n                                color=if(active) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha=.84f)\n                            )\n                        }\n                    }\n                }\n            }\n        }\n    }\n\n'''
s=s[:start]+bar+s[end:]

# Let rows scroll beneath the glass while keeping the final item reachable above it.
s=s.replace('contentPadding=PaddingValues(horizontal=16.dp,vertical=5.dp)','contentPadding=PaddingValues(start=16.dp,end=16.dp,top=5.dp,bottom=110.dp)',1)
s=s.replace('contentPadding=PaddingValues(start=16.dp,end=6.dp,bottom=12.dp)','contentPadding=PaddingValues(start=16.dp,end=6.dp,bottom=110.dp)',1)
s=s.replace('Column(Modifier.fillMaxSize().padding(horizontal=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {','Column(Modifier.fillMaxSize().padding(horizontal=10.dp).padding(bottom=86.dp),horizontalAlignment=Alignment.CenterHorizontally) {',1)
p.write_text(s)

p=Path('app/build.gradle.kts')
s=p.read_text().replace('versionCode = 26','versionCode = 27').replace('versionName = "0.11.8"','versionName = "0.11.9"')
needle='    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")\n'
if 'io.github.kyant0:backdrop' not in s:
    s=s.replace(needle,needle+'    implementation("io.github.kyant0:backdrop:2.0.1")\n',1)
p.write_text(s)
