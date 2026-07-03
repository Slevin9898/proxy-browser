package com.example.proxybrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val prefs = getSharedPreferences("settings", MODE_PRIVATE)

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(40, 60, 40, 40)

        val title = TextView(this)
        title.text = "Proxy Browser"
        title.textSize = 26f
        layout.addView(title)

        val label = TextView(this)
        label.text = "Прокси (ip:порт:логин:пароль):"
        label.textSize = 16f
        label.setPadding(0, 50, 0, 10)
        layout.addView(label)

        val proxyInput = EditText(this)
        proxyInput.hint = "185.80.150.79:8000:gry8CL:SdrwLb"
        proxyInput.setText(prefs.getString("proxy", ""))
        layout.addView(proxyInput)

        val useProxyCheck = CheckBox(this)
        useProxyCheck.text = "Использовать прокси"
        useProxyCheck.textSize = 16f
        useProxyCheck.isChecked = prefs.getBoolean("use_proxy", false)
        val cbParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        cbParams.topMargin = 30
        useProxyCheck.layoutParams = cbParams
        layout.addView(useProxyCheck)

        val statusText = TextView(this)
        statusText.text = ""
        statusText.textSize = 14f
        statusText.setPadding(0, 20, 0, 0)
        layout.addView(statusText)

        val startButton = Button(this)
        startButton.text = "Запустить браузер"
        startButton.textSize = 18f
        val btnParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT)
        btnParams.topMargin = 40
        startButton.layoutParams = btnParams
        layout.addView(startButton)

        startButton.setOnClickListener {
            val proxyText = proxyInput.text.toString().trim()
            val useProxy = useProxyCheck.isChecked

            prefs.edit()
                .putString("proxy", proxyText)
                .putBoolean("use_proxy", useProxy)
                .apply()

            if (!useProxy) {
                startActivity(Intent(this, BrowserActivity::class.java))
                return@setOnClickListener
            }

            val parts = proxyText.split(":")
            if (parts.size < 2) {
                statusText.text = "Неверный формат. Нужно: ip:порт:логин:пароль"
                Toast.makeText(this, "Неверный формат прокси", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            val host = parts[0].trim()
            val port = parts[1].trim().toIntOrNull()
            val user = if (parts.size > 2) parts[2].trim() else null
            val pass = if (parts.size > 3) parts[3].trim() else null

            if (host.isEmpty() || port == null || port !in 1..65535) {
                statusText.text = "Неверный формат. Нужно: ip:порт:логин:пароль"
                Toast.makeText(this, "Неверный формат прокси", Toast.LENGTH_LONG).show()
                return@setOnClickListener
            }

            startButton.isEnabled = false
            startButton.text = "Проверяем прокси..."
            statusText.text = "Подключаемся к прокси-серверу..."

            Thread {
                var errorMessage: String? = null
                try {
                    ProxyTester.test(host, port, user, pass)
                } catch (e: Exception) {
                    errorMessage = e.message ?: "не удалось подключиться"
                }
                runOnUiThread {
                    startButton.isEnabled = true
                    startButton.text = "Запустить браузер"
                    if (errorMessage == null) {
                        statusText.text = "Прокси работает!"
                        startActivity(Intent(this, BrowserActivity::class.java))
                    } else {
                        statusText.text = "Ошибка: " + errorMessage
                        Toast.makeText(this, "Прокси не работает: " + errorMessage, Toast.LENGTH_LONG).show()
                    }
                }
            }.start()
        }

        setContentView(layout)
    }
}
