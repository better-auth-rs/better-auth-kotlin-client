# Verification coverage

The JVM protocol suite observed all **118 unique HTTP method/path contracts** exposed by the core client and its bundled plugins on 2026-10-22. The 118 contracts come from 119 direct request call sites: `organization.setActive` and `organization.unsetActive` share `POST /organization/set-active`. A reset token in the URL is represented as `{token}`.

## Reproduce the endpoint check

```sh
devenv shell -- ./gradlew :check
```

`TestServer` records requests received by Ktor MockEngine. `verifyEndpointCoverage` compares those observations with the explicit endpoint inventory in [`EndpointCoverage.kt`](../src/test/kotlin/io/github/betterauth/client/coverage/EndpointCoverage.kt). The task fails when an expected method/path has no observation. Source text matches do not count as test evidence.

The test task deletes the previous observation file before a new run. Gradle tracks the observation file as a test output. The verifier runs after the test task succeeds. The generated reports are:

- `build/reports/endpoint-coverage/observed.tsv`: observed method/path and test source pairs.
- `build/reports/endpoint-coverage/verified.tsv`: all expected endpoints and their observed test sources.
- `build/reports/tests/test/index.html`: JUnit results for the protocol, storage, transport, and regression tests.

When a wrapper adds or removes an endpoint, update the expected inventory and this table. The inventory describes the supported surface; the observations prove execution. A request observation does not prove every optional parameter, response shape, error condition, or server configuration.

## Separate verification layers

| Layer | Scope and evidence |
| --- | --- |
| JVM protocol and regression tests | 128 tests passed without skips. Includes request forwarding, response decoding, session lifecycle, cancellation, credential isolation, malformed responses, and local HTTP engine tests. |
| Better Auth server integration | `ServerIntegrationTest` runs against the pinned Better Auth 1.7.12 fixture in `integration/`. Its four scenarios cover email/password lifecycle; cookie mode, email OTP, magic links, anonymous and one-time sessions; two-factor and phone verification; and admin/organization/team/role operations. This suite is separate from the 118-endpoint observation count. |
| Android JVM tests | 22 tests passed without skips. Covers encrypted storage behavior and Android request/plugin hooks. |
| Android device tests | 10 tests passed without skips on an API 36 emulator: 2 client tests and 8 Keystore/storage tests. `lintDebug` reported no errors. |
| Redis cryptography | 3 AES-GCM unit tests passed without skips. |
| Redis and Valkey storage | 7 integration cases passed without skips; each case ran against Redis 8.10.2 and Valkey 9.1.2. Covers isolation, persistence, removal, TTL expiry and renewal, encrypted bytes, corruption, wrong keys, and SDK session round trips. |

Run the local Better Auth fixture with `devenv shell -- node integration/test.mjs`. Run Android device tests with `devenv shell -- ./gradlew :android:connectedDebugAndroidTest` after an emulator or device is available. See the [Redis module instructions](../redis/README.md) for Redis and Valkey integration prerequisites.

Browser OAuth UI, provider account configuration, deep-link registration in an application, and Android Credential Manager UI remain application/platform integration responsibilities. The passkey tests check the raw WebAuthn JSON exchange and session handling. They do not perform hardware authenticator ceremonies. The Android device run covers one API level and one emulator, not every device-specific Keystore implementation. Redis TLS, Sentinel, and Cluster deployments were not exercised.

## Endpoint evidence

Each row names one test that emitted the listed request in the successful JVM run. `verified.tsv` includes all observed sources when multiple tests exercise an endpoint. Paths are relative to the configured Better Auth base URL, commonly `/api/auth`.

