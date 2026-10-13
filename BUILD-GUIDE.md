# Building the MH Analyst Android app (Kotlin + Jetpack Compose)

The app keeps its own copy of your Tally data on the phone, so every screen opens instantly and works offline.
It downloads only what changed since last time, and the sync icon asks the Tally PC for fresh data.

**Before installing the app, update the website** with the latest `Mahalaxmi-upload.zip`: the app talks to the new `api/app.php`.

You build the APK once per update. The easiest way is to let GitHub build it for you (free, nothing to install).

## Option A: GitHub builds it (recommended)

**One-time setup (about 10 minutes):**

1. Create a free account at github.com, then a **new repository**, e.g. `mh-analyst-app`. Choose **Private**.
2. On the new repository page, click **uploading an existing file**. From the unzipped `android-native` folder, drag in **everything inside it**: the `app`, `gradle` and `.github` folders, and all the files. Click **Commit changes**.
3. Add your signing key, so the new app installs over the current one:
   - Find `mh-analyst-release.jks` and its password, from `mh-analyst-signing-key.zip` / `KEEP-THIS-SAFE.txt`.
   - On your PC, open **PowerShell** and run (change the path to where the file is):
     ```
     [Convert]::ToBase64String([IO.File]::ReadAllBytes("C:\Users\you\Downloads\mh-analyst-release.jks")) | Set-Clipboard
     ```
   - In the repository, open **Settings › Secrets and variables › Actions › New repository secret**:
     - Name `MH_KEYSTORE_BASE64`, then paste (Ctrl+V) as the value.
     - Name `MH_STORE_PASSWORD`, with the password as the value.

**Each build:**

4. Open the **Actions** tab, then **Build APK › Run workflow**. Uploading new code also starts a build by itself.
5. After about 5–10 minutes the run shows a green tick. Open it, and under **Artifacts** download **MH-Analyst-apk**. It's a zip containing `MH-Analyst.apk`.
6. Send the APK to your phone and install it. It replaces the current MH Analyst app.

**If the build fails (red cross):** open the failed run, click the step with the red cross, copy the lines marked `e:` or `error`, and send them to Claude. Small fixes are normal on a first build.

## Option B: Android Studio on your PC

1. Install Android Studio (free) from developer.android.com/studio.
2. **File › Open** the `android-native` folder, and wait for "Gradle sync" to finish (the first time downloads a lot).
3. **Build › Generate Signed App Bundle or APK › APK**:
   - choose `mh-analyst-release.jks`, alias `mhanalyst`, and the password, twice
   - pick **release**
4. The APK appears in `app/release/`.

## First use

1. Open the app and sign in with your website username and password, then your authenticator code.
   - New users must sign in on the website once first, to set up the authenticator.
2. The first download copies your data to the phone (a minute or two for large books). After that, opening the app is instant.

**Moving around:**
- **Back:** on a main tab, Back closes the app. Inside a voucher, ledger or item, Back returns to the list.
- **Account (person icon):** switch company, see website vouchers, open the website, or sign out. Signing out removes the data from the phone.

**Security:**
- The phone gets its own sign-in key, kept encrypted in Android's secure keystore.
- See or sign out phones on the website under **Account and security**. Admins can sign out a user's phones under **Users**.
- Changing a password or resetting the authenticator signs out that person's phones automatically.
- App data is excluded from phone backups.
