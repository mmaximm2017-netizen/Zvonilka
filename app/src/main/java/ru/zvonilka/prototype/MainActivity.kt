package ru.zvonilka.prototype

import android.Manifest
import android.app.role.RoleManager
import android.content.*
import android.os.Bundle
import android.net.Uri
import android.provider.CallLog
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Brush
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.*
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import java.text.SimpleDateFormat
import java.util.Locale

private object HistoryCache {
    @Volatile var items:List<HistoryRecord> = emptyList()
}

class MainActivity : ComponentActivity() {
    override fun attachBaseContext(base: Context) { super.attachBaseContext(ThemeSettings.wrap(base)) }
    private var pendingDelete=emptySet<Long>()
    private val deletePermission=registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        val ids=pendingDelete;pendingDelete=emptySet()
        if(granted) deleteCalls(ids) else error="Удаление недоступно: разрешите изменение журнала в настройках приложения."
    }
    private fun deleteCalls(ids:Set<Long>) {
        if(ids.isEmpty()) return
        if(!data.allowed(Manifest.permission.WRITE_CALL_LOG)) { pendingDelete=ids;deletePermission.launch(Manifest.permission.WRITE_CALL_LOG);return }
        lifecycleScope.launch {
            loading=true
            runCatching { withContext(Dispatchers.IO) { data.deleteHistory(ids) } }
                .onSuccess { history=history.filterNot{it.id in ids};toast("Записи удалены") }
                .onFailure { error="Не удалось удалить записи: ${it.message}" }
            refresh()
        }
    }
    private fun confirmDelete(ids:Set<Long>) {
        confirmation=(if(ids.size==1) "Удалить этот вызов из журнала телефона?" else "Удалить выбранные вызовы (${ids.size}) из журнала телефона?") to { deleteCalls(ids) }
    }
    private val data by lazy { PhoneData(this) }
    private var people by mutableStateOf(emptyList<PersonRecord>())
    private var history by mutableStateOf(HistoryCache.items)
    private var error by mutableStateOf<String?>(null)
    private var loading by mutableStateOf(false)
    private var tab by mutableIntStateOf(0)
    private var number by mutableStateOf("")
    private var settings by mutableStateOf(false)
    private var edit by mutableStateOf<PersonRecord?>(null)
    private var editing by mutableStateOf(false)
    private var photoPreviewName by mutableStateOf("")
    private var cropSource by mutableStateOf<ByteArray?>(null)
    private var photoOriginal:ByteArray?=null
    private var photoDraft by mutableStateOf<ByteArray?>(null)
    private var changedPhoto by mutableStateOf(false)
    private var selected by mutableStateOf<PersonRecord?>(null)
    private var confirmation by mutableStateOf<Pair<String,()->Unit>?>(null)
    private var simEditing by mutableStateOf<Pair<SimBook,SimSource?>?>(null)
    private var simStatus by mutableStateOf("")
    private var missedCount by mutableIntStateOf(0)
    private val missedListener=android.content.SharedPreferences.OnSharedPreferenceChangeListener { _,_->missedCount=MissedCalls.count(this) }
    private var callScreenIssue by mutableStateOf<String?>(null)
    private var simRevision by mutableIntStateOf(0)
    private val photoPicker=registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if(uri!=null) lifecycleScope.launch { runCatching { withContext(Dispatchers.IO) { data.photo(uri) } }.onSuccess { cropSource=it }.onFailure { error="Не удалось прочитать фото" } }
    }
    private val export=registerForActivityResult(ActivityResultContracts.CreateDocument("text/vcard")) { uri ->
        if(uri!=null) work { val bytes=Vcf.encode(data.exportContacts(people)).toByteArray(Charsets.UTF_8);contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("Файл недоступен") }
    }
    private val importFile=registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if(uri!=null) lifecycleScope.launch {
            runCatching { withContext(Dispatchers.IO) { contentResolver.openInputStream(uri)?.use { input ->
                val out=java.io.ByteArrayOutputStream();val buffer=ByteArray(8192)
                while(true) { val n=input.read(buffer);if(n<0) break;require(out.size()+n<=32_000_000){"Файл больше 32 МБ"};out.write(buffer,0,n) }
                var photoBytes=0
                Vcf.decode(out.toByteArray().toString(Charsets.UTF_8)).map { p->val photo=p.photo?.let(data::importPhoto);photoBytes+=photo?.size ?: 0;require(photoBytes<=Vcf.MAX_PHOTOS){"В VCF слишком много фотографий"};p.copy(photo=photo) }
            } ?: error("Файл недоступен") } }.onSuccess { entries ->
                val existing=people.filter{it.id>=0}.map(Vcf::contactKey).toSet()
                val fresh=entries.distinctBy(Vcf::contactKey).filter{Vcf.contactKey(it) !in existing}
                if(fresh.isEmpty()){toast("Все контакты уже есть в телефоне");return@onSuccess}
                confirmation="Добавить ${fresh.size} контактов с фото в память телефона? Пропущено совпадений: ${entries.size-fresh.size}. Существующие не заменяются." to {
                    work { var added=0; try { fresh.forEach { data.save(null,it.name,it.numbers,it.photo,it.photo!=null);added++ } } catch(e:Exception) { error("Добавлено $added из ${fresh.size}. ${e.message}") } }
                }
            }.onFailure { error=it.message }
        }
    }
    private val permissions=registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { refresh() }
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        number=savedInstanceState?.getString("number").orEmpty()
        tab=savedInstanceState?.getInt("tab") ?: 0
        settings=savedInstanceState?.getBoolean("settings") ?: false
        pendingDelete=savedInstanceState?.getLongArray("pendingDelete")?.toSet().orEmpty()
        if(savedInstanceState==null) handle(intent)
        setContent { PhoneTheme { App() } }
    }
    override fun onNewIntent(intent:Intent) { super.onNewIntent(intent);setIntent(intent);handle(intent) }
    private fun handle(intent:Intent) {
        if(intent.action=="recent") { tab=0;settings=false;selected=null }
        if(intent.action==Intent.ACTION_DIAL) { tab=2;number=NumberTools.clean(intent.data?.schemeSpecificPart.orEmpty()) }
    }
    override fun onSaveInstanceState(outState:Bundle) { outState.putString("number",number);outState.putInt("tab",tab);outState.putBoolean("settings",settings);outState.putLongArray("pendingDelete",pendingDelete.toLongArray());super.onSaveInstanceState(outState) }
    override fun onStart() {super.onStart();getSharedPreferences("missed",0).registerOnSharedPreferenceChangeListener(missedListener);missedCount=MissedCalls.count(this)}
    override fun onStop() {getSharedPreferences("missed",0).unregisterOnSharedPreferenceChangeListener(missedListener);super.onStop()}
    override fun onResume() { super.onResume();callScreenIssue=CallScreenAccess.issue(this);refresh() }
    private fun refresh() {
        lifecycleScope.launch {
            val historyLoad=async(Dispatchers.IO) { runCatching { data.history() } }
            val contactsLoad=async(Dispatchers.IO) { runCatching { data.contacts() } }

            historyLoad.await()
                .onSuccess { h-> history=h;HistoryCache.items=h;if(tab==0) MissedCalls.clear(this@MainActivity) }
                .onFailure { error="Не удалось загрузить журнал: ${it.message}" }

            contactsLoad.await()
                .onSuccess { p-> people=p;simStatus=data.sim.status;ContactCache.people=p;selected=selected?.let { old->p.find { it.id==old.id } } }
                .onFailure { error="Не удалось загрузить контакты: ${it.message}" }
        }
    }
    private fun work(action:()->Unit) {
        lifecycleScope.launch {
            loading=true
            runCatching { withContext(Dispatchers.IO) { action() } }.onFailure { error=it.message ?: "Операция не выполнена" }
            refresh()
        }
    }
    private fun toast(s:String)=android.widget.Toast.makeText(this,s,android.widget.Toast.LENGTH_SHORT).show()
    private fun setup() {
        val roles=getSystemService(RoleManager::class.java)
        if(!roles.isRoleHeld(RoleManager.ROLE_DIALER)) {
            if(roles.isRoleAvailable(RoleManager.ROLE_DIALER)) startActivity(roles.createRequestRoleIntent(RoleManager.ROLE_DIALER))
            return
        }
        val list=mutableListOf(Manifest.permission.CALL_PHONE,Manifest.permission.READ_CONTACTS,Manifest.permission.WRITE_CONTACTS,Manifest.permission.READ_CALL_LOG,Manifest.permission.WRITE_CALL_LOG,Manifest.permission.READ_PHONE_STATE)
        if(android.os.Build.VERSION.SDK_INT>=33) list.add(Manifest.permission.POST_NOTIFICATIONS)
        val missing=list.filter { !data.allowed(it) }
        if(missing.isNotEmpty()) { permissions.launch(missing.toTypedArray());return }
        val manager=getSystemService(android.app.NotificationManager::class.java)
        if(android.os.Build.VERSION.SDK_INT>=34 && !manager.canUseFullScreenIntent()) startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,Uri.parse("package:$packageName")))
        else toast("Разрешения настроены")
    }
    private fun dial(value:String)=Dialing.place(this,value) { setup() }
    private fun editorLocal(p:PersonRecord?) { edit=p;photoDraft=p?.photo;photoOriginal=null;cropSource=null;changedPhoto=false;editing=true }
    private fun editor(p:PersonRecord?) {
        if(p==null) {
            lifecycleScope.launch {
                val books=withContext(Dispatchers.IO){data.sim.books()}
                if(books.isEmpty()) editorLocal(null)
                else android.app.AlertDialog.Builder(this@MainActivity).setTitle("Где сохранить контакт?")
                    .setItems((listOf("Телефон")+books.map{it.label}).toTypedArray()){_,i->if(i==0)editorLocal(null) else simEditing=books[i-1] to null}.setNegativeButton("Отмена",null).show()
            };return
        }
        if(p.simSources.isEmpty()) {editorLocal(p);return}
        val local=p.id>=0
        val labels=(if(local)listOf("Телефон")else emptyList())+p.simSources.map{it.book.label+" · "+it.name}
        android.app.AlertDialog.Builder(this).setTitle("Какую запись изменить?").setItems(labels.toTypedArray()){_,i->
            if(local && i==0)editorLocal(p) else p.simSources[i-(if(local)1 else 0)].let{simEditing=it.book to it}
        }.setNegativeButton("Отмена",null).show()
    }
    private fun deletePerson(p:PersonRecord) {
        fun local(){confirmation="Удалить ${p.name} из памяти телефона?" to {selected=null;work{data.deleteContact(p.id)}}}
        fun sim(source:SimSource){confirmation="Удалить ${source.name} с ${source.book.label}?" to {selected=null;work{data.sim.delete(source)}}}
        if(p.simSources.isEmpty()){local();return}
        val hasLocal=p.id>=0
        val labels=(if(hasLocal)listOf("Телефон")else emptyList())+p.simSources.map{it.book.label+" · "+it.name}
        android.app.AlertDialog.Builder(this).setTitle("Какую запись удалить?").setItems(labels.toTypedArray()){_,i->if(hasLocal && i==0)local() else sim(p.simSources[i-(if(hasLocal)1 else 0)])}.setNegativeButton("Отмена",null).show()
    }
    @Composable private fun SimEditor(book:SimBook,source:SimSource?) {
        var name by remember(book,source){mutableStateOf(source?.name.orEmpty())}
        var num by remember(book,source){mutableStateOf(source?.number.orEmpty())}
        AlertDialog(onDismissRequest={if(!loading)simEditing=null},title={Text(book.label+" · контакт")},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Text("На SIM сохраняются имя и один номер. Фото хранится только в телефоне.")
            OutlinedTextField(name,{name=it},label={Text("Имя")},enabled=!loading)
            OutlinedTextField(num,{num=NumberTools.clean(it)},label={Text("Номер")},enabled=!loading,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
        }},confirmButton={TextButton(enabled=!loading && name.isNotBlank() && num.isNotBlank(),onClick={
            lifecycleScope.launch {loading=true;runCatching{withContext(Dispatchers.IO){data.sim.save(book,source,name.trim(),num)}}.onSuccess{simEditing=null}.onFailure{error=it.message ?: "SIM не сохранила контакт"};refresh()}
        }){Text("Сохранить")}},dismissButton={TextButton(enabled=!loading,onClick={simEditing=null}){Text("Отмена")}})
    }
    private fun copy(value:String) {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("Номер",value));toast("Номер скопирован")
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable private fun App() {
        var query by rememberSaveable { mutableStateOf("") }
        val scope=rememberCoroutineScope()
        BackHandler(settings || selected!=null) { settings=false;selected=null }
        Scaffold(
            topBar={ TopAppBar(
                colors=TopAppBarDefaults.topAppBarColors(containerColor=MaterialTheme.colorScheme.background),
                title={ Text(if(settings) "Настройки" else if(selected!=null) "Контакт" else listOf("Недавние","Контакты","Клавиши")[tab],fontSize=24.sp,fontWeight=FontWeight.SemiBold) },
                navigationIcon={ if(settings||selected!=null) IconButton(onClick={settings=false;selected=null}) { Icon(Icons.Default.ArrowBack,"Назад") } },
                actions={
                    if(tab==1 && selected==null && !settings) IconButton(onClick={editor(null)}) { Icon(Icons.Default.Add,"Создать контакт") }
                    IconButton(onClick={settings=!settings}) { Icon(Icons.Outlined.Settings,"Настройки") }
                }
            ) },
            bottomBar={ if(!settings && selected==null) NavigationBar(containerColor=MaterialTheme.colorScheme.surface,tonalElevation=0.dp) {
                listOf(Icons.Outlined.History,Icons.Outlined.Contacts,Icons.Outlined.Dialpad).forEachIndexed { i,icon->
                    NavigationBarItem(
                        colors=NavigationBarItemDefaults.colors(indicatorColor=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.55f),selectedIconColor=MaterialTheme.colorScheme.primary,selectedTextColor=MaterialTheme.colorScheme.primary),
                        selected=tab==i,onClick={tab=i;if(i==0) MissedCalls.clear(this@MainActivity)},
                        icon={BadgedBox(badge={if(i==0 && missedCount>0)Badge{Text(if(missedCount>99)"99+" else missedCount.toString())}}){Icon(icon,null,Modifier.size(22.dp))}},
                        label={Text(listOf("Недавние","Контакты","Клавиши")[i],fontSize=10.sp,fontWeight=FontWeight.Normal)}
                    )
                }
            } }
        ) { padding->
            Column(Modifier.padding(padding).fillMaxSize()) {
                if(loading) LinearProgressIndicator(Modifier.fillMaxWidth())
                if(!data.allowed(Manifest.permission.READ_CONTACTS) || !data.allowed(Manifest.permission.CALL_PHONE)) TextButton(onClick={setup()}) { Text("Настроить звонки и доступ к данным") }
                if(!settings) callScreenIssue?.let { message->TextButton(onClick={CallScreenAccess.openSettings(this@MainActivity)}) { Icon(Icons.Default.NotificationsActive,null);Spacer(Modifier.width(8.dp));Text(message) } }
                when {
                    settings -> Settings()
                    selected!=null -> ContactCard(selected!!)
                    tab==0 -> HistoryList(history)
                    tab==1 -> {
                        OutlinedTextField(query,{query=it},Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=6.dp).height(52.dp),placeholder={Text("Имя или номер")},leadingIcon={Icon(Icons.Default.Search,null,Modifier.size(20.dp))},trailingIcon={if(query.isNotEmpty()) IconButton(onClick={query=""}){Icon(Icons.Default.Close,"Очистить поиск",Modifier.size(18.dp))}},shape=RoundedCornerShape(14.dp),colors=OutlinedTextFieldDefaults.colors(unfocusedContainerColor=MaterialTheme.colorScheme.surfaceVariant,focusedContainerColor=MaterialTheme.colorScheme.surface,unfocusedBorderColor=Color.Transparent),singleLine=true)
                        val filtered=remember(people,query) { people.filter { NumberTools.matches(it.name,it.numbers,query) } }
                        val state=rememberLazyListState()
                        Row(Modifier.weight(1f)) {
                            LazyColumn(state=state,modifier=Modifier.weight(1f),contentPadding=PaddingValues(start=16.dp,end=6.dp,bottom=12.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                                if(filtered.isEmpty()) item { EmptySection(if(query.isBlank()) "Здесь будут контакты" else "Ничего не найдено",if(query.isBlank()) "Добавьте первый контакт кнопкой +" else "Попробуйте другое имя или номер") }
                                itemsIndexed(filtered,key={_,p->p.id}) { index,p->
                                    SwipeCall(shape=listRowShape(index==0,index==filtered.lastIndex),onCall={dial(p.primary)},onTap={selected=p}) {
                                        Column { PersonRow(p);if(index!=filtered.lastIndex) HorizontalDivider(Modifier.padding(start=62.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.outlineVariant) }
                                    }
                                }
                            }
                            val alphabet="АБВГДЕЁЖЗИЙКЛМНОПРСТУФХЦЧШЩЪЫЬЭЮЯABCDEFGHIJKLMNOPQRSTUVWXYZ#"
                            val letters=alphabet.filter { ch->filtered.any { p->if(ch=='#') p.name.firstOrNull()?.isLetter()!=true else p.name.startsWith(ch.toString(),true) } }
                            Column(Modifier.width(24.dp).padding(top=6.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                                letters.forEach { ch->
                                    Text(ch.toString(),fontSize=9.sp,color=MaterialTheme.colorScheme.primary.copy(alpha=.8f),modifier=Modifier.clickable {
                                        val i=filtered.indexOfFirst { p->if(ch=='#') p.name.firstOrNull()?.isLetter()!=true else p.name.startsWith(ch.toString(),true) }
                                        if(i>=0) scope.launch { state.animateScrollToItem(i) }
                                    }.padding(horizontal=4.dp,vertical=2.5.dp))
                                }
                            }
                        }
                    }
                    else -> Keypad()
                }
            }
        }
        if(editing) Editor()
        simEditing?.let{(book,source)->SimEditor(book,source)}
        cropSource?.let { source -> PhotoCropEditor(source,photoPreviewName,onSave={photoDraft=it;photoOriginal=source;changedPhoto=true;cropSource=null},onDismiss={cropSource=null}) }
        confirmation?.let { (title,action)->Confirm(title,{confirmation=null}) { confirmation=null;action() } }
        error?.let { message->AlertDialog(onDismissRequest={error=null},title={Text("Звонилка")},text={Text(message)},confirmButton={TextButton(onClick={error=null}){Text("Понятно")}}) }
    }

    @Composable private fun SimBadge(p:PersonRecord) {
        if(p.simSources.isEmpty()) return
        val label=p.simSources.map{it.book.label}.distinct().joinToString()
        Surface(shape=RoundedCornerShape(5.dp),color=MaterialTheme.colorScheme.primaryContainer.copy(alpha=.28f)) {
            Row(Modifier.padding(horizontal=4.dp,vertical=1.dp),verticalAlignment=Alignment.CenterVertically) {
                Icon(Icons.Default.SimCard,null,Modifier.size(9.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(Modifier.width(2.dp));Text(label,fontSize=8.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
            }
        }
    }
    @Composable private fun PersonRow(p:PersonRecord,number:String=p.primary) {
        Row(Modifier.fillMaxWidth().padding(horizontal=12.dp,vertical=8.dp),verticalAlignment=Alignment.CenterVertically) {
            Photo(p,Modifier.size(40.dp).clip(CircleShape));Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment=Alignment.CenterVertically) {
                    Text(p.name,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                    if(p.simSources.isNotEmpty()) { Spacer(Modifier.width(6.dp));SimBadge(p) }
                }
                Spacer(Modifier.height(2.dp));Text(NumberTools.display(number),fontSize=12.sp,letterSpacing=0.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }

    @Composable private fun HistoryList(rows:List<HistoryRecord>) {
        var expanded by remember { mutableStateOf<Long?>(null) }
        var chosen by remember { mutableStateOf(emptySet<Long>()) }
        var editMode by remember { mutableStateOf(false) }
        var missedOnly by remember { mutableStateOf(false) }
        var contextCall by remember { mutableStateOf<HistoryRecord?>(null) }
        val visible=if(missedOnly) rows.filter{it.type==CallLog.Calls.MISSED_TYPE} else rows
        LaunchedEffect(rows) { chosen=chosen.intersect(rows.map{it.id}.toSet()) }
        val zone=java.time.ZoneId.systemDefault()
        val today=java.time.LocalDate.now(zone)
        val dates=remember(visible,zone) { visible.map { java.time.Instant.ofEpochMilli(it.date).atZone(zone).toLocalDate() } }
        Column {
            Row(Modifier.fillMaxWidth().padding(horizontal=16.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically) {
                Row(Modifier.widthIn(max=210.dp).clip(RoundedCornerShape(11.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(3.dp),horizontalArrangement=Arrangement.spacedBy(2.dp)) {
                    listOf("Все","Пропущенные").forEachIndexed { index,label ->
                        val active=missedOnly==(index==1)
                        Surface(onClick={missedOnly=index==1},modifier=Modifier.width(if(index==0)76.dp else 112.dp),shape=RoundedCornerShape(9.dp),color=if(active) MaterialTheme.colorScheme.surface else Color.Transparent,shadowElevation=if(active) 1.dp else 0.dp) {
                            Box(Modifier.heightIn(min=36.dp).padding(horizontal=4.dp,vertical=6.dp),contentAlignment=Alignment.Center) {
                                Text(label,fontSize=12.sp,fontWeight=if(active) FontWeight.Medium else FontWeight.Normal,color=if(active) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick={editMode=!editMode;chosen=emptySet()}){Text(if(editMode) "Готово" else "Править",fontSize=13.sp)}
            }
            if(editMode) Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),verticalAlignment=Alignment.CenterVertically) {
                TextButton(onClick={chosen=visible.map{it.id}.toSet()}){Text("Выбрать все")}
                TextButton(enabled=chosen.isNotEmpty() && !loading,onClick={confirmDelete(chosen.toSet())}){Text("Удалить (${chosen.size})",color=MaterialTheme.colorScheme.error)}
            }
            LazyColumn(Modifier.weight(1f),contentPadding=PaddingValues(horizontal=16.dp,vertical=5.dp),verticalArrangement=Arrangement.spacedBy(0.dp)) {
                if(visible.isEmpty()) item { EmptySection(if(missedOnly) "Нет пропущенных" else "Пока ни одного вызова","Здесь будет ваша история звонков") }
                itemsIndexed(visible,key={_,h->h.id}) { index,h->
                    val day=dates[index]
                    val first=index==0 || dates[index-1]!=day
                    val last=index==visible.lastIndex || dates[index+1]!=day
                    val dayLabel=when(day) { today->"Сегодня";today.minusDays(1)->"Вчера";else->day.format(java.time.format.DateTimeFormatter.ofPattern("d MMMM yyyy",Locale("ru"))) }
                    Column {
                    if(first) Text(dayLabel,Modifier.padding(start=12.dp,top=if(index==0) 3.dp else 11.dp,bottom=6.dp),fontSize=12.sp,fontWeight=FontWeight.Medium,color=MaterialTheme.colorScheme.onSurfaceVariant)
                    val p=people.firstOrNull { it.numbers.any { n->NumberTools.key(n)==NumberTools.key(h.number) } }
                    val color=historyColor(h.type)
                    val kind=when(h.type) { CallLog.Calls.MISSED_TYPE->"Пропущенный";CallLog.Calls.OUTGOING_TYPE->"Исходящий";CallLog.Calls.REJECTED_TYPE->"Отклонённый";else->"Входящий" }
                    fun selectRow() { chosen=if(h.id in chosen) chosen-h.id else chosen+h.id }
                    SwipeCall(shape=RoundedCornerShape(0.dp),containerColor=MaterialTheme.colorScheme.background,enabled=!editMode,onCall={dial(h.number)},onTap={if(editMode) selectRow() else expanded=if(expanded==h.id) null else h.id},onLong={contextCall=h}) {
                        Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth().padding(horizontal=10.dp,vertical=9.dp)) {
                            Row(verticalAlignment=Alignment.CenterVertically) {
                                if(editMode) Checkbox(checked=h.id in chosen,onCheckedChange={selectRow()})
                                Photo(p,Modifier.size(40.dp).clip(CircleShape))
                                Spacer(Modifier.width(10.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(p?.name ?: NumberTools.display(h.number).ifBlank { "Скрытый номер" },color=color,style=MaterialTheme.typography.titleMedium,maxLines=1,overflow=androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                                    Row(verticalAlignment=Alignment.CenterVertically) {
                                        Icon(if(h.type==CallLog.Calls.OUTGOING_TYPE) Icons.Default.CallMade else if(h.type==CallLog.Calls.MISSED_TYPE) Icons.Default.CallMissed else Icons.Default.CallReceived,null,Modifier.size(13.dp),tint=color)
                                        Spacer(Modifier.width(5.dp));Text(NumberTools.duration(h.seconds),fontSize=11.sp,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=1)
                                    }
                                }
                                Text(SimpleDateFormat("HH:mm",Locale.getDefault()).format(h.date),Modifier.padding(start=6.dp),fontSize=11.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurfaceVariant)
                                IconButton(onClick={expanded=if(expanded==h.id) null else h.id},modifier=Modifier.size(40.dp)){Icon(Icons.Outlined.Info,"Сведения о вызове",tint=MaterialTheme.colorScheme.primary.copy(alpha=.8f),modifier=Modifier.size(19.dp))}
                            }
                            if(expanded==h.id) {
                                HorizontalDivider(Modifier.padding(vertical=8.dp),color=MaterialTheme.colorScheme.outlineVariant)
                                Text("$kind · "+SimpleDateFormat("dd.MM.yyyy HH:mm:ss",Locale.getDefault()).format(h.date),style=MaterialTheme.typography.bodySmall)
                                Row(Modifier.horizontalScroll(rememberScrollState())) {
                                    TextButton(onClick={dial(h.number)}) { Text("Позвонить") }
                                    TextButton(onClick={copy(h.number)}){Text("Копировать")}
                                    if(p!=null)TextButton(onClick={selected=p}){Text("Контакт")}
                                }
                                TextButton(enabled=!loading,onClick={confirmDelete(setOf(h.id))}) { Icon(Icons.Default.Delete,null,Modifier.size(18.dp),tint=MaterialTheme.colorScheme.error);Spacer(Modifier.width(6.dp));Text("Удалить вызов",color=MaterialTheme.colorScheme.error) }
                            }
                        }
                        if(!last) HorizontalDivider(Modifier.padding(start=60.dp),thickness=.5.dp,color=MaterialTheme.colorScheme.outlineVariant)
                        }
                    }
                    }
                }
            }
        }
        contextCall?.let { h ->
            AlertDialog(onDismissRequest={contextCall=null},title={Text("Действия с вызовом")},text={Text(NumberTools.display(h.number))},
                confirmButton={TextButton(onClick={contextCall=null;confirmDelete(setOf(h.id))}){Text("Удалить",color=MaterialTheme.colorScheme.error)}},
                dismissButton={TextButton(onClick={contextCall=null}){Text("Отмена")}})
        }
    }
    private fun simLabel(id:String?) = Dialing.accounts(this).firstOrNull { it.id==id }?.let { Dialing.label(this,it) } ?: "SIM не указана"

    @OptIn(ExperimentalFoundationApi::class)
    @Composable private fun Keypad() {
        var picked by remember { mutableStateOf(false) }
        val matches=remember(number,people,picked) { if(number.isBlank()||picked) emptyList() else people.flatMap { p->p.numbers.filter { NumberTools.key(it).contains(NumberTools.key(number)) || NumberTools.t9(p.name).contains(number) }.map { p to it } }.take(4) }
        val exact=people.firstOrNull { it.numbers.any { n->NumberTools.key(n)==NumberTools.key(number) } }
        val labels=mapOf(
            '2' to "АБВГ\nABC", '3' to "ДЕЁЖЗ\nDEF", '4' to "ИЙКЛ\nGHI",
            '5' to "МНОП\nJKL", '6' to "РСТУ\nMNO", '7' to "ФХЦЧ\nPQRS",
            '8' to "ШЩЪЫ\nTUV", '9' to "ЬЭЮЯ\nWXYZ", '0' to "+"
        )
        Column(Modifier.fillMaxSize().padding(horizontal=10.dp),horizontalAlignment=Alignment.CenterHorizontally) {
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
            Spacer(Modifier.height(if(matches.isEmpty() && exact==null && number.isBlank())46.dp else 18.dp))
            listOf("123","456","789","*0#").forEach { line->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceEvenly) {
                    line.forEach { digit->
                        Surface(
                            Modifier.padding(vertical=6.dp).size(86.dp)
                                .border(1.dp,MaterialTheme.colorScheme.outline.copy(alpha=.85f),CircleShape)
                                .combinedClickable(onClick={
                                    number+=digit;picked=false
                                    if(getSharedPreferences("settings",0).getBoolean("haptic",true)) window.decorView.performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                                },onLongClick={if(digit=='0') { number+="+";picked=false }}),
                            shape=CircleShape,
                            color=Color.Transparent
                        ) {
                            Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center) {
                                Text(digit.toString(),fontSize=36.sp,fontWeight=FontWeight.Normal,color=MaterialTheme.colorScheme.onSurface)
                                Text(labels[digit].orEmpty(),fontSize=10.sp,lineHeight=11.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center,color=MaterialTheme.colorScheme.onSurfaceVariant,fontWeight=FontWeight.Medium)
                            }
                        }
                    }
                }
            }
            val ignored=simRevision
            Spacer(Modifier.height(14.dp))
            Box(Modifier.size(76.dp).clip(CircleShape).background(Green).combinedClickable(onClick={dial(number)},onLongClick={Dialing.choose(this@MainActivity,number.ifBlank { null }){simRevision++}}),contentAlignment=Alignment.Center) {
                Icon(Icons.Default.Call,"Позвонить; удерживать для выбора SIM",Modifier.size(33.dp),tint=Color.White)
            }
            TextButton(onClick={Dialing.choose(this@MainActivity,number.ifBlank{null}){simRevision++}},contentPadding=PaddingValues(horizontal=10.dp,vertical=4.dp)) {
                Icon(Icons.Default.SimCard,null,Modifier.size(17.dp));Spacer(Modifier.width(5.dp));Text(Dialing.selectedLabel(this@MainActivity,number),style=MaterialTheme.typography.labelMedium);Icon(Icons.Default.ExpandMore,null,Modifier.size(17.dp))
            }
            Spacer(Modifier.weight(1f))
        }
    }

    @Composable private fun ContactCard(p:PersonRecord) {
        var menu by remember { mutableStateOf(false) }
        Column(Modifier.verticalScroll(rememberScrollState())) {
            Box(Modifier.padding(horizontal=16.dp,vertical=8.dp).fillMaxWidth().height(300.dp).clip(RoundedCornerShape(28.dp))) { Photo(p,Modifier.fillMaxSize(),true);Text(p.name,Modifier.align(Alignment.BottomStart).background(Brush.verticalGradient(listOf(Color.Transparent,Color(0xDD071A31)))).fillMaxWidth().padding(20.dp),fontSize=30.sp,color=Color.White) }
            Row(Modifier.fillMaxWidth().padding(vertical=12.dp),horizontalArrangement=Arrangement.SpaceEvenly) {
                FilledTonalIconButton(onClick={dial(p.primary)}){Icon(Icons.Default.Call,"Позвонить")}
                FilledTonalIconButton(onClick={try { startActivity(Intent(Intent.ACTION_SENDTO,Uri.fromParts("smsto",p.primary,null))) } catch(_:ActivityNotFoundException){toast("Приложение SMS недоступно")}}){Icon(Icons.Default.Sms,"SMS")}
                FilledTonalIconButton(onClick={copy(p.primary)}){Icon(Icons.Default.ContentCopy,"Скопировать")}
                FilledTonalIconButton(onClick={editor(p)}){Icon(Icons.Default.Edit,"Редактировать")}
                Box { FilledTonalIconButton(onClick={menu=true}){Icon(Icons.Default.MoreVert,"Меню")};DropdownMenu(menu,{menu=false}){DropdownMenuItem(text={Text("Удалить контакт")},onClick={menu=false;deletePerson(p)})} }
            }
            if(p.simSources.isNotEmpty()) Text("Источники: "+(if(p.id>=0)"Телефон · " else "")+p.simSources.joinToString{it.book.label},Modifier.padding(horizontal=16.dp),style=MaterialTheme.typography.bodySmall)
            val simState=simRevision
            TextButton(onClick={Dialing.choose(this@MainActivity,p.primary){simRevision++}},modifier=Modifier.padding(horizontal=16.dp)) { Icon(Icons.Default.SimCard,null);Spacer(Modifier.width(8.dp));Text(Dialing.selectedLabel(this@MainActivity,p.primary));Icon(Icons.Default.ExpandMore,null) }
            p.numbers.forEachIndexed { i,n->TextButton(onClick={
                android.app.AlertDialog.Builder(this@MainActivity).setTitle(NumberTools.display(n)).setItems(arrayOf("Позвонить","Сделать основным","Скопировать")) { _,action->when(action){0->dial(n);1->if(p.id>=0)work{data.save(p.id,p.name,listOf(n)+p.numbers.filter{it!=n},null,false)} else toast("На SIM хранится один номер");2->copy(n)} }.show()
            }) { Text(NumberTools.display(n)+(if(i==0) " · основной" else "")) } }
            Text("История",Modifier.padding(16.dp),style=MaterialTheme.typography.titleLarge)
            history.filter { h->p.numbers.any { NumberTools.key(it)==NumberTools.key(h.number) } }.forEach { h->
                val color=historyColor(h.type)
                Text((if(h.type==CallLog.Calls.OUTGOING_TYPE) "↗ " else "↙ ")+SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(h.date)+" · "+NumberTools.duration(h.seconds)+" · "+simLabel(h.accountId),Modifier.padding(horizontal=16.dp,vertical=8.dp),color=color)
            }
        }
    }
    @Composable private fun Editor() {
        var name by remember(edit) { mutableStateOf(edit?.name.orEmpty()) }
        var nums by remember(edit) { mutableStateOf(edit?.numbers?.ifEmpty { listOf("") } ?: listOf("")) }
        AlertDialog(onDismissRequest={editing=false},title={Text(if(edit==null||edit!!.id<0) "Новый контакт" else "Редактирование")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                Photo(PersonRecord(-1,name,nums,photoDraft),Modifier.size(120.dp).clip(CircleShape))
                TextButton(onClick={photoPreviewName=name;photoPicker.launch("image/*")}) { Text("Выбрать фото") }
                if(photoDraft!=null) TextButton(onClick={
                    photoPreviewName=name
                    lifecycleScope.launch {
                        runCatching { withContext(Dispatchers.IO) { photoOriginal ?: edit?.id?.takeIf{it>=0}?.let{data.editPhoto(it)} ?: photoDraft } }
                            .onSuccess { if(it!=null){cropSource=it} }.onFailure{error="Не удалось открыть фото"}
                    }
                }) { Text("Настроить кадр звонка") }
                OutlinedTextField(name,{name=it},label={Text("Имя")},singleLine=true)
                nums.forEachIndexed { i,n->Row(verticalAlignment=Alignment.CenterVertically) {
                    OutlinedTextField(n,{value->nums=nums.toMutableList().also{it[i]=NumberTools.clean(value)}},Modifier.weight(1f),label={Text(if(i==0) "Основной номер" else "Номер")},keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Phone))
                    IconButton(onClick={nums=nums.filterIndexed{j,_->i!=j}.ifEmpty{listOf("")}}) { Icon(Icons.Default.Remove,"Удалить номер") }
                } }
                TextButton(onClick={nums=nums+""}) { Text("Добавить номер") }
                Text("Хранение: телефон",style=MaterialTheme.typography.bodySmall)
            }
        },confirmButton={TextButton(enabled=!loading && name.isNotBlank() && nums.any{it.isNotBlank()},onClick={
            val id=edit?.id?.takeIf{it>=0};val photo=photoDraft;val change=changedPhoto
            lifecycleScope.launch { loading=true;runCatching{withContext(Dispatchers.IO){data.save(id,name,nums,photo,change,photoOriginal)}}.onSuccess{editing=false}.onFailure{error=it.message};refresh() }
        }){Text("Сохранить")}},dismissButton={TextButton(onClick={editing=false}){Text("Отмена")}})
    }

    @Composable private fun SettingsRow(title:String,value:String?=null,ok:Boolean?=null,enabled:Boolean=true,onClick:(()->Unit)?=null) {
        Row(Modifier.fillMaxWidth().heightIn(min=48.dp).then(if(onClick!=null) Modifier.clickable(enabled=enabled,onClick=onClick) else Modifier).padding(horizontal=2.dp,vertical=5.dp),verticalAlignment=Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title,style=MaterialTheme.typography.bodyLarge,color=if(enabled) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.onSurfaceVariant)
                value?.let { Text(it,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant,maxLines=2) }
            }
            ok?.let { Icon(if(it) Icons.Default.CheckCircle else Icons.Default.Error,null,Modifier.size(20.dp),tint=if(it) Green else MaterialTheme.colorScheme.error) }
            if(onClick!=null) { Spacer(Modifier.width(6.dp));Icon(Icons.Default.ChevronRight,null,Modifier.size(20.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant) }
        }
    }
    @Composable private fun Settings() {
        var callerId by remember { mutableStateOf(CallerId.enabled(this)) }
        var haptic by remember { mutableStateOf(getSharedPreferences("settings",0).getBoolean("haptic",true)) }
        var advanced by rememberSaveable { mutableStateOf(false) }
        val roleOk=getSystemService(RoleManager::class.java).isRoleHeld(RoleManager.ROLE_DIALER)
        val contactsOk=data.allowed(Manifest.permission.READ_CONTACTS)
        val historyOk=data.allowed(Manifest.permission.READ_CALL_LOG)
        val callScreenOk=callScreenIssue==null
        val needsSetup=!roleOk || !contactsOk || !historyOk || !data.allowed(Manifest.permission.CALL_PHONE) || !callScreenOk
        Column(Modifier.padding(horizontal=16.dp).verticalScroll(rememberScrollState()).padding(bottom=20.dp),verticalArrangement=Arrangement.spacedBy(7.dp)) {
            SectionLabel("ОФОРМЛЕНИЕ")
            SettingsGroup {
                Text("Тема",style=MaterialTheme.typography.titleMedium)
                listOf("system" to "Как в системе","light" to "Светлая","dark" to "Тёмная").forEach { (mode,label) ->
                    Row(Modifier.fillMaxWidth().heightIn(min=44.dp).clickable { if(ThemeSettings.mode(this@MainActivity)!=mode) { ThemeSettings.set(this@MainActivity,mode);recreate() } },verticalAlignment=Alignment.CenterVertically) {
                        RadioButton(selected=ThemeSettings.mode(this@MainActivity)==mode,onClick={if(ThemeSettings.mode(this@MainActivity)!=mode) { ThemeSettings.set(this@MainActivity,mode);recreate()}})
                        Text(label,style=MaterialTheme.typography.bodyLarge)
                    }
                }
                Row(Modifier.heightIn(min=46.dp),verticalAlignment=Alignment.CenterVertically) { Text("Вибрация клавиш",Modifier.weight(1f));Switch(haptic,{haptic=it;getSharedPreferences("settings",0).edit().putBoolean("haptic",it).apply()}) }
            }
            SectionLabel("ЗВОНКИ И ДОСТУП")
            SettingsGroup {
                if(needsSetup) Button(onClick={setup()},modifier=Modifier.fillMaxWidth()) { Text("Настроить звонки и разрешения") }
                SettingsRow("Телефон по умолчанию",if(roleOk)"Звонилка" else "Нужно назначить",roleOk,onClick=if(roleOk)null else ::setup)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsRow("Контакты",if(contactsOk)"Доступ разрешён" else "Нет доступа",contactsOk,onClick=if(contactsOk)null else ::setup)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsRow("История вызовов",if(historyOk)"Доступ разрешён" else "Нет доступа",historyOk,onClick=if(historyOk)null else ::setup)
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsRow("Входящие на экране блокировки",callScreenIssue ?: "Разрешены",callScreenOk,onClick={CallScreenAccess.openSettings(this@MainActivity)})
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                val ignored=simRevision
                SettingsRow("SIM по умолчанию",Dialing.selectedLabel(this@MainActivity,""),onClick={Dialing.choose(this@MainActivity,null){simRevision++}})
            }
            SectionLabel("КОНТАКТЫ И КОПИИ")
            SettingsGroup {
                SettingsRow("Экспорт контактов","Сохранить копию в VCF",enabled=people.isNotEmpty(),onClick={export.launch("Zvonilka-contacts.vcf")})
                HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                SettingsRow("Импорт контактов","Добавить контакты из VCF",enabled=data.allowed(Manifest.permission.WRITE_CONTACTS),onClick={importFile.launch(arrayOf("text/*","application/octet-stream"))})
            }
            SectionLabel("ВОЗМОЖНОСТИ")
            SettingsGroup {
                Row(Modifier.heightIn(min=50.dp),verticalAlignment=Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("PhoneBlock",style=MaterialTheme.typography.bodyLarge);Text("Бесплатная проверка неизвестных номеров на спам",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant) }
                    Switch(callerId,{ enabled -> callerId=enabled;CallerId.setEnabled(this@MainActivity,enabled) })
                }
                Text("Неизвестный номер проверяется через PhoneBlock. Контакты и журнал не передаются; звонки не блокируются.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick={startActivity(Intent(Intent.ACTION_VIEW,Uri.parse("https://phoneblock.net/phoneblock/")))},contentPadding=PaddingValues(horizontal=10.dp,vertical=2.dp)) { Text("О сервисе PhoneBlock",fontSize=12.sp) }
            }
            SectionLabel("ДОПОЛНИТЕЛЬНО")
            SettingsGroup {
                SettingsRow("Дополнительно",if(advanced)"Скрыть технические параметры" else "Диагностика и состояние SIM",onClick={advanced=!advanced})
                if(advanced) {
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow("Диагностика звонков","Технический отчёт для поиска проблем",onClick={
                        val report=CallDiagnostics.report(this@MainActivity)
                        android.app.AlertDialog.Builder(this@MainActivity).setTitle("Диагностика звонков").setMessage(report).setPositiveButton("Копировать") { _,_->
                            getSystemService(android.content.ClipboardManager::class.java).setPrimaryClip(android.content.ClipData.newPlainText("Звонилка: диагностика",report));toast("Диагностика скопирована")
                        }.setNegativeButton("Закрыть",null).show()
                    })
                    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
                    SettingsRow("SIM-книга",simStatus.ifBlank{"Ещё не проверена"})
                }
            }
        }
    }
}
