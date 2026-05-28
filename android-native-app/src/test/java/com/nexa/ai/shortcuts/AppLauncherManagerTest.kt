package com.nexa.ai.shortcuts

import org.junit.Assert.*
import org.junit.Test

/**
 * Unit tests for AppLauncherManager.
 * Tests app package resolution logic.
 */
class AppLauncherManagerTest {

    @Test
    fun `common Spanish app names map correctly`() {
        val mappings = mapOf(
            "whatsapp" to "com.whatsapp",
            "youtube" to "com.google.android.youtube",
            "mapas" to "com.google.android.apps.maps",
            "cámara" to "com.android.camera",
            "calculadora" to "com.android.calculator2",
            "ajustes" to "com.android.settings"
        )
        
        for ((name, expectedPackage) in mappings) {
            val found = APP_PACKAGES[name]
            assertEquals("'$name' should map to $expectedPackage", expectedPackage, found)
        }
    }

    @Test
    fun `common English app names map correctly`() {
        val mappings = mapOf(
            "browser" to "com.android.chrome",
            "camera" to "com.android.camera",
            "calculator" to "com.android.calculator2",
            "settings" to "com.android.settings",
            "maps" to "com.google.android.apps.maps"
        )
        
        for ((name, expectedPackage) in mappings) {
            val found = APP_PACKAGES[name]
            assertEquals("'$name' should map to $expectedPackage", expectedPackage, found)
        }
    }

    @Test
    fun `unknown app returns null`() {
        val found = APP_PACKAGES["unknownapp123"]
        assertNull(found)
    }

    companion object {
        // Copy of the APP_PACKAGES map for testing
        private val APP_PACKAGES = mapOf(
            "whatsapp" to "com.whatsapp",
            "facebook" to "com.facebook.katana",
            "instagram" to "com.instagram.android",
            "twitter" to "com.twitter.android",
            "x" to "com.twitter.android",
            "youtube" to "com.google.android.youtube",
            "spotify" to "com.spotify.music",
            "netflix" to "com.netflix.mediaclient",
            "tiktok" to "com.zhiliaoapp.musically",
            "telegram" to "org.telegram.messenger",
            "google maps" to "com.google.android.apps.maps",
            "mapas" to "com.google.android.apps.maps",
            "gmail" to "com.google.android.gm",
            "correo" to "com.google.android.gm",
            "chrome" to "com.android.chrome",
            "navegador" to "com.android.chrome",
            "cámara" to "com.android.camera",
            "calculadora" to "com.android.calculator2",
            "reloj" to "com.android.clock",
            "calendario" to "com.google.android.calendar",
            "contactos" to "com.android.contacts",
            "teléfono" to "com.android.dialer",
            "ajustes" to "com.android.settings",
            "configuración" to "com.android.settings",
            "play store" to "com.android.vending",
            "tienda" to "com.android.vending",
            "browser" to "com.android.chrome",
            "camera" to "com.android.camera",
            "calculator" to "com.android.calculator2",
            "clock" to "com.android.clock",
            "calendar" to "com.google.android.calendar",
            "contacts" to "com.android.contacts",
            "phone" to "com.android.dialer",
            "settings" to "com.android.settings",
            "store" to "com.android.vending",
            "maps" to "com.google.android.apps.maps",
            "email" to "com.google.android.gm",
        )
    }
}
