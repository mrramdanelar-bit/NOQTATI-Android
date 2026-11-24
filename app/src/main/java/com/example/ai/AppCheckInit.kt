
package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheckInit"

fun configureAppCheck(context: Context, intent: Intent?): String? {
    return try {
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }

        FirebaseAppCheck.getInstance().installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        Log.d(TAG, "Firebase Debug App Check provider installed.")
        null
    } catch (e: Exception) {
        Log.e(TAG, "Failed to initialize Firebase App Check", e)
        null
    }
}
