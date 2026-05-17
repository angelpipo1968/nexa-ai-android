package com.nexa.ai.ui

import com.nexa.ai.viewmodel.AppLanguage

object NexaStrings {
    fun get(key: String, lang: AppLanguage): String {
        return when (lang) {
            AppLanguage.SPANISH -> spanish[key] ?: key
            AppLanguage.ENGLISH -> english[key] ?: key
        }
    }

    private val spanish = mapOf(
        "new_chat" to "Nuevo chat", "settings" to "Ajustes", "language" to "Idioma", "voice" to "Voz", "theme" to "Tema",
        "dark" to "Oscuro", "light" to "Claro", "system" to "Sistema", "male_1" to "Hombre 1", "male_2" to "Hombre 2", "male_3" to "Hombre 3",
        "female_1" to "Mujer 1", "female_2" to "Mujer 2", "female_3" to "Mujer 3", "login" to "Iniciar sesión",
        "logout" to "Cerrar sesión", "register" to "Registrarse", "email" to "Correo electrónico", "password" to "Contraseña",
        "thinking" to "pensando...", "input_hint" to "Escribe un mensaje...", "listening" to "🎙️ Escuchando...",
        "mic_hint" to "🎙️ hablar • ↵ enviar", "messages_count" to "mensajes", "delete_chat" to "Borrar chat",
        "pin_chat" to "Fijar chat", "rename_chat" to "Renombrar", "clone_chat" to "Clonar",
        "archive_chat" to "Archivar", "share_chat" to "Compartir", "download_chat" to "Descargar",
        "regenerate" to "Regenerar", "search_chats" to "Buscar chats...", "no_chats" to "No hay chats aún",
        "no_results" to "Sin resultados",
        "auto_speak" to "Lectura automática", "auto_speak_desc" to "NEXA habla las respuestas", "text_only" to "Solo texto",
        "welcome_msg" to "Toca el micrófono y habla,\no escribe tu mensaje.", "clear_chat" to "Limpiar chat",
        "attach" to "Adjuntar archivo", "send_img" to "Enviar imagen", "back" to "Volver",
        "update_available" to "Actualización disponible", "update_now" to "Actualizar", "later" to "Después",
        "menu" to "Menú", "surprise_me" to "Sorpréndeme", "disable_voice" to "Desactivar voz",
        "enable_voice" to "Activar voz", "stop" to "Detener", "history" to "Historial", "chats" to "chats",
        "general" to "GENERAL", "interface_section" to "INTERFAZ", "account_section" to "CUENTA",
        "upload_photo" to "Subir foto", "upload_pdf" to "Subir PDF", "send" to "Enviar", "export_pdf" to "Exportar PDF",
        "read_aloud" to "Leer en voz alta", "login_title" to "Inicia sesión", "create_account" to "Crea tu cuenta",
        "no_account" to "¿No tienes cuenta?", "has_account" to "¿Ya tienes cuenta?", "create_account_btn" to "Crear cuenta",
        "name" to "Nombre", "your_name" to "Tu nombre", "min_6" to "Mínimo 6 caracteres",
        "confirm_password" to "Confirmar contraseña", "repeat_password" to "Repite la contraseña",
        "fill_all" to "Completa todos los campos", "invalid_email" to "Email no válido", "min_chars" to "Mínimo 6 caracteres",
        "passwords_no_match" to "Las contraseñas no coinciden", "email_taken" to "Este email ya está registrado",
        "session_expired" to "Sesión expirada. Inicia sesión de nuevo.", "voice_unavailable" to "Reconocimiento de voz no disponible",
        "online" to "EN LÍNEA", "copied" to "Copiado ✓", "copy" to "Copiar", "nothing_to_export" to "No hay contenido para exportar",
        "voice_error" to "Error de voz", "connection_error" to "Error de conexión", "unknown" to "desconocido",
        "export_pdf_title" to "Exportar PDF", "generated_by" to "Generado por NEXA PRO",
        "rate_limit" to "Límite de mensajes alcanzado. Intenta más tarde.", "server_error" to "Error del servidor",
        "translator" to "Traductor en Vivo", "voice_mode" to "Modo Conversación", "voice_mode_on" to "Modo conversación activado",
        "voice_mode_off" to "Modo conversación desactivado", "voice_mode_hint" to "Habla libremente",
        "voice_mode_listening" to "Escuchando", "voice_mode_thinking" to "Pensando",
        "voice_mode_speaking" to "Respondiendo", "tap_to_stop" to "Tocar para detener",
        "pull_to_clear" to "Soltar para limpiar", "activate_voice" to "Toca para activar voz",
        "male_label" to "Hombre", "female_label" to "Mujer",
        "preferences" to "PREFERENCIAS", "danger_zone" to "ZONA DE PELIGRO",
        "email_placeholder" to "tu@email.com",
        "lottery" to "Lotería", "lottery_results" to "📊 Resultados", "lottery_generate" to "🎲 Generar",
        "lottery_view_result" to "Ver último resultado", "lottery_generate_tickets" to "Generar 5 boletos recomendados",
        "lottery_draw" to "Sorteo", "lottery_next_draw" to "Próximo sorteo",
        "lottery_prize" to "Premio", "lottery_ticket" to "Boleto",
        "lottery_recommended" to "⭐ Números recomendados"
    )

