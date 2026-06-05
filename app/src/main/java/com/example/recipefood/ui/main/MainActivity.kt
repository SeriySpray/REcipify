package com.example.recipefood.ui.main

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.example.recipefood.R

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        val viewPager = findViewById<ViewPager2>(R.id.viewPager)
        viewPager.adapter = MainPagerAdapter(this)
        
        // Визначаємо початкову сторінку: 0 для трекера, 1 для рецептів (за замовчуванням)
        val startPage = if (intent.getBooleanExtra("goto_tracker", false)) 0 else 1
        viewPager.currentItem = startPage

        val dot0 = findViewById<View>(R.id.dot0)
        val dot1 = findViewById<View>(R.id.dot1)

        fun updateDots(position: Int) {
            dot0.alpha = if (position == 0) 1f else 0.35f
            dot1.alpha = if (position == 1) 1f else 0.35f
        }
        updateDots(startPage)

        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) = updateDots(position)
        })
    }
}
