import mongoose from "mongoose";

const pairingCodeSchema = new mongoose.Schema(
  {
    userId: {
      type: mongoose.Schema.Types.ObjectId,
      ref: "User",
      required: true,
      index: true,
    },

    codeHash: {
      type: String,
      required: true,
      unique: true,
    },

    expiresAt: {
      type: Date,
      required: true,
    },

    consumedAt: {
      type: Date,
      default: null,
    },

    platform: {
      type: String,
      enum: ["android", "windows", "any"],
      default: "any",
    },
  },
  {
    timestamps: { createdAt: true, updatedAt: false },
    versionKey: false,
  }
);

pairingCodeSchema.index({ expiresAt: 1 }, { expireAfterSeconds: 0 });
pairingCodeSchema.index({ userId: 1, consumedAt: 1, expiresAt: -1 });

const PairingCode =
  mongoose.models.PairingCode ||
  mongoose.model("PairingCode", pairingCodeSchema);

export default PairingCode;
