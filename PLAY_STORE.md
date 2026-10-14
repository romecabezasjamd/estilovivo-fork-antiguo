# EstiloVivo — Ficha de Google Play (todo listo para copiar y pegar)

Paquete: `com.estilovivo.app` · Versión inicial: `1.0.0` (versionCode 1) · Idioma por defecto: **Español (España)**.
Los textos están dentro de los límites de Play (título ≤ 30, descripción corta ≤ 80, completa ≤ 4000).

---

## 1. Datos básicos de la app (Play Console → Crear app)
| Campo | Valor |
|---|---|
| Nombre de la app | **EstiloVivo: Armario y Looks** (27 car.) |
| Idioma predeterminado | Español (España) – es-ES |
| Tipo | Aplicación |
| Gratis o de pago | **Gratis** (no hay compras dentro de la app; Premium es sólo informativa) |
| Categoría | **Estilo de vida** (alternativa: Compras) |
| Etiquetas sugeridas | Moda, Estilo de vida, Redes sociales, Compras |
| Correo de contacto | appestilovivo@gmail.com |
| Sitio web | https://estilovivo.xyoncloud.win |
| Teléfono | (opcional) |

## 2. Textos de la ficha (es-ES)
**Descripción breve (≤80):**
```
Tu armario digital: crea looks, pruébate la ropa y comparte tu estilo.
```

**Descripción completa (≤4000):**
```
EstiloVivo es tu armario inteligente y tu comunidad de moda en el móvil. Sube tus prendas, crea looks, pruébatelos sobre tu propia foto, planifica tu semana y comparte tu estilo con otras personas.

👗 TU ARMARIO, ORGANIZADO
• Sube fotos de tus prendas con la cámara o la galería; el fondo se recorta automáticamente.
• Filtra por categoría, temporada, color o marca y encuentra lo que buscas al instante.
• Mira qué prendas usas más y cuáles casi no tocas.
• Lavadora virtual: marca lo que está en la colada.

✨ PROBADOR VIRTUAL
• Colócate la ropa sobre tu foto de cuerpo entero: arrastra, escala y gira con los dedos.
• Auto-ajuste inteligente al cuerpo: la camiseta se coloca sola en los hombros y el pantalón en las caderas.
• Compara antes y después, cambia el fondo, usa el espejo y deja que "Sorpréndeme" arme un look por ti.
• Guarda tus montajes, compártelos o publícalos como look.

🗓️ PLANIFICA Y VIAJA
• Planificador semanal, mensual y diario: decide qué te pones cada día.
• Recomendación diaria según tu estado de ánimo, el tiempo y tu armario.
• Maletas de viaje con checklist y las prendas que te llevas.

💬 COMUNIDAD Y SEGUNDA MANO
• Feed de looks, historias de 24 h, retos semanales y tendencias de moda.
• Compra y vende ropa de segunda mano y chatea con otras personas en tiempo real.
• Wishlist de tus favoritos y perfil con tu nivel, logros e insignias.

🎨 TUYA, A TU MANERA
• Modo claro y oscuro, tamaño de letra ajustable y colores de marca para cada perfil.
• Privacidad bajo tu control: perfil público o privado y borrado de cuenta desde la app.

Descarga EstiloVivo y dale vida a tu armario.
```

**Novedades de la versión 1.0.0 (≤500):**
```
¡Primera versión nativa de EstiloVivo! Armario, probador virtual con auto-ajuste, planificador, viajes, comunidad, chat y temas de color por perfil.
```

### English (en-US) — opcional
- **Title:** EstiloVivo: Wardrobe & Looks
- **Short:** Your digital wardrobe: build outfits, try them on and share your style.
- **Full:** EstiloVivo is your smart wardrobe and fashion community. Upload your clothes with automatic background removal, build outfits, try them on your own photo with smart body fitting, plan your week and trips, discover trends, chat, buy and sell second-hand fashion, and share looks and stories with the community. Light/dark mode and colour themes per profile.

