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
            text = "Звонилка · 0.1"
            textSize = 26f
            setTextColor(Color.WHITE)
        })
        root.addView(TextView(this).apply {
            text = "Технический прототип. Звонки пока через Samsung Phone."
            textSize = 15f
            setTextColor(Color.LTGRAY)
        }, LinearLayout.LayoutParams(-1, 0, 1f))
        number = EditText(this).apply {
            id = 1001
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
            text = "Открыть звонок"
            setTextColor(Color.WHITE)
            backgroundTintList = android.content.res.ColorStateList.valueOf(Color.rgb(30, 150, 70))
            setOnClickListener {
                val value = number.text.toString().filter { it in "0123456789+*#" }
                if (value.isEmpty()) {
                    Toast.makeText(this@MainActivity, "Введите номер", Toast.LENGTH_SHORT).show()
                } else try {
                    startActivity(Intent(Intent.ACTION_DIAL, Uri.fromParts("tel", value, null)))
                } catch (_: ActivityNotFoundException) {
                    Toast.makeText(this@MainActivity, "Системная звонилка недоступна", Toast.LENGTH_LONG).show()
                }
            }
        })
        setContentView(root)
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
