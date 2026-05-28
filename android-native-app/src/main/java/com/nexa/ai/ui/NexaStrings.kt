package com.nexa.ai.ui

import android.content.Context
import android.content.res.Configuration
import com.nexa.ai.i18n.LocaleManager
import com.nexa.ai.viewmodel.AppLanguage
import com.nexa.ai.R

/**
 * NexaStrings — Centralized i18n string provider for NEXA PRO.
 *
 * This is the SINGLE SOURCE OF TRUTH for all user-facing strings.
 * All strings are defined in res/values/strings.xml (English, default)
 * and res/values-es/strings.xml (Spanish).
 *
 * Supports adding new languages by simply creating a new values-XX/strings.xml.
 *
 * Usage:
 *   // From ViewModel (needs Context):
 *   NexaStrings.get(context, "new_chat")                       // uses current system locale
 *   NexaStrings.get(context, "voice_cmd_image_prompt", prompt) // format string
 *
 *   // From Composable (preferred — automatic locale):
 *   stringResource(R.string.new_chat)
 *
 *   // Legacy API (backward compatible):
 *   NexaStrings.get("new_chat", AppLanguage.ENGLISH)          // requires init(appContext)
 */
object NexaStrings {

    // Cached application context for legacy API
    private var appContext: Context? = null

    // Cached localized contexts to avoid recreating them
    @Volatile
    private var cachedEsContext: Context? = null
    @Volatile
    private var cachedEnContext: Context? = null

    /**
     * Initialize with application context. Call once from Application.onCreate().
     * Required for the legacy get(key, lang) API.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
    }

    /**
     * Clear cached contexts (e.g., after locale change).
     */
    fun clearCache() {
        cachedEsContext = null
        cachedEnContext = null
    }

    // ─── Context-based API (recommended) ───────────────────────────────────

    /**
     * Get a localized string using the current system locale.
     * Use this from ViewModels or any code that has a Context.
     */
    fun get(context: Context, key: String): String {
        return getStringFromResources(context, key)
    }

    /**
     * Get a localized string with a format argument.
     * Use for strings like "Generating video: %s"
     */
    fun get(context: Context, key: String, vararg formatArgs: Any): String {
        val base = getStringFromResources(context, key)
        return if (formatArgs.isEmpty()) base else base.format(*formatArgs)
    }

    /**
     * Get a localized string for a specific language.
     * Useful when you need a specific language regardless of system locale.
     */
    fun get(context: Context, key: String, lang: AppLanguage): String {
        val localizedContext = getLocalizedContext(context, lang)
        return getStringFromResources(localizedContext, key)
    }

    /**
     * Get a localized string for a specific language with format arguments.
     */
    fun get(context: Context, key: String, lang: AppLanguage, vararg formatArgs: Any): String {
        val localizedContext = getLocalizedContext(context, lang)
        val base = getStringFromResources(localizedContext, key)
        return if (formatArgs.isEmpty()) base else base.format(*formatArgs)
    }

    // ─── Legacy API (backward compatible) ──────────────────────────────────

    /**
     * Legacy API — get string by key and language enum.
     * Requires init() to have been called first.
     */
    fun get(key: String, lang: AppLanguage): String {
        val ctx = appContext
            ?: throw IllegalStateException("NexaStrings not initialized. Call NexaStrings.init(context) in Application.onCreate()")
        return get(ctx, key, lang)
    }

    // ─── Internal ──────────────────────────────────────────────────────────

    /**
     * Get a localized context for the given language.
     * Caches contexts to avoid recreating Configuration objects on every call.
     */
    private fun getLocalizedContext(context: Context, lang: AppLanguage): Context {
        return when (lang) {
            AppLanguage.SPANISH -> cachedEsContext ?: createLocalizedContext(context, lang).also { cachedEsContext = it }
            AppLanguage.ENGLISH -> cachedEnContext ?: createLocalizedContext(context, lang).also { cachedEnContext = it }
        }
    }

    /**
     * Create a new Context with the given language configuration.
     */
    private fun createLocalizedContext(context: Context, lang: AppLanguage): Context {
        val locale = LocaleManager.toLocale(lang)
        val config = Configuration(context.resources.configuration)
        config.setLocale(locale)
        return context.createConfigurationContext(config)
    }

    /**
     * Resolve a string resource by key name (e.g., "new_chat" → R.string.new_chat).
     * Falls back to the key name itself if no resource is found.
     */
    private fun getStringFromResources(context: Context, key: String): String {
        val res = context.resources
        val packageName = context.packageName
        val resId = res.getIdentifier(key, "string", packageName)
        return if (resId != 0) res.getString(resId) else key
    }
}
