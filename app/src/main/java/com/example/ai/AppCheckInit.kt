package com.example.ai

import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.BuildConfig
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck
import com.google.firebase.appcheck.debug.DebugAppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

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

        if (BuildConfig.DEBUG) {
            // Clear the previously injected placeholder exactly once. The official
            // Debug provider will then generate and persist its own real device token.
            val migrationPrefs = context.getSharedPreferences(
                "noqtati_appcheck_migration",
                Context.MODE_PRIVATE
            )
            if (!migrationPrefs.getBoolean("cleared_placeholder_token_v1", false)) {
                val debugPreferencesName =
                    "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}"
                context.getSharedPreferences(debugPreferencesName, Context.MODE_PRIVATE)
                    .edit()
                    .remove("com.google.firebase.appcheck.debug.DEBUG_SECRET")
                    .commit()
                migrationPrefs.edit()
                    .putBoolean("cleared_placeholder_token_v1", true)
                    .commit()
            }
        }

        val appCheck = FirebaseAppCheck.getInstance(firebaseApp)
        if (BuildConfig.DEBUG) {
            appCheck.installAppCheckProviderFactory(
                DebugAppCheckProviderFactory.getInstance()
            )
            Log.i(TAG, "Firebase Debug App Check provider installed for debug build.")
        } else {
            appCheck.installAppCheckProviderFactory(
                PlayIntegrityAppCheckProviderFactory.getInstance()
            )
            Log.i(TAG, "Firebase Play Integrity App Check provider installed for release build.")
        }

        Log.i(
            TAG,
            "Firebase initialized successfully. projectId=${firebaseApp.options.projectId}"
        )
        null
    } catch (e: Exception) {
        Log.e(TAG, "Firebase initialization/App Check setup failed", e)
        "تعذرت تهيئة Firebase: ${e.localizedMessage ?: e.javaClass.simpleName}. " +
            "تحقق من إعدادات Firebase واتصال الإنترنت."
    }
}

/**
 * Forces the official Debug provider to create/load its device token, then returns
 * the local debug secret so a tester can register that exact value in Firebase Console.
 * This is available only in debug builds and must never be shown in production.
 */
fun requestDebugAppCheckSecret(context: Context, onReady: (String?) -> Unit) {
    if (!BuildConfig.DEBUG) {
        onReady(null)
        return
    }

    val firebaseApp = FirebaseApp.getInstance()
    FirebaseAppCheck.getInstance(firebaseApp).getToken(false)
        .addOnCompleteListener { task ->
            if (!task.isSuccessful) {
                Log.e(TAG, "Failed to request an App Check token for debug diagnostics", task.exception)
                onReady(null)
                return@addOnCompleteListener
            }

            val preferencesName =
                "com.google.firebase.appcheck.debug.store.${firebaseApp.persistenceKey}"
            val secret = context.getSharedPreferences(preferencesName, Context.MODE_PRIVATE)
                .getString("com.google.firebase.appcheck.debug.DEBUG_SECRET", null)
            onReady(secret)
        }
}