## 3. Recursos gráficos
| Recurso | Requisito | Cómo obtenerlo |
|---|---|---|
| Icono de la app | PNG 512×512, ≤1 MB, 32 bits | Android Studio → clic derecho en `res` → New → Image Asset → *Launcher Icons (Adaptive and Legacy)* → pestaña **Play Store icon** (export). El icono vectorial ya está en `drawable/ic_launcher_*`. |
| Gráfico de funciones | PNG/JPG 1024×500 | Fondo oscuro `#0B0B0F` con el logo (degradado arcoíris) y el lema "Tu armario. Tu comunidad. Tu estilo." |
| Capturas de teléfono | 2–8, 9:16, lado corto ≥ 1080 px | Recomendadas (en este orden): 1) Bienvenida, 2) Tienda, 3) Probador virtual, 4) Armario, 5) Looks/Planificador, 6) Chat, 7) Perfil con colores de marca |
| Capturas de tablet (opcional) | 7" y 10" | Sólo si quieres aparecer en tablets |

Consejo: hacer las capturas con el emulador o un móvil con cuenta de prueba cargada con prendas y looks.

## 4. Contenido de la app (Play Console → Política → Contenido de la app)

### 4.1 Política de privacidad
URL pública: **https://estilovivo.xyoncloud.win/privacy** (la página existe y se ve sin iniciar sesión; revisar que el texto coincida con el de la app).

### 4.2 Acceso a la app
La app **requiere cuenta**. Debes dar a Google credenciales de prueba:
- Instrucciones: "Registrarse con un email (se envía un correo de verificación pero se puede entrar igualmente) o usar el usuario de prueba."
- Usuario de prueba: `__CREAR_CUENTA_DE_PRUEBA__@…` / contraseña `__________` ← **crear una cuenta de prueba con prendas y looks y pegarla aquí**.

### 4.3 Anuncios
**No contiene anuncios.**

### 4.4 Público objetivo y contenido
- Grupo de edad: **13 años o más** (la app exige ≥13 en el registro). NO marcar menores de 13: no sería "Diseñada para familias".
- ¿Atrae a menores? No.

### 4.5 Clasificación de contenido (cuestionario IARC)
Categoría: **Redes sociales / Comunicación o Referencia, noticias…** → elige *"Red social, mensajería o comunidad"*.
- Violencia, sexo, lenguaje soez, drogas, juego: **No**.
- Contenido generado por usuarios / interacción entre usuarios: **Sí** (feed, comentarios, historias, chat).
- Comparte ubicación con otros usuarios: **No** (la ubicación aproximada sólo se usa para el tiempo).
- Compras digitales: **No**.
Resultado esperado: **PEGI 3 / Everyone con aviso de "interacción entre usuarios"** (puede salir 12+/Teen por el contenido de usuarios).

### 4.4b Contenido generado por usuarios (UGC) — **REQUISITO DE POLÍTICA, PENDIENTE**
Google exige para apps con contenido de usuarios: (1) términos de uso, (2) **herramienta para denunciar contenido y usuarios dentro de la app**, (3) **bloquear usuarios**, (4) moderación. **La app aún NO tiene "Denunciar" ni "Bloquear"** (ni el servidor tiene endpoints para ello). Esto puede provocar el rechazo de la revisión. Hay que implementarlo antes de enviar a producción (ver HANDOFF.md).

### 4.6 Eliminación de cuenta (obligatorio si hay registro)
- En la app: **Perfil → Ajustes → Eliminar cuenta** (doble confirmación). Borra cuenta, prendas, looks y fotos.
- Play pide además una **URL web** de eliminación: usar `https://estilovivo.xyoncloud.win/privacy` y añadir allí una sección "Cómo eliminar tu cuenta" con los pasos anteriores y el email appestilovivo@gmail.com para solicitarlo si no tienes acceso. **Pendiente: añadir esa sección a la web.**
- Datos que se eliminan: todo (cuenta, perfil, prendas, looks, comentarios, mensajes propios, fotos). Sin retención (salvo copias de seguridad del servidor hasta 30 días — ajustar si no aplica).

### 4.7 Permisos sensibles (declaración)
| Permiso | Uso | Obligatorio |
|---|---|---|
| `CAMERA` | Foto de prendas, foto de cuerpo para el probador, fotos de historias | No (función opcional; hay galería) |
| `ACCESS_COARSE_LOCATION` | Ubicación **aproximada** sólo para mostrar el tiempo y sugerir ropa | No (se puede escribir la ciudad en el perfil) |
| `POST_NOTIFICATIONS` | Avisos de mensajes, me gusta, comentarios | No |
| `INTERNET`, `ACCESS_NETWORK_STATE` | Conexión con el servidor | Sí |
| `VIBRATE` | Háptica de la interfaz | Sí (ajustable en Ajustes) |
No se usan permisos de contactos, SMS, micrófono, almacenamiento amplio ni ubicación precisa/en segundo plano.

