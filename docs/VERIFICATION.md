# Verification — 2026-09-29

- `mvn verify`: passed, 11 tests, 0 failures, 0 errors. Local database: H2 2.3 in PostgreSQL compatibility mode.
- `npm ci` / `npm run build`: dependency installation and TypeScript/Vite production build passed.
- Real browser end-to-end, against the Java backend (no mocked API): login as employee, request Thursday, logout, login as manager, review, finalize and download CSV passed. No browser JavaScript errors observed.
- Desktop (1440px), mobile (390px) and approved-only print rendering checked. Mobile document had no horizontal overflow; the roster has intentional horizontal scrolling.
- Automated mail test: simulated SMTP failure preserves finalization; manual retry sends once and records attempts. This verifies application behavior, not real company SMTP connectivity.
- GitHub CI is configured to run backend tests against PostgreSQL 17 and build the frontend. It has not run until the repository is pushed and Actions executes it.
- Docker Engine was unavailable in the build environment, so the complete Compose stack was not executed here. Java and React were run directly for the browser verification.
- Company SMTP, TLS deployment, account onboarding for real staff and organization-wide deployment remain environment configuration tasks.
- GitHub write attempt was blocked by the integration's repository permissions (HTTP 403). No source commit has been pushed remotely from this environment.
