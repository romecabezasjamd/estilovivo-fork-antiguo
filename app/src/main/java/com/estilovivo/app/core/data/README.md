# core/data — guía rápida para features

Todo está en Koin (`dataModule`) como singleton. Inyecta **interfaces** en tus ViewModels:
`class X(private val garments: GarmentRepository) : ViewModel()` + `viewModel { X(get()) }`.

- Todos los métodos `suspend` devuelven `ApiResult<T>` (`Success(value)` / `Failure(error)`); nunca lanzan.
  `error.message` ya está en español (listo para `toast.error`), `error.kind` distingue red/sesión/validación...
  Helpers: `map`, `fold`, `onSuccess { }`, `onFailure { }`, `getOrNull()`, `getOrDefault()`.
- Las imágenes ya son URLs absolutas. Modelos en `core.data.model` (todos `@Immutable`).
- Listados que la web pagina (`products`, `looks`) se devuelven **completos** (cursor hasta agotar, `limit=100`).
- Subidas: `ImageUpload.fromUri(context, uri)` (reduce a 2048 px y corrige EXIF) o `ImageUpload.fromFile(file)`.
- Sesión caducada: la capa de red renueva el token sola (403 "Invalid or expired token" / 401 → `POST /auth/refresh`,
  reintento único). Si no se puede, limpia la sesión y `MainActivity` lleva a Welcome con un toast. No lo gestiones tú.

## Sesión y preferencias
- `SessionManager`: `state: StateFlow<SessionState>` (LoggedOut/LoggedIn/Expired), `user: StateFlow<User?>`, `isLoggedIn`,
  `userId`, `hasSession`. Tokens en EncryptedSharedPreferences. Para salir usa `AuthRepository.logout()` y luego
  `LocalNavigator.current.resetToWelcome()`.
- `AppPrefs` (DataStore): Flows `themeMode` (`ThemeMode.System/Light/Dark`), `rememberMe`, `onboardingDone`, `fontScale`
  (0.85–1.5), `highContrast`, `haptics`, o `settings: Flow<AppSettings>`; setters `setThemeMode(...)`, etc. `current()` lectura puntual.
  `MainActivity` ya aplica `themeMode` y `fontScale`; `highContrast` y `haptics` los debe leer quien los use.
- `AppScope` (`.scope`): corrutinas con vida de app, para trabajo que debe sobrevivir a una pantalla.

## Repositorios (`core.data.repo`)
- **AuthRepository**: `currentUser`, `login(email, password, remember)`, `register(email, password, name, gender?, birthDate?, remember)`
  (`loggedIn=false` ⇒ falta verificar email), `logout()`, `refreshMe()`, `updateProfile(ProfileUpdate)` (multipart si hay
  `avatar`/`fullBodyAvatar`), `updatePreferences(PreferencesUpdate)`, `changePassword`, `forgotPassword`, `resetPassword(token, new)`,
  `verifyEmail(token)`, `resendVerification(email)`, `deleteAccount()`.
- **GarmentRepository**: `garments` (caché compartida), `getAll()`, `add(GarmentDraft, ImageUpload?)`, `update(id, GarmentDraft, ImageUpload?)`
  (edición completa: parte de `garment.toDraft().copy(...)`), `delete(id)`, `markWorn(id)`, `shop(search?, category?)`, `toggleFavorite(id)`.
- **LookRepository**: `myLooks` (caché), `getMine()`, `feed(cursor?, limit=20)` → `Page<Look>`, `create(...)`, `createWithImage(..., ImageUpload)`,
  `update(id, LookChanges)`, `delete(id)`.
- **PlannerRepository**: `getAll()`, `save(date "YYYY-MM-DD", lookId?, eventNote)`, `delete(date)`.
- **TripRepository**: `getAll()`, `create(destination, dateStart, dateEnd, items, garmentIds)`, `updateMeta(tripId, ...)`, `delete(tripId)`,
  `addItem(tripId, label, isEssential)`, `updateItem(tripId, itemId, checked?, label?, isEssential?)`, `deleteItem(tripId, itemId)`.
- **SocialRepository**: `toggleLike(lookId)`, `getComments(lookId)`, `addComment(lookId, content, parentId?)`, `deleteComment(id)`,
  `toggleLookFavorite(id)`, `toggleGarmentFavorite(id)`, `getFavorites()`, `toggleFollow(userId)` (`requesting` en perfiles privados),
  `getFollowRequests()`, `approveFollowRequest(id)`, `rejectFollowRequest(id)`, `getUserProfile(userId)` → `PublicProfile`, `getTopUsers(forceRefresh)`.
- **ChatRepository**: `getConversations()`, `createConversation(targetUserId, itemId?, itemTitle?, itemImage?, initialMessage?)`,
  `getMessages(conversationId)`, `sendMessage(conversationId, content, imagePath?, productId?)`, `uploadImage(ImageUpload)` → `UploadedImage(path,url)`,
  `sendImage(conversationId, image, caption)`.
- **StoryRepository**: `getStories()`, `create(type "text"|"image", text?, image?, existingImageUrl?)`, `markViewed(id)`, `delete(id)`, `react(id, emoji)`.
- **ChallengeRepository**: `getCurrent()`, `getMySubmissions()`, `getHistory()`, `submit(challengeId, description, ImageUpload)` → `SubmitResult`
  (`leveledUp` ⇒ confeti), `deleteSubmission(id)`.
- **GamificationRepository**: `getProgress()`, `getAchievements()`, `getBadges()`, `checkAchievements()`, `dailyLogin()` (llámalo al abrir con sesión).
- **NotificationRepository**: `notifications`, `unreadCount` (se actualizan solos con el socket), `refresh()`, `markRead(id)`, `markAllRead()`.
- **TrendsRepository**: `getTrends(forceRefresh)` (caché 5 min). **TryonPresetRepository**: `getAll()`, `save(name, thumbnail, layers, rating?, occasion?)`,
  `update(id, ...)`, `delete(id)`. **StatsRepository**: `getStats()`.

## Tiempo real (`SocketManager`)
Se conecta solo al iniciar sesión y se cierra al salir; reconecta y renueva el token si hace falta.
`events: SharedFlow<SocketEvent>` → `NewMessage(message)`, `Notification(notification)`, `LevelUp(level, experiencePoints)`,
`AchievementUnlocked(...)`, `Typing(userId, roomId, isTyping)`. `joinRoom(id)` / `leaveRoom(id)` / `typing(id, isTyping)`; `connected: StateFlow<Boolean>`.
(Recuerda `joinRoom` al abrir un chat para recibir `Typing`.) Hoy el servidor sólo emite `new_message`, `notification` y `typing`;
`level_up` / `achievement_unlocked` están escuchados por si se añaden.

## Imágenes
`ImageLoader` de Coil 3 con OkHttp y caché en disco de 100 MB, registrado como cargador global (`AsyncImage`/`EvImage` lo usan sin más).

## Notas
- `PublicProfile` (perfil de otra persona) se llama así para no chocar con la ruta `UserProfile` de `Routes.kt`.
- `rememberMe` se guarda como preferencia (rellenar email); los tokens siempre se conservan (norma en móvil).