### 4.8 Seguridad de los datos (formulario "Data safety")
**¿Recoge o comparte datos?** Sí, recoge datos. **¿Cifrado en tránsito?** Sí (HTTPS). **¿Pueden los usuarios solicitar su eliminación?** Sí.

| Tipo de dato | ¿Recopilado? | ¿Compartido con terceros? | Finalidad | ¿Opcional? |
|---|---|---|---|---|
| Nombre | Sí | No | Funciones de la app, cuenta | No |
| Correo electrónico | Sí | No | Cuenta, comunicaciones, seguridad | No |
| Fecha de nacimiento / género | Sí | No | Cuenta y personalización | Sí |
| ID de usuario | Sí | No | Cuenta | No |
| Fotos | Sí (prendas, perfil, cuerpo, looks, historias) | No | Funciones de la app | Sí |
| Mensajes / publicaciones / comentarios | Sí | No (visibles a otros usuarios según tu privacidad) | Funciones de la app | No |
| Ubicación aproximada | **Se envía a Open-Meteo (tercero) para obtener el tiempo**; no se guarda en nuestro servidor | **Sí – servicio de tiempo Open-Meteo** | Funciones de la app | Sí |
| Datos de salud (ciclo, opcional) | **No se envía**: se guarda cifrado sólo en el dispositivo | No | Personalización | Sí |
| Información de pedidos/compras | No | — | — | — |
| Identificadores de dispositivo / publicidad | No | — | — | — |
| Fallos / diagnóstico | No (no hay Crashlytics en esta versión) | — | — | — |

Declaraciones de seguridad: datos cifrados en tránsito ✔ · Se puede solicitar el borrado ✔ · Sin SDK de publicidad ✔. Si más adelante se añade Firebase (push/analytics/crashlytics) hay que actualizar este formulario.

### 4.9 Otras declaraciones
- ¿App gubernamental / financiera / de salud? **No** (el ciclo es un dato personal de bienestar, sin diagnóstico; si Play pregunta por "funciones de salud", responder que **no ofrece funciones médicas**).
- Funciones de pago dentro de la app: **ninguna**. (Premium sólo informa; **no añadir cobro fuera de Google Play Billing**.)
- ID de publicidad: **No lo usa**.
- Servicios de VPN, accesibilidad, SMS: **No**.
- Noticias: no. COVID: no. 
- Declaración de uso de Google Play Services / ML Kit (recorte y detección de pose): se usa **ML Kit** en el dispositivo; el recorte descarga un módulo de Google Play Services la primera vez.

## 5. Configuración de la publicación
- **Pistas:** primero **Prueba interna** (hasta 100 testers por email, sin revisión larga) → **Prueba cerrada** (las cuentas personales nuevas deben cumplir los **requisitos de prueba de Google: mínimo 12 testers durante 14 días seguidos** antes de pedir producción; cuentas de organización no) → Producción.
- **Países:** España y, si quieres, Latinoamérica (los textos están en español).
- **Precio:** gratis.
- **Play App Signing:** aceptar (recomendado). El bundle se firma con una **clave de subida** y Google gestiona la clave final.
- **Formato de subida:** `.aab` (el APK sólo para pruebas directas).
- **Requisitos técnicos que ya cumple:** targetSdk 36, minSdk 26, 64 bits, R8 activo, sin permisos peligrosos innecesarios, `allowBackup=false`, sólo HTTPS.

## 6. Lista de comprobación antes de enviar
- [ ] Clave de subida creada y **copia de seguridad guardada** (ver `COMPILAR_FIRMADO.md`).
- [ ] Bundle `.aab` firmado subido a Prueba interna y probado en un móvil real.
- [ ] Cuenta de prueba creada y credenciales puestas en §4.2.
- [ ] Icono 512, gráfico 1024×500 y ≥2 capturas subidos.
- [ ] Política de privacidad en URL pública y con la sección de eliminación de cuenta.
- [ ] Formulario de seguridad de datos, clasificación y público objetivo completados.
- [ ] **Denunciar/Bloquear (UGC) implementado** (ver §4.4b).
- [ ] Revisar que Premium no ofrezca pago fuera de Play.
- [ ] Cumplir los 12 testers × 14 días si la cuenta de desarrollador es personal.
