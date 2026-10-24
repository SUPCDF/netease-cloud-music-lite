package com.ncmusic.designsystem.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ncmusic.designsystem.R

/**
 * 全局字体：PingFang SC（苹方）。
 *
 * 需求是「文字都要加粗」，所以**所有字重统一映射到 Bold 文件**：
 * 这样即使某处写的是 Normal / Medium（Material 组件默认值也是如此），
 * 渲染出来也是真实苹方粗体，不会出现「有的粗有的细」。
 */
private val NcmusicFontFamily = FontFamily(
    Font(resId = R.font.pingfang_bold, weight = FontWeight.Normal),
    Font(resId = R.font.pingfang_bold, weight = FontWeight.Medium),
    Font(resId = R.font.pingfang_bold, weight = FontWeight.SemiBold),
    Font(resId = R.font.pingfang_bold, weight = FontWeight.Bold),
    Font(resId = R.font.pingfang_bold, weight = FontWeight.Black),
)

/** M3 默认字体表（仅用于取未自定义样式的默认值） */
private val DefaultTypography = Typography()

/** 全局字体样式（在 Material3 默认基础上做轻量调整，并统一应用 PingFang SC） */
/**
 * 全局字体样式：所有层级统一使用 Bold（真实苹方粗体）。
 * 需求明确要求「文字都要加粗」，因此正文字重也从 Normal 调整为 Bold。
 */
val NcmusicTypography = Typography(
    displayLarge = DefaultTypography.displayLarge.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    displayMedium = DefaultTypography.displayMedium.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    displaySmall = DefaultTypography.displaySmall.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    headlineLarge = DefaultTypography.headlineLarge.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    headlineMedium = DefaultTypography.headlineMedium.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    headlineSmall = DefaultTypography.headlineSmall.copy(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
    ),
    titleLarge = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp,
        lineHeight = 28.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    titleSmall = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelLarge = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    ),
    labelMedium = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = NcmusicFontFamily,
        fontWeight = FontWeight.Bold,
        fontSize = 11.sp,
        lineHeight = 16.sp,
    ),
)

/** 全局圆角（iOS 风格：平滑一致的圆角矩形） */
val NcmusicShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp),
)
