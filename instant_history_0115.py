from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()
old='''    private fun refresh() {\n        lifecycleScope.launch {\n            loading=true\n            runCatching { withContext(Dispatchers.IO) { data.contacts() to data.history() } }\n                .onSuccess { (p,h)-> people=p;simStatus=data.sim.status;ContactCache.people=p;history=h;selected=selected?.let { old->p.find { it.id==old.id } } }\n                .onFailure { error=\"Не удалось загрузить данные: ${it.message}\" }\n            loading=false\n            if(tab==0) MissedCalls.clear(this@MainActivity)\n        }\n    }\n'''
new='''    private fun refresh() {\n        lifecycleScope.launch {\n            val historyLoad=async(Dispatchers.IO) { runCatching { data.history() } }\n            val contactsLoad=async(Dispatchers.IO) { runCatching { data.contacts() } }\n\n            historyLoad.await()\n                .onSuccess { h-> history=h;HistoryCache.items=h;if(tab==0) MissedCalls.clear(this@MainActivity) }\n                .onFailure { error=\"Не удалось загрузить журнал: ${it.message}\" }\n\n            contactsLoad.await()\n                .onSuccess { p-> people=p;simStatus=data.sim.status;ContactCache.people=p;selected=selected?.let { old->p.find { it.id==old.id } } }\n                .onFailure { error=\"Не удалось загрузить контакты: ${it.message}\" }\n        }\n    }\n'''
if old not in s:
    raise SystemExit('refresh block not found')
s=s.replace(old,new,1)
s=s.replace('private var history by mutableStateOf(emptyList<HistoryRecord>())','private var history by mutableStateOf(HistoryCache.items)',1)
anchor='''import java.util.Locale\n\nclass MainActivity'''
if anchor not in s:
    raise SystemExit('anchor not found')
s=s.replace(anchor,'''import java.util.Locale\n\nprivate object HistoryCache {\n    @Volatile var items:List<HistoryRecord> = emptyList()\n}\n\nclass MainActivity''',1)
p.write_text(s)

g=Path('app/build.gradle.kts')
t=g.read_text()
if 'versionCode = 22' not in t or 'versionName = "0.11.4"' not in t:
    raise SystemExit('unexpected version')
t=t.replace('versionCode = 22','versionCode = 23',1).replace('versionName = "0.11.4"','versionName = "0.11.5"',1)
g.write_text(t)
