# Firebase Cloud Messaging Setup For Detox Task Sync

This document describes the Firebase Cloud Messaging (FCM) setup needed for
Detox task synchronization between:

- this middleware repository (`detox-remote-care-manager`)
- the Detox@Home Android patient app (`detox-patient-prototype`)

FCM is used as a wake-up mechanism only. The actual task XML is still fetched
from or uploaded to the middleware through the normal SenSeeAct APIs.

## What FCM Is Used For

The current implementation uses FCM for two cases:

- wake the patient app when a `task_refresh_requests` row is created, so the
  app can upload its current tasks as an `APP` snapshot
- wake the patient app when a new `task_configurations` row is created, so the
  app can fetch and apply the latest `WEB` snapshot

If FCM is unavailable, the patient app should still work through pull-based
fallback on app start, app resume, and restored connectivity. FCM improves
timeliness, especially while the app is in the background.

## Relevant Middleware Behavior

The middleware sends push messages through the Firebase Admin SDK in:

- [PushNotificationService.java](/Users/huublievestro/src/detox-remote-care-manager/SenSeeActService/src/main/java/nl/rrd/senseeact/service/PushNotificationService.java)
- [MobileWakeService.java](/Users/huublievestro/src/detox-remote-care-manager/SenSeeActService/src/main/java/nl/rrd/senseeact/service/scheduled/MobileWakeService.java)

Both services initialize Firebase using:

- `GoogleCredentials.getApplicationDefault()`

That means the runtime environment of the middleware must provide valid Google
Application Default Credentials.

The middleware accepts push registration through:

- `POST /v{version}/sync/project/{project}/register-push?user={subject}`
- `POST /v{version}/sync/project/{project}/unregister-push?user={subject}&deviceId={deviceId}`

For compatibility with the current patient app implementation, the registration
payload may contain either `fcmToken` or `token`.

## Relevant Patient App Assumptions

The current patient app implementation does not rely on `google-services.json`.
Instead, it manually initializes Firebase Messaging from four configuration
values:

- `DETOX_FIREBASE_APP_ID`
- `DETOX_FIREBASE_API_KEY`
- `DETOX_FIREBASE_PROJECT_ID`
- `DETOX_FIREBASE_SENDER_ID`

Those values are wired into the app build in:

- `detox-patient-prototype/app/build.gradle`

and consumed in:

- `detox-patient-prototype/app/src/main/java/com/detoxathome/tasksync/FirebaseMessagingBootstrap.kt`

The app registers a Firebase messaging service in:

- `detox-patient-prototype/app/src/main/AndroidManifest.xml`

## Firebase Project Setup

Use one Firebase project for the Detox task sync environment you are testing or
deploying.

Recommended high-level steps:

1. Create or open the Firebase project in the Firebase console.
2. Enable Cloud Messaging for that project.
3. Register the Android app used by Detox@Home.
4. Create or select a Google service account that the middleware may use for
   Firebase Admin SDK access.

Official Firebase documentation:

- [Add Firebase to your Android project](https://firebase.google.com/docs/android/setup)
- [Get started with Firebase Cloud Messaging in Android apps](https://firebase.google.com/docs/cloud-messaging/android/first-message)
- [Add the Firebase Admin SDK to your server](https://firebase.google.com/docs/admin/setup)

## Android App Registration

The patient app currently uses:

- release application ID: `com.detoxathome`
- debug application ID: `com.detoxathome.debug`

The debug ID is inferred from the patient app Gradle configuration because the
debug build adds `.debug` as an application ID suffix.

If you want push notifications to work in both release and debug builds,
register both Android apps in Firebase:

1. `com.detoxathome`
2. `com.detoxathome.debug`

For basic FCM delivery in this use case, you do not need Google Analytics.

## Values Needed By The Patient App

From the Firebase console, collect the following values for the Android app:

- Firebase App ID
- Web API key
- Project ID
- Sender ID or project number

Provide them to the patient app through one of:

- Gradle project properties
- environment variables
- `local.properties`

The expected keys are:

```properties
DETOX_FIREBASE_APP_ID=...
DETOX_FIREBASE_API_KEY=...
DETOX_FIREBASE_PROJECT_ID=...
DETOX_FIREBASE_SENDER_ID=...
```

Because the patient app uses manual Firebase initialization, these values are
required even if `google-services.json` is not used.

## Credentials Needed By The Middleware

The middleware must be able to authenticate to Firebase Admin SDK.

The simplest setup is:

1. Create a service account in Google Cloud or Firebase for the same Firebase
   project.
2. Download the service account JSON key securely.
3. Set `GOOGLE_APPLICATION_CREDENTIALS` for the middleware runtime process.

Example:

```bash
export GOOGLE_APPLICATION_CREDENTIALS=/absolute/path/to/firebase-service-account.json
```

Then start the middleware in the same environment.

If the middleware runs on Google Cloud with a properly bound service account,
you may use Application Default Credentials without a local JSON file. The key
requirement is that `GoogleCredentials.getApplicationDefault()` succeeds at
runtime.

## Expected Push Payload Shape

The middleware sends small data messages that identify what should be refreshed.
The patient app expects the FCM data payload to contain:

- `project`
- `user`
- `table`

The patient app subscribes to updates for:

- `task_refresh_requests`
- `task_configurations`

## Expected Patient App Registration Payload

The current patient app registers push with a payload shaped like:

```json
{
  "deviceId": "device-id",
  "token": "fcm-token",
  "includeTables": ["task_refresh_requests", "task_configurations"]
}
```

The middleware accepts this shape.

## Suggested Local Verification Checklist

When real integration testing becomes possible, verify the following:

1. Start the middleware with valid Firebase Admin credentials.
2. Start the patient app with valid Firebase configuration values.
3. Confirm the app obtains an FCM token and registers push with the middleware.
4. Create a task refresh request from the web editor and verify:
   - the app wakes quickly
   - the app uploads a new `APP` snapshot to `task_configurations`
   - the uploaded snapshot echoes the matching `requestToken`
5. Publish a `WEB` task snapshot from the web editor and verify:
   - the app wakes quickly
   - the app fetches and imports the new snapshot
6. Temporarily disable push delivery and verify the fallback path still works
   on app start, app resume, or connectivity recovery.

## Troubleshooting

If no push arrives at the app:

- verify the Firebase project values in the app are non-empty
- verify the middleware has valid `GOOGLE_APPLICATION_CREDENTIALS`
- verify the device has Google Play services
- verify the patient app successfully registered push for the correct patient
  subject and `deviceId`
- verify the middleware stored a push registration for the expected project,
  user, and included tables

If the middleware logs errors while sending push:

- check that the service account belongs to the correct Firebase project
- check that the app token belongs to the same Firebase project
- check for expired or invalid tokens in middleware logs

If push is unavailable but sync still needs to work:

- use the implemented pull fallback in the patient app
- expect slower pickup of remote changes while the app is backgrounded

## Notes

- FCM is part of the v1 implementation because it improves refresh reliability
  and timeliness.
- FCM is not the transport for the task XML itself.
- Task definitions remain patient-scoped, not device-scoped.
