from pathlib import Path

# Ui.kt: remove runtime backdrop rendering from journal swipe; keep green glass-like visual safely.
p=Path('app/src/main/java/ru/zvonilka/prototype/Ui.kt')
s=p.read_text()
for line in [
    'import androidx.compose.foundation.shape.AbsoluteRoundedCornerShape\n',
    'import com.kyant.backdrop.Backdrop\n',
    'import com.kyant.backdrop.drawBackdrop\n',
    'import com.kyant.backdrop.effects.blur\n',
    'import com.kyant.backdrop.effects.lens\n',
    'import com.kyant.backdrop.effects.vibrancy\n',
]:
    s=s.replace(line,'')
s=s.replace('glassBackdrop:Backdrop?=null,greenGlass:Boolean=false,','greenGlass:Boolean=false,')
old='''        val glassShape=AbsoluteRoundedCornerShape(18.dp)\n        val revealModifier=if(glassBackdrop!=null && greenGlass && offset>0f) {\n            Modifier.matchParentSize().drawBackdrop(\n                backdrop=glassBackdrop,\n                shape={glassShape},\n                effects={vibrancy();blur(9.dp.toPx());lens(14.dp.toPx(),10.dp.toPx(),depthEffect=true,chromaticAberration=true)},\n                onDrawSurface={drawRect(Color(0xFF20B86A).copy(alpha=.18f + .22f*reveal))}\n            ).border(1.dp,Color(0xFFB8FFD6).copy(alpha=.20f + .25f*reveal),glassShape)\n        } else Modifier.matchParentSize().background(if(offset>0f) Color(0xFF20B86A).copy(alpha=.30f) else containerColor)'''
new='''        val revealModifier=if(greenGlass && offset>0f) {\n            Modifier.matchParentSize()\n                .background(\n                    Brush.horizontalGradient(\n                        listOf(\n                            Color(0xFF16A85F).copy(alpha=.58f + .16f*reveal),\n                            Color(0xFF43C987).copy(alpha=.34f + .14f*reveal),\n                            Color.White.copy(alpha=.10f + .06f*reveal)\n                        )\n                    )\n                )\n                .border(1.dp,Color(0xFFC6FFE0).copy(alpha=.34f + .26f*reveal),RoundedCornerShape(18.dp))\n        } else Modifier.matchParentSize().background(if(offset>0f) Ocean else containerColor)'''
if old not in s:
    raise SystemExit('Ui journal glass block not found')
s=s.replace(old,new,1)
p.write_text(s)

# MainActivity.kt: no Backdrop is passed into journal rows anymore.
p=Path('app/src/main/java/ru/zvonilka/prototype/MainActivity.kt')
s=p.read_text()
s=s.replace('tab==0 -> HistoryList(history,glassBackdrop)','tab==0 -> HistoryList(history)')
s=s.replace('@Composable private fun HistoryList(rows:List<HistoryRecord>,glassBackdrop:Backdrop) {','@Composable private fun HistoryList(rows:List<HistoryRecord>) {')
s=s.replace(',glassBackdrop=glassBackdrop,greenGlass=true',',greenGlass=true')
p.write_text(s)

# PhoneService.kt: Android 16 fallback path avoids CallStyle notification rejection.
p=Path('app/src/main/java/ru/zvonilka/prototype/PhoneService.kt')
s=p.read_text()
old='''        if (Build.VERSION.SDK_INT >= 31) {\n            val person = Person.Builder().setName(displayName).setImportant(true).build()\n            builder.setStyle(if (ringing) Notification.CallStyle.forIncomingCall(person, action("reject"), action("answer"))\n                else Notification.CallStyle.forOngoingCall(person, action("hangup")))\n        } else {\n            if (ringing) builder.addAction(Notification.Action.Builder(null, "Принять", action("answer")).build())\n            builder.addAction(Notification.Action.Builder(null, if (ringing) "Отклонить" else "Завершить", action(if (ringing) "reject" else "hangup")).build())\n        }\n        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {\n            manager.notify(id, builder.build())\n        }'''
new='''        // Samsung/Android 16 rejects this CallStyle payload with IllegalArgumentException.\n        // Keep CallStyle where it is known to work, and use an ordinary CATEGORY_CALL\n        // notification with the same actions/full-screen intent on API 36+.\n        if (Build.VERSION.SDK_INT in 31..35) {\n            val person = Person.Builder().setName(displayName).setImportant(true).build()\n            builder.setStyle(if (ringing) Notification.CallStyle.forIncomingCall(person, action("reject"), action("answer"))\n                else Notification.CallStyle.forOngoingCall(person, action("hangup")))\n        } else {\n            if (ringing) builder.addAction(Notification.Action.Builder(null, "Принять", action("answer")).build())\n            builder.addAction(Notification.Action.Builder(null, if (ringing) "Отклонить" else "Завершить", action(if (ringing) "reject" else "hangup")).build())\n        }\n        if (Build.VERSION.SDK_INT < 33 || checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) == android.content.pm.PackageManager.PERMISSION_GRANTED) {\n            try {\n                manager.notify(id, builder.build())\n            } catch (error: IllegalArgumentException) {\n                CallDiagnostics.record(this, "notification_primary_rejected", error)\n                val fallback = Notification.Builder(this, if (ringing) "calls" else "ongoing")\n                    .setSmallIcon(android.R.drawable.sym_action_call)\n                    .setContentTitle(displayName)\n                    .setContentText(CallStore.state(call))\n                    .setCategory(Notification.CATEGORY_CALL)\n                    .setOngoing(true)\n                    .setOnlyAlertOnce(true)\n                    .setVisibility(Notification.VISIBILITY_PRIVATE)\n                    .setContentIntent(open)\n                if (ringing) {\n                    fallback.setFullScreenIntent(open,true)\n                    fallback.addAction(Notification.Action.Builder(null,"Принять",action("answer")).build())\n                }\n                fallback.addAction(Notification.Action.Builder(null,if(ringing) "Отклонить" else "Завершить",action(if(ringing) "reject" else "hangup")).build())\n                manager.notify(id,fallback.build())\n            }\n        }'''
if old not in s:
    raise SystemExit('PhoneService notification block not found')
s=s.replace(old,new,1)
p.write_text(s)

# version
p=Path('app/build.gradle.kts')
s=p.read_text().replace('versionCode = 30','versionCode = 31').replace('versionName = "0.12.2"','versionName = "0.12.3"')
p.write_text(s)
