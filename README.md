# Kharcha — your money discipline companion

An Android app that tracks every PhonePe / UPI payment automatically, files it into a monthly
expense list, and tells you where you're overspending and where you can save.

## What it does

| | |
|---|---|
| **Auto-tracks PhonePe payments** | Reads your bank's transaction SMS (every UPI payment triggers one) and PhonePe's payment notifications. When both arrive for the same payment they are merged into one entry. |
| **Monthly expense list** | Every transaction, grouped by day, searchable and filterable by category. Edit, add notes, add cash spends manually, export to CSV (opens in Sheets / Excel). |
| **Bank balance** | Picked up automatically from the "Avl Bal" in bank SMS, across all your accounts. You can also add/edit accounts by hand. |
| **Where you spend more** | Category breakdown, month-vs-month changes, top merchants, 6-month trend, daily spend chart. |
| **Where you can save** | Action plan with rupee amounts: budget overshoot projection, food-delivery habit, small-spend leaks, subscriptions, late-night & weekend splurges, 50/30/20 check. |
| **Savings** | Savings rate, savings goals with "save ₹X/month to hit it by …", investments tracked separately from spending. |
| **Discipline score** | 0–100 score from budget pace, savings rate, wants control and no-spend days. |
| **Safe to spend today** | Your remaining budget divided by the days left, recalculated after every payment. |
| **Budget alerts** | Notification the moment a payment pushes a budget past 80% or 100%. |
| **Learns your categories** | Re-categorise a merchant once and every future payment there is filed the same way. |

## Install it

1. Open the repository's **Releases** page on your phone → **Kharcha (latest build)** → download `Kharcha.apk`.
   (Or: **Actions** tab → latest run → `Kharcha-apk` artifact.)
2. Open the file. Android will ask to allow installing from your browser/files app — allow it.
3. Open Kharcha, set your income and budget, allow **SMS**, and tap **Import last 6 months**.
4. Optional but recommended: allow **notification access** so PhonePe merchant names come through cleanly.

> Android 13+ may show "Restricted setting" when you enable notification access for a sideloaded app.
> Go to **Settings → Apps → Kharcha → ⋮ (top right) → Allow restricted settings**, then try again.

## Privacy

Everything is stored on your phone in a local database. The app has **no internet permission**,
so your financial data physically cannot leave the device.

## Why SMS and not PhonePe directly?

PhonePe has no public API for personal transaction history. Your bank, however, sends an SMS for
every UPI debit/credit — including the balance — so that is the most complete and reliable source.
PhonePe's own notifications are used on top of that for cleaner merchant names.

## Build from source

Requires JDK 17 and the Android SDK (or just open the folder in Android Studio).

```bash
./gradlew testDebugUnitTest   # parser + insights tests
./gradlew assembleRelease     # APK at app/build/outputs/apk/release/
```

## Project layout

```
app/src/main/java/com/kharcha/app/
├── parser/      SMS + notification parsing, merchant categoriser (pure Kotlin, unit tested)
├── insights/    Insights engine: overspend detection, savings tips, discipline score
├── data/        Room database, repository (dedup/merge), settings
├── ingest/      SMS receiver, SMS history import, notification listener, budget alerts
└── ui/          Jetpack Compose screens: Home, Expenses, Insights, Plan, Settings, Onboarding
```
