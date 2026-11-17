import { z } from "zod";

const emailSchema = z
  .string()
  .trim()
  .email("Please enter a valid email address")
  .max(254)
  .transform((email) => email.toLowerCase());

const passwordSchema = z
  .string()
  .min(8, "Password must contain at least 8 characters")
  .max(72, "Password cannot exceed 72 characters")
  .regex(/[A-Z]/, "Password must contain an uppercase letter")
  .regex(/[a-z]/, "Password must contain a lowercase letter")
  .regex(/[0-9]/, "Password must contain a number")
  .regex(
    /[^A-Za-z0-9]/,
    "Password must contain at least one special character"
  );

// Initial registration: name and email only
export const registrationStartSchema = z.object({
  name: z
    .string()
    .trim()
    .min(2, "Name must contain at least 2 characters")
    .max(60, "Name cannot exceed 60 characters"),

  email: emailSchema,
});

// Kept for compatibility with any existing code using registerSchema
export const registerSchema = registrationStartSchema.extend({
  password: passwordSchema,
});

// Login
export const loginSchema = z.object({
  email: emailSchema,
  password: z.string().min(1, "Password is required").max(72),
});

// Email verification
export const verifyEmailSchema = z.object({
  email: emailSchema,
  code: z
    .string()
    .trim()
    .regex(/^\d{6}$/, "Enter the 6-digit verification code"),
});

// Final password creation
export const createPasswordSchema = z.object({
  password: passwordSchema,
});

// Resend verification code
export const resendVerificationSchema = z.object({
  email: emailSchema,
});

// Forgot password
export const forgotPasswordSchema = z.object({
  email: emailSchema,
});

// Reset password
export const resetPasswordSchema = z.object({
  token: z.string().min(1, "Reset token is required"),
  password: passwordSchema,
});