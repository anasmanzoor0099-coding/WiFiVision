import crypto from "node:crypto";
import bcrypt from "bcryptjs";
import User from "../models/User.js";
import {
  registrationStartSchema,
  verifyEmailSchema,
  createPasswordSchema,
  resendVerificationSchema,
  loginSchema,
} from "../validators/auth.validator.js";
import {
  generateVerificationCode,
  hashToken,
  generateAuthToken,
  getAuthCookieOptions,
} from "../utils/auth.utils.js";
import { sendVerificationEmail } from "../services/email.service.js";

const COOKIE_NAME = "wifivision_token";
const SETUP_COOKIE_NAME = "wifivision_setup_token";

const OTP_EXPIRY_MS = 10 * 60 * 1000;
const OTP_RESEND_COOLDOWN_MS = 60 * 1000;
const PASSWORD_SETUP_EXPIRY_MS = 15 * 60 * 1000;
const MAX_OTP_ATTEMPTS = 5;

const normalizeEmail = (email) =>
  String(email || "").trim().toLowerCase();

const getSetupCookieOptions = () => ({
  httpOnly: true,
  secure: process.env.NODE_ENV === "production",
  sameSite: "strict",
  path: "/",
  maxAge: PASSWORD_SETUP_EXPIRY_MS,
});

const clearSetupCookie = (res) => {
  const options = getSetupCookieOptions();
  delete options.maxAge;
  res.clearCookie(SETUP_COOKIE_NAME, options);
};

const issueVerificationCode = async (user, email) => {
  const code = generateVerificationCode();
  const isNewUser = user.isNew;

  user.emailVerificationToken = hashToken(code);
  user.emailVerificationExpires = new Date(
    Date.now() + OTP_EXPIRY_MS
  );
  user.emailVerificationLastSentAt = new Date();
  user.emailVerificationAttempts = 0;

  await user.save();

  try {
    await sendVerificationEmail(email, code);
  } catch (error) {
    if (isNewUser) {
      await User.deleteOne({ _id: user._id, isEmailVerified: false });
    } else {
      user.emailVerificationToken = undefined;
      user.emailVerificationExpires = undefined;
      user.emailVerificationLastSentAt = null;
      user.emailVerificationAttempts = 0;
      await user.save();
    }

    throw error;
  }
};

// STEP 1: Start registration and send OTP
export const startRegistration = async (req, res, next) => {
  try {
    const validation = registrationStartSchema.safeParse(req.body || {});

    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Please provide a valid name and email address.",
      });
    }

    const cleanName = validation.data.name;
    const cleanEmail = validation.data.email;

    let user = await User.findOne({ email: cleanEmail }).select(
      "+emailVerificationToken +emailVerificationExpires +emailVerificationLastSentAt"
    );

    if (user?.isEmailVerified) {
      return res.status(409).json({
        success: false,
        message: "An account with this email already exists. Please log in.",
      });
    }

    if (!user) {
      user = new User({
        name: cleanName,
        email: cleanEmail,
        isEmailVerified: false,
      });
    } else {
      user.name = cleanName;
    }

    const lastSentAt = user.emailVerificationLastSentAt?.getTime();

    if (
      lastSentAt &&
      Date.now() - lastSentAt < OTP_RESEND_COOLDOWN_MS
    ) {
      return res.status(429).json({
        success: false,
        message: "Please wait before requesting another verification code.",
        retryAfter: Math.ceil(
          (OTP_RESEND_COOLDOWN_MS - (Date.now() - lastSentAt)) / 1000
        ),
      });
    }

    await issueVerificationCode(user, cleanEmail);

    return res.status(200).json({
      success: true,
      message: "Verification code sent successfully.",
      email: cleanEmail,
      name: cleanName,
    });
  } catch (error) {
    next(error);
  }
};

