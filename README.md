# WorkAlert

Android app that pulls employee checklists from your web service and reminds staff on WhatsApp about pending and overdue work.

## Get the APK

Every push builds the app on GitHub:

1. Open the **Actions** tab → latest **Android build** run.
2. Download **WorkAlert-debug-apk** under *Artifacts*, unzip, and install `app-debug.apk` on the phone (allow "Install unknown apps").

## Features

- **Web Service Sync** – set the URL, GET/POST and Authorization header; tasks, items and employees are merged in.
- **Background auto-sync** – every 15 / 30 / 60 min while the phone has internet (Android's minimum is 15 min).
- **Overdue from the clock** – a task is overdue once its due date/time has passed (`Today`, `Tomorrow`, `2026-11-02`, `02/11/2026`, `09:30 AM`, `18:00` are understood). If the date can't be read, the server's `is_overdue` is used.
- **WhatsApp reminders**
  - *Manual*: opens WhatsApp with the message filled in; you press Send. Logged as "Opened in WhatsApp".
  - *Automatic*: set a **Webhook URL** (EasySocial or any WhatsApp API relay) and switch on **Auto-send overdue reminders**. After each sync the app POSTs `{phone, message, task_title, timestamp}` with `Authorization: Bearer <key>` and `x-api-key: <key>`, at most once per task per 1/2/4 h.
- **Staff directory and reminder history.**

## Web service JSON

An array, or an object with `checklists` / `tasks` / `data` / `items` / `pending`:

```json
[{
  "id": "chk_101",
  "title": "Morning Store Opening",
  "employee_name": "Rahul Sharma",
  "employee_phone": "+91 98765 43210",
  "department": "Store Operations",
  "due_date": "Today",
  "due_time": "09:30 AM",
  "status": "PENDING",
  "priority": "HIGH",
  "items": ["AC check", {"title": "Cash float", "done": true}]
}]
```

## Build locally

Needs JDK 17+, Gradle 9.3.1 and the Android SDK (platform 36.1).

```bash
# AI Studio provides debug.keystore; outside it, create one once:
keytool -genkeypair -keystore debug.keystore -storepass android -keypass android \
  -alias androiddebugkey -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
gradle testDebugUnitTest assembleDebug   # or run `gradle wrapper` once, then ./gradlew
```

The project is also edited in Google AI Studio, which syncs to `main`.
