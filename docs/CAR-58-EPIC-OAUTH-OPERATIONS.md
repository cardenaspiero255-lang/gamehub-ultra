# CAR-58 — Epic Integration 2.0: OAuth, privacy and integration boundary

## What the Android client can verify locally

- A request uses Epic's documented authorization endpoint, `https://www.epicgames.com/id/authorize`, with `response_type=code`, `scope=basic_profile`, a 64-hex random `state`, and the redirect URI registered by the owner.
- Only the exact HTTPS registered redirect is intercepted. A callback without a matching `state` and a unique, valid authorization `code` is rejected. The client no longer extracts authorization codes from page text or cookies.
- When a specific Epic account is selected for `SYNC`, the authenticated 32-hex account ID must match that public identity. A different account, vanity name, or malformed ID is rejected before persistence.
- The client retains only nonsecret public account/library metadata. The access token remains in memory during an authorized fetch; token serialization was removed.
- Remote errors during identity verification or library fetch leave existing local library metadata unchanged. Each successful replacement remains scoped to a single account.
- Cached metadata is explicitly labeled as unverified or stale; the UI must never claim a live Epic session from a cached timestamp.

## Required owner configuration (not included in source control)

Create and configure a **separate, owned** Epic Account Services OAuth client under an Epic Developer Portal product. Configure the HTTPS redirect URI there and satisfy Epic's applicable consent, scope and brand review requirements.

Provide these build-time **nonsecret** values to the Android build:

| Setting | Purpose |
| --- | --- |
| `EPIC_OAUTH_CLIENT_ID` | Epic OAuth client ID for this project; not the launcher/client ID of another app |
| `EPIC_OAUTH_REDIRECT_URI` | HTTPS redirect URI registered to that client |
| `EPIC_AUTH_BACKEND_URL` | HTTPS backend endpoint for exchanging the one-time authorization code |

The backend must validate the client, redirect URI and code, then exchange the code with Epic using the **client secret only server-side**. Never put an Epic client secret, refresh token or long-lived bearer token in Android BuildConfig, committed Gradle properties, logs, intents, or local plain preferences.

Without a configured client, matching redirect and backend, the Android Epic connector **fails closed** with an actionable message. Do not attempt to bypass official login with embedded credentials, reuse another product's OAuth client, scrape page text, or automate an unsupported login path.

## Scope and limitations

Official Epic Account Services documentation establishes OAuth authorization and permitted user profile scopes. It does **not automatically grant** third-party apps a general-purpose entitlement to read a user's entire purchased Epic Games Store library.

The preexisting `EpicStoreClient.fetchLibrary` uses a library endpoint that must be reviewed for supported access, consent, and permissions before treating end-to-end owned-games import as a production capability. If Epic does not support the required permission, report the unsupported boundary and rely only on already authorized, locally cached public metadata. A successful local unit test is **not** proof of an Epic login or EGS entitlement in production.

References:
- Official Epic Account Services setup: https://dev.epicgames.com/docs/epic-online-services/accounts-and-social/eos-epic-account-services/getting-started
- Official Auth Web APIs: https://dev.epicgames.com/docs/web-api-ref/authentication

## CAR-29 release gate

Do not merge CAR-58 while applicable required checks or review threads are open, the patch's executable-line JaCoCo coverage is below 90%, backend integration cannot be verified, or any security assumption is unresolved. CAR-58 is a staged implementation until external prerequisites are met.

Keep the consolidated physical-device validation campaign deferred until after CAR-69.
