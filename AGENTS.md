# EstiloVivo Android nativa — guía para agentes

App nativa Kotlin + Jetpack Compose que replica **toda** la web (`../web`). Backend: `https://estilovivo.xyoncloud.win/api`
(contrato real: `../web/services/api.ts` y `../web/server/src/index.ts`; tipos: `../web/types.ts`).
Diseño de referencia: editorial, blanco/negro, tarjetas muy redondeadas, titulares con serif cursiva, mucho movimiento.

## Reglas de Compose modernas (obligatorias)
- `collectAsStateWithLifecycle()`, nunca `collectAsState()`.
- MVVM + flujo unidireccional: `ViewModel` expone `StateFlow<UiState>` (data class/sealed `@Immutable`), la UI envía eventos.
- Composables sin estado (state hoisting), `modifier: Modifier = Modifier` como primer parámetro opcional.
- Listas: `key = { it.id }` siempre. Lambdas estables; lecturas de estado diferidas (`graphicsLayer { }`, `drawBehind`).
- `Scaffold`/insets: respetar `statusBarsPadding()` / `navigationBarsPadding()`; las pestañas dejan ~132dp abajo por la barra flotante.
- Navegación: rutas tipadas de `app/Routes.kt` con `LocalNavigator.current.navigate(...)`. No añadir dependencias entre features: sólo `core` ↔ `feature`.
- Material 3 estable (NO `MaterialExpressiveTheme`, es internal). Iconos: `Icons.Rounded.*` (extended).
- Strings de UI en español, tuteo, tono cercano. Sin `println`; sin secretos en el código.
- Koin: ViewModels con `viewModel { }` en el módulo del feature, `koinViewModel()` en la UI.

## Diseño y movimiento (no inventar estilos)
Usar SIEMPRE `core/design`: `EvTheme.colors`, `EvType.*`, `EvShapes`, `EvSpace`, y los componentes `Ev*`
(`EvPillButton`, `EvCircleButton`, `EvChip(Row)`, `EvSearchBar`, `EvTextField`, `EvSegmented`, `EvProductCard`,
`EvCard`, `EvStatTile`, `EvImage`, `EvEmptyState`, `EvConfetti`, `LocalEvToast`…).
- Todo lo pulsable: `Modifier.evClickable` (escala elástica + háptica). Nada de `clickable` pelado ni `Button` de Material.
- Entradas de pantallas/listas: `Modifier.staggeredEntrance(index)`. Cargas: `shimmer`/`EvImage`. Muelles: `EvMotion.bouncy/smooth/snappy`.
- Cambios de estado visibles se animan (`animate*AsState`, `AnimatedContent`, `AnimatedVisibility`, `animateContentSize`).
- Éxitos: `toast.success`, confeti en logros/retos/subida de nivel. Errores: `toast.error` con mensaje útil del servidor.
- Foto→detalle: `sharedElementIfAvailable(SharedKey(id))` y destino envuelto en `SharedScope { }`.
- Titulares: `EvHeadline("Texto con *palabra* destacada")`. Fuentes: Poppins + Instrument Serif Italic.
- Accesibilidad: `contentDescription`, objetivos ≥ 44dp, contraste correcto en claro y oscuro (probar ambos).

## Reparto por módulos (cada agente sólo toca su paquete)
`core/data` (API, modelos, repos, sesión, sockets) · `feature/auth` · `home` · `wardrobe` · `planner` · `suitcase`(+wishlist) ·
`tryon` · `social`(+ usuario ajeno, chat, historias, retos) · `profile`(+ajustes, gamificación, premium, privacidad) · `notifications`.
Cada feature ya tiene huecos (`*Routes.kt`, `*Tab.kt`, `*Module.kt`, `*Placeholder.kt`): sustitúyelos. No edites `app/` salvo
que sea imprescindible (si lo es, cambio mínimo y avísalo en tu informe).

## Compilar (Windows)
```
$env:TMP="C:\gtmp"; $env:TEMP="C:\gtmp"; $env:JAVA_TOOL_OPTIONS="-Djava.io.tmpdir=C:\gtmp"
$env:JAVA_HOME="C:\Program Files\Android\Android Studio\jbr"
.\gradlew.bat :app:compileDebugKotlin -q      # comprobación rápida
.\gradlew.bat :app:assembleDebug -q           # APK en app/build/outputs/apk/debug
```
Debe compilar sin errores antes de dar tu trabajo por terminado. Ejecuta `git commit` en tu rama al acabar.

## Seguridad
Tokens sólo en `EncryptedSharedPreferences`; HTTPS; nunca registrar tokens/contraseñas; validar entradas; la API ya
aplica autorización (no confiar en IDs del cliente).
