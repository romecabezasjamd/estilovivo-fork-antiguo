# HANDOFF — App nativa Android de EstiloVivo (para continuar con otra IA)

Léelo entero antes de tocar nada. Después lee `AGENTS.md` (reglas de código/diseño) y `app/src/main/java/com/estilovivo/app/core/data/README.md` (API de los repositorios).

## 1. Qué es y dónde está todo
App **nativa Kotlin + Jetpack Compose** que replica TODA la web de EstiloVivo y usa **el mismo servidor** (`https://estilovivo.xyoncloud.win/api`, misma BD, mismas cuentas).

Carpeta base: `C:\Users\daniel.gonzalez\Downloads\estilovivo\`
| Ruta | Qué es |
|---|---|
| `android-app/` | **Proyecto nativo** (repo git, rama `master`). Aquí se integra todo. |
| `wt/<modulo>/` | Worktrees git de cada módulo (ramas `feat-<modulo>`). |
| `web/` | Clon de la web (https://github.com/mkandreum/estilovivo): referencia funcional (`pages/*.tsx`, `services/api.ts`, `types.ts`) y servidor (`server/src/index.ts`, ya auditado). Ver `web/AUDITORIA.md`. |
| `estilovivoapp/` | Contenedor Capacitor antiguo (WebView). **Obsoleto**; la nativa lo sustituye. |

Diseño de referencia: la imagen "Runova" que dio el cliente (3 móviles: bienvenida con letras gigantes + deslizador, inicio con titular serif cursiva + buscador + chips + rejilla de tarjetas con corazón, detalle de producto). Ya implementado en `core/design` y las pantallas `WelcomeScreen`, `feature/social/shop/*`. **Todo lo demás debe verse igual de editorial y con animaciones/micro-animaciones.**

Exigencia del cliente: *"hay funcionalidades en la web que no van bien; en la app nativa quiero que todo vaya perfecto"* → la web dice QUÉ hace cada función, no CÓMO; no copiar sus fallos, validar contra el servidor, cuidar casos límite/errores/offline.

## 2. Estado (actualiza esta tabla al avanzar)
| Módulo | Estado | Rama |
|---|---|---|
| core/design (tema, componentes `Ev*`, movimiento) | ✅ en master, verificado en emulador | master |
| core/data (red, sesión, repos, sockets, Coil) | ✅ en master (32 tests) | master |
| core/util `CycleStore` (ciclo, cifrado) | ✅ en master | master |
| feature/social/shop + chat (Tienda, Favoritos, Detalle, Chat tiempo real) | ✅ en master (90 tests) | master |
| feature/wardrobe (armario, looks, ventas, lavadora) | ✅ en master (46 tests) | master |
| feature/social/feed (feed, historias, retos, perfil ajeno, tendencias) | ✅ en master (74 tests) | master |
| feature/home | ⏳ agente en curso: **código sin commit** en `wt/home` (11 ficheros) | feat-home |
| feature/planner | ⏳ agente en curso: **código sin commit** en `wt/planner` (9 ficheros) | feat-planner |
| feature/suitcase (+wishlist) | ⏳ agente en curso: **código sin commit** en `wt/suitcase` (5 ficheros) | feat-suitcase |
| feature/profile (+ajustes, premium, privacidad) | ⏳ agente recién lanzado, aún sin ficheros en `wt/profile` | feat-profile |
| feature/auth (login/registro/onboarding/deep links) | ❌ pendiente (placeholder; `Welcome` ya hecha) | — |
| feature/tryon (probador virtual) | ❌ pendiente (placeholder) | — |
| feature/notifications (campana, pantalla) + pulido transversal | ❌ pendiente (placeholder) | — |
| Integración final + pruebas en emulador + APK release firmado | ❌ | — |

Comprueba el estado real con: `cd android-app; git branch -a; git log --oneline --all --graph | head -30; git worktree list`. Una rama con commit propio (`git log master..feat-X`) está terminada y se integra con `git merge --no-edit feat-X`. Si un worktree tiene cambios sin commitear, el agente murió a medias: revisa `git -C ../wt/X status`, compila y haz commit tú.

### Estado de las ramas a fecha de la última actualización (13/10/2026)
- `master` incluye: diseño, datos, `CycleStore`, tienda+chat, armario, social-feed (commit `60990b9` + docs).
- Ramas `feat-home`, `feat-planner`, `feat-suitcase`, `feat-profile`: **0 commits propios todavía**. Había agentes escribiendo en sus worktrees (`wt/home`, `wt/planner`, `wt/suitcase`, `wt/profile`). Si esos agentes ya no están activos al retomar:
  1. `git -C ../wt/<m> status --short` → si hay ficheros, es trabajo a medias: ábrelo, revisa que no falten ficheros, `:app:compileDebugKotlin` desde el worktree (copia `local.properties` si falta), corrige y `git add -A && git commit`.
  2. Después `git merge --no-edit feat-<m>` en `android-app` (conflicto típico: la lista de `AppModule.kt`; conserva todos los módulos).
  3. Si un módulo está casi vacío, vuelve a hacerlo con el prompt de la sección 3.2 (los de home, planner y suitcase siguen la misma plantilla).
- Aún no lanzados: **auth**, **tryon**, **notifications** (+ pulido transversal).
- Prompts originales de los agentes: sección 3.2 resume las funciones; las reglas comunes están en `AGENTS.md`.
- Los informes de los módulos ya terminados traen cosas por verificar en emulador: gestos del visor de historias (toque/mantener/arrastrar), elementos compartidos de avatar y rejilla→detalle, cámara/permisos, `ModalBottomSheet` con teclado, entrega real por socket (socket.io 2.1.2 con OkHttp 5: compila, **sin probar en runtime**).

## 3. Pendiente por hacer (en este orden)
### 3.1 Integrar ramas terminadas
`git merge --no-edit feat-social-feed feat-home feat-planner feat-suitcase` (de una en una). Conflictos esperables: `app/di/AppModule.kt` (cada módulo añade una línea; quedarse con todas), `core/data/model|net` (cambios aditivos), `SocialTab.kt`. Tras cada merge: `:app:compileDebugKotlin` y `:app:testDebugUnitTest`.

### 3.2 Módulos pendientes (un agente/sesión por módulo, en worktree propio)
Crear worktree: `git worktree add ../wt/<m> -b feat-<m>` + copiar `local.properties`. En el prompt: lecturas obligatorias (AGENTS.md, README de core/data, componentes `core/design`, un módulo ya hecho como ejemplo, p.ej. `feature/wardrobe`), regla de calidad del cliente, ficheros de la web a leer, paquete propio, rutas de `app/Routes.kt`, Koin propio + UNA línea en `AppModule.kt`, tests unitarios JVM con repos falsos, compilar y commit.

**profile** (`feature/profile/`; web: `pages/Profile.tsx` 2181 líneas, `Premium.tsx`, `Privacy.tsx`, `src/context/{DarkMode,Theme,Language}Context.tsx`, `src/utils/{theme,themePresets,darkMode}.ts`): `ProfileTab` con secciones Estadísticas (stats + gráfico de donut por categoría, `StatsRepository`), Progreso (nivel/XP, racha, logros, insignias, `GamificationRepository`), Favoritos (resumen → Wishlist), Ajustes. Ajustes: editar perfil (nombre, bio, avatar y foto de cuerpo entero con cámara/galería), género, perfil público/privado, solicitudes de seguimiento recibidas (aprobar/rechazar), notificaciones por email (maestro + 4 sub-toggles), tema (claro/oscuro/sistema → `AppPrefs.setThemeMode`; `MainActivity` ya lo aplica), tamaño de fuente (`fontScale`), alto contraste y háptica (`AppPrefs.highContrast/haptics`: **nadie los aplica aún**; hacer que `EvHaptics`/`evClickable` respeten `haptics` y `EvTheme` `highContrast`), seguimiento del ciclo (solo perfil femenino: activar + periodo con `CycleStore`), ubicación para el tiempo (`locationName`), cambiar contraseña (mensajes `samePassword`/`invalidCurrentPassword`), cerrar sesión (`AuthRepository.logout()` + `LocalNavigator.resetToWelcome()`), eliminar cuenta (doble confirmación, escribir "ELIMINAR"). Rutas `Premium` y `Privacy` (en `profileRoutes()`): Premium informativo (**Google Play prohíbe cobrar funciones digitales fuera de Play Billing**: no pedir pago por email dentro de la app; dejar CTA neutro/"próximamente" o integrar Play Billing con verificación en servidor), Privacidad con el texto de `Privacy.tsx`.

**auth** (`feature/auth/`; web: `pages/AuthPage.tsx`, `Onboarding.tsx`, `components/StyleQuiz.tsx`, `App.tsx` para deep links): rutas `Auth` y `Onboarding` (hay placeholders). Pestañas Entrar/Registro (animadas), campos `EvTextField`, validación (email, contraseña ≥8, nombre ≥2, género, fecha nacimiento), "Recordarme", mostrar/ocultar contraseña, olvidé mi contraseña, reenviar verificación, estado "revisa tu correo", errores del servidor legibles. Enlaces de email/deep links en `MainActivity` (intent-filters ya en el manifest): `https://estilovivo.xyoncloud.win/auth?verifyToken=…`, `…/auth?token=…&type=reset`, `/reset-password/:token`, esquema `estilovivo://`; funcionar aunque haya sesión abierta. Tras login: `GamificationRepository.dailyLogin()`, onboarding si `!onboardingDone` y sin preferencias (quiz de estilo: colores, estilos, ocasiones, tejidos → `AuthRepository.updatePreferences`), luego `LocalNavigator.enterMain()`. Pedir permiso POST_NOTIFICATIONS en contexto.

**tryon** (`feature/tryon/`; web: `pages/VirtualTryOn.tsx` 1636 líneas, `components/FittingRoomModal.tsx`, `PoseGuide.tsx`, `BackgroundRemover.tsx`, `src/utils/{tryOnEngine,poseDetection,garmentProcessor,imageUtils}.ts`): ruta `TryOn(garmentId?)`. Editor de lienzo nativo: foto de cuerpo entero (cámara/galería o `fullBodyAvatar` del perfil), capas de prendas del armario (arrastrar, pellizcar para escalar, rotar, opacidad, voltear, orden Z, bloquear, eliminar), selector de prendas por categoría, deshacer/rehacer, comparar antes/después, fondo claro/oscuro, espejo, exportar PNG (HD), compartir (`ACTION_SEND` + FileProvider), guardar como look (`LookRepository.createWithImage`, público opcional), presets (`TryonPresetRepository`: guardar/cargar/renombrar/borrar, valoración y ocasión), guía de pose. La detección de pose/recorte con TF.js de la web se sustituye por ML Kit (opcional, dependencia nueva) o se omite documentándolo.

**notifications** (`feature/notifications/`; web: `components/NotificationBell.tsx`, `src/context/NotificationContext.tsx`, `src/utils/notifications.ts`): campana real (`NotificationRepository.unreadCount` + badge animado, vibración sutil al llegar por socket), pantalla `Notifications` (lista agrupada por día, marcar leída/todas, tocar → navegar: CHAT→`Chat(relatedId)`, LIKE/COMMENT→Social feed, FOLLOW→`UserProfile(relatedId)` o Perfil si "quiere seguirte", WASHING→Armario, STORY_REACTION→`Chat`), vacíos y errores. Push (FCM): hace falta `google-services.json` y envío desde servidor (no existe aún) → dejarlo preparado/documentado. **Pulido transversal** en este módulo: aplicar `AppPrefs.haptics`/`highContrast` en `core/design`, banner de sin conexión, botón atrás con modales, estados de carga consistentes.

### 3.2b Temas de color por perfil (pedido del cliente, pendiente)
"Que cada perfil pueda cambiar el tema/color manteniendo la estética de la app". Servidor y modelo ya soportan `themePreset` y `customColor` (`User`, `PreferencesUpdate`, `AuthRepository.updatePreferences`). Presets de la web (`web/src/utils/themePresets.ts`): pink, blue, green, lavender, amber, rose, indigo, teal, forest, midnight, petrol… + "clásico" (negro editorial actual, **por defecto en nativa**) + color personalizado (hex).
Diseño: SOLO cambia el color de marca; el resto (lienzo gris, tarjetas blancas, tipografía, formas, movimiento) no cambia, para conservar la estética editorial.
- `EvColors` gana `brand`, `onBrand` (negro/blanco automático por contraste ≥ 4.5:1), `brandSoft` (brand al ~12 % sobre la superficie). Derivar variantes distintas para claro y oscuro (aclarar en oscuro).
- Usan `brand`: `EvPillButton` Primary, `EvChip`/`EvSegmented` seleccionados, indicador activo de `EvBottomBar`, anillo/barras de XP y progreso, enlaces, `EvLoadingDots`, switches. NO cambian: fondos, tarjetas, texto, la pista oscura del deslizador de bienvenida.
- Aplicación: `AppPrefs` guarda `themePreset/customColor` para pintar el tema al arrancar sin red; al iniciar sesión se sincroniza desde el perfil del servidor; cambio animado (fundido de color ~320 ms, ya existe para claro/oscuro en `EvTheme`).
- UI: Perfil → Ajustes → "Tema": carrusel de muestras con vista previa en vivo (mini tarjeta con botón/chip/barra), selector de color propio (tono + saturación + brillo, con aviso/corrección si el contraste es insuficiente), guardado optimista con rollback (`updatePreferences(themePreset=..., customColor=...)`).
- Tests: contraste `onBrand`, derivación claro/oscuro, parseo/validación de hex, preset desconocido → clásico.
Hacerlo DESPUÉS de integrar `feat-notifications` (ambos tocan `core/design/Theme.kt`).

### 3.2c Recorte de prendas y probador (prioridad del cliente: "que se haga bien y estable")
La web falla en: ponerse ropa sobre la foto con los dedos, y el recorte de la ropa al subirla. En nativa:
- `core/image/GarmentCutout` (lo crea el agente `tryon`): ML Kit Subject Segmentation + fallback flood-fill, previsualización antes/después, caché por prenda.
- **Pendiente tras integrar `feat-tryon`**: conectar `GarmentCutout` al flujo de AÑADIR/EDITAR prenda del módulo `feature/wardrobe` (el agente de Armario NO lo hizo): tras elegir la foto, ofrecer "Quitar fondo" (activado por defecto) con vista previa y opción de usar la original; subir PNG con alfa. Ojo: el servidor (`imageProcessor.ts`) convierte a webp y hace un trim de blancos/grises; comprobar que el alfa se conserva (webp con alfa) y que `removeBg=true` no destroza el recorte ya hecho.
- Auto-ajuste con ML Kit Pose Detection en el Probador y gestos multitáctiles propios (ver prompt de ampliación en el historial del agente `tryon`).
- Probar SIEMPRE en un móvil real con dos dedos (el emulador no simula bien el pellizco).

### 3.2d Funciones de la web NO cubiertas aún (decidir)
Idioma/dialecto (`LanguageContext`/`translations.ts`, la app está sólo en español), presets de tema de la web (ver 3.2b), `musicSync`, ubicación en el quiz del onboarding, push FCM, Play Billing, ruta `/look/{id}` (deep link sin destino), exportar datos (la web lo promete en Privacy y no existe), alertas de precio (inexistentes en la web).

### 3.2e Requisitos de Google Play pendientes (ver PLAY_STORE.md)
- **UGC: denunciar y bloquear** (feed, comentarios, historias, chat, perfiles): Play lo exige y la app NO lo tiene; tampoco el servidor (`web/server`: faltan modelos `Report`/`Block`, endpoints `POST /reports`, `POST/DELETE /blocks`, y filtrar contenido/mensajes/seguimiento de usuarios bloqueados). Implementar servidor + app (menú "⋯" → Denunciar / Bloquear, pantalla "Usuarios bloqueados" en Ajustes).
- **Página web de eliminación de cuenta** (URL pública) en `web/pages/Privacy.tsx` o nueva ruta.
- **Cuenta de prueba** para los revisores de Play.
- Docs para entregar: `PLAY_STORE.md` (ficha completa) y `COMPILAR_FIRMADO.md` (guía de Android Studio para compilar firmado). Zip de fuentes sin `build/`, `.gradle/`, `local.properties`, `.git`, claves.

### 3.2f Estado al subir a GitHub (13/10/2026) — LEER
- **Subido a `main`**: app nativa (`mkandreum/estilovivoapp`, el contenedor Capacitor antiguo quedó en la rama `legacy-capacitor`) y web (`mkandreum/estilovivo`, incluye el arreglo del servidor para prendas ya recortadas con transparencia).
- **Parado por el cliente, SIN HACER**: (a) auditoría de seguridad de la app nativa (checklist MASVS: almacenamiento, FileProvider `files-path path="."` demasiado amplio, logout que borre cachés/borradores/recortes, deep links, R8, dependencias, `CycleStore` con fallback en claro); (b) auditoría de seguridad del backend+web, segunda pasada (refresh tokens en claro en BD, JWT de 7 días → 15–30 min, `authLimiter` compartido de 5/15 min, CSP/cabeceras en la web, importmap esm.sh en `index.html`, validación zod que falta en tryon-presets/stories/challenges, `npm audit`, Dockerfile/`prisma db push` en producción); (c) reparación de funciones de ropa de la WEB (rama `fix-garments`, WIP SIN TERMINAR: `components/TryOnStage.tsx`, `src/utils/alphaMap.ts`, `src/utils/tryOnLayers.ts`, cambios en `pages/VirtualTryOn.tsx`; objetivo: gestos táctiles con Pointer Events, hit-test sobre alfa, capas en fracciones del lienzo, recorte de prendas en cliente estable, contadores/venta/lavadora, servidor de prendas/looks). **La rama WIP no está fusionada en main: puede no compilar.**
- La app nativa NO ha pasado ninguna auditoría de seguridad propia ni se ha probado en un móvil real.

### 3.3 Cierre
1. Compilar todo, `:app:testDebugUnitTest` verde, `:app:lintDebug` sin errores graves.
2. Probar en emulador cada pantalla y flujo (ver §5), en claro y oscuro, con fuente grande y sin red. Arreglar visual/funcional.
3. Pantalla de bienvenida: ya hecha; añadir el icono/adaptive icon definitivo y splash.
4. APK debug a `C:\Users\daniel.gonzalez\OneDrive - Velilla Group Europe S.L\Escritorio\EstiloVivo-nativa-debug.apk` (el que hay ahora es **solo el diseño base antiguo**; reemplazarlo).
5. Release firmado: `$env:KEYSTORE_PATH, KEYSTORE_PASSWORD, KEY_ALIAS, KEY_PASSWORD, VERSION_CODE, VERSION_NAME` y `.\gradlew.bat :app:bundleRelease :app:assembleRelease`. La clave de firma NO existe aún: la crea el cliente (no inventarla ni guardarla en el repo). R8 ya pasa (`minifyReleaseWithR8`).
6. Pendiente de servidor/cliente: `ANDROID_SHA256_FINGERPRINTS` y `ADMIN_USER_IDS` en producción (ver `web/AUDITORIA.md`), Firebase `google-services.json`, política Play Billing.

## 4. Compilar / probar (Windows, esta máquina: 16 GB RAM)
```powershell
$env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"; $env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=C:\gtmp"
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
cd C:\Users\daniel.gonzalez\Downloads\estilovivo\android-app
.\gradlew.bat :app:compileDebugKotlin -q     # rápido
.\gradlew.bat :app:testDebugUnitTest -q
.\gradlew.bat :app:assembleDebug -q          # app\build\outputs\apk\debug\app-debug.apk
```
- **Sin** `TMP=C:\gtmp` Gradle falla con "Unable to establish loopback connection" (ruta corta `DANIEL~1.GON`). Crear `C:\gtmp` si no existe.
- Stack: AGP 9.4.1 (necesita Gradle 9.6.0, ya en el wrapper), Kotlin 2.4.21 (Kotlin integrado en AGP 9: no usar el plugin `kotlin-android`), compileSdk 37 (las libs lo exigen), Compose BOM 2026.09.00, Material3 estable (las API *Expressive* son `internal`: NO usar `MaterialExpressiveTheme`), Koin 4.2, Retrofit 3, OkHttp 5.5, Coil 3.
- **Memoria**: solo 1–2 Gradle a la vez; no ejecutar `gradlew --stop` si hay otros agentes. `gradle.properties` ya limita heap y usa Kotlin in-process.
- Los heredocs largos en Bash fallan en esta herramienta: crear ficheros con la herramienta de escritura de ficheros o scripts Python.

## 5. Emulador
AVD ya creado: `ev_pixel` (Pixel 7, Android 36 x86_64, render por software → LENTO; puede salir "System UI isn't responding": pulsar *Wait* y esperar ~1 min).
```bash
SDK="$LOCALAPPDATA/Android/Sdk"
"$SDK/emulator/emulator.exe" -avd ev_pixel -no-snapshot -no-audio -gpu swiftshader_indirect &   # ejecutar fuera del sandbox
"$SDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
"$SDK/platform-tools/adb.exe" shell am start -n com.estilovivo.app/.MainActivity
"$SDK/platform-tools/adb.exe" exec-out screencap -p > shot.png    # y leer la imagen para revisar el diseño
```
No hay `avdmanager`/cmdline-tools. Si `adb` dice "version mismatch" se reinicia solo: esperar y reintentar. **No crear cuentas en producción** para probar: levantar el servidor local (`web/server`: `npm install`, `npx prisma db push`, variables `DATABASE_URL=file:...`, `JWT_SECRET`, `UPLOADS_DIR`; `npx tsx src/index.ts` en :3000) y compilar con `-PapiBase=http://10.0.2.2:3000` (en *debug* ya se permite HTTP a 10.0.2.2/localhost/127.0.0.1).

## 6. Convenciones clave (resumen; detalle en AGENTS.md)
- MVVM + `StateFlow`, `collectAsStateWithLifecycle`, Koin (`viewModel { }` en el módulo del feature, una línea en `app/di/AppModule.kt`).
- Navegación tipada en `app/Routes.kt` con `LocalNavigator.current.navigate(...)`; foto→detalle con `sharedElementIfAvailable(SharedKey(id))` + `SharedScope { }`.
- UI SOLO con `core/design` (`EvTheme.colors`, `EvType`, `Ev*`, `Modifier.evClickable/staggeredEntrance/shimmer`, `EvMotion`, `LocalEvToast`, `EvConfetti`). Titulares: `EvHeadline("Texto *destacado*")` (Poppins + Instrument Serif cursiva).
- Fechas locales (`java.time`), nunca UTC. Imágenes ya llegan como URL absoluta. Errores: `ApiResult.Failure.error.message` ya está en español.
- Seguridad: tokens solo en EncryptedSharedPreferences, HTTPS en release, no registrar datos sensibles.

## 7. Fallos de la web/servidor ya detectados (NO reproducir; algunos requieren arreglar el servidor)
- Servidor emite por socket un mensaje inicial con id sintético `init_…` que no existe en BD.
- `/products/shop` limitado a 200 sin paginar; no hay endpoint de detalle de artículo ajeno; `/chat/.../messages` limitado a 500.
- El servidor no devuelve al emisor sus propios mensajes por socket (otros dispositivos suyos no se actualizan).
- Acepta precio 0 con `forSale=true`; envía notificación/email de lavadora cada vez que `isWashing` pasa a true; el PUT de producto exige nombre y categoría; ignora nulos (para borrar campos hay que enviar cadena vacía).
- La web: contador de categorías ≠ filtro; looks con etiqueta `custom` por defecto; permite lavar prendas en venta; tabs de Social sin "set".
- Premium sin campo `isPremium` en BD (límite de 5 ventas para todas).
- Push: la app obtiene el token FCM pero el servidor no lo recibe ni envía push.

## 8. Entregables ya hechos fuera del repo
- Web auditada y subida a https://github.com/mkandreum/estilovivo (main). Contenedor antiguo en https://github.com/mkandreum/estilovivoapp (README apunta a la web).
- `EstiloVivo-nativa-debug.apk` en el Escritorio = **solo diseño base** (bienvenida, tienda y detalle de muestra).
- El repo nativo (`android-app/`) **aún no está subido a GitHub**: crear/usar `https://github.com/mkandreum/estilovivoapp` (reemplazar el contenedor Capacitor o usar rama/repo nuevo; pedir confirmación al cliente) y hacer push cuando la integración compile.
