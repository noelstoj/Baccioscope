package com.aamon.baccioscope.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.googlefonts.GoogleFont
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.unit.sp
import com.aamon.baccioscope.R

// Google Font Provider Setup
val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs
)

val BinkFamily = FontFamily(
    Font(R.font.bitcount_ink, FontWeight.Normal)
)

// SWAPPING TO "Caveat" - A KNOWN WORKING GOOGLE FONT
val fontName = GoogleFont("Indie Flower")
val handWriting = FontFamily(
    Font(googleFont = fontName, fontProvider = provider)
)
val deviceFont = FontFamily(
    Font(googleFont = GoogleFont("Electrolize"), fontProvider = provider)
)



val Typography = Typography(
    bodyLarge = TextStyle(
        fontFamily = FontFamily.Default,
        fontWeight = FontWeight.Normal,
        fontSize = 16.sp,
        lineHeight = 24.sp,
        letterSpacing = 0.5.sp
    ),
    titleLarge = TextStyle(
        fontFamily = BinkFamily,
        fontWeight = FontWeight.Thin,
        fontSize = 22.sp,
        lineHeight = 28.sp,
        letterSpacing = 0.sp
    )
)