    private val english = mapOf(
        "new_chat" to "New Chat", "settings" to "Settings", "language" to "Language", "voice" to "Voice", "theme" to "Theme",
        "dark" to "Dark", "light" to "Light", "system" to "System", "male_1" to "Male 1", "male_2" to "Male 2", "male_3" to "Male 3",
        "female_1" to "Female 1", "female_2" to "Female 2", "female_3" to "Female 3", "login" to "Login",
        "logout" to "Logout", "register" to "Register", "email" to "Email", "password" to "Password",
        "thinking" to "thinking...", "input_hint" to "Type a message...", "listening" to "🎙️ Listening...",
        "mic_hint" to "🎙️ speak • ↵ send", "messages_count" to "messages", "delete_chat" to "Delete chat",
        "pin_chat" to "Pin chat", "rename_chat" to "Rename", "clone_chat" to "Clone",
        "archive_chat" to "Archive", "share_chat" to "Share", "download_chat" to "Download",
        "regenerate" to "Regenerate", "search_chats" to "Search chats...", "no_chats" to "No chats yet",
        "no_results" to "No results",
        "auto_speak" to "Auto-speak", "auto_speak_desc" to "NEXA speaks responses", "text_only" to "Text only",
        "welcome_msg" to "Tap the mic and speak,\nor type your message.", "clear_chat" to "Clear chat",
        "attach" to "Attach file", "send_img" to "Send image", "back" to "Back",
        "update_available" to "Update Available", "update_now" to "Update", "later" to "Later",
        "menu" to "Menu", "surprise_me" to "Surprise me", "disable_voice" to "Disable voice",
        "enable_voice" to "Enable voice", "stop" to "Stop", "history" to "History", "chats" to "chats",
        "general" to "GENERAL", "interface_section" to "INTERFACE", "account_section" to "ACCOUNT",
        "upload_photo" to "Upload photo", "upload_pdf" to "Upload PDF", "send" to "Send", "export_pdf" to "Export PDF",
        "read_aloud" to "Read aloud", "login_title" to "Sign in", "create_account" to "Create account",
        "no_account" to "Don't have an account?", "has_account" to "Already have an account?",
        "create_account_btn" to "Create account", "name" to "Name", "your_name" to "Your name",
        "min_6" to "Minimum 6 characters", "confirm_password" to "Confirm password", "repeat_password" to "Repeat password",
        "fill_all" to "Fill in all fields", "invalid_email" to "Invalid email", "min_chars" to "Minimum 6 characters",
        "passwords_no_match" to "Passwords don't match", "email_taken" to "This email is already registered",
        "session_expired" to "Session expired. Please sign in again.", "voice_unavailable" to "Voice recognition not available",
        "online" to "ONLINE", "copied" to "Copied ✓", "copy" to "Copy", "nothing_to_export" to "No content to export",
        "voice_error" to "Voice error", "connection_error" to "Connection error", "unknown" to "unknown",
        "export_pdf_title" to "Export PDF", "generated_by" to "Generated by NEXA PRO",
        "rate_limit" to "Message limit reached. Try again later.", "server_error" to "Server error",
        "voice_mode" to "Voice Mode", "voice_mode_on" to "Voice mode activated",
        "voice_mode_off" to "Voice mode deactivated", "voice_mode_hint" to "Speak freely",
        "voice_mode_listening" to "Listening", "voice_mode_thinking" to "Thinking",
        "voice_mode_speaking" to "Responding", "tap_to_stop" to "Tap to stop",
        "pull_to_clear" to "Release to clear", "activate_voice" to "Tap to activate voice",
        "male_label" to "Male", "female_label" to "Female",
        "preferences" to "PREFERENCES", "danger_zone" to "DANGER ZONE",
        "email_placeholder" to "your@email.com",
        "lottery" to "Lottery", "lottery_results" to "📊 Results", "lottery_generate" to "🎲 Generate",
        "lottery_view_result" to "View latest result", "lottery_generate_tickets" to "Generate 5 recommended tickets",
        "lottery_draw" to "Draw", "lottery_next_draw" to "Next draw",
        "lottery_prize" to "Prize", "lottery_ticket" to "Ticket",
        "lottery_recommended" to "⭐ Recommended numbers"
    )
}
