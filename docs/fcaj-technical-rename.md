# FCAJ technical rename — phase 1

Implemented on 2026-10-14. This phase changes technical names and source paths;
business behavior, persisted data, and visible branding remain as before.

| Previous | Current |
| --- | --- |
| `back-end/src/seal-api` | `back-end/src/fcaj-api` |
| `front-end/src/seal-web` | `front-end/src/fcaj-web` |
| `com.seal.hackathon` | `com.fcaj.hackathon` (production and tests) |
| Maven group `com.seal` | `com.fcaj` |
| Maven / Spring application name `seal-hackathon` | `fcaj-hackathon` |
| npm package `@figma/my-make-file` | `fcaj-web` |
| `SealFooter`, `sealLogo` | `AppFooter`, `brandLogo` |
| `seal-backend`, `seal-frontend` | `fcaj-backend`, `fcaj-frontend` |
| `seal_mysql`, `seal_backend`, `seal_frontend`, `seal_caddy` | `fcaj_mysql`, `fcaj_backend`, `fcaj_frontend`, `fcaj_caddy` |
| `seal_network` | `fcaj_network` |

Maven artifact `hackathon` and its version remain unchanged. Source paths in
GitHub Actions, Docker Compose, E2E launch scripts, and current learning/test
documentation were updated. Caddy upstreams match the renamed services.
Frontend CSS selectors and animation names now describe their purpose, such as
`landing-marquee`, `event-certificate`, and `notificationBannerShrink`.

## Deferred work

- Database `seal_hackathon`, SQL scripts, and seed/event data.
- Domains, email addresses, OAuth callbacks, cookie domains, and secrets.
- Existing server checkout path (`APP_DIR`) and Docker data volumes.
- Browser storage keys, including onboarding, rules, and notification state.
- Logos, visible product branding, rules, and certificate content.
- Historical AI logs and reports.

No deployment, database migration, commit, or push was performed. Service and
container renames take effect only when the stack is subsequently deployed.

## Validation

- Frontend: `npm test` — 162 tests passed; `npm run build` passed.
- Backend: Maven 3.9.16 `test -Dspring.profiles.active=test` — 433 tests passed;
  compiled 265 production and 30 test Java files under the new namespace.
- Docker Compose: `docker compose config --no-env-resolution --no-interpolate --quiet`
  passed. This checks configuration syntax, without starting services.
- Source/path scan: no obsolete package, module path, component, or service
  references remain in active source/configuration, excluding historical records
  and this mapping document.

The Windows Maven wrapper failed to initialize in this environment, so validation
used the already installed Maven 3.9.16. The backend test log contains H2 schema
creation warnings, including a missing `HackathonEvent` table, although the
context test and all other tests pass. These tests do not establish full database
schema compatibility. Frontend build also reports the existing large bundle
warning. Neither issue was changed in this rename phase.
