# Keyword Record - Mobile Keyboard with MongoDB Logging

A custom **Android mobile keyboard** that records everything you type and saves each keystroke to **MongoDB** with a **unique record ID**.

## Project Structure

```
Keyword_record/
├── backend/              # Node.js API + web dashboard → MongoDB
└── android-keyboard/     # Android IME keyboard app
```

## How It Works

1. You install and enable the Android keyboard.
2. Every key press (letters, numbers, space, delete, enter) is sent to the backend API.
3. The backend saves each event in MongoDB with:
   - **recordId** — unique UUID for every keystroke record
   - **deviceUniqueId** — persistent ID for your phone/installation
   - **keyPressed**, **fullText**, **appPackage**, **action**, **timestamp**

## Backend Setup

### Requirements
- Node.js 18+

### Run the server

```bash
cd backend
npm install
npm start
```

Server runs at `http://localhost:3000`.

**Web dashboard:** open `http://localhost:3000` in your browser to view all typed records, filter by device, and search text.

MongoDB connection is configured in `backend/.env` (already set with your cluster URL).

### API Endpoints

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/` | Web dashboard |
| GET | `/health` | Check if server is running |
| POST | `/api/keystrokes` | Save a keystroke record |
| GET | `/api/records` | Get records (optional `deviceUniqueId`, `limit`) |
| GET | `/api/devices` | List devices with record counts |
| GET | `/api/keystrokes/:deviceUniqueId` | Get records for a device |

### Example record in MongoDB

```json
{
  "recordId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "deviceUniqueId": "phone-uuid-stored-on-device",
  "keyPressed": "h",
  "fullText": "hello",
  "appPackage": "com.whatsapp",
  "action": "key",
  "createdAt": "2026-06-13T10:30:00.000Z"
}
```

## Android Keyboard Setup

### Requirements
- Android Studio (Ladybug or newer)
- Android phone or emulator (API 24+)

### Build & install

**Option A — Android Studio (recommended)**

1. Open the `android-keyboard` folder in **Android Studio**.
2. Wait for Gradle sync to finish.
3. Connect your phone (USB debugging) or start an emulator.
4. Click **Run** to install the app.

**Option B — Build APK from command line**

```powershell
cd android-keyboard
.\build-apk.ps1
```

APK output: `android-keyboard/app/build/outputs/apk/debug/app-debug.apk`

Copy this file to your phone and install it (enable "Install unknown apps" if needed).

### Keyboard features

- **ABC** — letters and numbers (QWERTY mobile layout)
- **123#** — symbols like `! @ # $ % & * ( )`
- **😀** — emoji panel
- Space, delete, enter, shift — all recorded to MongoDB

### Enable the keyboard

1. Start the backend server on your PC.
2. Open the **Keyword Record Keyboard** app on your phone.
3. Set the **Backend Server URL**:
   - **Emulator:** `http://10.0.2.2:3000`
   - **Real phone:** `http://YOUR_PC_IP:3000` (e.g. `http://192.168.1.5:3000`)
4. Go to **Settings → System → Languages & input → On-screen keyboard**.
5. Enable **Keyword Record Keyboard**.
6. Switch to this keyboard when typing in any app.

## Security Note

Your MongoDB credentials are in `backend/.env`. Do **not** share this file or commit it to public repos. For production, rotate your database password and use environment variables on a hosted server.

## Viewing Your Data

**Web dashboard:** `http://localhost:3000`

In [MongoDB Atlas](https://cloud.mongodb.com):
- Database: `keyword_record`
- Collection: `keystrokerecords`

Or call: `GET http://localhost:3000/api/keystrokes/YOUR_DEVICE_UNIQUE_ID`
