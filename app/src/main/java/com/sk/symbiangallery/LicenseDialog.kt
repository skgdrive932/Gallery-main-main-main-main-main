package com.sk.symbiangallery

import android.app.AlertDialog
import android.content.Context

object LicenseDialog {
    fun show(context: Context) {
        val text = context.resources.openRawResource(R.raw.apache_license)
            .bufferedReader()
            .use { it.readText() }

        AlertDialog.Builder(context)
            .setTitle("Apache License 2.0")
            .setMessage(text)
            .setPositiveButton("OK", null)
            .show()
    }
}
