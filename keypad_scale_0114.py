from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()
repls=[
    ('Column(Modifier.fillMaxSize().padding(horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally)', 'Column(Modifier.fillMaxSize().padding(horizontal=10.dp),horizontalAlignment=Alignment.CenterHorizontally)'),
    ('Spacer(Modifier.height(if(matches.isEmpty() && exact==null && number.isBlank())24.dp else 10.dp))', 'Spacer(Modifier.height(if(matches.isEmpty() && exact==null && number.isBlank())46.dp else 18.dp))'),
    ('Modifier.padding(vertical=4.dp).size(74.dp)', 'Modifier.padding(vertical=6.dp).size(86.dp)'),
    ('Text(digit.toString(),fontSize=31.sp', 'Text(digit.toString(),fontSize=36.sp'),
    ('Text(labels[digit].orEmpty(),fontSize=9.sp,lineHeight=10.sp', 'Text(labels[digit].orEmpty(),fontSize=10.sp,lineHeight=11.sp'),
    ('Spacer(Modifier.height(10.dp))\n            Box(Modifier.size(68.dp)', 'Spacer(Modifier.height(14.dp))\n            Box(Modifier.size(76.dp)'),
    ('Icon(Icons.Default.Call,"Позвонить; удерживать для выбора SIM",Modifier.size(30.dp)', 'Icon(Icons.Default.Call,"Позвонить; удерживать для выбора SIM",Modifier.size(33.dp)')
]
for old,new in repls:
    if old not in s:
        raise SystemExit(f'Missing expected text: {old}')
    s=s.replace(old,new,1)
p.write_text(s)

g=Path('app/build.gradle.kts')
t=g.read_text()
if 'versionCode = 21' not in t or 'versionName = "0.11.3"' not in t:
    raise SystemExit('Unexpected version')
t=t.replace('versionCode = 21','versionCode = 22',1).replace('versionName = "0.11.3"','versionName = "0.11.4"',1)
g.write_text(t)
