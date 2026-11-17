import mongoose from "mongoose";

const deviceCredentialSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },

    deviceName: {
      type: String,
      trim: true,
      minlength: 1,
      maxlength: 60,
      default: "Android Companion",
    },

    platform: {
      type: String,
      enum: ["android", "windows"],
      default: "android",
      index: true,
    },

    secretHash: {
      type: String,
      required: true,
      select: false,
    },

    scopes: {
      type: [String],
      enum: ["observations:write"],
      default: ["observations:write"],
    },

    status: {
      type: String,
      enum: ["active", "revoked"],
      default: "active",
      index: true,
    },

    revokedAt: {
      type: Date,
      default: null,
    },

    expiresAt: {
      type: Date,
      required: true,
    },

    lastUsedAt: {
      type: Date,
      default: null,
    },
  },
  {
    timestamps: true,
    versionKey: false,
  }
);

deviceCredentialSchema.index({ userId: 1, status: 1, createdAt: -1 });

const DeviceCredential =
  mongoose.models.DeviceCredential ||
  mongoose.model("DeviceCredential", deviceCredentialSchema);

export default DeviceCredential;
