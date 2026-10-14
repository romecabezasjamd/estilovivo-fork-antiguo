package com.estilovivo.app.core.design

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.estilovivo.app.R

/** Sans geométrica (titulares en negrita y texto). */
val Poppins = FontFamily(
    Font(R.font.poppins_regular, FontWeight.Normal),
    Font(R.font.poppins_medium, FontWeight.Medium),
    Font(R.font.poppins_semibold, FontWeight.SemiBold),
    Font(R.font.poppins_bold, FontWeight.Bold),
    Font(R.font.poppins_extrabold, FontWeight.ExtraBold),
)

/** Serif cursiva para las palabras destacadas de los titulares ("*Handpicked* for you"). */
val InstrumentSerif = FontFamily(
    Font(R.font.instrument_serif_italic, FontWeight.Normal, FontStyle.Italic),
)

/** Estilos propios además de la tipografía Material. */
@Immutable
object EvType {
    /** Letras gigantes de la bienvenida. */
    val giant = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.ExtraBold,
        fontSize = 112.sp, lineHeight = 100.sp, letterSpacing = (-4).sp,
    )
    /** Titular de pantalla (parte en sans). */
    val headline = TextStyle(
        fontFamily = Poppins, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 36.sp, letterSpacing = (-0.8).sp,
    )
    /** Palabra destacada del titular (serif cursiva, un poco mayor para igualar el ojo óptico). */
    val headlineAccent = TextStyle(
        fontFamily = InstrumentSerif, fontStyle = FontStyle.Italic, fontWeight = FontWeight.Normal,
        fontSize = 34.sp, lineHeight = 36.sp, letterSpacing = (-0.4).sp,
    )
    val title = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 18.sp, lineHeight = 24.sp)
    val subtitle = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 15.sp, lineHeight = 22.sp)
    val body = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 14.sp, lineHeight = 21.sp)
    val bodySmall = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Normal, fontSize = 12.sp, lineHeight = 18.sp)
    val label = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.SemiBold, fontSize = 13.sp, lineHeight = 18.sp)
    val caption = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Medium, fontSize = 11.sp, lineHeight = 16.sp, letterSpacing = 0.2.sp)
    val price = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.Bold, fontSize = 16.sp, lineHeight = 20.sp)
    val bigNumber = TextStyle(fontFamily = Poppins, fontWeight = FontWeight.ExtraBold, fontSize = 34.sp, lineHeight = 38.sp, letterSpacing = (-1).sp)
}

val EvTypography = Typography(
    displayLarge = EvType.giant,
    displayMedium = EvType.bigNumber,
    headlineLarge = EvType.headline,
    headlineMedium = EvType.title,
    headlineSmall = EvType.subtitle,
    titleLarge = EvType.title,
    titleMedium = EvType.subtitle,
    titleSmall = EvType.label,
    bodyLarge = EvType.body,
    bodyMedium = EvType.body,
    bodySmall = EvType.bodySmall,
    labelLarge = EvType.label,
    labelMedium = EvType.caption,
    labelSmall = EvType.caption,
)