// STEP 2: Verify OTP and create a temporary password-setup session
export const verifyRegistrationCode = async (req, res, next) => {
  try {
    const validation = verifyEmailSchema.safeParse(req.body || {});

    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Please enter a valid email and verification code.",
      });
    }

    const { email: cleanEmail, code: cleanCode } = validation.data;

    const user = await User.findOne({
      email: cleanEmail,
      isEmailVerified: false,
    }).select(
      "+emailVerificationToken +emailVerificationExpires +emailVerificationAttempts"
    );

    if (
      !user ||
      !user.emailVerificationToken ||
      !user.emailVerificationExpires ||
      user.emailVerificationExpires.getTime() <= Date.now()
    ) {
      return res.status(400).json({
        success: false,
        message: "The verification code is invalid or has expired.",
      });
    }

    const submittedHash = hashToken(cleanCode);

    const submittedBuffer = Buffer.from(submittedHash);
    const storedBuffer = Buffer.from(user.emailVerificationToken);

    const matches =
      submittedBuffer.length === storedBuffer.length &&
      crypto.timingSafeEqual(submittedBuffer, storedBuffer);

    if (!matches) {
      const nextAttempts = (user.emailVerificationAttempts || 0) + 1;

      if (nextAttempts >= MAX_OTP_ATTEMPTS) {
        user.emailVerificationToken = undefined;
        user.emailVerificationExpires = undefined;
        user.emailVerificationAttempts = 0;
        await user.save();

        return res.status(429).json({
          success: false,
          message:
            "Too many incorrect verification attempts. Please request a new verification code.",
        });
      }

      user.emailVerificationAttempts = nextAttempts;
      await user.save();

      return res.status(400).json({
        success: false,
        message: "The verification code is incorrect.",
      });
    }

    const setupToken = crypto.randomBytes(32).toString("hex");

    user.emailVerificationToken = undefined;
    user.emailVerificationExpires = undefined;
    user.emailVerificationLastSentAt = null;
    user.emailVerificationAttempts = 0;

    user.passwordSetupToken = hashToken(setupToken);
    user.passwordSetupExpires = new Date(
      Date.now() + PASSWORD_SETUP_EXPIRY_MS
    );

    await user.save();

    res.cookie(
      SETUP_COOKIE_NAME,
      setupToken,
      getSetupCookieOptions()
    );

    return res.status(200).json({
      success: true,
      message: "Email verified. You can now create your password.",
      user: {
        name: user.name,
        email: user.email,
      },
    });
  } catch (error) {
    next(error);
  }
};

// STEP 3: Return the verified user's details for the password page
export const getPasswordSetup = async (req, res, next) => {
  try {
    const setupToken = req.cookies?.[SETUP_COOKIE_NAME];

    if (!setupToken) {
      return res.status(401).json({
        success: false,
        message: "Your password-setup session is missing or expired.",
      });
    }

    const user = await User.findOne({
      passwordSetupToken: hashToken(setupToken),
      passwordSetupExpires: { $gt: new Date() },
      isEmailVerified: false,
    }).select("+passwordSetupToken +passwordSetupExpires");

    if (!user) {
      clearSetupCookie(res);

      return res.status(401).json({
        success: false,
        message: "Your password-setup session is invalid or expired.",
      });
    }

    return res.status(200).json({
      success: true,
      user: {
        name: user.name,
        email: user.email,
      },
    });
  } catch (error) {
    next(error);
  }
};

// STEP 4: Save password and complete registration
export const createAccountPassword = async (req, res, next) => {
  try {
    const setupToken = req.cookies?.[SETUP_COOKIE_NAME];

    if (!setupToken) {
      return res.status(401).json({
        success: false,
        message: "Your password-setup session is missing or expired.",
      });
    }

    const validation = createPasswordSchema.safeParse(req.body || {});

    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message:
          "Password must be 8-72 characters and contain uppercase and lowercase letters, a number, and a special character.",
      });
    }

    const { password } = validation.data;

    const user = await User.findOne({
      passwordSetupToken: hashToken(setupToken),
      passwordSetupExpires: { $gt: new Date() },
      isEmailVerified: false,
    }).select("+passwordSetupToken +passwordSetupExpires");

    if (!user) {
      clearSetupCookie(res);

      return res.status(401).json({
        success: false,
        message: "Your password-setup session is invalid or expired.",
      });
    }

    user.password = await bcrypt.hash(password, 12);
    user.isEmailVerified = true;

    user.passwordSetupToken = undefined;
    user.passwordSetupExpires = undefined;

    await user.save();

    clearSetupCookie(res);

    return res.status(201).json({
      success: true,
      message: "Your account has been created successfully. You can now log in.",
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        isEmailVerified: user.isEmailVerified,
      },
    });
  } catch (error) {
    next(error);
  }
};