| HTTP method and path | Observed test |
| --- | --- |
| `GET /account-info` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): account profile preserves provider data and unlink forwards account ID |
| `GET /admin/get-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `GET /admin/list-users` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin list users passes query and parses page |
| `GET /get-session` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): getSession decodes session and user with timestamps |
| `GET /jwks` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): jwt token and jwks |
| `GET /list-accounts` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): listSessions and listAccounts |
| `GET /list-sessions` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): listSessions and listAccounts |
| `GET /magic-link/verify` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): magic link request and verify |
| `GET /multi-session/list-device-sessions` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): multi session lists switches and revokes using jar cookies |
| `GET /ok` | [PluginMechanicsTest](../src/test/kotlin/io/github/betterauth/client/PluginMechanicsTest.kt): onResponse runs after the SDK stored the response's token |
| `GET /one-time-token/generate` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): one time token |
| `GET /organization/get-active-member` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/get-active-member-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/get-full-organization` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/get-invitation` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `GET /organization/get-organization` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/get-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `GET /organization/list` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/list-invitations` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `GET /organization/list-members` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization read endpoints |
| `GET /organization/list-roles` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `GET /organization/list-team-members` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `GET /organization/list-teams` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `GET /organization/list-user-invitations` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `GET /organization/list-user-teams` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `GET /passkey/generate-authenticate-options` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey sign in |
| `GET /passkey/generate-register-options` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey registration round trip |
| `GET /passkey/list-user-passkeys` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey registration round trip |
| `GET /reset-password/{token}` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): password reset callback returns the server redirect without following it |
| `GET /token` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): jwt token and jwks |
| `GET /verify-email` | [TransportTest](../src/test/kotlin/io/github/betterauth/client/TransportTest.kt): redirects are not followed |
| `POST /admin/ban-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `POST /admin/create-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `POST /admin/has-permission` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/impersonate-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/list-user-sessions` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/remove-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/revoke-user-session` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/revoke-user-sessions` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/set-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `POST /admin/set-user-password` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin sessions impersonation and permissions |
| `POST /admin/stop-impersonating` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): stop impersonation decodes the admin session and restores its signed token |
| `POST /admin/unban-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `POST /admin/update-user` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): admin user management |
| `POST /change-email` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): email verification and email change forward callbackURL |
| `POST /change-password` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): changePassword returns the rotated token |
| `POST /delete-anonymous-user` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): anonymous sign in and delete |
| `POST /delete-user` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): deleteUser clears the local session |
| `POST /email-otp/change-email` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/check-verification-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/request-email-change` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/request-password-reset` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/reset-password` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/send-verification-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /email-otp/verify-email` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp verify and sign in return the session |
| `POST /forget-password/email-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp endpoints use wire types |
| `POST /get-access-token` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): linkSocial and getAccessToken |
| `POST /link-social` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): linkSocial and getAccessToken |
| `POST /multi-session/revoke` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): multi session lists switches and revokes using jar cookies |
| `POST /multi-session/set-active` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): multi session lists switches and revokes using jar cookies |
| `POST /one-time-token/verify` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): one time token |
| `POST /organization/accept-invitation` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `POST /organization/add-team-member` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /organization/cancel-invitation` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `POST /organization/check-slug` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization update delete check slug |
| `POST /organization/create` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization create keeps server extra fields |
| `POST /organization/create-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `POST /organization/create-team` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /organization/delete` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization update delete check slug |
| `POST /organization/delete-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `POST /organization/has-permission` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `POST /organization/invite-member` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `POST /organization/leave` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): members |
| `POST /organization/reject-invitation` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): invitations |
| `POST /organization/remove-member` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): members |
| `POST /organization/remove-team` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /organization/remove-team-member` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /organization/set-active` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization set active and unset |
| `POST /organization/set-active-team` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /organization/update` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): organization update delete check slug |
| `POST /organization/update-member-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): members |
| `POST /organization/update-role` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): dynamic roles and permission check |
| `POST /organization/update-team` | [AdminAndOrganizationTest](../src/test/kotlin/io/github/betterauth/client/AdminAndOrganizationTest.kt): teams |
| `POST /passkey/delete-passkey` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey registration round trip |
| `POST /passkey/update-passkey` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey registration round trip |
| `POST /passkey/verify-authentication` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey sign in |
| `POST /passkey/verify-registration` | [SessionPluginsTest](../src/test/kotlin/io/github/betterauth/client/SessionPluginsTest.kt): passkey registration round trip |
| `POST /phone-number/request-password-reset` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): phone number flows |
| `POST /phone-number/reset-password` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): phone number flows |
| `POST /phone-number/send-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): phone number flows |
| `POST /phone-number/verify` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): phone number flows |
| `POST /refresh-token` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): provider token refresh preserves account selection and token expiry |
| `POST /request-password-reset` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): status style acknowledgements |
| `POST /reset-password` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): password reset supports explicit token and cookie token selection |
| `POST /revoke-other-sessions` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): status style acknowledgements |
| `POST /revoke-session` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): session revocations forward the target and preserve false acknowledgements |
| `POST /revoke-sessions` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): session revocations forward the target and preserve false acknowledgements |
| `POST /send-verification-email` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): email verification and email change forward callbackURL |
| `POST /sign-in/anonymous` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): anonymous sign in and delete |
| `POST /sign-in/email` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor verify completes sign in with token |
| `POST /sign-in/email-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): email otp verify and sign in return the session |
| `POST /sign-in/magic-link` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): magic link request and verify |
| `POST /sign-in/phone-number` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): phone number flows |
| `POST /sign-in/social` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): social sign in with id token authenticates |
| `POST /sign-out` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): signOut clears the local session on success |
| `POST /sign-up/email` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): sign up merges additional fields and parses user plus passthrough |
| `POST /two-factor/disable` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor password protected endpoints |
| `POST /two-factor/enable` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor enable parses uri and backup codes |
| `POST /two-factor/generate-backup-codes` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor password protected endpoints |
| `POST /two-factor/get-totp-uri` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor password protected endpoints |
| `POST /two-factor/send-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor otp and backup code endpoints |
| `POST /two-factor/verify-backup-code` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor otp and backup code endpoints |
| `POST /two-factor/verify-otp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor otp and backup code endpoints |
| `POST /two-factor/verify-totp` | [CredentialPluginsTest](../src/test/kotlin/io/github/betterauth/client/CredentialPluginsTest.kt): two factor verify completes sign in with token |
| `POST /unlink-account` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): account profile preserves provider data and unlink forwards account ID |
| `POST /update-session` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): session update allows an absent response session |
| `POST /update-user` | [CoreApiTest](../src/test/kotlin/io/github/betterauth/client/CoreApiTest.kt): status style acknowledgements |
| `POST /verify-password` | [CoreEndpointContractTest](../src/test/kotlin/io/github/betterauth/client/coverage/CoreEndpointContractTest.kt): password verification propagates API errors without replacing the session |
