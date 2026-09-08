from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
s=p.read_text()
old='''        val revealModifier=if(glassBackdrop!=null && greenGlass && offset>0f) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Color(0xFF20B86A).copy(alpha=.30f) else containerColor)'''
new='''        val revealModifier=if(greenGlass && offset>0f) {\n            Modifier.matchParentSize()\n                .background(\n                    Brush.horizontalGradient(\n                        listOf(\n                            Color(0xFF0F8E4F).copy(alpha=.72f),\n                            Color(0xFF31C978).copy(alpha=.48f),\n                            Color.White.copy(alpha=.12f)\n                        )\n                    )\n                )\n                .border(1.dp,Color(0xFFC8FFE0).copy(alpha=.35f + .35f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Ocean else containerColor)'''
if old not in s: raise SystemExit('Ui swipe hotfix pattern not found')
s=s.replace(old,new,1)
p.write_text(s)

p=Path('app/build.gradle.kts')
s=p.read_text()
s=s.replace('versionCode = 30','versionCode = 31').replace('versionName = "0.12.2"','versionName = "0.12.3"')
p.write_text(s)
