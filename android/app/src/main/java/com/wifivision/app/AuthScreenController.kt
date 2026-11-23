package com.wifivision.app

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputFilter
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.CheckBox
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView

/**
 * Native onboarding and authentication experience covering Sign In, Account Creation,
 * 6-digit Email OTP Verification with Resend cooldown, and Password Setup.
 */
class AuthScreenController(
    private val context: Context,
    private val authSessionManager: AuthSessionManager,
    private val preferences: AppPreferences,
    private val onAuthenticated: (UserSessionCredential) -> Unit,
    private val onBaseUrlUpdated: (String) -> Unit
) {

    enum class Mode {
        SIGN_IN,
        REGISTER_START,
        VERIFY_OTP,
        CREATE_PASSWORD
    }

    private var mode: Mode = Mode.SIGN_IN
    private var currentBaseUrl: String = preferences.loadBaseUrl()
    private var isBusy = false
    private var resendSecondsLeft = 0
    private var showUrlEditor = false
    private var signInPasswordVisible = false
    private var createPwdVisible = false
    private var confirmPwdVisible = false

    private val handler = Handler(Looper.getMainLooper())
    private var countdownRunnable: Runnable? = null

    val rootView: ScrollView
    private val contentCol: LinearLayout
    private val stepPill: TextView
    private val titleText: TextView
    private val subtitleText: TextView

    private val bannerBox: LinearLayout
    private val bannerProgress: ProgressBar
    private val bannerText: TextView

    // Sections
    private val signInPanel: LinearLayout
    private val registerPanel: LinearLayout
    private val verifyPanel: LinearLayout
    private val createPasswordPanel: LinearLayout

    // Sign In views
    private val signInEmailInput: EditText
    private val signInPasswordInput: EditText
    private val signInTogglePwdBtn: WiFiActionButton
    private val signInSubmitBtn: WiFiActionButton
    private val urlToggleBtn: WiFiActionButton
    private val urlEditorBox: LinearLayout
    private val urlInput: EditText
    private val urlApplyBtn: WiFiActionButton
    private val urlEndpointText: TextView

    // Register views
    private val regNameInput: EditText
    private val regEmailInput: EditText
    private val regPrivacyCheck: CheckBox
    private val regSubmitBtn: WiFiActionButton

    // Verify OTP views
    private val verifyEmailInput: EditText
    private val verifyOtpInput: EditText
    private val verifySubmitBtn: WiFiActionButton
    private val resendBtn: WiFiActionButton

    // Create Password views
    private val setupProfileText: TextView
    private val createPwdInput: EditText
    private val confirmPwdInput: EditText
    private val createTogglePwdBtn: WiFiActionButton
    private val createRuleText: TextView
    private val createMatchText: TextView
    private val createSubmitBtn: WiFiActionButton

    init {
        contentCol = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(18), context.dp(20), context.dp(18), context.dp(28))
        }

        rootView = ScrollView(context).apply {
            setBackgroundColor(WiFiVisionPalette.warmIvory)
            isFillViewport = true
            addView(contentCol)
        }

        // Brand Header Hero
        val hero = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            background = heroGradientDrawable(context)
            setPadding(context.dp(18), context.dp(18), context.dp(18), context.dp(18))
        }
        val brandRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val emblem = ImageView(context).apply {
            setImageResource(R.drawable.ic_brand_badge)
            contentDescription = "WiFiVision"
        }
        val brandText = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(context.dp(12), 0, 0, 0)
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        brandText.addView(TextView(context).apply {
            text = "WiFiVision"
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.pureWhite)
        })
        brandText.addView(TextView(context).apply {
            text = "Network Intelligence Platform"
            textSize = 12f
            setTextColor(WiFiVisionPalette.heroMutedText)
        })
        brandRow.addView(emblem, LinearLayout.LayoutParams(context.dp(42), context.dp(42)))
        brandRow.addView(brandText)
        hero.addView(brandRow)
        hero.addView(TextView(context).apply {
            text = "Unified Wi-Fi scanning, spatial GPS heatmaps, signal quality intelligence, and live performance analytics."
            textSize = 12.5f
            setTextColor(WiFiVisionPalette.heroMutedText)
            setPadding(0, context.dp(10), 0, 0)
        })

        // Main Form Card
        val formCard = createCard(context, paddingDp = 18)

        stepPill = createPill(
            context,
            label = "ACCOUNT ACCESS",
            textColor = WiFiVisionPalette.deepGold,
            bgColor = WiFiVisionPalette.softGoldBg,
            borderColor = WiFiVisionPalette.goldBorder
        )

        titleText = TextView(context).apply {
            text = "Welcome back"
            textSize = 21f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.darkSlate)
            setPadding(0, context.dp(10), 0, context.dp(4))
        }

        subtitleText = TextView(context).apply {
            text = "Sign in to sync observations and view your analytics workspace."
            textSize = 13f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(0, 0, 0, context.dp(12))
        }

        bannerBox = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            visibility = View.GONE
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
        }
        bannerProgress = ProgressBar(context, null, android.R.attr.progressBarStyleSmall).apply {
            visibility = View.GONE
            indeterminateTintList = android.content.res.ColorStateList.valueOf(WiFiVisionPalette.emeraldDeep)
        }
        bannerText = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        bannerBox.addView(bannerProgress, LinearLayout.LayoutParams(context.dp(16), context.dp(16)).apply {
            marginEnd = context.dp(8)
        })
        bannerBox.addView(bannerText)

        formCard.addView(stepPill, LinearLayout.LayoutParams(LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        formCard.addView(titleText)
        formCard.addView(subtitleText)
        formCard.addView(bannerBox, marginBottomParams(12))

        // ---------------------------------------------------------------------
        // PANEL 1: SIGN IN
        // ---------------------------------------------------------------------
        signInPanel = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        signInEmailInput = createStyledInput(
            context,
            "you@example.com",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS
        )
        signInPasswordInput = createStyledInput(
            context,
            "Enter your password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        signInTogglePwdBtn = WiFiActionButton(context).apply {
            configure(
                label = "Show",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener {
                signInPasswordVisible = !signInPasswordVisible
                applyPasswordInputType(signInPasswordInput, signInPasswordVisible)
                text = if (signInPasswordVisible) "Hide" else "Show"
            }
        }
        val signInPwdRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            addView(signInPasswordInput, LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(8)
            })
            addView(signInTogglePwdBtn, LinearLayout.LayoutParams(context.dp(68), LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        signInSubmitBtn = WiFiActionButton(context).apply {
            configure(
                label = "Sign In",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 12,
                textSizeSp = 13.5f
            )
            setOnClickListener { handleSignIn() }
        }
        val goRegisterBtn = WiFiActionButton(context).apply {
            configure(
                label = "Create a New Account",
                textColor = WiFiVisionPalette.emeraldDeep,
                fillColor = WiFiVisionPalette.emeraldTint,
                strokeColor = WiFiVisionPalette.emeraldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 13f
            )
            setOnClickListener { switchMode(Mode.REGISTER_START) }
        }
        val haveCodeBtn = WiFiActionButton(context).apply {
            configure(
                label = "Already have an OTP verification code?",
                textColor = WiFiVisionPalette.deepGold,
                fillColor = WiFiVisionPalette.softGoldBg,
                strokeColor = WiFiVisionPalette.goldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 12f
            )
            setOnClickListener {
                verifyEmailInput.setText(signInEmailInput.text?.toString().orEmpty())
                switchMode(Mode.VERIFY_OTP)
            }
        }

        // Collapsible Server URL override
        urlEndpointText = TextView(context).apply {
            text = "Backend: $currentBaseUrl"
            textSize = 11f
            setTextColor(WiFiVisionPalette.mutedSlate)
            setPadding(0, context.dp(10), 0, context.dp(6))
        }
        urlToggleBtn = WiFiActionButton(context).apply {
            configure(
                label = "Server Endpoint Settings",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener {
                showUrlEditor = !showUrlEditor
                urlEditorBox.visibility = if (showUrlEditor) View.VISIBLE else View.GONE
            }
        }
        urlInput = createStyledInput(context, BuildConfig.DEFAULT_API_BASE_URL, InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI).apply {
            setText(currentBaseUrl)
        }
        urlApplyBtn = WiFiActionButton(context).apply {
            configure(
                label = "Save Backend URL",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.darkSlate,
                strokeColor = WiFiVisionPalette.darkSlate,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 10,
                textSizeSp = 12f
            )
            setOnClickListener { handleSaveUrl() }
        }
        urlEditorBox = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
            setPadding(context.dp(10), context.dp(10), context.dp(10), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.warmIvory, WiFiVisionPalette.hairline, 12)
            addView(createFieldLabel(context, if (BuildConfig.ALLOW_CLEARTEXT_DEV_URL) "Backend URL (HTTPS recommended)" else "Production HTTPS Backend URL"), marginBottomParams(6))
            addView(urlInput, marginBottomParams(8))
            addView(urlApplyBtn, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT))
        }

        signInPanel.addView(createFieldLabel(context, "Email Address"), marginBottomParams(5))
        signInPanel.addView(signInEmailInput, marginBottomParams(12))
        signInPanel.addView(createFieldLabel(context, "Password"), marginBottomParams(5))
        signInPanel.addView(signInPwdRow, marginBottomParams(14))
        signInPanel.addView(signInSubmitBtn, marginBottomParams(10))
        signInPanel.addView(goRegisterBtn, marginBottomParams(8))
        signInPanel.addView(haveCodeBtn, marginBottomParams(6))
        signInPanel.addView(urlEndpointText)
        signInPanel.addView(urlToggleBtn, marginBottomParams(8))
        signInPanel.addView(urlEditorBox)

        // ---------------------------------------------------------------------
        // PANEL 2: REGISTER START
        // ---------------------------------------------------------------------
        registerPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        regNameInput = createStyledInput(context, "Full name (2-60 characters)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PERSON_NAME)
        regEmailInput = createStyledInput(context, "you@example.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        regPrivacyCheck = CheckBox(context).apply {
            text = "I acknowledge the WiFiVision Privacy Policy (local-first scanning & explicit user uploads)."
            textSize = 12f
            setTextColor(WiFiVisionPalette.bodySlate)
        }
        val viewPrivacyLink = WiFiActionButton(context).apply {
            configure(
                label = "Read Privacy Summary",
                textColor = WiFiVisionPalette.deepGold,
                fillColor = WiFiVisionPalette.softGoldBg,
                strokeColor = WiFiVisionPalette.goldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener { showPrivacyDialog() }
        }
        regSubmitBtn = WiFiActionButton(context).apply {
            configure(
                label = "Send Verification Code",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 12,
                textSizeSp = 13.5f
            )
            setOnClickListener { handleStartRegistration() }
        }
        val backSignInFromReg = WiFiActionButton(context).apply {
            configure(
                label = "Back to Sign In",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { switchMode(Mode.SIGN_IN) }
        }

        registerPanel.addView(createFieldLabel(context, "Full Name"), marginBottomParams(5))
        registerPanel.addView(regNameInput, marginBottomParams(12))
        registerPanel.addView(createFieldLabel(context, "Email Address"), marginBottomParams(5))
        registerPanel.addView(regEmailInput, marginBottomParams(10))
        registerPanel.addView(regPrivacyCheck, marginBottomParams(8))
        registerPanel.addView(viewPrivacyLink, marginBottomParams(12))
        registerPanel.addView(regSubmitBtn, marginBottomParams(10))
        registerPanel.addView(backSignInFromReg)

        // ---------------------------------------------------------------------
        // PANEL 3: VERIFY OTP
        // ---------------------------------------------------------------------
        verifyPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        verifyEmailInput = createStyledInput(context, "you@example.com", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
        verifyOtpInput = createStyledInput(context, "6-digit verification code", InputType.TYPE_CLASS_NUMBER).apply {
            filters = arrayOf(InputFilter.LengthFilter(6))
            letterSpacing = 0.25f
            typeface = Typeface.MONOSPACE
        }
        verifySubmitBtn = WiFiActionButton(context).apply {
            configure(
                label = "Verify Email & Continue",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 12,
                textSizeSp = 13.5f
            )
            setOnClickListener { handleVerifyCode() }
        }
        resendBtn = WiFiActionButton(context).apply {
            configure(
                label = "Resend Verification Code",
                textColor = WiFiVisionPalette.deepGold,
                fillColor = WiFiVisionPalette.softGoldBg,
                strokeColor = WiFiVisionPalette.goldBorder,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { handleResendCode() }
        }
        val backFromVerify = WiFiActionButton(context).apply {
            configure(
                label = "Back to Registration / Sign In",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { switchMode(Mode.SIGN_IN) }
        }

        verifyPanel.addView(createFieldLabel(context, "Registered Email"), marginBottomParams(5))
        verifyPanel.addView(verifyEmailInput, marginBottomParams(12))
        verifyPanel.addView(createFieldLabel(context, "6-Digit Code"), marginBottomParams(5))
        verifyPanel.addView(verifyOtpInput, marginBottomParams(14))
        verifyPanel.addView(verifySubmitBtn, marginBottomParams(10))
        verifyPanel.addView(resendBtn, marginBottomParams(8))
        verifyPanel.addView(backFromVerify)

        // ---------------------------------------------------------------------
        // PANEL 4: CREATE PASSWORD
        // ---------------------------------------------------------------------
        createPasswordPanel = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            visibility = View.GONE
        }
        setupProfileText = TextView(context).apply {
            textSize = 12.5f
            typeface = Typeface.DEFAULT_BOLD
            setTextColor(WiFiVisionPalette.emeraldDeep)
            setPadding(context.dp(12), context.dp(10), context.dp(12), context.dp(10))
            background = roundedDrawable(context, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder, 10)
        }
        createPwdInput = createStyledInput(
            context,
            "Create a strong password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        confirmPwdInput = createStyledInput(
            context,
            "Confirm your password",
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        )
        createTogglePwdBtn = WiFiActionButton(context).apply {
            configure(
                label = "Show Passwords",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 10,
                textSizeSp = 11.5f
            )
            setOnClickListener {
                createPwdVisible = !createPwdVisible
                confirmPwdVisible = createPwdVisible
                applyPasswordInputType(createPwdInput, createPwdVisible)
                applyPasswordInputType(confirmPwdInput, confirmPwdVisible)
                text = if (createPwdVisible) "Hide Passwords" else "Show Passwords"
            }
        }
        createRuleText = TextView(context).apply {
            text = "8-72 chars with uppercase, lowercase, number & symbol."
            textSize = 11.5f
            setTextColor(WiFiVisionPalette.bodySlate)
            setPadding(context.dp(10), context.dp(8), context.dp(10), context.dp(8))
            background = roundedDrawable(context, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline, 10)
        }
        createMatchText = TextView(context).apply {
            textSize = 12f
            typeface = Typeface.DEFAULT_BOLD
            visibility = View.GONE
        }
        createSubmitBtn = WiFiActionButton(context).apply {
            configure(
                label = "Complete Account Setup",
                textColor = WiFiVisionPalette.pureWhite,
                fillColor = WiFiVisionPalette.emeraldDeep,
                strokeColor = WiFiVisionPalette.emeraldDeep,
                rippleColor = WiFiVisionPalette.rippleLight,
                radiusDp = 12,
                textSizeSp = 13.5f
            )
            setOnClickListener { handleCreatePassword() }
        }
        val restartRegBtn = WiFiActionButton(context).apply {
            configure(
                label = "Return to Sign In",
                textColor = WiFiVisionPalette.bodySlate,
                fillColor = WiFiVisionPalette.softIvory,
                strokeColor = WiFiVisionPalette.hairline,
                rippleColor = WiFiVisionPalette.rippleDark,
                radiusDp = 12,
                textSizeSp = 12.5f
            )
            setOnClickListener { switchMode(Mode.SIGN_IN) }
        }

        val pwdWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) { updatePasswordValidationHints() }
        }
        createPwdInput.addTextChangedListener(pwdWatcher)
        confirmPwdInput.addTextChangedListener(pwdWatcher)

        createPasswordPanel.addView(setupProfileText, marginBottomParams(12))
        createPasswordPanel.addView(createFieldLabel(context, "New Password"), marginBottomParams(5))
        createPasswordPanel.addView(createPwdInput, marginBottomParams(10))
        createPasswordPanel.addView(createFieldLabel(context, "Confirm Password"), marginBottomParams(5))
        createPasswordPanel.addView(confirmPwdInput, marginBottomParams(8))
        createPasswordPanel.addView(createTogglePwdBtn, marginBottomParams(10))
        createPasswordPanel.addView(createRuleText, marginBottomParams(8))
        createPasswordPanel.addView(createMatchText, marginBottomParams(10))
        createPasswordPanel.addView(createSubmitBtn, marginBottomParams(10))
        createPasswordPanel.addView(restartRegBtn)

        formCard.addView(signInPanel)
        formCard.addView(registerPanel)
        formCard.addView(verifyPanel)
        formCard.addView(createPasswordPanel)

        contentCol.addView(hero, marginBottomParams(14))
        contentCol.addView(formCard)
    }

    fun applyWindowInsets(topPx: Int, bottomPx: Int) {
        contentCol.setPadding(
            context.dp(18),
            context.dp(16) + topPx,
            context.dp(18),
            context.dp(24) + bottomPx
        )
    }

    fun updateBaseUrlDisplay(newUrl: String) {
        currentBaseUrl = newUrl
        urlEndpointText.text = "Backend: $currentBaseUrl"
        urlInput.setText(currentBaseUrl)
    }

    fun showSignIn(bannerMessage: String? = null, isError: Boolean = false) {
        switchMode(Mode.SIGN_IN)
        if (!bannerMessage.isNullOrBlank()) {
            setBanner(bannerMessage, error = isError, success = !isError)
        } else {
            hideBanner()
        }
    }

    fun showCreatePasswordIfSetupActive(): Boolean {
        val activeSetup = authSessionManager.getActivePasswordSetup() ?: return false
        setupProfileText.text = "${activeSetup.name.ifBlank { "Account Verified" }} • ${activeSetup.email}"
        switchMode(Mode.CREATE_PASSWORD)
        setBusy(true, "Validating password setup session...")
        authSessionManager.getPasswordSetup(currentBaseUrl) { res ->
            setBusy(false)
            when (res) {
                is ApiResult.Success -> {
                    setupProfileText.text = "${res.data.name.ifBlank { "Account" }} • ${res.data.email}"
                    setBanner("Email verified. Create your password below.", success = true)
                }
                is ApiResult.Unauthorized -> {
                    switchMode(Mode.REGISTER_START)
                    setBanner(res.message, error = true)
                }
                else -> {
                    setBanner(extractErrorMessage(res), warning = true)
                }
            }
        }
        return true
    }

    private fun switchMode(nextMode: Mode) {
        mode = nextMode
        hideBanner()
        signInPanel.visibility = if (nextMode == Mode.SIGN_IN) View.VISIBLE else View.GONE
        registerPanel.visibility = if (nextMode == Mode.REGISTER_START) View.VISIBLE else View.GONE
        verifyPanel.visibility = if (nextMode == Mode.VERIFY_OTP) View.VISIBLE else View.GONE
        createPasswordPanel.visibility = if (nextMode == Mode.CREATE_PASSWORD) View.VISIBLE else View.GONE

        when (nextMode) {
            Mode.SIGN_IN -> {
                stepPill.text = "01 • ACCOUNT SIGN IN"
                titleText.text = "Welcome back"
                subtitleText.text = "Sign in to explore nearby Wi-Fi telemetry, heatmaps, and analytics."
            }
            Mode.REGISTER_START -> {
                stepPill.text = "01 • CREATE ACCOUNT"
                titleText.text = "Create your account"
                subtitleText.text = "Enter your full name and email address to receive a 6-digit verification OTP."
            }
            Mode.VERIFY_OTP -> {
                stepPill.text = "02 • EMAIL VERIFICATION"
                titleText.text = "Verify your email"
                subtitleText.text = "Enter the 6-digit code sent to your email address (expires after 10 minutes)."
            }
            Mode.CREATE_PASSWORD -> {
                stepPill.text = "03 • PASSWORD SETUP"
                titleText.text = "Create your password"
                subtitleText.text = "Set a strong password to complete account activation."
                updatePasswordValidationHints()
            }
        }
    }

    private fun handleSaveUrl() {
        val raw = urlInput.text?.toString().orEmpty()
        when (val validation = ApiContractUtils.validateBaseUrl(raw, BuildConfig.ALLOW_CLEARTEXT_DEV_URL)) {
            is UrlValidationResult.Valid -> {
                preferences.saveBaseUrl(validation.normalizedUrl)
                updateBaseUrlDisplay(validation.normalizedUrl)
                onBaseUrlUpdated(validation.normalizedUrl)
                setBanner("Backend URL updated: ${validation.normalizedUrl}", success = true)
            }
            is UrlValidationResult.Invalid -> {
                setBanner(validation.reason, error = true)
            }
        }
    }

    private fun handleSignIn() {
        if (isBusy) return
        val email = signInEmailInput.text?.toString().orEmpty().trim()
        val pwd = signInPasswordInput.text?.toString().orEmpty()
        if (!ApiContractUtils.isValidEmail(email) || pwd.isEmpty()) {
            setBanner("Enter a valid email address and password.", error = true)
            return
        }

        setBusy(true, "Signing in securely...")
        authSessionManager.login(currentBaseUrl, email, pwd) { result ->
            setBusy(false)
            when (result) {
                is ApiResult.Success -> {
                    signInPasswordInput.setText("")
                    onAuthenticated(result.data.session)
                }
                else -> {
                    setBanner(extractErrorMessage(result), error = true)
                }
            }
        }
    }

    private fun handleStartRegistration() {
        if (isBusy) return
        val name = regNameInput.text?.toString().orEmpty().trim()
        val email = regEmailInput.text?.toString().orEmpty().trim()
        if (!ApiContractUtils.isValidName(name)) {
            setBanner("Name must contain between 2 and 60 characters.", error = true)
            return
        }
        if (!ApiContractUtils.isValidEmail(email)) {
            setBanner("Please enter a valid email address.", error = true)
            return
        }
        if (!regPrivacyCheck.isChecked) {
            setBanner("Please acknowledge the Privacy Policy before continuing.", error = true)
            return
        }

        setBusy(true, "Sending verification code...")
        authSessionManager.startRegistration(currentBaseUrl, name, email) { result ->
            setBusy(false)
            when (result) {
                is ApiResult.Success -> {
                    verifyEmailInput.setText(result.data.email)
                    verifyOtpInput.setText("")
                    switchMode(Mode.VERIFY_OTP)
                    startResendCooldown(result.data.resendCooldownSeconds?.toInt() ?: 60)
                    setBanner(result.data.message, success = true)
                }
                is ApiResult.RateLimited -> {
                    result.retryAfterSeconds?.toInt()?.let { startResendCooldown(it) }
                    setBanner(result.message, error = true)
                }
                else -> {
                    setBanner(extractErrorMessage(result), error = true)
                }
            }
        }
    }

    private fun handleVerifyCode() {
        if (isBusy) return
        val email = verifyEmailInput.text?.toString().orEmpty().trim()
        val code = verifyOtpInput.text?.toString().orEmpty().trim()
        if (!ApiContractUtils.isValidEmail(email)) {
            setBanner("Enter your registered email address.", error = true)
            return
        }
        if (!ApiContractUtils.isValidOtpCode(code)) {
            setBanner("Enter all 6 digits of the verification code.", error = true)
            return
        }

        setBusy(true, "Verifying 6-digit code...")
        authSessionManager.verifyRegistrationCode(currentBaseUrl, email, code) { result ->
            setBusy(false)
            when (result) {
                is ApiResult.Success -> {
                    createPwdInput.setText("")
                    confirmPwdInput.setText("")
                    setupProfileText.text = "${result.data.name.ifBlank { "Verified Account" }} • ${result.data.email}"
                    switchMode(Mode.CREATE_PASSWORD)
                    setBanner("Email verified. Create your password to finish setup.", success = true)
                }
                else -> {
                    setBanner(extractErrorMessage(result), error = true)
                }
            }
        }
    }

    private fun handleResendCode() {
        if (isBusy || resendSecondsLeft > 0) return
        val email = verifyEmailInput.text?.toString().orEmpty().trim()
        if (!ApiContractUtils.isValidEmail(email)) {
            setBanner("Enter your registered email address first.", error = true)
            return
        }

        setBusy(true, "Requesting new verification code...")
        authSessionManager.resendVerificationCode(currentBaseUrl, email) { result ->
            setBusy(false)
            when (result) {
                is ApiResult.Success -> {
                    verifyOtpInput.setText("")
                    startResendCooldown(result.data.resendCooldownSeconds?.toInt() ?: 60)
                    setBanner(result.data.message, success = true)
                }
                is ApiResult.RateLimited -> {
                    result.retryAfterSeconds?.toInt()?.let { startResendCooldown(it) }
                    setBanner(result.message, error = true)
                }
                else -> {
                    setBanner(extractErrorMessage(result), error = true)
                }
            }
        }
    }

    private fun handleCreatePassword() {
        if (isBusy) return
        val pwd = createPwdInput.text?.toString().orEmpty()
        val confirm = confirmPwdInput.text?.toString().orEmpty()
        val err = ApiContractUtils.validatePasswordStrength(pwd)
        if (err != null) {
            setBanner(err, error = true)
            return
        }
        if (pwd != confirm) {
            setBanner("Passwords do not match.", error = true)
            return
        }

        setBusy(true, "Completing account registration...")
        authSessionManager.createAccountPassword(currentBaseUrl, pwd) { result ->
            setBusy(false)
            when (result) {
                is ApiResult.Success -> {
                    val createdEmail = result.data.user.email
                    createPwdInput.setText("")
                    confirmPwdInput.setText("")
                    signInEmailInput.setText(createdEmail)
                    signInPasswordInput.setText("")
                    showSignIn(
                        bannerMessage = "Account created for $createdEmail. Sign in with your password.",
                        isError = false
                    )
                }
                is ApiResult.Unauthorized -> {
                    switchMode(Mode.REGISTER_START)
                    setBanner(result.message, error = true)
                }
                else -> {
                    setBanner(extractErrorMessage(result), error = true)
                }
            }
        }
    }

    private fun updatePasswordValidationHints() {
        val pwd = createPwdInput.text?.toString().orEmpty()
        val confirm = confirmPwdInput.text?.toString().orEmpty()
        val err = if (pwd.isEmpty()) null else ApiContractUtils.validatePasswordStrength(pwd)
        if (pwd.isEmpty()) {
            createRuleText.text = "Requirements: 8-72 chars, uppercase, lowercase, digit & special symbol."
            createRuleText.setTextColor(WiFiVisionPalette.bodySlate)
        } else if (err == null) {
            createRuleText.text = "Password meets all complexity requirements."
            createRuleText.setTextColor(WiFiVisionPalette.emeraldDeep)
        } else {
            createRuleText.text = err
            createRuleText.setTextColor(WiFiVisionPalette.amberText)
        }

        if (confirm.isEmpty()) {
            createMatchText.visibility = View.GONE
        } else {
            createMatchText.visibility = View.VISIBLE
            if (pwd == confirm) {
                createMatchText.text = "Passwords match"
                createMatchText.setTextColor(WiFiVisionPalette.emeraldDeep)
            } else {
                createMatchText.text = "Passwords do not match"
                createMatchText.setTextColor(WiFiVisionPalette.crimsonText)
            }
        }
    }

    private fun startResendCooldown(seconds: Int) {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        resendSecondsLeft = seconds.coerceAtLeast(0)
        updateResendButton()
        if (resendSecondsLeft <= 0) return

        val tick = object : Runnable {
            override fun run() {
                resendSecondsLeft = (resendSecondsLeft - 1).coerceAtLeast(0)
                updateResendButton()
                if (resendSecondsLeft > 0) {
                    handler.postDelayed(this, 1000L)
                }
            }
        }
        countdownRunnable = tick
        handler.postDelayed(tick, 1000L)
    }

    private fun updateResendButton() {
        resendBtn.isEnabled = !isBusy && resendSecondsLeft <= 0
        resendBtn.text = if (resendSecondsLeft > 0) {
            "Resend Code in ${resendSecondsLeft}s"
        } else {
            "Resend Verification Code"
        }
    }

    private fun setBusy(busy: Boolean, loadingMessage: String? = null) {
        isBusy = busy
        signInSubmitBtn.isEnabled = !busy
        regSubmitBtn.isEnabled = !busy
        verifySubmitBtn.isEnabled = !busy
        createSubmitBtn.isEnabled = !busy
        urlApplyBtn.isEnabled = !busy
        updateResendButton()

        if (busy && !loadingMessage.isNullOrBlank()) {
            bannerProgress.visibility = View.VISIBLE
            setBanner(loadingMessage)
        } else {
            bannerProgress.visibility = View.GONE
        }
    }

    private fun setBanner(
        message: String,
        error: Boolean = false,
        warning: Boolean = false,
        success: Boolean = false
    ) {
        bannerText.text = message
        val (tc, bg, bc) = when {
            error -> Triple(WiFiVisionPalette.crimsonText, WiFiVisionPalette.crimsonBg, WiFiVisionPalette.crimsonBorder)
            warning -> Triple(WiFiVisionPalette.amberText, WiFiVisionPalette.amberBg, WiFiVisionPalette.amberBorder)
            success -> Triple(WiFiVisionPalette.emeraldDeep, WiFiVisionPalette.emeraldTint, WiFiVisionPalette.emeraldBorder)
            else -> Triple(WiFiVisionPalette.bodySlate, WiFiVisionPalette.softIvory, WiFiVisionPalette.hairline)
        }
        bannerText.setTextColor(tc)
        bannerBox.background = roundedDrawable(context, bg, bc, 10)
        bannerBox.visibility = View.VISIBLE
    }

    private fun hideBanner() {
        bannerProgress.visibility = View.GONE
        bannerBox.visibility = View.GONE
    }

    private fun applyPasswordInputType(editText: EditText, visible: Boolean) {
        val sel = editText.selectionEnd.coerceAtLeast(0)
        editText.inputType = if (visible) {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD
        } else {
            InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
        }
        editText.setSelection(minOf(sel, editText.text?.length ?: 0))
    }

    private fun showPrivacyDialog() {
        AlertDialog.Builder(context)
            .setTitle("WiFiVision Privacy Summary")
            .setMessage(
                "• Account credentials & session tokens are encrypted using Android Keystore AES-256-GCM.\n" +
                    "• Wi-Fi scanning and GPS fixes remain local until you explicitly confirm Upload Now.\n" +
                    "• Paired devices use scoped upload credentials stored separately from your account login.\n" +
                    "• Coordinates reflect your scanning device location, never router hardware locations."
            )
            .setPositiveButton("Close", null)
            .show()
    }

    private fun extractErrorMessage(result: ApiResult<*>): String = when (result) {
        is ApiResult.Unauthorized -> result.message
        is ApiResult.Forbidden -> result.message
        is ApiResult.RateLimited -> result.message
        is ApiResult.ClientError -> result.message
        is ApiResult.ServerError -> result.message
        is ApiResult.NetworkError -> result.message
        is ApiResult.Success -> result.message ?: "Completed"
    }

    private fun marginBottomParams(dpVal: Int): LinearLayout.LayoutParams {
        return LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = context.dp(dpVal)
        }
    }

    fun cleanup() {
        countdownRunnable?.let { handler.removeCallbacks(it) }
        countdownRunnable = null
        handler.removeCallbacksAndMessages(null)
    }
}
