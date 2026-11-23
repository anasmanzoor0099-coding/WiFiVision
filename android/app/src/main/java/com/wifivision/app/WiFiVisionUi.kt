package com.wifivision.app

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.text.InputType
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView

object WiFiVisionPalette {
    // Ivory & White Surfaces
    val warmIvory = 0xFFF7F6F2.toInt()
    val softIvory = 0xFFF1EFE9.toInt()
    val pureWhite = 0xFFFFFFFF.toInt()
    val hairline = 0xFFE5E1D6.toInt()
    val borderStrong = 0xFFD4CEBE.toInt()

    // Deep Emerald & Jade Accents
    val emeraldDeep = 0xFF0F4C3A.toInt()
    val emeraldMid = 0xFF16614A.toInt()
    val emeraldBright = 0xFF176049.toInt()
    val jade = 0xFF1E7A5B.toInt()
    val emeraldTint = 0xFFE6F4EE.toInt()
    val emeraldBorder = 0xFFB7DFCE.toInt()
    val heroGlassBg = 0x22FFFFFF.toInt()
    val heroGlassBorder = 0x55C6A15B.toInt()
    val heroMutedText = 0xFFD2E5DC.toInt()

    // Champagne Gold Highlights
    val champagneGold = 0xFFC6A15B.toInt()
    val deepGold = 0xFF967332.toInt()
    val softGoldBg = 0xFFFBF5E8.toInt()
    val goldBorder = 0xFFE7D5AE.toInt()

    // Slate Typography
    val darkSlate = 0xFF242B35.toInt()
    val bodySlate = 0xFF4E5866.toInt()
    val mutedSlate = 0xFF7B8492.toInt()

    // Semantic Status Tones
    val amberText = 0xFF9A5B13.toInt()
    val amberBg = 0xFFFEF4E2.toInt()
    val amberBorder = 0xFFF0D5A3.toInt()
    val crimsonText = 0xFFA62E2E.toInt()
    val crimsonBg = 0xFFFDECEC.toInt()
    val crimsonBorder = 0xFFF2C0C0.toInt()

    // Ripple Overlays
    val rippleLight = 0x33FFFFFF.toInt()
    val rippleDark = 0x1A0F4C3A.toInt()
}

fun Context.dp(value: Int): Int {
    return (value * resources.displayMetrics.density).toInt()
}

fun Context.dpF(value: Float): Float {
    return value * resources.displayMetrics.density
}

fun roundedDrawable(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    radiusDp: Int,
    strokeWidthDp: Int = 1
): GradientDrawable {
    return GradientDrawable().apply {
        setColor(fillColor)
        if (strokeWidthDp > 0) {
            setStroke(context.dp(strokeWidthDp), strokeColor)
        }
        cornerRadius = context.dpF(radiusDp.toFloat())
    }
}

fun pillDrawable(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    strokeWidthDp: Int = 1
): GradientDrawable {
    return roundedDrawable(context, fillColor, strokeColor, 999, strokeWidthDp)
}

fun heroGradientDrawable(context: Context): GradientDrawable {
    return GradientDrawable(
        GradientDrawable.Orientation.TL_BR,
        intArrayOf(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldBright)
    ).apply {
        cornerRadius = context.dpF(18f)
        setStroke(context.dp(1), WiFiVisionPalette.heroGlassBorder)
    }
}

fun rippleRoundedDrawable(
    context: Context,
    fillColor: Int,
    strokeColor: Int,
    rippleColor: Int,
    radiusDp: Int,
    strokeWidthDp: Int = 1
): RippleDrawable {
    val content = roundedDrawable(context, fillColor, strokeColor, radiusDp, strokeWidthDp)
    val mask = GradientDrawable().apply {
        setColor(WiFiVisionPalette.pureWhite)
        cornerRadius = context.dpF(radiusDp.toFloat())
    }
    return RippleDrawable(ColorStateList.valueOf(rippleColor), content, mask)
}

fun createCard(
    context: Context,
    fillColor: Int = WiFiVisionPalette.pureWhite,
    strokeColor: Int = WiFiVisionPalette.hairline,
    paddingDp: Int = 16
): LinearLayout {
    return LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(context.dp(paddingDp), context.dp(paddingDp), context.dp(paddingDp), context.dp(paddingDp))
        background = roundedDrawable(context, fillColor, strokeColor, 16)
        elevation = context.dpF(2f)
        outlineProvider = ViewOutlineProvider.BACKGROUND
        clipToOutline = false
    }
}

