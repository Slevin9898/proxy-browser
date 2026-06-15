package com.example.proxybrowser

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {
override fun onCreate(savedInstanceState: Bundle?) {
super.onCreate(savedInstanceState)
val tv = TextView(this)
tv.text = "Приложение работает! 🎉"
tv.textSize = 24f
setContentView(tv)
}
}
