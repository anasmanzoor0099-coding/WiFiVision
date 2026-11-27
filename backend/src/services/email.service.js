import nodemailer from "nodemailer";

let cachedTransporter = null;
let cachedConfigKey = null;

function cleanEnvValue(raw) {
  if (typeof raw !== "string") return "";
  return raw.trim().replace(/^["']+|["']+$/g, "").trim();
}

export function getSmtpConfig() {
  const host =
    cleanEnvValue(
      process.env.SMTP_HOST ||
        process.env.EMAIL_HOST ||
        process.env.MAIL_HOST
    ) || "smtp.gmail.com";

  const rawPort = cleanEnvValue(
    process.env.SMTP_PORT ||
      process.env.EMAIL_PORT ||
      process.env.MAIL_PORT
  );
  const parsedPort = Number(rawPort);
  const port =
    Number.isInteger(parsedPort) && parsedPort > 0 ? parsedPort : 465;

  const rawSecure = cleanEnvValue(
    process.env.SMTP_SECURE ?? process.env.EMAIL_SECURE ?? ""
  ).toLowerCase();
  const secure =
    rawSecure === "true"
      ? true
      : rawSecure === "false"
        ? false
        : port === 465;

  const user =
    cleanEnvValue(
      process.env.SMTP_USER ||
        process.env.EMAIL_USER ||
        process.env.GMAIL_USER ||
        process.env.MAIL_USER ||
        process.env.SMTP_USERNAME
    ) || undefined;

  let pass =
    cleanEnvValue(
      process.env.SMTP_PASS ||
        process.env.EMAIL_PASS ||
        process.env.GMAIL_PASS ||
        process.env.GMAIL_APP_PASSWORD ||
        process.env.MAIL_PASS ||
        process.env.SMTP_PASSWORD
    ) || undefined;

  // Gmail 16-character App Passwords are often copied in 4-char blocks with spaces ("xxxx xxxx xxxx xxxx").
  if (pass && /gmail\.com$/i.test(host) && /^[a-zA-Z]{4}(\s+[a-zA-Z]{4}){3}$/.test(pass)) {
    pass = pass.replace(/\s+/g, "");
  }

  const fromAddress =
    cleanEnvValue(process.env.SMTP_FROM || process.env.EMAIL_FROM) ||
    (user ? `"WiFiVision Security" <${user}>` : undefined);

  const frontendBaseUrl =
    cleanEnvValue(process.env.FRONTEND_URL) ||
    cleanEnvValue((process.env.CORS_ORIGIN || "").split(",")[0]) ||
    "https://wi-fi-vision-d6kt-theta.vercel.app";

  return {
    host,
    port,
    secure,
    user,
    pass,
    fromAddress,
    frontendBaseUrl: frontendBaseUrl.replace(/\/+$/, ""),
  };
}

export function getEmailServiceDiagnostics() {
  const config = getSmtpConfig();
  const missingVars = [];
  if (!config.user) missingVars.push("SMTP_USER");
  if (!config.pass) missingVars.push("SMTP_PASS");

  return {
    configured: missingVars.length === 0,
    host: config.host,
    port: config.port,
    secure: config.secure,
    missingVars,
  };
}

function buildTransportOptions(config, portOverride = null, secureOverride = null) {
  const port = portOverride ?? config.port;
  const secure = secureOverride ?? config.secure;
  return {
    host: config.host,
    port,
    secure,
    requireTLS: !secure && port === 587,
    connectionTimeout: 8000,
    greetingTimeout: 8000,
    socketTimeout: 10000,
    auth: {
      user: config.user,
      pass: config.pass,
    },
  };
}

function getTransporter(config) {
  const configKey = `${config.host}:${config.port}:${config.secure}:${config.user}:${config.pass ? config.pass.length : 0}`;

  if (!cachedTransporter || cachedConfigKey !== configKey) {
    cachedTransporter = nodemailer.createTransport(buildTransportOptions(config));
    cachedConfigKey = configKey;
  }

  return cachedTransporter;
}

export async function verifyEmailServiceConnection() {
  const config = getSmtpConfig();
  const diag = getEmailServiceDiagnostics();

  if (!diag.configured) {
    return {
      ...diag,
      smtpVerified: false,
      reason: "SMTP_NOT_CONFIGURED",
      errorCode: "SMTP_NOT_CONFIGURED",
      smtpResponseCode: null,
    };
  }

  try {
    const transporter = nodemailer.createTransport(buildTransportOptions(config));
    await transporter.verify();
    return {
      ...diag,
      smtpVerified: true,
      reason: "OK",
      errorCode: null,
      smtpResponseCode: 250,
    };
  } catch (error) {
    const isAuthError =
      error?.code === "EAUTH" || error?.responseCode === 535;
    return {
      ...diag,
      smtpVerified: false,
      reason: isAuthError ? "SMTP_AUTH_FAILED" : "SMTP_CONNECTION_FAILED",
      errorCode: error?.code || "SMTP_ERROR",
      smtpResponseCode: error?.responseCode || null,
    };
  }
}

export async function sendVerificationEmail(email, code) {
  const config = getSmtpConfig();
  const isTest = process.env.NODE_ENV === "test";
  const isProduction = process.env.NODE_ENV === "production";

  if (isTest) {
    return {
      accepted: [email],
      fallback: true,
      messageId: "test-fallback",
    };
  }

  if (!config.user || !config.pass) {
    if (!isProduction) {
      console.warn(
        `SMTP credentials not configured; skipped sending verification email to ${email}.`
      );
      return {
        accepted: [email],
        fallback: true,
        messageId: "dev-fallback",
      };
    }

    const missingErr = new Error("Email service is not configured");
    missingErr.code = "EMAIL_UNAVAILABLE";
    missingErr.reason = "SMTP_NOT_CONFIGURED";
    missingErr.statusCode = 503;
    throw missingErr;
  }

  const verifyUrl = `${config.frontendBaseUrl}/verify-email?email=${encodeURIComponent(email)}`;
  const mailOptions = {
    from: config.fromAddress || `"WiFiVision Security" <${config.user}>`,
    to: email,
    subject: "Verify your WiFiVision account",
    text: `Your WiFiVision verification code is ${code}. It expires in 10 minutes. Verify at: ${verifyUrl} — Do not share this code with anyone.`,
    html: `
      <div style="margin:0;padding:35px 15px;background:#080d16;font-family:Arial,sans-serif;color:#e2e8f0;">
        <div style="max-width:520px;margin:auto;padding:35px;background:#101927;border:1px solid #26364a;border-radius:16px;">
          <h1 style="margin:0;color:#22d3ee;font-size:27px;">WiFiVision</h1>
          <p style="color:#94a3b8;font-size:12px;letter-spacing:2px;">NETWORK INTELLIGENCE</p>

          <h2 style="margin-top:35px;color:#ffffff;">Verify your email address</h2>

          <p style="color:#cbd5e1;line-height:1.7;">
            Thank you for creating your WiFiVision account.
            Enter the 6-digit verification code below to confirm your email address.
          </p>

          <div style="margin:30px 0;padding:20px;text-align:center;background:#080d16;border:1px solid #22d3ee;border-radius:12px;">
            <span style="font-size:32px;font-weight:bold;letter-spacing:9px;color:#22d3ee;">
              ${code}
            </span>
          </div>

          <p style="margin:20px 0;text-align:center;">
            <a href="${verifyUrl}" style="display:inline-block;padding:12px 22px;background:#2E7D5B;color:#ffffff;text-decoration:none;border-radius:10px;font-weight:bold;font-size:14px;">
              Open Verification Page
            </a>
          </p>

          <p style="color:#94a3b8;font-size:14px;">
            This code expires in <strong>10 minutes</strong>.
            Do not share it with anyone.
          </p>

          <hr style="border:0;border-top:1px solid #26364a;margin:30px 0;" />

          <p style="color:#64748b;font-size:12px;">
            If you did not request this verification, you can safely ignore this email.
          </p>
          <p style="color:#64748b;font-size:12px;">© WiFiVision — Network Intelligence</p>
        </div>
      </div>
    `,
  };

  const transporter = getTransporter(config);

  try {
    return await transporter.sendMail(mailOptions);
  } catch (primaryError) {
    cachedTransporter = null;
    cachedConfigKey = null;

    const isAuthError =
      primaryError?.code === "EAUTH" || primaryError?.responseCode === 535;

    // If port 465 timed out or reset (not an auth rejection), attempt STARTTLS fallback on port 587 once.
    if (!isAuthError && config.port === 465) {
      try {
        const fallbackTransporter = nodemailer.createTransport(
          buildTransportOptions(config, 587, false)
        );
        return await fallbackTransporter.sendMail(mailOptions);
      } catch {
        // Proceed to log and throw the primary error below
      }
    }

    console.error("Verification email delivery failed:", {
      code: primaryError?.code || "SMTP_ERROR",
      responseCode: primaryError?.responseCode || null,
      command: primaryError?.command || null,
      message: primaryError?.message || "Unknown SMTP error",
    });

    const deliveryErr = new Error(
      "Verification email service is temporarily unavailable."
    );
    deliveryErr.code = "EMAIL_UNAVAILABLE";
    deliveryErr.reason = isAuthError
      ? "SMTP_AUTH_FAILED"
      : "SMTP_CONNECTION_FAILED";
    deliveryErr.statusCode = 503;
    throw deliveryErr;
  }
}