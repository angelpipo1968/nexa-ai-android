'use client';

import React from 'react';
import { Shield, Eye, Mic, Car, Lock, Server, ArrowLeft, CheckCircle, FileText } from 'lucide-react';
import Link from 'next/link';

export default function PrivacyPolicy() {
    return (
        <div className="min-h-screen bg-[#02020a] text-[#e8e8f0] selection:bg-[#00e5a0] selection:text-[#02020a] overflow-y-auto px-4 py-12 md:py-20">
            {/* Background Glows */}
            <div className="absolute top-0 left-1/4 w-[500px] h-[500px] bg-purple-900/10 rounded-full blur-[120px] pointer-events-none" />
            <div className="absolute bottom-10 right-1/4 w-[600px] h-[600px] bg-cyan-900/10 rounded-full blur-[150px] pointer-events-none" />

            <div className="max-w-4xl mx-auto relative z-10">
                {/* Back to Home Button */}
                <div className="mb-8 msg-enter" style={{ animationDelay: '0.1s' }}>
                    <Link 
                        href="/" 
                        className="inline-flex items-center gap-2 px-4 py-2 rounded-xl bg-white/5 border border-white/10 text-sm font-medium text-[#94a3b8] hover:text-[#f8fafc] hover:bg-white/10 hover:border-purple-500/30 transition-all duration-300 backdrop-blur-md"
                    >
                        <ArrowLeft className="w-4 h-4 text-[#00e5a0]" />
                        <span>Volver a NEXA OS</span>
                    </Link>
                </div>

                {/* Header */}
                <header className="mb-12 msg-enter" style={{ animationDelay: '0.2s' }}>
                    <div className="inline-flex items-center gap-3 px-3 py-1.5 rounded-full bg-purple-500/10 border border-purple-500/20 text-xs font-semibold text-[#a855f7] mb-4 uppercase tracking-wider">
                        <Shield className="w-3.5 h-3.5" />
                        Privacidad Garantizada por Hardware
                    </div>
                    <h1 className="text-4xl md:text-5xl font-extrabold tracking-tight mb-4">
                        Política de <span className="gradient-text">Privacidad</span>
                    </h1>
                    <p className="text-[#94a3b8] text-lg max-w-2xl leading-relaxed">
                        NEXA AI es una plataforma de inteligencia artificial local, privada y offline-first diseñada para smartphones, Wear OS y Android Automotive OS. Descubre cómo protegemos tu seguridad.
                    </p>
                    <div className="mt-6 flex flex-wrap gap-4 text-sm text-[#94a3b8]">
                        <span className="flex items-center gap-1.5">
                            <FileText className="w-4 h-4 text-[#00e5a0]" />
                            Última actualización: Mayo 2026
                        </span>
                        <span className="flex items-center gap-1.5">
                            <CheckCircle className="w-4 h-4 text-purple-500" />
                            100% Play Store Compliant
                        </span>
                    </div>
                </header>

                {/* Highlights Grid */}
                <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-12 msg-enter" style={{ animationDelay: '0.3s' }}>
                    <div className="p-6 rounded-2xl bg-white/[0.02] border border-white/5 backdrop-blur-md hover:border-purple-500/20 transition-all duration-300">
                        <div className="w-10 h-10 rounded-xl bg-purple-500/10 flex items-center justify-center mb-4 text-[#a855f7]">
                            <Lock className="w-5 h-5" />
                        </div>
                        <h3 className="text-base font-bold text-white mb-2">Inferencia 100% Local</h3>
                        <p className="text-xs text-[#94a3b8] leading-relaxed">
                            Tus consultas por texto, voz o imágenes se procesan de forma local en tu dispositivo utilizando modelos GGUF optimizados. Cero dependencias de la nube.
                        </p>
                    </div>

                    <div className="p-6 rounded-2xl bg-white/[0.02] border border-white/5 backdrop-blur-md hover:border-cyan-500/20 transition-all duration-300">
                        <div className="w-10 h-10 rounded-xl bg-cyan-500/10 flex items-center justify-center mb-4 text-[#06b6d4]">
                            <Mic className="w-5 h-5" />
                        </div>
                        <h3 className="text-base font-bold text-white mb-2">Micrófono Sandboxed</h3>
                        <p className="text-xs text-[#94a3b8] leading-relaxed">
                            El micrófono se activa únicamente para comandos por voz local (Whisper STT). Las grabaciones se procesan en RAM y se destruyen inmediatamente.
                        </p>
                    </div>

                    <div className="p-6 rounded-2xl bg-white/[0.02] border border-white/5 backdrop-blur-md hover:border-[#00e5a0]/20 transition-all duration-300">
                        <div className="w-10 h-10 rounded-xl bg-[#00e5a0]/10 flex items-center justify-center mb-4 text-[#00e5a0]">
                            <Car className="w-5 h-5" />
                        </div>
                        <h3 className="text-base font-bold text-white mb-2">Telemetría de Borde</h3>
                        <p className="text-xs text-[#94a3b8] leading-relaxed">
                            Los datos de sensores OBD-II / CAN-Bus permanecen cifrados en memoria para la lógica de seguridad y el diagnóstico local del copiloto. Nunca se retransmiten.
                        </p>
                    </div>
                </div>

                {/* Privacy Policy Content */}
                <main className="space-y-10 text-[#e8e8f0] msg-enter" style={{ animationDelay: '0.4s' }}>
                    
                    {/* Section 1 */}
                    <section className="p-8 rounded-2xl bg-white/[0.01] border border-white/5 backdrop-blur-md space-y-4">
                        <h2 className="text-xl font-bold text-white flex items-center gap-3">
                            <span className="text-[#00e5a0]">01.</span> Introducción y Compromiso
                        </h2>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            En <strong>NEXA AI</strong> (accesible desde <a href="https://nexa-ai.dev" className="text-purple-400 hover:underline">nexa-ai.dev</a>), la privacidad del usuario no es una opción configurables: es el pilar de nuestra arquitectura. Esta política describe cómo tratamos la información en nuestras aplicaciones para dispositivos móviles, Wear OS y unidades de infoentretenimiento de vehículos compatibles con <strong>Android Auto</strong> y <strong>Android Automotive OS</strong>.
                        </p>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            Al utilizar NEXA AI, puedes tener la absoluta certeza de que <strong>ningún dato de voz, texto, imagen o telemetría de tu coche es transmitido a servidores externos</strong> sin tu consentimiento explícito y consciente.
                        </p>
                    </section>

                    {/* Section 2 */}
                    <section className="p-8 rounded-2xl bg-white/[0.01] border border-white/5 backdrop-blur-md space-y-4">
                        <h2 className="text-xl font-bold text-white flex items-center gap-3">
                            <span className="text-cyan-400">02.</span> Permisos Requeridos y Justificación
                        </h2>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            Para ofrecer una experiencia interactiva manos libres y diagnóstico vehicular local, la aplicación solicita los siguientes permisos críticos de Android:
                        </p>
                        
                        <div className="space-y-4 mt-4">
                            <div className="flex gap-4 items-start p-4 rounded-xl bg-white/[0.02] border border-white/5">
                                <Mic className="w-5 h-5 text-cyan-400 shrink-0 mt-0.5" />
                                <div>
                                    <h4 className="text-sm font-bold text-white">Grabar Audio (android.permission.RECORD_AUDIO)</h4>
                                    <p className="text-xs text-[#94a3b8] leading-relaxed mt-1">
                                        Requerido para activar la entrada por voz y permitir la transcripción de tus peticiones locales a través del motor Whisper. El audio se procesa en búferes de RAM volátil y se destruye inmediatamente tras el parseo del comando. No almacenamos copias de voz ni enviamos flujos de audio a la nube.
                                    </p>
                                </div>
                            </div>

                            <div className="flex gap-4 items-start p-4 rounded-xl bg-white/[0.02] border border-white/5">
                                <Car className="w-5 h-5 text-[#00e5a0] shrink-0 mt-0.5" />
                                <div>
                                    <h4 className="text-sm font-bold text-white">Acceso a Sensores del Vehículo (OBD-II / CAN-Bus Bluetooth)</h4>
                                    <p className="text-xs text-[#94a3b8] leading-relaxed mt-1">
                                        NEXA AI Automotive lee los PIDs de velocidad, RPM, niveles de combustible e indicadores térmicos a través de adaptadores Bluetooth ELM327 y CAN-Bus USB. Esta información tiene dos fines locales estrictos: (a) alimentar el contexto de diagnóstico de tu asistente local y (b) aplicar las restricciones de interfaz visual en movimiento (Car UX Restrictions), bloqueando pantallas y teclados para tu total seguridad.
                                    </p>
                                </div>
                            </div>

                            <div className="flex gap-4 items-start p-4 rounded-xl bg-white/[0.02] border border-white/5">
                                <Shield className="w-5 h-5 text-purple-400 shrink-0 mt-0.5" />
                                <div>
                                    <h4 className="text-sm font-bold text-white">Servicios de Automoción (android.permission.BIND_CAR_APP)</h4>
                                    <p className="text-xs text-[#94a3b8] leading-relaxed mt-1">
                                        Obligatorio para que la app se comunique e integre con las pantallas de infoentretenimiento de Android Auto y Automotive. Garantiza la proyección segura basada estrictamente en las plantillas y reglas de diseño aprobadas por Google.
                                    </p>
                                </div>
                            </div>
                        </div>
                    </section>

                    {/* Section 3 */}
                    <section className="p-8 rounded-2xl bg-white/[0.01] border border-white/5 backdrop-blur-md space-y-4">
                        <h2 className="text-xl font-bold text-white flex items-center gap-3">
                            <span className="text-purple-400">03.</span> Almacenamiento y Protección de Datos
                        </h2>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            NEXA AI sigue una estricta política de <strong>retención cero en servidor</strong>:
                        </p>
                        <ul className="list-disc pl-6 space-y-2 text-sm text-[#94a3b8]">
                            <li>
                                <strong className="text-white">Historial de Chat Local:</strong> Tus conversaciones se almacenan localmente en la base de datos Room cifrada del propio dispositivo. Nadie fuera de ti tiene acceso físico o lógico a este historial.
                            </li>
                            <li>
                                <strong className="text-white">Memoria Episódica Emocional:</strong> El motor de memoria y empatía local procesa los perfiles directamente en el almacenamiento privado de la app en la memoria flash de tu smartphone o coche, sin sincronización automática en la nube.
                            </li>
                            <li>
                                <strong className="text-white">Sincronización Opcional:</strong> Si decides habilitar la sincronización en la nube (a través de Supabase en la sección de configuración web), los datos viajan cifrados mediante HTTPS y se resguardan de forma segura bajo credenciales de usuario únicas.
                            </li>
                        </ul>
                    </section>

                    {/* Section 4 */}
                    <section className="p-8 rounded-2xl bg-white/[0.01] border border-white/5 backdrop-blur-md space-y-4">
                        <h2 className="text-xl font-bold text-white flex items-center gap-3">
                            <span className="text-cyan-400">04.</span> Proveedores de Servicios y API de Terceros
                        </h2>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            NEXA AI puede utilizar componentes locales de ML Kit (como Language Identification, Translation y Smart Reply) provistos por Google. Estas APIs operan localmente en el SDK de tu teléfono Android y cumplen rigurosamente con las políticas de privacidad de los Google Play Services.
                        </p>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            Si el usuario decide activar manualmente la conexión en línea, las búsquedas en la web (Web Search Manager) se procesan de manera anonimizada para proteger tu ubicación e identidad física.
                        </p>
                    </section>

                    {/* Section 5 */}
                    <section className="p-8 rounded-2xl bg-white/[0.01] border border-white/5 backdrop-blur-md space-y-4">
                        <h2 className="text-xl font-bold text-white flex items-center gap-3">
                            <span className="text-[#00e5a0]">05.</span> Cambios en esta Política
                        </h2>
                        <p className="text-sm text-[#94a3b8] leading-relaxed">
                            Podemos actualizar nuestra Política de Privacidad periódicamente para reflejar ajustes regulatorios o nuevas características locales. Te recomendamos revisar esta página periódicamente. Cualquier modificación sustancial se notificará mediante un banner claro dentro de la aplicación móvil.
                        </p>
                    </section>

                    {/* Section 6 */}
                    <section className="p-8 rounded-2xl bg-gradient-to-r from-purple-950/20 to-cyan-950/20 border border-purple-500/15 space-y-4 text-center">
                        <Server className="w-8 h-8 text-[#00e5a0] mx-auto mb-2" />
                        <h3 className="text-lg font-bold text-white">¿Tienes alguna pregunta técnica sobre nuestra arquitectura de borde?</h3>
                        <p className="text-xs text-[#94a3b8] max-w-lg mx-auto leading-relaxed">
                            Dado que somos una plataforma descentralizada sin recopilación de datos, no tenemos base de datos de usuarios a la cual consultar. Para dudas sobre el código abierto o la integración del motor nativo C++, por favor contáctanos.
                        </p>
                        <div className="pt-2">
                            <a 
                                href="mailto:support@nexa-ai.dev" 
                                className="inline-block px-6 py-2.5 rounded-xl bg-purple-600 hover:bg-purple-500 text-white font-semibold text-sm transition-all duration-300 shadow-lg shadow-purple-600/20"
                            >
                                Contactar Soporte
                            </a>
                        </div>
                    </section>
                </main>

                {/* Footer */}
                <footer className="mt-16 pt-8 border-t border-white/5 text-center text-xs text-[#94a3b8] space-y-2">
                    <p>© 2026 NEXA AI. Todos los derechos reservados.</p>
                    <p>Desarrollado para el ecosistema móvil, Wear OS y Android Automotive OS.</p>
                </footer>
            </div>
        </div>
    );
}
