package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory

private const val TAG = "AppCheckInit"

/**
 * Initializes Firebase and configures the debug App Check provider for test builds.
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

        if (BuildConfig.DEBUG && BuildConfig.APP_CHECK_DEBUG_TOKEN.isNotBlank()) {
            // Firebase Android's Debug provider currently has no public runtime API
            // for supplying a fixed token. This uses the provider's documented-in-issue
            // persistence key workaround; it may need updating if Firebase changes it.
            val preferencesName =
                "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}"
            context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .edit()
                .putString(
                    "com.google.firebase.appcheck.debug.DEBUG_SECRET",
                    BuildConfig.APP_CHECK_DEBUG_TOKEN
                )
                .commit()
            Log.i(TAG, "A registered Firebase App Check debug token was configured.")
        }

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
