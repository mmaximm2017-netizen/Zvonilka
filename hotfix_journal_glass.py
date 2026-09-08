from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
s=p.read_text()
old='''        val revealModifier=if(glassBackdrop!=null && greenGlass) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Ocean else containerColor)'''
new='''        val revealModifier=if(glassBackdrop!=null && greenGlass && offset>0f) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Color(0xFF20B86A).copy(alpha=.30f) else containerColor)'''
if old not in s: raise SystemExit('Ui hotfix pattern not found')
p.write_text(s.replace(old,new,1))

p=Path('app/build.gradle.kts')
s=p.read_text()
s=s.replace('versionCode = 29','versionCode = 30').replace('versionName = "0.12.1"','versionName = "0.12.2"')
p.write_text(s)
