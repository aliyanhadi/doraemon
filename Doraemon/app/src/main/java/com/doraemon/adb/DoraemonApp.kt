package com.doraemon.adb

import android.app.Application
import android.os.Build
import org.conscrypt.Conscrypt
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.security.Security

class DoraemonApp : Application() {
    override fun onCreate() {
        super.onCreate()
        // libadb-android needs Conscrypt for the TLS handshake used by Wireless Debugging
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) HiddenApiBypass.addHiddenApiExemptions("L")
        Security.insertProviderAt(Conscrypt.newProvider(), 1)
    }
}
