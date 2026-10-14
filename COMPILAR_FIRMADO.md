# Cómo compilar EstiloVivo firmada (APK y bundle para Play Store) con Android Studio

Guía para quien tenga la cuenta de Google Play. No hace falta saber programar: son pasos de menú.
Tiempo estimado: 30–40 min la primera vez (la mayor parte es esperar descargas).

---

## 0. Qué necesitas
- Un ordenador (Windows, Mac o Linux) con ≥ 16 GB de RAM y ~15 GB libres.
- **Android Studio 2026.1 o más reciente** (compilación 261+; nombre "Panda"/"Quail" o posterior). Descarga: https://developer.android.com/studio . *Con una versión anterior el proyecto no abre* porque usa el plugin de Android 9.4.
- Conexión a internet (descarga unos 2–3 GB de dependencias la primera vez).
- La carpeta del proyecto `android-app` (te la pasan en un .zip: `EstiloVivo-android-fuente.zip`).
- Tu cuenta de **Google Play Console** (cuota única de 25 USD si aún no la tienes): https://play.google.com/console

## 1. Abrir el proyecto
1. Descomprime el zip en una ruta **corta y sin espacios ni tildes**, p. ej. `C:\Proyectos\EstiloVivo` (en Mac/Linux `~/EstiloVivo`).
2. Abre Android Studio → **Open** → selecciona la carpeta `android-app` (la que contiene `settings.gradle.kts`).
3. Si pregunta "Trust project", pulsa **Trust Project**.
4. Espera a que termine **Gradle Sync** (barra inferior). La primera vez tarda 5–15 min.
   - Si pide instalar el **SDK 37** ("Install missing SDK package(s)"): acepta (*SDK Manager → Android 17 (API 37) → Apply*).
   - Si pide actualizar Gradle o el plugin: **NO** aceptes actualizaciones automáticas; usa las versiones del proyecto.
   - Si falla con "JDK": *Settings → Build, Execution, Deployment → Build Tools → Gradle → Gradle JDK* → elige **jbr-21** (el que trae Android Studio).

## 2. Comprobar que la app arranca (recomendado)
Conecta un móvil Android con depuración USB o crea un emulador y pulsa ▶ **Run 'app'**. Debe abrirse la bienvenida de EstiloVivo. Puedes iniciar sesión con tu cuenta de la web.

## 3. Crear la clave de subida (SOLO LA PRIMERA VEZ — ¡guárdala bien!)
> ⚠️ Esta clave identifica tus actualizaciones. Si la pierdes, tendrás que pedir a Google un restablecimiento (con Play App Signing es posible pero lento). **Haz copia en 2 sitios** (p. ej. disco externo y gestor de contraseñas) y **nunca la subas a GitHub ni la envíes por chat sin cifrar**.

1. Menú **Build → Generate Signed App Bundle / APK…**
2. Elige **Android App Bundle** → **Next**.
3. En *Key store path* pulsa **Create new…**:
   - **Key store path:** elige una carpeta **fuera del proyecto**, p. ej. `C:\Claves\estilovivo-upload.jks`.
   - **Password / Confirm:** una contraseña fuerte (anótala).
   - **Alias:** `estilovivo`
   - **Key password:** la misma (más fácil) o distinta.
   - **Validity (years):** `30` (mínimo 25).
   - **Certificate:** nombre y apellidos, organización, país `ES`.
   - **OK**.
4. Marca **Remember passwords** sólo si es tu ordenador personal.

## 4. Generar el bundle firmado (para subir a Play)
1. Sigue en el asistente de *Generate Signed App Bundle* → **Next**.
2. **Build Variants: `release`** → **Create**.
3. Espera (3–8 min). Aparece un aviso "App bundle(s) generated successfully" → **locate**.
4. El fichero queda en `android-app\app\release\app-release.aab`. **Ese es el que se sube a Play.**

## 5. Generar también el APK firmado (para instalar directamente / pruebas)
1. **Build → Generate Signed App Bundle / APK…** → esta vez **APK** → **Next**.
2. Selecciona el mismo `.jks`, alias `estilovivo`, contraseñas → **Next**.
3. **release** + marca *V1* y *V2* (firma) → **Create**.
4. Resultado: `android-app\app\release\app-release.apk`. Cópialo al móvil e instala (permitiendo "orígenes desconocidos").

## 6. Subir a Google Play
1. Play Console → **Crear app** (datos en `PLAY_STORE.md` §1).
2. **Prueba interna → Crear versión** → acepta **Play App Signing** → sube `app-release.aab`.
3. Añade testers (emails), guarda y publica en prueba interna; abre el enlace de prueba desde un móvil.
4. Completa el contenido de la ficha y los formularios con **`PLAY_STORE.md`** (copiar/pegar).
5. Cuando pase la prueba (y si tu cuenta es personal, los **12 testers × 14 días**), pide el acceso a **Producción**.

## 7. Cada vez que publiques una actualización
1. Sube el número de versión: abre `app/build.gradle.kts` y cambia
   `versionCode = ... "1"` → `"2"`, `"3"`… (siempre mayor que el anterior) y, si quieres, `versionName` (`1.0.1`).
   *(O compila con las variables de entorno `VERSION_CODE` y `VERSION_NAME`.)*
2. Repite los pasos 4–6 con **el mismo `.jks`**, mismo alias y contraseña (ahora eliges *Choose existing…*).

## 8. Alternativa por línea de comandos (opcional)
En una terminal dentro de `android-app`:
```
# Windows (PowerShell)
$env:KEYSTORE_PATH="C:\Claves\estilovivo-upload.jks"
$env:KEYSTORE_PASSWORD="TU_CONTRASEÑA"; $env:KEY_ALIAS="estilovivo"; $env:KEY_PASSWORD="TU_CONTRASEÑA"
$env:VERSION_CODE="1"; $env:VERSION_NAME="1.0.0"
.\gradlew.bat :app:bundleRelease :app:assembleRelease
# Mac/Linux: export en vez de $env: y ./gradlew
```
Salidas: `app/build/outputs/bundle/release/app-release.aab` y `app/build/outputs/apk/release/app-release.apk`.
*(Si en Windows da "Unable to establish loopback connection": crea la carpeta `C:\gtmp` y ejecuta antes `$env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"; $env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=C:\gtmp"`.)*

## 9. Problemas frecuentes
| Síntoma | Solución |
|---|---|
| "Minimum supported Gradle version is 9.6.0" | Usa el Gradle del proyecto (wrapper): *Settings → Gradle → Use Gradle from: 'gradle-wrapper.properties'*. |
| "compileSdk 37 not found" | SDK Manager → instalar **Android 17 / API 37**. |
| Sync falla por red | Reintenta; si estás en una red corporativa, usa datos móviles/otra red. |
| "Keystore was tampered with, or password was incorrect" | La contraseña o el alias no coinciden con los del `.jks`. |
| Play dice "Has usado una clave de firma distinta" | Debes firmar siempre con la **misma clave de subida** que usaste en la primera subida. |
| Play dice "versionCode ya usado" | Sube `versionCode` (paso 7). |
| El bundle pesa mucho (~60–90 MB) | Normal: incluye ML Kit (detección de pose). Play entrega sólo lo necesario a cada móvil. |
| Error de memoria en Gradle | En `gradle.properties` sube `org.gradle.jvmargs=-Xmx4g`. |

## 10. Qué NO compartir
No subas a ningún sitio: el fichero `.jks`, sus contraseñas, ni `local.properties`. El zip de entrega ya los excluye.
