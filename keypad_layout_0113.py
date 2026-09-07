from pathlib import Path

path = Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s = path.read_text()
start = s.index('    @OptIn(ExperimentalFoundationApi::class)\n    @Composable private fun Keypad() {')
end = s.index('    @Composable private fun ContactCard', start)
new = r'''    @OptIn(ExperimentalFoundationApi::class)
    @Composable private fun Keypad() {
        var picked by remember { mutableStateOf(false) }
        val matches=remember(number,people,picked) { if(number.isBlank()||picked) emptyList() else people.flatMap { p->p.numbers.filter { NumberTools.key(it).contains(NumberTools.key(number)) || NumberTools.t9(p.name).contains(number) }.map { p to it } }.take(4) }
        val exact=people.firstOrNull { it.numbers.any { n->NumberTools.key(n)==NumberTools.key(number) } }
        val labels=mapOf(
            '2' to "АБВГ\nABC", '3' to "ДЕЁЖЗ\nDEF", '4' to "ИЙКЛ\nGHI",
            '5' to "МНОП\nJKL", '6' to "РСТУ\nMNO", '7' to "ФХЦЧ\nPQRS",
            '8' to "ШЩЪЫ\nTUV", '9' to "ЬЭЮЯ\nWXYZ", '0' to "+"
        )
        Column(Modifier.fillMaxSize().padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally) {
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth().height(54.dp),verticalAlignment=Alignment.CenterVertically) {
                Box(
                    Modifier.size(44.dp).clip(CircleShape).clickable {
                        if(number.isBlank()) toast("Сначала введите номер") else editor(PersonRecord(-1,"",listOf(number)))
                    },
                    contentAlignment=Alignment.Center
                ) { Icon(Icons.Default.AddCircleOutline,"Добавить контакт",Modifier.size(28.dp),tint=MaterialTheme.colorScheme.primary) }
                Text(
                    NumberTools.display(number).ifBlank { "Введите номер" },
                    Modifier.weight(1f).padding(horizontal=6.dp).combinedClickable(onClick={},onLongClick={
                        val clip=getSystemService(ClipboardManager::class.java).primaryClip
                        if(clip!=null && clip.itemCount>0) { number=NumberTools.clean(clip.getItemAt(0).coerceToText(this@MainActivity).toString());picked=false }
                    }),
                    fontSize=if(number.isBlank())22.sp else 27.sp,
                    fontWeight=FontWeight.Normal,
                    color=if(number.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
                    maxLines=1,
                    textAlign=androidx.compose.ui.text.style.TextAlign.Center
                )
                Box(
                    Modifier.size(44.dp).combinedClickable(onClick={number=number.dropLast(1);picked=false},onLongClick={number="";picked=false}),
                    contentAlignment=Alignment.Center
                ) { Icon(Icons.Default.Backspace,"Удалить; удерживать для очистки",Modifier.size(25.dp),tint=MaterialTheme.colorScheme.primary) }
            }
            if(matches.isNotEmpty()) {
                LazyColumn(Modifier.fillMaxWidth().heightIn(max=104.dp).clip(RoundedCornerShape(14.dp)).background(MaterialTheme.colorScheme.surface),contentPadding=PaddingValues(vertical=2.dp)) {
                    items(matches,key={"${it.first.id}:${it.second}"}) { (p,n)->Box(Modifier.clickable { number=NumberTools.clean(n);picked=true }) { PersonRow(p,n) } }
                }
            } else if(exact!=null) {
                Row(Modifier.padding(top=2.dp,bottom=2.dp),verticalAlignment=Alignment.CenterVertically) {
                    Photo(exact,Modifier.size(26.dp).clip(CircleShape));Spacer(Modifier.width(7.dp));Text(exact.name,style=MaterialTheme.typography.titleMedium)
                }
            } else if(number.isNotBlank()) {
                Row(Modifier.horizontalScroll(rememberScrollState())) {
                    TextButton(onClick={editor(PersonRecord(-1,"",listOf(number)))}) { Text("Создать контакт") }
                    TextButton(onClick={
                        if(people.isEmpty()) toast("Сначала создайте контакт") else android.app.AlertDialog.Builder(this@MainActivity).setTitle("Добавить номер к контакту")
                            .setItems(people.map { it.name }.toTypedArray()) { _,i->editor(people[i].copy(numbers=people[i].numbers+number)) }.show()
                    }) { Text("Добавить к существующему") }
                }
            }
            Spacer(Modifier.height(if(matches.isEmpty() && exact==null && number.isBlank())24.dp else 10.dp))
            listOf("123","456","789","*0#").forEach { line->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    line.forEach { digit->
                        Surface(
                            Modifier.padding(vertical=4.dp).size(74.dp)
                                .border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.85f),CircleShape)
                                .combinedClickable(onClick={
                                    number+=digit;picked=false
                                    if(getSharedPreferences("settings",0).getBoolean("haptic",true)) window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                },onLongClick={if(digit=='0') { number+="+";picked=false }}),
                            shape=CircleShape,
                            color=Color.Transparent
                        ) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Text(digit.toString(),fontSize=31.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurface)
                                Text(labels[digit].orEmpty(),fontSize=9.sp,lineHeight=10.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            val ignored=simRevision
            Spacer(Modifier.height(10.dp))
            Box(Modifier.size(68.dp).clip(CircleShape).background(Green).combinedClickable(onClick={dial(number)},onLongClick={Dialing.choose(this@MainActivity,number.ifBlank { null }){simRevision++}}),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Call,"Позвонить; удерживать для выбора SIM",Modifier.size(30.dp),tint=Color.White)
            }
            TextButton(onClick={Dialing.choose(this@MainActivity,number.ifBlank{null}){simRevision++}},contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) {
                Icon(Icons.Default.SimCard,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text(Dialing.selectedLabel(this@MainActivity,number),style=MaterialTheme.typography.labelMedium);Icon(Icons.Default.ExpandMore,null,Modifier.size(17.dp))
            }
            Spacer(Modifier.weight(1f))
        }
    }

'''
s = s[:start] + new + s[end:]
path.write_text(s)

gradle=Path('app/build.gradle.kts')
g=gradle.read_text()
if 'versionCode = 20' not in g or 'versionName = "0.11.2"' not in g:
    raise SystemExit('Unexpected version')
g=g.replace('versionCode = 20','versionCode = 21',1).replace('versionName = "0.11.2"','versionName = "0.11.3"',1)
gradle.write_text(g)
