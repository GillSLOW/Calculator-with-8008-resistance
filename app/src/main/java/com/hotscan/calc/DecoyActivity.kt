package com.hotscan.calc

import android.graphics.Color
import android.os.Bundle
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * Harmless-looking screen shown when the decoy code is entered — an empty "nothing here"
 * placeholder so a shoulder-surfer who tries a guessed code sees nothing of interest.
 */
class DecoyActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setBackgroundColor(Color.parseColor("#FF101418"))
            addView(TextView(this@DecoyActivity).apply {
                text = "No history"
                setTextColor(Color.parseColor("#FF607D8B"))
                textSize = 16f
            })
        })
    }
}
