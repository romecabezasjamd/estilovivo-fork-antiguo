package com.estilovivo.app.core.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Ámbito de corrutinas de toda la vida de la app (cachés, sockets). Inyectable con Koin para trabajo que debe sobrevivir a una pantalla. */
class AppScope(val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default))