fun createSectionTitle(context: Context, title: String): TextView {
    return TextView(context).apply {
        text = title
        textSize = 11.5f
        letterSpacing = 0.08f
        setTextColor(WiFiVisionPalette.deepGold)
        typeface = Typeface.DEFAULT_BOLD
    }
}

fun createPill(
    context: Context,
    label: String,
    textColor: Int,
    bgColor: Int,
    borderColor: Int,
    dotColor: Int? = null
): TextView {
    return TextView(context).apply {
        text = label
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        setPadding(context.dp(10), context.dp(4), context.dp(10), context.dp(4))
        background = pillDrawable(context, bgColor, borderColor)
        maxLines = 1
        ellipsize = TextUtils.TruncateAt.END
        gravity = Gravity.CENTER_VERTICAL
        if (dotColor != null) {
            val dot = GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(dotColor)
                setSize(context.dp(7), context.dp(7))
            }
            setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
            compoundDrawablePadding = context.dp(6)
        }
    }
}

fun updatePillStyle(
    context: Context,
    pill: TextView,
    label: String,
    textColor: Int,
    bgColor: Int,
    borderColor: Int,
    dotColor: Int? = null
) {
    pill.text = label
    pill.setTextColor(textColor)
    pill.background = pillDrawable(context, bgColor, borderColor)
    if (dotColor != null) {
        val dot = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(dotColor)
            setSize(context.dp(7), context.dp(7))
        }
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(dot, null, null, null)
        pill.compoundDrawablePadding = context.dp(6)
    } else {
        pill.setCompoundDrawablesRelativeWithIntrinsicBounds(null, null, null, null)
    }
}

fun createMetadataChip(
    context: Context,
    label: String,
    textColor: Int = WiFiVisionPalette.bodySlate,
    bgColor: Int = WiFiVisionPalette.softIvory,
    borderColor: Int = WiFiVisionPalette.hairline
): TextView {
    return TextView(context).apply {
        text = label
        textSize = 11f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(textColor)
        setPadding(context.dp(9), context.dp(4), context.dp(9), context.dp(4))
        background = roundedDrawable(context, bgColor, borderColor, 8)
        maxLines = 1
    }
}

fun createStyledInput(
    context: Context,
    hintText: String,
    inputTypeFlags: Int = InputType.TYPE_CLASS_TEXT
): EditText {
    return EditText(context).apply {
        hint = hintText
        textSize = 13.5f
        setTextColor(WiFiVisionPalette.darkSlate)
        setHintTextColor(WiFiVisionPalette.mutedSlate)
        inputType = inputTypeFlags
        isSingleLine = true
        minHeight = context.dp(46)
        setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        background = roundedDrawable(
            context,
            WiFiVisionPalette.softIvory,
            WiFiVisionPalette.borderStrong,
            12
        )
    }
}

fun createFieldLabel(context: Context, label: String): TextView {
    return TextView(context).apply {
        text = label
        textSize = 11.5f
        typeface = Typeface.DEFAULT_BOLD
        setTextColor(WiFiVisionPalette.bodySlate)
    }
}

/**
 * Custom 4-bar stepped RSSI indicator rendered cleanly without bitmap assets.
 */
class SignalBarsView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val barRect = RectF()
    private val heightFractions = floatArrayOf(0.35f, 0.55f, 0.78f, 1.0f)

    var activeBars: Int = 0
        set(value) {
            val coerced = value.coerceIn(0, 4)
            if (field != coerced) {
                field = coerced
                invalidate()
            }
        }

    var activeColor: Int = WiFiVisionPalette.jade
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var inactiveColor: Int = WiFiVisionPalette.hairline
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultW = context.dp(24)
        val defaultH = context.dp(18)
        setMeasuredDimension(
            resolveSize(defaultW, widthMeasureSpec),
            resolveSize(defaultH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0f || h <= 0f) return

        val barCount = 4
        val gap = (w * 0.14f).coerceAtLeast(context.dpF(2f))
        val barWidth = (w - gap * (barCount - 1)) / barCount
        val radius = barWidth * 0.45f
        val bottom = (height - paddingBottom).toFloat()

        for (i in 0 until barCount) {
            val left = paddingLeft + i * (barWidth + gap)
            val barHeight = (h * heightFractions[i]).coerceAtLeast(barWidth)
            barRect.set(left, bottom - barHeight, left + barWidth, bottom)
            paint.color = if (i < activeBars) activeColor else inactiveColor
            canvas.drawRoundRect(barRect, radius, radius, paint)
        }
    }
}

