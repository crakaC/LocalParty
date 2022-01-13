package com.crakac.localparty

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast

fun Activity.showInstalledAppDetails() {
    val intent = Intent()
        .setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        .setData(Uri.parse("package:" + BuildConfig.APPLICATION_ID))
    startActivity(intent)
}

fun Context.showToast(text: String) {
    Toast.makeText(this, text, Toast.LENGTH_SHORT).show()
}