# 🧠 Arquitectura Recomendada para el Sistema de Visión Multimodal (NEXA OS)

Esta propuesta detalla la arquitectura técnica e implementación recomendada para el pipeline de **Visión Multimodal Inteligente** de NEXA OS. Esta arquitectura equilibra el procesamiento ágil en el dispositivo del usuario (Edge) con la potencia cognitiva de los modelos de frontera en la nube (Cloud Multimodal Orchestrator).

---

## 🏗️ Diagrama de Arquitectura del Pipeline de Visión

```mermaid
graph TD
    %% Capa Cliente (Edge)
    subgraph Capa_Edge ["📱 Capa de Cliente (Edge - Android & Capacitor)"]
        A[Captura de Cámara / Galería] --> B[Compresor de Imagen Nativo]
        B --> C{¿Tiene Código QR / EAN?}
        C -- Sí (10ms) --> D[ML Kit / ZXing Local]
        C -- No --> E[Auto-Crop & Deskew]
        E --> F[Codificador Base64]
    end

    %% Capa de Orquestación (Next.js)
    subgraph Capa_Cloud_Orchestrator ["☁️ Capa de Orquestación (Next.js /api/vision)"]
        F --> G[Receptor API REST /api/vision]
        G --> H[Categorizador por NLP / Prompt]
        H --> I{Categorías Detectadas}
        I -->|Documento / OCR| J[Prompt Document Mode]
        I -->|UI / Screenshot| K[Prompt UI/UX Mode]
        I -->|Ecuación / Math| L[Prompt LaTeX Math Mode]
        I -->|General / Fotos| M[Prompt Base Mode]
    end

    %% Capa de Inferencia (AI Providers)
    subgraph Capa_Inferencia ["🤖 Capa de Inferencia (AI Engines)"]
        J & K & L & M --> N[Manejador de Proveedor Híbrido]
        N --> O{Inferencia Multimodal}
        O -->|Primario (Streaming)| P[Gemini 1.5 Flash / Pro]
        O -->|Fallback (Resiliente)| Q[Dify Vision Agent / GLM-4.6V]
        P & Q --> R[Parseador de Salida Unificada]
    end

    %% Flujo de Retorno
    D --> S[Usuario - Respuesta Instantánea]
    R --> S[Usuario - Respuesta Completa / Streaming]
    
    classDef edge fill:#1e1b4b,stroke:#818cf8,stroke-width:2px,color:#fff;
    classDef cloud fill:#0f172a,stroke:#38bdf8,stroke-width:2px,color:#fff;
    classDef ai fill:#022c22,stroke:#34d399,stroke-width:2px,color:#fff;
    class A,B,C,D,E,F edge;
    class G,H,I,J,K,L,M,R cloud;
    class N,O,P,Q ai;
```

---

## 📱 1. Procesamiento en el Dispositivo (Edge Pre-processing)

Para optimizar el ancho de banda y garantizar una respuesta fluida en la red móvil del usuario, la aplicación nativa de Android debe realizar tres pre-procesos clave antes de enviar la imagen al servidor:

### A. Compresión Inteligente y Escalado
Las cámaras móviles actuales capturan imágenes de más de 12 megapíxeles (10MB+). Enviar esto satura la red móvil y causa altos costos de tokens.
* **Resolución Recomendada**: Escalar la imagen a una resolución máxima de **1024x1024 píxeles** (o **2048x2048 píxeles** si se detecta un documento o fórmula matemática).
* **Formato**: Comprimir en **JPEG** con una calidad del **80%**. Esto reduce el peso de la imagen de 10MB a menos de **300KB** sin pérdida perceptible para el LLM.

### B. Escaneo de QR/Código de Barras Local (Edge-OCR)
No debemos gastar recursos en la nube para procesar QRs simples.
* **Componente**: Usar **Google ML Kit Barcode Scanning** en el dispositivo nativo de Android.
* **Flujo**: Si ML Kit detecta un código QR o de barras en menos de 10ms, se procesa en el dispositivo de inmediato, evitando cualquier viaje de red a la nube.

### C. Perspectiva y Recorte de Documentos (Deskew)
Si el usuario fotografía un documento en ángulo, la precisión del OCR baja.
* **Componente**: Usar **ML Kit Document Scanner API** en Android.
* **Efecto**: Detecta las esquinas del papel, corrige la perspectiva en 2D y elimina sombras del fondo de forma local.

---

## ☁️ 2. Orquestador de Visión en la Nube (Cloud Multimodal Orchestrator)

El endpoint `/api/vision` de Next.js actúa como el cerebro de enrutamiento visual:

### A. Categorización Avanzada (Clasificador Pre-Inferencia)
El orquestador utiliza la biblioteca `vision-plus.ts` para detectar la intención de la pregunta del usuario (`detectVisionCategory`). Las categorías clave son:
1. **`document`**: Para cartas, facturas o contratos. Activa el modo OCR estricto con salida en tablas Markdown.
2. **`ui_screenshot`**: Para capturas de pantalla de aplicaciones o páginas. Analiza heurísticas de espaciado, colores e interactividad.
3. **`math`**: Para ecuaciones y problemas lógicos. Activa el parseado de fórmulas matemáticas con formato LaTeX `$ ... $`.
4. **`code_screenshot`**: Para código fuente. Transcribe el código manteniendo indentación original e identifica sintaxis de bugs.

### B. Inyección de Prompt Dinámico (Adaptive Prompting)
Según la categoría detectada por `vision-plus.ts`, se inyectan instrucciones explícitas de comportamiento al sistema de IA (ej. `getCategoryInstructions`). Esto evita respuestas vagas y fuerza al modelo a concentrarse en los detalles más críticos.

---

## 🤖 3. Capa de Inferencia Multimodal Resiliente (Double-Provider Fallback)

El backend de visión cuenta con tolerancia a fallos total:

```typescript
const VISION_PROVIDERS = {
    gemini: {
        model: 'gemini-1.5-flash', // Alta velocidad, bajo coste, excelente para visión
        keyEnv: 'GOOGLE_AI_API_KEY'
    },
    dify: {
        model: 'dify-vision-agent', // Fallback inteligente orquestado
        keyEnv: 'DIFY_API_KEY'
    }
};
```

### Flujo de Tolerancia de Inferencia:
1. **Proveedor Primario (Gemini 1.5 Flash / Pro)**:
   * **Ventajas**: Inferencia de visión ultrarrápida (menos de 2 segundos), ventana de contexto gigante y soporte nativo para Base64.
2. **Proveedor Fallback (Dify Vision Agent / GLM-4.6V)**:
   * Si las cuotas de Gemini fallan o la red se interrumpe, la petición se redirige de forma transparente al agente inteligente de **Dify** con soporte visual nativo, garantizando que el usuario nunca vea un mensaje de error.

---

## 📊 Formato de Respuesta y Parseador Unificado

Para que la app de Android pueda renderizar los resultados de forma visualmente atractiva, la salida del backend se normaliza en tres estructuras:

* **LaTeX ($...$)**: Para renderizado matemático dinámico usando MathJax o KaTeX en la aplicación nativa.
* **Markdown Tables**: Para estructuras de datos financieros y facturas.
* **JSON de Metadatos**: Enrutado asíncrono si se detectan coordenadas de clics en capturas de pantalla (UI/UX bounding boxes).
