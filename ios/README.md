# KampüsAğı iOS

Native SwiftUI app with the same backend (Supabase), the same rules and the same KVKK flow as the
Android app in `app/`. The Android app is not affected by anything in this folder.

- **Project:** `project.yml` (XcodeGen). The `.xcodeproj` is generated, not committed.
- **Backend:** [supabase-swift](https://github.com/supabase/supabase-swift) 2.x; the same database functions,
  parameters and error codes as Android (`KampusAgi/Data/Services.swift`, `ErrorMapping.swift`).
- **Configuration:** `Config/Secrets.xcconfig` (never committed) is written by
  `scripts/write-secrets.sh` from `SUPABASE_URL`, `SUPABASE_ANON_KEY` and `WEBSITE_URL`. Without it,
  the app builds and shows "Henüz bağlı değil".
- **Build and tests:** `.github/workflows/ios.yml` on a macOS runner: unit tests on the simulator,
  a simulator `.app` and an **unsigned** device build (`KampusAgi-unsigned.ipa`) as artifacts.

## Local build (macOS + Xcode 16)

```sh
brew install xcodegen
cd ios
SUPABASE_URL=… SUPABASE_ANON_KEY=… WEBSITE_URL=… ./scripts/write-secrets.sh
xcodegen generate
open KampusAgi.xcodeproj
```

## Installing on an iPhone / TestFlight

An Apple Developer Program membership is required: an iOS distribution certificate, an App Store
provisioning profile for `com.kampusagi.ios`, and an App Store Connect app record. Until these exist,
CI produces only unsigned builds (simulator app and unsigned `.ipa`).
