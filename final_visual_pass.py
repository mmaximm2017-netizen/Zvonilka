from pathlib import Path

main = Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s = main.read_text()
replacements = [
    (
        'Surface(shape=RoundedCornerShape(6.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.65f)) {\n            Row(Modifier.padding(horizontal=5.dp,vertical=1.dp),verticalAlignment=Alignment.CenterVertically) {\n                Icon(Icons.Default.SimCard,null,Modifier.size(10.dp),tint=MaterialTheme.colorScheme.primary)\n                Spacer(Modifier.width(2.dp));Text(label,fontSize=9.sp,color=MaterialTheme.colorScheme.primary,maxLines=1)\n            }\n        }',
        'Surface(shape=RoundedCornerShape(5.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.28f)) {\n            Row(Modifier.padding(horizontal=4.dp,vertical=1.dp),verticalAlignment=Alignment.CenterVertically) {\n                Icon(Icons.Default.SimCard,null,Modifier.size(9.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)\n                Spacer(Modifier.width(2.dp));Text(label,fontSize=8.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)\n            }\n        }'
    ),
    (
        'SwipeCall(shape=RoundedCornerShape(0.dp),enabled=!editMode,onCall={dial(h.number)},onTap={if(editMode) selectRow() else expanded=if(expanded==h.id) null else h.id},onLong={contextCall=h}) {',
        'SwipeCall(shape=RoundedCornerShape(0.dp),containerColor=MaterialTheme.colorScheme.background,enabled=!editMode,onCall={dial(h.number)},onTap={if(editMode) selectRow() else expanded=if(expanded==h.id) null else h.id},onLong={contextCall=h}) {'
    ),
    ('            Spacer(Modifier.height(44.dp))\n            listOf("123","456","789","*0#")', '            Spacer(Modifier.height(18.dp))\n            listOf("123","456","789","*0#")'),
    (
        'Text("При включении неизвестный входящий номер передаётся PhoneBlock через интернет. Контакты и журнал не загружаются; звонки не блокируются.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)',
        'Text("Неизвестный номер проверяется через PhoneBlock. Контакты и журнал не передаются; звонки не блокируются.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)'
    ),
    ('TextButton(onClick={startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://phoneblock.net/phoneblock/")))}) { Text("О сервисе PhoneBlock") }', 'TextButton(onClick={startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://phoneblock.net/phoneblock/")))},contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp)) { Text("О сервисе PhoneBlock",fontSize=12.sp) }')
]
for old, new in replacements:
    if old not in s:
        raise SystemExit('Missing MainActivity snippet: ' + old[:100])
    s = s.replace(old, new, 1)
main.write_text(s)

ui = Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
u = ui.read_text()
ui_replacements = [
    ('Text(text,Modifier.padding(start=4.dp,top=12.dp,bottom=6.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)', 'Text(text,Modifier.padding(start=4.dp,top=10.dp,bottom=4.dp),style=MaterialTheme.typography.labelLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)'),
    ('Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp),content=content)', 'Column(Modifier.padding(15.dp),verticalArrangement=Arrangement.spacedBy(8.dp),content=content)'),
    ('@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(20.dp),content:@Composable ()->Unit) {', '@Composable fun SwipeCall(modifier:Modifier=Modifier,onCall:()->Unit,onTap:()->Unit,onLong:()->Unit={},enabled:Boolean=true,shape:androidx.compose.ui.graphics.Shape=RoundedCornerShape(20.dp),containerColor:Color=MaterialTheme.colorScheme.surface,content:@Composable ()->Unit) {'),
    ('Box(Modifier.matchParentSize().background(if(offset.value>0f) Ocean else MaterialTheme.colorScheme.surface)) {', 'Box(Modifier.matchParentSize().background(if(offset.value>0f) Ocean else containerColor)) {'),
    ('Box(Modifier.offset { IntOffset(offset.value.toInt(),0) }.fillMaxWidth().background(MaterialTheme.colorScheme.surface)', 'Box(Modifier.offset { IntOffset(offset.value.toInt(),0) }.fillMaxWidth().background(containerColor)')
]
for old, new in ui_replacements:
    if old not in u:
        raise SystemExit('Missing Ui snippet: ' + old[:100])
    u = u.replace(old, new, 1)
ui.write_text(u)

gradle = Path('app/build.gradle.kts')
g = gradle.read_text()
if 'versionCode = 19' not in g or 'versionName = "0.11.1"' not in g:
    raise SystemExit('Unexpected app version')
g = g.replace('versionCode = 19', 'versionCode = 20', 1).replace('versionName = "0.11.1"', 'versionName = "0.11.2"', 1)
gradle.write_text(g)
