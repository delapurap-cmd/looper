# GUÍA PARA LA IA IMPLEMENTADORA — NeonLoop

Eres una IA asistiendo a Cami (vibe coder, sin conocimiento técnico profundo).
**Responde corto, numerado, en lenguaje simple, en español.**

## Qué es esto
Looper de 8 pistas para Android. Kotlin + Jetpack Compose. Sin librerías de audio externas (todo DSP propio).

## Arquitectura (no la cambies sin avisar)
```
LooperViewModel.kt        ← orquestador central (estado + lógica)
audio/AudioEngine.kt      ← grabación (AudioRecord) y playback (AudioTrack), 44.1kHz mono 16-bit
audio/TransientDetector.kt← detecta golpes por salto de energía RMS
audio/BpmDetector.kt      ← BPM por intervalos entre onsets (60–180)
audio/Quantizer.kt        ← corta el loop al múltiplo de beat más cercano + fades anti-click
audio/PitchShifter.kt     ← pitch (semitonos) y time-stretch (OLA con ventana Hann)
audio/Mixer.kt            ← mezcla 8 slots en tiempo real, vol/pan/mute/solo
data/WavIO.kt             ← lee/escribe WAV PCM
data/ProjectManager.kt    ← proyectos en filesDir/projects/<id>/ (JSON + WAVs)
data/Exporter.kt          ← exporta mezcla a WAV estéreo
ui/                       ← Compose: pads 2x4, mixer sheet, pitch sheet, tema neón
```

## Flujo de grabación (importante)
1. Usuario toca pad vacío → `armTrack()` → estado ARMADO, escuchando.
2. Primer transiente detectado → empieza a grabar de verdad (sin silencio inicial).
3. Usuario toca de nuevo → `stopRecording()` → cuantiza al BPM → suena en loop.
4. **El primer loop grabado define el BPM del proyecto** (autodetect; fallback 120).

## Estado actual
- ✅ Esqueleto completo, compila la estructura (verificar en primer build)
- ⚠️ NO probado en dispositivo. Esperar bugs de timing/latencia.

## Pendientes (en orden)
1. Primer build + arreglar errores de compilación si los hay.
2. Probar grabación real: ajustar `threshold` del TransientDetector si no detecta o detecta de más.
3. Latencia: si los loops quedan desfasados, compensar la latencia de entrada (típico ~50–150ms; restar samples al inicio).
4. Sincronizar inicio de pistas nuevas con el loop maestro (que entren en el beat 1, no en cualquier momento).
5. Pantalla de proyectos (lista, cargar, borrar) — `ProjectManager` ya tiene todo, falta UI.
6. Export M4A/AAC con `MediaCodec` (Android no trae encoder MP3 nativo). Si Cami insiste en MP3 real, agregar LAME (mp3lame JNI o lib `com.naman14:TAndroidLame`— verificar disponibilidad).
7. Calidad de pitch: el OLA propio es decente. Para calidad pro, migrar a SoundTouch vía JNI.

## Reglas para trabajar con Cami
1. Respuestas extremadamente cortas y numeradas.
2. Lenguaje simple, nada de jerga sin explicar.
3. Un cambio a la vez; Cami prueba y da feedback iterativo.
4. Entregar archivos completos, no fragmentos sueltos.

## Entorno de Cami (respetar)
1. JDK 17 recién instalado (el 11 sigue para Flutter; no romper eso).
2. Android SDK con platforms 31–36.1 y build-tools hasta 36.1 → compileSdk 35 OK.
3. Gradle: SOLO usar el wrapper (`./gradlew`), no instalar global.
4. Si Gradle agarra el JDK equivocado, usar `org.gradle.java.home` en gradle.properties.
