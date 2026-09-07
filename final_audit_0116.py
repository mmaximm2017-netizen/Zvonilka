from pathlib import Path

# MainActivity: fix loading lifecycle, serialize refreshes, restore/persist cold history snapshot.
p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()
s=s.replace('''    private var callScreenIssue by mutableStateOf<String?>(null)\n    private var simRevision by mutableIntStateOf(0)\n''','''    private var callScreenIssue by mutableStateOf<String?>(null)\n    private var simRevision by mutableIntStateOf(0)\n    private var refreshJob:Job?=null\n''',1)
s=s.replace('''        settings=savedInstanceState?.getBoolean("settings") ?: false\n        pendingDelete=savedInstanceState?.getLongArray("pendingDelete")?.toSet().orEmpty()\n''','''        settings=savedInstanceState?.getBoolean("settings") ?: false\n        pendingDelete=savedInstanceState?.getLongArray("pendingDelete")?.toSet().orEmpty()\n        if(history.isEmpty()) { history=HistorySnapshot.load(this);HistoryCache.items=history }\n''',1)
old='''        lifecycleScope.launch {\n            loading=true\n            runCatching { withContext(Dispatchers.IO) { data.deleteHistory(ids) } }\n                .onSuccess { history=history.filterNot{it.id in ids};toast("Записи удалены") }\n                .onFailure { error="Не удалось удалить записи: ${it.message}" }\n            refresh()\n        }\n'''
new='''        lifecycleScope.launch {\n            loading=true\n            try {\n                runCatching { withContext(Dispatchers.IO) { data.deleteHistory(ids) } }\n                    .onSuccess {\n                        history=history.filterNot{it.id in ids};HistoryCache.items=history\n                        withContext(Dispatchers.IO){HistorySnapshot.save(this@MainActivity,history)}\n                        toast("Записи удалены")\n                    }\n                    .onFailure { error="Не удалось удалить записи: ${it.message}" }\n            } finally { loading=false }\n            refresh()\n        }\n'''
if old not in s: raise SystemExit('deleteCalls block not found')
s=s.replace(old,new,1)
old='''    private fun refresh() {\n        lifecycleScope.launch {\n            val historyLoad=async(Dispatchers.IO) { runCatching { data.history() } }\n            val contactsLoad=async(Dispatchers.IO) { runCatching { data.contacts() } }\n\n            historyLoad.await()\n                .onSuccess { h-> history=h;HistoryCache.items=h;if(tab==0) MissedCalls.clear(this@MainActivity) }\n                .onFailure { error="Не удалось загрузить журнал: ${it.message}" }\n\n            contactsLoad.await()\n                .onSuccess { p-> people=p;simStatus=data.sim.status;ContactCache.people=p;selected=selected?.let { old->p.find { it.id==old.id } } }\n                .onFailure { error="Не удалось загрузить контакты: ${it.message}" }\n        }\n    }\n'''
new='''    private fun refresh() {\n        refreshJob?.cancel()\n        refreshJob=lifecycleScope.launch {\n            val historyLoad=async(Dispatchers.IO) { runCatching { data.history() } }\n            val contactsLoad=async(Dispatchers.IO) { runCatching { data.contacts() } }\n\n            historyLoad.await()\n                .onSuccess { h->\n                    history=h;HistoryCache.items=h\n                    withContext(Dispatchers.IO){HistorySnapshot.save(this@MainActivity,h)}\n                    if(tab==0) MissedCalls.clear(this@MainActivity)\n                }\n                .onFailure { if(it !is CancellationException) error="Не удалось загрузить журнал: ${it.message}" }\n\n            contactsLoad.await()\n                .onSuccess { p-> people=p;simStatus=data.sim.status;ContactCache.people=p;selected=selected?.let { old->p.find { it.id==old.id } } }\n                .onFailure { if(it !is CancellationException) error="Не удалось загрузить контакты: ${it.message}" }\n        }\n    }\n'''
if old not in s: raise SystemExit('refresh block not found')
s=s.replace(old,new,1)
old='''    private fun work(action:()->Unit) {\n        lifecycleScope.launch {\n            loading=true\n            runCatching { withContext(Dispatchers.IO) { action() } }.onFailure { error=it.message ?: "Операция не выполнена" }\n            refresh()\n        }\n    }\n'''
new='''    private fun work(action:()->Unit) {\n        lifecycleScope.launch {\n            loading=true\n            try { runCatching { withContext(Dispatchers.IO) { action() } }.onFailure { error=it.message ?: "Операция не выполнена" } }\n            finally { loading=false }\n            refresh()\n        }\n    }\n'''
if old not in s: raise SystemExit('work block not found')
s=s.replace(old,new,1)
p.write_text(s)

# PhoneData: expose fast local-only contacts, then merge SIM separately.
p=Path('app/src/main/java/ru/zvonilka/prototype/PhoneData.kt')
s=p.read_text()
s=s.replace('    fun contacts(): List<PersonRecord> {','    fun localContacts(): List<PersonRecord> {',1)
old='''        val collator=Collator.getInstance(Locale("ru"))\n        return SimMerge.merge(result,sim.read()).sortedWith { a,b -> collator.compare(a.name,b.name) }\n    }\n'''
new='''        val collator=Collator.getInstance(Locale("ru"))\n        return result.sortedWith { a,b -> collator.compare(a.name,b.name) }\n    }\n    fun mergeSim(localContacts:List<PersonRecord>):List<PersonRecord> {\n        val collator=Collator.getInstance(Locale("ru"))\n        return SimMerge.merge(localContacts,sim.read()).sortedWith { a,b -> collator.compare(a.name,b.name) }\n    }\n    fun contacts():List<PersonRecord> = mergeSim(localContacts())\n'''
if old not in s: raise SystemExit('PhoneData contacts tail not found')
s=s.replace(old,new,1)
p.write_text(s)