// RESEND OTP
export const resendVerificationCode = async (req, res, next) => {
  try {
    const validation = resendVerificationSchema.safeParse(req.body || {});

    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Please provide a valid email address.",
      });
    }

    const cleanEmail = validation.data.email;

    const user = await User.findOne({
      email: cleanEmail,
      isEmailVerified: false,
    }).select(
      "+emailVerificationToken +emailVerificationExpires +emailVerificationLastSentAt"
    );

    // Avoid revealing whether an email is registered.
    if (!user) {
      return res.status(200).json({
        success: true,
        message: "If the account is eligible, a new code will be sent.",
      });
    }

    const lastSentAt = user.emailVerificationLastSentAt?.getTime();

    if (
      lastSentAt &&
      Date.now() - lastSentAt < OTP_RESEND_COOLDOWN_MS
    ) {
      return res.status(429).json({
        success: false,
        message: "Please wait before requesting another code.",
        retryAfter: Math.ceil(
          (OTP_RESEND_COOLDOWN_MS - (Date.now() - lastSentAt)) / 1000
        ),
      });
    }

    await issueVerificationCode(user, cleanEmail);

    return res.status(200).json({
      success: true,
      message: "A new verification code has been sent.",
    });
  } catch (error) {
    next(error);
  }
};

// LOGIN
export const loginUser = async (req, res, next) => {
  try {
    const validation = loginSchema.safeParse(req.body);

    if (!validation.success) {
      return res.status(400).json({
        success: false,
        message: "Please provide valid login details.",
      });
    }

    const email = normalizeEmail(validation.data.email);

    const user = await User.findOne({ email }).select("+password");

    if (!user || !user.password) {
      return res.status(401).json({
        success: false,
        message: "Invalid email or password.",
      });
    }

    if (!user.isEmailVerified) {
      return res.status(403).json({
        success: false,
        message: "Please complete email verification and account setup first.",
      });
    }

    const passwordMatches = await bcrypt.compare(
      validation.data.password,
      user.password
    );

    if (!passwordMatches) {
      return res.status(401).json({
        success: false,
        message: "Invalid email or password.",
      });
    }

    user.lastLoginAt = new Date();
    await user.save();

    const token = generateAuthToken(user);

    res.cookie(COOKIE_NAME, token, getAuthCookieOptions());

    return res.status(200).json({
      success: true,
      message: "Login successful.",
      user: {
        id: user._id,
        name: user.name,
        email: user.email,
        isEmailVerified: user.isEmailVerified,
      },
    });
  } catch (error) {
    next(error);
  }
};

// LOGOUT
export const logoutUser = async (req, res, next) => {
  try {
    const options = getAuthCookieOptions();
    delete options.maxAge;

    res.clearCookie(COOKIE_NAME, options);
    clearSetupCookie(res);

    return res.status(200).json({
      success: true,
      message: "Logged out successfully.",
    });
  } catch (error) {
    next(error);
  }
};

// LOGOUT ALL SESSIONS (increments tokenVersion to revoke all issued JWTs)
export const logoutAllSessions = async (req, res, next) => {
  try {
    if (req.user?._id) {
      await User.updateOne(
        { _id: req.user._id },
        { $inc: { tokenVersion: 1 } }
      );
    }

    const options = getAuthCookieOptions();
    delete options.maxAge;

    res.clearCookie(COOKIE_NAME, options);
    clearSetupCookie(res);

    return res.status(200).json({
      success: true,
      message: "Signed out of all active sessions.",
    });
  } catch (error) {
    next(error);
  }
};

// CURRENT USER
export const getCurrentUser = async (req, res, next) => {
  try {
    if (!req.user?._id) {
      return res.status(401).json({
        success: false,
        message: "Authentication required.",
      });
    }

    return res.status(200).json({
      success: true,
      user: {
        id: req.user._id,
        name: req.user.name,
        email: req.user.email,
        isEmailVerified: req.user.isEmailVerified,
      },
    });
  } catch (error) {
    next(error);
  }
};