const API_URL = process.env.NEXT_PUBLIC_API_URL;

export interface AuthUser {
  id: string;
  name: string;
  email: string;
  isEmailVerified: boolean;
}

export interface ApiResponse {
  success: boolean;
  message: string;
  user?: AuthUser;
  email?: string;
  name?: string;
  retryAfter?: number;
  retryAfterSeconds?: number;
  retryAfterSecondsNormalized?: number;
  resendCooldown?:number;
}

interface RegistrationData {
  name: string;
  email: string;
}

interface LoginData {
  email: string;
  password: string;
}

interface VerifyEmailData {
  email: string;
  code: string;
}

interface CreatePasswordData {
  password: string;
}

interface ResendVerificationData {
  email: string;
}

async function authRequest<T extends ApiResponse>(
  endpoint: string,
  options: RequestInit = {}
): Promise<T> {
  if (!API_URL) {
    throw new Error("Frontend API URL is not configured.");
  }

  const response = await fetch(
    `${API_URL.replace(/\/+$/, "")}/auth${endpoint}`,
    {
      ...options,
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        ...options.headers,
      },
    }
  );

  const data = await response.json().catch(() => null);

  if (!response.ok || !data?.success) {
    const error = new Error(
      data?.message || "Something went wrong. Please try again."
    ) as Error & {
      retryAfterSeconds?: number;
    };

    error.retryAfterSeconds =
      data?.retryAfterSeconds ?? data?.retryAfter;

    throw error;
  }

  return data as T;
}

// STEP 1: Start registration
export async function startRegistration(data: RegistrationData) {
  return authRequest<ApiResponse>("/register/start", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// STEP 2: Verify registration OTP
export async function verifyRegistrationCode(data: VerifyEmailData) {
  return authRequest<ApiResponse>("/register/verify", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// STEP 3: Retrieve account details using setup cookie
export async function getPasswordSetup() {
  return authRequest<ApiResponse>("/register/password-setup", {
    method: "GET",
  });
}

// STEP 4: Create account password
export async function createAccountPassword(
  data: CreatePasswordData
) {
  return authRequest<ApiResponse>("/register/create-password", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// RESEND VERIFICATION CODE
export async function resendVerificationCode(
  data: ResendVerificationData
) {
  return authRequest<ApiResponse>("/resend-code", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// LOGIN
export async function loginUser(data: LoginData) {
  return authRequest<ApiResponse>("/login", {
    method: "POST",
    body: JSON.stringify(data),
  });
}

// LOGOUT
export async function logoutUser() {
  return authRequest<ApiResponse>("/logout", {
    method: "POST",
  });
}

// LOGOUT ALL SESSIONS (revokes active JWTs via tokenVersion)
export async function logoutAllSessions() {
  return authRequest<ApiResponse>("/logout-all", {
    method: "POST",
  });
}

// CURRENT USER
export async function getCurrentUser() {
  return authRequest<ApiResponse>("/me", {
    method: "GET",
  });
}

// Backward-compatible verification function
export async function verifyEmail(data: VerifyEmailData) {
  return verifyRegistrationCode(data);
}