from pathlib import Path

p=Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
s=p.read_text()
old='''        val glassShape=AbsoluteRoundedCornerShape(18.dp)\n        val revealModifier=if(glassBackdrop!=null && greenGlass && offset>0f) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Color(0xFF20B86A).copy(alpha=.30f) else containerColor)'''
new='''        val glassShape=AbsoluteRoundedCornerShape(18.dp)\n        val revealModifier=if(greenGlass && offset>0f) {\n            Modifier.matchParentSize()\n                .background(\n                    Brush.horizontalGradient(\n                        listOf(\n                            Color(0xFF2FD27C).copy(alpha=.34f + .18f*reveal),\n                            Color(0xFF169D5A).copy(alpha=.22f + .12f*reveal),\n                            Color.White.copy(alpha=.08f + .08f*reveal)\n                        )\n                    ),\n                    glassShape\n                )\n                .border(1.dp,Color(0xFFB8FFD6).copy(alpha=.34f + .24f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Color(0xFF20B86A).copy(alpha=.30f) else containerColor)'''
if old not in s: raise SystemExit('Ui pattern not found')
p.write_text(s.replace(old,new,1))

p=Path('app/build.gradle.kts')
s=p.read_text().replace('versionCode = 30','versionCode = 31').replace('versionName = "0.12.2"','versionName = "0.12.3"')
p.write_text(s)
