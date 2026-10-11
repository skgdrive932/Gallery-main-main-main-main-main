package com.sk.symbiangallery

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class AboutActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_about)

        findViewById<TextView>(R.id.backButton).setOnClickListener { finish() }

        findViewById<TextView>(R.id.licenseButton).setOnClickListener {
            LicenseDialog.show(this)
        }
    }
}