# PhoneService: publish local contact names before waiting for SIM phonebook.
p=Path('app/src/main/java/ru/zvonilka/prototype/PhoneService.kt')
s=p.read_text()
old='''        io.execute {\n            runCatching { PhoneData(this).contacts() }.onSuccess { people ->\n                ContactCache.people=people\n                contactsReady=true\n                handler.post {\n                    // An incoming call can arrive before the asynchronous contacts query finishes.\n                    // Rebuild every still-live call notification so Android CallStyle receives the contact name,\n                    // instead of leaving the initial number-only heads-up notification on screen.\n                    CallStore.calls.toMap().forEach { (key, call) ->\n                        if (call.state != Call.STATE_DISCONNECTED) refresh(call, key)\n                    }\n                    CallStore.changed()\n                }\n            }\n        }\n'''
new='''        io.execute {\n            val phoneData=PhoneData(this)\n            runCatching { phoneData.localContacts() }.onSuccess { localPeople ->\n                // Publish phone-memory contacts immediately. A slow SIM phonebook must not delay\n                // the caller name on the first incoming notification after a cold process start.\n                ContactCache.people=localPeople\n                handler.post {\n                    CallStore.calls.toMap().forEach { (key, call) -> if(call.state!=Call.STATE_DISCONNECTED) refresh(call,key) }\n                    CallStore.changed()\n                }\n                val allPeople=runCatching { phoneData.mergeSim(localPeople) }.getOrElse { localPeople }\n                ContactCache.people=allPeople\n                contactsReady=true\n                handler.post {\n                    CallStore.calls.toMap().forEach { (key, call) -> if(call.state!=Call.STATE_DISCONNECTED) refresh(call,key) }\n                    CallStore.changed()\n                }\n            }\n        }\n'''
if old not in s: raise SystemExit('PhoneService contact preload block not found')
s=s.replace(old,new,1)
p.write_text(s)

# Version bump.
p=Path('app/build.gradle.kts')
s=p.read_text()
if 'versionCode = 23' not in s or 'versionName = "0.11.5"' not in s: raise SystemExit('unexpected version')
s=s.replace('versionCode = 23','versionCode = 24',1).replace('versionName = "0.11.5"','versionName = "0.11.6"',1)
p.write_text(s)

# CI: production candidate is a non-debuggable release APK; keep unit tests.
p=Path('.github/workflows/build.yml')
s=p.read_text()
s=s.replace('gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon','gradle :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --no-daemon',1)
s=s.replace('cp app/build/outputs/apk/debug/app-debug.apk build/delivery/app-debug.apk','cp app/build/outputs/apk/release/app-release-unsigned.apk build/delivery/app-release-unsigned.apk',1)
p.write_text(s)

# README current version/build notes.
p=Path('README.md')
s=p.read_text()
s=s.replace('# Звонилка 0.10.2','# Звонилка 0.11.6',1)
s=s.replace('- Журнал вызовов с отдельной строкой на каждый вызов, фильтром пропущенных, датой, временем, длительностью и доступной меткой SIM.','- Журнал вызовов с отдельной строкой на каждый вызов, фильтром пропущенных, датой, временем и длительностью; последний снимок хранится локально для мгновенного холодного показа и затем тихо сверяется с системным журналом.',1)
insert='''\n## 0.11.6: финальный пакет после аудита\n\n- Исправлено зависание общего состояния загрузки после операций изменения данных.\n- Повторные `refresh()` теперь отменяют предыдущую публикацию, поэтому старый запрос не может перезаписать более свежий результат.\n- Последний снимок журнала хранится только во внутреннем приватном файле приложения и показывается сразу после холодного запуска; затем журнал перечитывается из Android CallLog.\n- `InCallService` сначала загружает локальные контакты и сразу обновляет имя входящего, не ожидая более медленного чтения SIM-книги; SIM-контакты присоединяются вторым этапом.\n- CI для пользовательского APK переведён с debug-варианта на non-debuggable release (`assembleRelease` + `lintRelease`). Приватный ключ по-прежнему не попадает в GitHub/CI.\n- Добавлены unit-тесты кодека локального снимка журнала.\n\n'''
marker='## 0.10.2: исправления после аудита\n'
if marker not in s: raise SystemExit('README marker not found')
s=s.replace(marker,insert+marker,1)
s=s.replace('gradle :app:testDebugUnitTest :app:assembleDebug :app:lintDebug --no-daemon','gradle :app:testDebugUnitTest :app:assembleRelease :app:lintRelease --no-daemon',1)
s=s.replace('CI выдаёт debug APK и `apksigner.jar` только как входные данные для локальной подписи.','CI выдаёт unsigned release APK и `apksigner.jar` только как входные данные для локальной подписи.',1)
p.write_text(s)
