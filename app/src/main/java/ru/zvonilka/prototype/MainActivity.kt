package ru.zvonilka.prototype

import android.app.Activity
import android.content.Intent
import android.content.ActivityNotFoundException
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.widget.*

class MainActivity : Activity() {
    private lateinit var number: EditText
    private lateinit var status: TextView
    private val roles get() = getSystemService(android.app.role.RoleManager::class.java)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 16, 24, 16)
            setBackgroundColor(Color.rgb(20, 20, 22))
        }
        root.setOnApplyWindowInsetsListener { view, insets ->
            view.setPadding(24, insets.systemWindowInsetTop + 16, 24, insets.systemWindowInsetBottom + 16)
            insets
        }
        root.addView(TextView(this).apply {
            text = "Звонилка · 0.2.1"
            textSize = 26f
            setTextColor(Color.WHITE)
        })
        status = TextView(this).apply { textSize = 15f; setTextColor(Color.LTGRAY) }
        root.addView(status, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(Button(this).apply {
            text = "Настроить звонки"
            setOnClickListener { setup() }
        })
        root.addView(Button(this).apply {
            text = "Вернуться к разговору"
            setOnClickListener {
                if (CallStore.liveCalls().isNotEmpty()) startActivity(Intent(this@MainActivity, CallActivity::class.java))
                else Toast.makeText(this@MainActivity, "Сейчас нет вызовов", Toast.LENGTH_SHORT).show()
            }
        })
        number = EditText(this).apply {
    id = android.view.View.generateViewId()
            inputType = android.text.InputType.TYPE_CLASS_PHONE
            showSoftInputOnFocus = false
            textSize = 30f
            gravity = Gravity.CENTER
            setSingleLine(true)
            setTextColor(Color.WHITE)
            hint = "Номер телефона"
            setHintTextColor(Color.GRAY)
            setText(savedInstanceState?.getString("number") ?: "")
        }
        root.addView(number)
        root.addView(Button(this).apply {
            text = "⌫"
            contentDescription = "Удалить цифру. Удерживайте, чтобы очистить номер"
            setOnClickListener {
                val start = number.selectionStart.coerceAtLeast(0)
                val end = number.selectionEnd.coerceAtLeast(start)
                if (end > start) number.text.delete(start, end)
                else if (start > 0) number.text.delete(start - 1, start)
            }
            setOnLongClickListener { number.text.clear(); true }
        })
        listOf("123", "456", "789", "*0#").forEach { digits ->
            val row = LinearLayout(this)
            digits.forEach { digit ->
                row.addView(Button(this).apply {
                    text = digit.toString()
                    textSize = 28f
                    setOnClickListener { insert(digit.toString()); performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) }
                    if (digit == '0') setOnLongClickListener { insert("+"); true }
                }, LinearLayout.LayoutParams(0, 72 * resources.displayMetrics.density.toInt().coerceAtLeast(1), 1f))
            }
            root.addView(row)
        }
        root.addView(Button(this).apply {
            text = "Позвонить"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 150, 70))
            setOnClickListener {
                val value = number.text.toString().filter { it in "0123456789+*#" }
                if (value.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Введите номер", Toast.LENGTH_SHORT).show()
                } else try {
                    if (!roles.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER) ||
                        checkSelfPermission(android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
                        setup()
                    } else {
                        getSystemService(android.telecom.TelecomManager::class.java).placeCall(Uri.fromParts("tel", value, null), Bundle())
                    }
                } catch (_: RuntimeException) {
                    Toast.makeText(this@MainActivity, "Не удалось начать вызов. Проверьте разрешения и SIM-карту", Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(root)
        if (savedInstanceState == null) readNumber(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        readNumber(intent)
    }
    private fun readNumber(intent: Intent) {
        if (intent.action == Intent.ACTION_DIAL && intent.data?.scheme == "tel") {
            number.setText(intent.data?.schemeSpecificPart ?: "")
            number.setSelection(number.length())
        }
    }
    override fun onResume() {
        super.onResume()
        val ready = roles.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)
        val notifications = getSystemService(android.app.NotificationManager::class.java)
        status.text = when {
            !ready -> "Назначьте Звонилку приложением телефона по умолчанию."
            checkSelfPermission(android.Manifest.permission.CALL_PHONE) != android.content.pm.PackageManager.PERMISSION_GRANTED -> "Разрешите телефонные вызовы."
            !notifications.areNotificationsEnabled() -> "Включите уведомления для входящих вызовов."
            android.os.Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent() -> "Разрешите полноэкранные уведомления для звонков при заблокированном экране."
            else -> "Звонилка назначена по умолчанию. Готова к проверке звонков."
        }
    }
    private fun setup() {
        if (!roles.isRoleHeld(android.app.role.RoleManager.ROLE_DIALER)) {
            if (roles.isRoleAvailable(android.app.role.RoleManager.ROLE_DIALER))
                startActivityForResult(roles.createRequestRoleIntent(android.app.role.RoleManager.ROLE_DIALER), 10)
            return
        }
        val permissions = mutableListOf(android.Manifest.permission.CALL_PHONE)
        if (android.os.Build.VERSION.SDK_INT >= 33) permissions.add(android.Manifest.permission.POST_NOTIFICATIONS)
        val missing = permissions.filter { checkSelfPermission(it) != android.content.pm.PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) {
            requestPermissions(missing.toTypedArray(), 11)
            return
        }
        val notifications = getSystemService(android.app.NotificationManager::class.java)
        if (!notifications.areNotificationsEnabled()) {
            startActivity(Intent(android.provider.Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(android.provider.Settings.EXTRA_APP_PACKAGE, packageName))
        } else if (android.os.Build.VERSION.SDK_INT >= 34 && !notifications.canUseFullScreenIntent()) {
            startActivity(Intent(android.provider.Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT, Uri.parse("package:$packageName")))
        } else Toast.makeText(this, "Звонки настроены", Toast.LENGTH_SHORT).show()
    }
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == 10 && resultCode == RESULT_OK) setup()
    }
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 11 && grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) setup()
    }

    private fun insert(value: String) {
        val start = number.selectionStart.coerceAtLeast(0)
        val end = number.selectionEnd.coerceAtLeast(start)
        number.text.replace(start, end, value)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        outState.putString("number", number.text.toString())
        super.onSaveInstanceState(outState)
    }
}
