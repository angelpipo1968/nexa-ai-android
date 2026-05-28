package com.nexa.ai;

/**
 * NEXA CORE - JNI Native Bridge
 * Permite a la aplicación de Android (Java/Kotlin) cargar y comunicarse con el
 * motor de inferencia nativo de alto rendimiento escrito en Zig.
 */
public class NexaInference {
    
    static {
        // Carga la librería dinámica nativa (nexa_inference.dll o libnexa_inference.so)
        try {
            System.loadLibrary("nexa_inference");
        } catch (UnsatisfiedLinkError e) {
            System.err.println("❌ [JNI] No se pudo cargar la librería nexa_inference: " + e.getMessage());
        }
    }

    /**
     * Inicializa el motor de inferencia nativo y asigna los contextos necesarios.
     * @return true si se inicializó correctamente, false en caso contrario.
     */
    public static native boolean nexaInit();

    /**
     * Libera todos los recursos y tensores asignados en la memoria nativa.
     */
    public static native void nexaFree();

    /**
     * Pasa una ruta de archivo al motor para parsear la estructura del modelo GGUF.
     * @param ggufPath Ruta absoluta al archivo .gguf
     * @return true si el parser se ejecutó sin errores.
     */
    public static native boolean nexaLoadGguf(String ggufPath);

    /**
     * Ejecuta una inferencia nativa pasando un prompt y recibiendo la respuesta.
     * @param prompt Texto de entrada para la inferencia
     * @return La respuesta decodificada directamente desde el motor de Zig.
     */
    public static native String nexaInfer(String prompt);
}
