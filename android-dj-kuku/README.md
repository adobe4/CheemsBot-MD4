# DJ Kuku Android Native Apps

Mfumo huu una apps mbili za Android Native (Kotlin) zinazotumia backend moja ya Firebase:

- `app-user` (`com.djkuku.listener`): app ya wasikilizaji bila usajili wala login. Inaonyesha nyimbo na simulizi kutoka Firestore, inacheza audio kwa Media3/ExoPlayer, na inapakua MP3 kupitia Android DownloadManager.
- `app-admin` (`com.djkuku.admin`): app ya admin yenye Firebase Authentication. Admin anaweza kupost MP3 kwenye Firebase Storage, kuandika/kusasisha simulizi za maandishi, na kufuta maudhui kupitia Firestore.
- `shared`: models na `DjKukuRepository` zinazotumiwa na apps zote mbili ili database na hifadhi ya mafaili viwe sehemu moja.

## Firebase schema

Collections zinazotumiwa:

```text
songs/{songId}: { id, title, artist, audioUrl, coverUrl, createdAt, downloads }
stories/{storyId}: { id, title, body, audioUrl, createdAt }
```

Storage paths:

```text
songs/{songId}.mp3
stories/{storyId}.mp3 (optional kwa simulizi za sauti)
```

## Setup

1. Tengeneza Firebase project moja.
2. Washa Authentication kwa Email/Password kwa admin app.
3. Washa Firestore Database na Firebase Storage.
4. Pakua `google-services.json` mbili kutoka Firebase console:
   - weka ya user app ndani ya `app-user/google-services.json`.
   - weka ya admin app ndani ya `app-admin/google-services.json`.
5. Fungua folder `android-dj-kuku` kwenye Android Studio na build modules zote mbili.


## Build APK links

APK hazijawekwa moja kwa moja kwenye Git kwa sababu zinahitaji `google-services.json` halisi za Firebase project yako. Njia mbili za kupata links ni:

1. **GitHub Actions artifacts**: weka repository secrets `DJ_KUKU_USER_GOOGLE_SERVICES_JSON` na `DJ_KUKU_ADMIN_GOOGLE_SERVICES_JSON`, kisha run workflow **Build DJ Kuku Android APKs**. Baada ya workflow kufanikiwa utapata downloadable artifact links:
   - `dj-kuku-listener-debug-apk`
   - `dj-kuku-admin-debug-apk`
2. **Local build**: weka `google-services.json` kwenye `app-user/` na `app-admin/`, kisha run:

```bash
./scripts/build-apks.sh
```

Local APK outputs zitakuwa:

```text
app-user/build/outputs/apk/debug/app-user-debug.apk
app-admin/build/outputs/apk/debug/app-admin-debug.apk
```

## Firestore security rules example

```text
rules_version = '2';
service cloud.firestore {
  match /databases/{database}/documents {
    match /songs/{id} {
      allow read: if true;
      allow write: if request.auth != null;
    }
    match /stories/{id} {
      allow read: if true;
      allow write: if request.auth != null;
    }
  }
}
```
