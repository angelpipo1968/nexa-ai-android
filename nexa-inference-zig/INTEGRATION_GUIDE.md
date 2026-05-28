# Guía de Integración: Motor de Inferencia en Zig

Esta guía explica cómo conectar la librería nativa de inferencia que acabamos de crear en `nexa-inference-zig` con la app de Android (vía JNI) y con Node.js/TypeScript (vía FFI).

## 1. Compilación del Motor
Asegúrate de tener Zig instalado (se descarga desde `ziglang.org`).
Desde la carpeta `c:\NexaIA\nexa-inference-zig`:

```bash
# Compilar la librería dinámica
zig build

# Correr las pruebas unitarias (Ej. prueba de MatMul)
zig build test
```
Esto generará los binarios en `zig-out/lib/`. En Windows generará `nexa_inference.dll`, en Linux `.so`, en Mac `.dylib`.

## 2. Integración con Android (JNI)

Para usar esto en Kotlin (`nexa-ai-android`), necesitamos exponer las funciones usando la API JNI de Java.

1. **Crear el wrapper JNI en C/Zig**:
```zig
const std = @import("std");
const c_api = @import("c_api.zig");

// JNI signature (ejemplo básico)
export fn Java_com_nexa_ai_InferenceEngine_init(env: ?*anyopaque, obj: ?*anyopaque) bool {
    return c_api.nexa_init();
}
```

2. **Cargar la librería en Kotlin**:
```kotlin
package com.nexa.ai

class InferenceEngine {
    init {
        System.loadLibrary("nexa_inference")
    }
    
    external fun init(): Boolean
    external fun infer(prompt: String): String
}
```

## 3. Integración con Node.js / TypeScript (FFI)

Para usarlo en el backend o en la CLI basada en TS, usaremos `ffi-napi` o `koffi`.

1. Instalar dependencias: `npm install koffi`
2. Crear archivo `src/inference/zig_engine.ts`:

```typescript
import { load } from 'koffi';
import * as path from 'path';

// Ruta al .dll o .so generado por zig build
const libPath = path.join(__dirname, '../../nexa-inference-zig/zig-out/lib/nexa_inference.dll');
const nexaLib = load(libPath);

// Mapear las funciones C
const nexa_init = nexaLib.func('nexa_init', 'bool', []);
const nexa_free = nexaLib.func('nexa_free', 'void', []);
const nexa_infer = nexaLib.func('nexa_infer', 'str', ['str']);

export class ZigEngine {
    constructor() {
        nexa_init();
    }
    
    infer(prompt: string): string {
        return nexa_infer(prompt);
    }
    
    destroy() {
        nexa_free();
    }
}
```

## Próximos Pasos (Hoja de Ruta)
- Importar los pesos reales (GGUF).
- Implementar la arquitectura Llama / Mistral.
- Optimizar las multiplicaciones de matrices usando OpenBLAS y compilando con `-O ReleaseFast`.
