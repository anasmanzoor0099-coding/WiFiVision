import jwt from "jsonwebtoken";
import mongoose from "mongoose";
import User from "../models/User.js";

const COOKIE_NAME = "wifivision_token";

export async function protect(req, res, next) {
  try {
    const token = req.cookies?.[COOKIE_NAME];

    if (!token) {
      return res.status(401).json({
        success: false,
        message: "Authentication required. Please log in.",
      });
    }

    if (!process.env.JWT_SECRET) {
      throw new Error("JWT_SECRET is not configured");
    }

    if (
      process.env.NODE_ENV === "production" &&
      process.env.JWT_SECRET.length < 32
    ) {
      throw new Error("JWT_SECRET must be at least 32 characters in production");
    }

    const decoded = jwt.verify(token, process.env.JWT_SECRET, {
      algorithms: ["HS256"],
      issuer: "wifivision",
      audience: "wifivision-users",
    });

    if (!decoded?.sub || !mongoose.Types.ObjectId.isValid(decoded.sub)) {
      return res.status(401).json({
        success: false,
        message: "Your session is invalid. Please log in again.",
      });
    }

    const user = await User.findById(decoded.sub).select(
      "_id name email isEmailVerified lastLoginAt tokenVersion"
    );

    if (!user) {
      return res.status(401).json({
        success: false,
        message: "Your session is no longer valid. Please log in again.",
      });
    }

    const tokenVersion =
      typeof decoded.tokenVersion === "number" ? decoded.tokenVersion : 0;
    if (tokenVersion !== (user.tokenVersion ?? 0)) {
      return res.status(401).json({
        success: false,
        message: "Your session has been revoked. Please log in again.",
      });
    }

    if (!user.isEmailVerified) {
      return res.status(403).json({
        success: false,
        message: "Please verify your email address before continuing.",
      });
    }

    req.user = user;

    return next();
  } catch (error) {
    if (
      error.name === "JsonWebTokenError" ||
      error.name === "TokenExpiredError"
    ) {
      return res.status(401).json({
        success: false,
        message: "Your session has expired. Please log in again.",
      });
    }

    return next(error);
  }
}