/**
 * Horizontal pill bar visualizing Wi-Fi RSSI intensity proportionally from 5..100%.
 */
class SignalStrengthBarView(context: Context) : View(context) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val rect = RectF()

    var progressPercent: Int = 5
        set(value) {
            val coerced = value.coerceIn(0, 100)
            if (field != coerced) {
                field = coerced
                invalidate()
            }
        }

    var fillColor: Int = WiFiVisionPalette.jade
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    var trackColor: Int = WiFiVisionPalette.softIvory
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val defaultW = context.dp(120)
        val defaultH = context.dp(6)
        setMeasuredDimension(
            resolveSize(defaultW, widthMeasureSpec),
            resolveSize(defaultH, heightMeasureSpec)
        )
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = (width - paddingLeft - paddingRight).toFloat()
        val h = (height - paddingTop - paddingBottom).toFloat()
        if (w <= 0f || h <= 0f) return

        val radius = h / 2f
        val top = paddingTop.toFloat()
        val bottom = (height - paddingBottom).toFloat()
        val left = paddingLeft.toFloat()

        rect.set(left, top, left + w, bottom)
        paint.color = trackColor
        canvas.drawRoundRect(rect, radius, radius, paint)

        if (progressPercent > 0) {
            val fillWidth = (w * (progressPercent / 100f)).coerceAtLeast(h)
            rect.set(left, top, (left + fillWidth).coerceAtMost(left + w), bottom)
            paint.color = fillColor
            canvas.drawRoundRect(rect, radius, radius, paint)
        }
    }
}

/**
 * Native LinearLayout-based action button supporting crisp tinted vector icons,
 * ripple touch feedback, and clear disabled visual dimming.
 */
class WiFiActionButton(context: Context) : LinearLayout(context) {
    private val iconView: ImageView
    private val labelView: TextView

    var text: CharSequence
        get() = labelView.text
        set(value) {
            labelView.text = value
            contentDescription = value
        }

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER
        isClickable = true
        isFocusable = true
        minimumHeight = context.dp(44)
        setPadding(context.dp(12), context.dp(8), context.dp(12), context.dp(8))

        iconView = ImageView(context).apply {
            visibility = GONE
            scaleType = ImageView.ScaleType.FIT_CENTER
        }
        labelView = TextView(context).apply {
            textSize = 13f
            typeface = Typeface.DEFAULT_BOLD
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            gravity = Gravity.CENTER
        }

        addView(
            iconView,
            LayoutParams(context.dp(18), context.dp(18)).apply {
                marginEnd = context.dp(8)
            }
        )
        addView(labelView, LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT))
    }

    fun configure(
        label: String,
        iconRes: Int? = null,
        textColor: Int,
        fillColor: Int,
        strokeColor: Int,
        rippleColor: Int,
        radiusDp: Int = 12,
        textSizeSp: Float = 13f
    ) {
        text = label
        labelView.textSize = textSizeSp
        applyStyle(textColor, fillColor, strokeColor, rippleColor, radiusDp)
        if (iconRes != null) {
            iconView.setImageResource(iconRes)
            iconView.setColorFilter(textColor)
            iconView.visibility = VISIBLE
        } else {
            iconView.visibility = GONE
        }
    }

    fun applyStyle(
        textColor: Int,
        fillColor: Int,
        strokeColor: Int,
        rippleColor: Int,
        radiusDp: Int = 12
    ) {
        labelView.setTextColor(textColor)
        if (iconView.visibility == VISIBLE) {
            iconView.setColorFilter(textColor)
        }
        background = rippleRoundedDrawable(context, fillColor, strokeColor, rippleColor, radiusDp)
    }

    override fun setEnabled(enabled: Boolean) {
        super.setEnabled(enabled)
        isClickable = enabled
        alpha = if (enabled) 1.0f else 0.48f
    }
}
