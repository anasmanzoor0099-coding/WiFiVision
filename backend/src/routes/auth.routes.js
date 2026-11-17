import express from "express";

import {
  startRegistration,
  verifyRegistrationCode,
  getPasswordSetup,
  createAccountPassword,
  resendVerificationCode,
  loginUser,
  logoutUser,
  logoutAllSessions,
  getCurrentUser,
} from "../controllers/auth.controller.js";

import { protect } from "../middleware/auth.middleware.js";
import { createDistributedLimiter } from "../utils/rateLimiter.js";

const router = express.Router();

const authLimiter = createDistributedLimiter({
  name: "auth-general",
  windowMs: 15 * 60 * 1000,
  limit: 10,
  message: "Too many authentication attempts. Please try again later.",
});

const registrationLimiter = createDistributedLimiter({
  name: "auth-registration",
  windowMs: 15 * 60 * 1000,
  limit: 5,
  message: "Too many registration attempts. Please try again later.",
});

const resendOtpLimiter = createDistributedLimiter({
  name: "auth-resend-otp",
  windowMs: 15 * 60 * 1000,
  limit: 5,
  message: "Too many verification code requests. Please try again later.",
});

// Registration: name and email
router.post(
  "/register/start",
  registrationLimiter,
  startRegistration
);

// Registration: verify six-digit OTP
router.post(
  "/register/verify",
  authLimiter,
  verifyRegistrationCode
);

// Registration: retrieve details using temporary setup cookie
router.get(
  "/register/password-setup",
  authLimiter,
  getPasswordSetup
);

// Registration: create password and complete account
router.post(
  "/register/create-password",
  authLimiter,
  createAccountPassword
);

// Resend verification code
router.post(
  "/resend-code",
  resendOtpLimiter,
  resendVerificationCode
);

// Login and account management
router.post("/login", authLimiter, loginUser);

router.post("/logout", protect, logoutUser);

router.post("/logout-all", protect, authLimiter, logoutAllSessions);

router.get("/me", protect, getCurrentUser);

// Authentication router test
router.get("/test", (req, res) => {
  res.status(200).json({
    success: true,
    message: "WiFiVision authentication router is working.",
  });
});

export default router;