# NeonLoop — Cómo implementarlo

## 0. ÚNICO requisito nuevo: JDK 17
Tu máquina tiene Java 11; el proyecto necesita Java 17 (no toques el 11, pueden convivir).

1. Descarga **Temurin JDK 17** de https://adoptium.net (elige tu sistema operativo).
2. Instálalo.
3. Crea el archivo `local.properties` está bien que lo genere el build; pero para apuntar al JDK 17 sin pelear con variables, agrega esta línea al archivo `gradle.properties` del proyecto (cambia la ruta por donde quedó instalado):
   - Windows: `org.gradle.java.home=C:/Program Files/Eclipse Adoptium/jdk-17.x.x-hotspot`
   - Mac: `org.gradle.java.home=/Library/Java/JavaVirtualMachines/temurin-17.jdk/Contents/Home`
   - Linux: `org.gradle.java.home=/usr/lib/jvm/temurin-17-jdk-amd64`

## 1. Lo que ya trae el zip (no instales Gradle)
1. `gradlew` / `gradlew.bat` → wrapper incluido, descarga Gradle 8.9 solo la primera vez.
2. Compatible con tu sistema: compileSdk 35 ✅, build-tools 35 ✅, Kotlin 2.0.0, AGP 8.5.2.

## 2. Primer build
1. Descomprime y abre la carpeta en VS Code.
2. Conecta el celular por USB (depuración activada).
3. Terminal:
   - Windows: `gradlew.bat installDebug`
   - Mac/Linux: `./gradlew installDebug`
4. La primera vez tarda (descarga Gradle y dependencias). Es normal.
5. Acepta el permiso de micrófono al abrir la app.

## 3. Darle contexto a tu IA
1. Pásale `GUIA_IA.md` al inicio de la sesión.
2. Dile: "Lee GUIA_IA.md y ayúdame con el pendiente #1".

## 4. Cómo se usa la app
1. Toca un pad → queda ARMADO (amarillo).
2. Toca/canta algo → graba desde el primer golpe (rojo).
3. Toca de nuevo → corta, cuantiza y loopea (color neón).
4. El primer loop define el BPM automáticamente.
5. Toca un pad sonando → editor de pitch.
6. Mantén presionado un pad → borra la pista.
7. Botones abajo: ▶ play/stop · MIX mezclador · 💾 guardar · WAV exportar.
