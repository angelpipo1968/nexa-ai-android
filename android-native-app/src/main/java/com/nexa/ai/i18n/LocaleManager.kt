package com.nexa.ai.i18n

import android.app.Activity
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import com.nexa.ai.viewmodel.AppLanguage
import java.util.Locale

/**
 * LocaleManager — Centralized language/locale management for NEXA PRO.
 *
 * Responsibilities:
 * 1. Map AppLanguage enum to Android Locale objects
 * 2. Apply locale changes at the Activity/Context level (Configuration + AppCompat)
 * 3. Provide the current display locale
 * 4. Resolve string resource IDs by key name for ViewModel/non-Composable use
 *
 * Usage:
 *   - In Activity.onCreate(): LocaleManager.applyLanguage(this, language)
 *   - In Activity.onAttachBaseContext(): LocaleManager.wrapContext(base, language)
 *   - In ViewModel: NexaStrings.get(context, "key") or NexaStrings.get(context, "key", formatArg)
 *   - In Composable: stringResource(R.string.key) — works automatically after locale is applied
 */
object LocaleManager {

    /**
     * Maps an AppLanguage to its corresponding Android Locale.
     */
    fun toLocale(lang: AppLanguage): Locale {
        return when (lang) {
            AppLanguage.SPANISH -> Locale("es", "ES")
            AppLanguage.ENGLISH -> Locale.US
        }
    }

    /**
     * Returns the language tag string (e.g. "es-ES", "en-US").
     */
    fun toLanguageTag(lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.SPANISH -> "es-ES"
            AppLanguage.ENGLISH -> "en-US"
        }
    }

    /**
     * Wrap a base context with the given language configuration.
     * Call this in Activity.attachBaseContext() to apply language before content is created.
     */
    fun wrapContext(base: Context, language: AppLanguage): Context {
        val locale = toLocale(language)
        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            base.createConfigurationContext(config)
        } else {
            @Suppress("DEPRECATION")
            base.resources.updateConfiguration(config, base.resources.displayMetrics)
            base
        }
    }

    /**
     * Apply the given language to an Activity context at runtime.
     * Use this for dynamic language switching (e.g. from Settings) when you want
     * to recreate the activity with the new language.
     */
    fun applyLanguage(activity: Activity, language: AppLanguage) {
        val locale = toLocale(language)
        val config = Configuration(activity.resources.configuration)
        config.setLocale(locale)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1) {
            config.setLayoutDirection(locale)
        }

        @Suppress("DEPRECATION")
        activity.resources.updateConfiguration(config, activity.resources.displayMetrics)

        // Recreate activity to apply changes
        activity.recreate()
    }

    /**
     * Get the current locale from the given context's configuration.
     */
    fun getCurrentLocale(context: Context): Locale {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            context.resources.configuration.locales[0]
        } else {
            @Suppress("DEPRECATION")
            context.resources.configuration.locale
        }
    }

    /**
     * Check if the current locale matches the given language.
     */
    fun isCurrentLanguage(context: Context, language: AppLanguage): Boolean {
        val current = getCurrentLocale(context)
        val target = toLocale(language)
        return current.language == target.language
    }
}
