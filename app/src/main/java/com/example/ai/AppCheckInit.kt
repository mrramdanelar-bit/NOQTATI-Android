package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheckInit"

/**
 * Initializes the default Firebase app before any Firebase AI calls.
 * Returns null on success, or a user-readable diagnostic on failure.
 */
fun configureAppCheck(context: Context, intent: Intent?): String? {
    return try {
        val firebaseApp = FirebaseApp.getApps(context)
            .firstOrNull { it.name == FirebaseApp.DEFAULT_APP_NAME }
            ?: FirebaseApp.initializeApp(context)
            ?: throw IllegalStateException(
                "لم يتم العثور على إعدادات Firebase داخل التطبيق. تحقق من app/google-services.json " +
                    "ومن تطابق اسم الحزمة مع التطبيق."
            )

        FirebaseAppCheck.getInstance(firebaseApp).installAppCheckProviderFactory(
            DebugAppCheckProviderFactory.getInstance()
        )

        Log.i(
            TAG,
            "Firebase initialized successfully. projectId=${firebaseApp.options.projectId}"
        )
        Log.i(TAG, "Firebase Debug App Check provider installed.")
        null
    } catch (e: Exception) {
        Log.e(TAG, "Firebase initialization/App Check setup failed", e)
        "تعذرت تهيئة Firebase: ${e.localizedMessage ?: e.javaClass.simpleName}. " +
            "تحقق من إعدادات Firebase واتصال الإنترنت."
    }
}
