import nodemailer from "nodemailer";

let cachedTransporter = null;
let cachedConfigKey = null;

function getSmtpConfig() {
  const host = (process.env.SMTP_HOST || "smtp.gmail.com").trim();
  const parsedPort = Number(String(process.env.SMTP_PORT || "").trim());
  const port = Number.isInteger(parsedPort) && parsedPort > 0 ? parsedPort : 465;
  const secure =
    process.env.SMTP_SECURE !== undefined
      ? String(process.env.SMTP_SECURE).trim().toLowerCase() === "true"
      : port === 465;
  const user = process.env.SMTP_USER?.trim();
  const pass = process.env.SMTP_PASS?.trim();

  return { host, port, secure, user, pass };
}

function getTransporter(config) {
  const configKey = `${config.host}:${config.port}:${config.secure}:${config.user}`;

  if (!cachedTransporter || cachedConfigKey !== configKey) {
    cachedTransporter = nodemailer.createTransport({
      host: config.host,
      port: config.port,
      secure: config.secure,
      connectionTimeout: 8000,
      greetingTimeout: 8000,
      socketTimeout: 10000,
      auth: {
        user: config.user,
        pass: config.pass,
      },
    });
    cachedConfigKey = configKey;
  }

  return cachedTransporter;
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
    missingErr.statusCode = 503;
    throw missingErr;
  }

  const transporter = getTransporter(config);

  const mailOptions = {
    from: `"WiFiVision Security" <${config.user}>`,
    to: email,
    subject: "Verify your WiFiVision account",
    text: `Your WiFiVision verification code is ${code}. It expires in 10 minutes. Do not share this code with anyone.`,
    html: `
      <div style="margin:0;padding:35px 15px;background:#080d16;font-family:Arial,sans-serif;color:#e2e8f0;">
        <div style="max-width:520px;margin:auto;padding:35px;background:#101927;border:1px solid #26364a;border-radius:16px;">
          <h1 style="margin:0;color:#22d3ee;font-size:27px;">WiFiVision</h1>
          <p style="color:#94a3b8;font-size:12px;letter-spacing:2px;">NETWORK INTELLIGENCE</p>

          <h2 style="margin-top:35px;color:#ffffff;">Verify your email address</h2>

          <p style="color:#cbd5e1;line-height:1.7;">
            Thank you for creating your WiFiVision account.
            Enter the verification code below to confirm your email address.
          </p>

          <div style="margin:30px 0;padding:20px;text-align:center;background:#080d16;border:1px solid #22d3ee;border-radius:12px;">
            <span style="font-size:32px;font-weight:bold;letter-spacing:9px;color:#22d3ee;">
              ${code}
            </span>
          </div>

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

  try {
    return await transporter.sendMail(mailOptions);
  } catch (error) {
    console.error("Verification email delivery failed:", {
      code: error?.code || "SMTP_ERROR",
      responseCode: error?.responseCode || null,
      command: error?.command || null,
      message: error?.message || "Unknown SMTP error",
    });
    const deliveryErr = new Error(
      "Verification email service is temporarily unavailable."
    );
    deliveryErr.code = "EMAIL_UNAVAILABLE";
    deliveryErr.statusCode = 503;
    throw deliveryErr;
  }
}