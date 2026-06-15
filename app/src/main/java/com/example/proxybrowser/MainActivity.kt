package com.example.proxybrowser

import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
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
        prefs.edit()
            .putString("proxy", proxyInput.text.toString().trim())
            .putBoolean("use_proxy", useProxyCheck.isChecked)
            .apply()
        startActivity(Intent(this, BrowserActivity::class.java))
    }

    setContentView(layout)
}
}
