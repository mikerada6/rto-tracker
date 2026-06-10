# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Development workflow — GitFlow

**This repository uses GitFlow.** `main` and `develop` are both branch-protected
on GitHub — direct pushes are rejected, all changes go through pull requests
that must pass `test-backend` and `test-frontend`.

### When the user asks for a new feature or non-trivial change

Always start work on a fresh feature branch off `develop`. Do not commit on
`develop` or `main` directly — both are protected and the push will be rejected.

```bash
git checkout develop && git pull
git checkout -b feature/short-kebab-description
```

Branch naming:
- `feature/*` for new work or enhancements (branched from `develop`)
- `hotfix/*` for urgent fixes against a shipped version (branched from `main`)
- `release/*` for release stabilisation (branched from `develop`)

### While working

- Add a bullet to `CHANGELOG.md` under `## [Unreleased]` if the change is
  user-visible (feature, bug fix, behaviour change). Skip for purely internal
  refactors or doc-only edits.
- Run the relevant tests before pushing:
  - Backend: `cd backend && mvn test`
  - Frontend: `cd frontend && npm run build`

### Finishing a feature

```bash
git push -u origin feature/short-kebab-description
# Open a PR targeting develop (the default branch).
```

Required checks (`test-backend`, `test-frontend`) must pass before merge.
Linear history is required, so use squash or rebase merges, not merge commits.

### Release / hotfix procedure

See `CONTRIBUTING.md` for the full release-branch dance (version bumps,
tagging, back-merging to develop) and hotfix flow. The short version: cut
a `release/X.Y.Z` branch from develop, bump versions to `X.Y.Z` (no SNAPSHOT),
update CHANGELOG, merge to **both** main and develop, then tag `vX.Y.Z` on
main — the tag triggers Docker Hub publish.

### Versioning

- `main` is always at the released version (e.g. `1.0.0`).
- `develop` is always at the next minor SNAPSHOT (e.g. `1.1.0-SNAPSHOT`).
- Version lives in `backend/pom.xml`, `frontend/package.json`, and
  `frontend/package-lock.json` — keep all three in sync.

## Commands

### Backend (Spring Boot / Maven)

```bash
cd backend
mvn spring-boot:run                          # run the backend (port 8080)
mvn test                                     # run all tests
mvn test -Dtest=EventServiceTest             # run a single test class
mvn test -Dtest=EventServiceTest#methodName  # run a single test method
mvn package -DskipTests                      # build jar without tests
```

### Frontend (Angular)

```bash
cd frontend
npm install                  # install dependencies
npm start                    # dev server on port 4200 (proxies /api -> localhost:8080)
npm run build                # production build to frontend/dist/
npm test                     # run unit tests
```

### Docker

```bash
docker-compose up -d         # build and start backend + frontend
docker-compose down          # stop all containers
```

Requires a `.env` file — copy `.env.example` and fill in your database credentials.
PostgreSQL and any observability infrastructure (Loki, Tempo, Prometheus, Grafana) are external.

---

## What This Repository Is

A **Commute & RTO Tracking System** consisting of:

- **Spring Boot backend** — receives zone entry/exit webhooks from Home Assistant and tracks compliance with a Return-to-Office policy
- **Angular 21 frontend** — SPA dashboard for visualising compliance, calendar, reports, zones, events, and settings

### Repository layout

```
backend/                    # Spring Boot source (Maven + Dockerfile)
frontend/                   # Angular 21 SPA (+ Dockerfile + Nginx)
home-assistant/             # Home Assistant integration example configs
docker-compose.yml          # Orchestrates backend + frontend containers
```

---

## Architecture

### Backend

**Stack:** Spring Boot · Maven · PostgreSQL · Flyway · Spring Security (API key auth)
**Pattern:** Layered — Controller -> Service -> Repository
**Auth:** `X-API-Key` header; keys are SHA-256 hashed in the DB, never stored plaintext

#### Core entities (in dependency order)

1. `User` — owns everything; holds `requiredDaysPerWeek` (default 3.0); roles `USER` / `ADMIN`
2. `Zone` — user-owned locations typed as `HOME`, `TRAIN_STATION`, `OFFICE`, `OTHER`; soft-deleted only
3. `ZoneEvent` — immutable; raw event log fired by Home Assistant; `externalId` resolves to a `Zone`
4. `OfficeDayRecord` — computed cache; one row per `(user, date)`; invalidated when a new event lands on that date
5. `InviteCode` — single-use codes that gate registration; created by admins

#### Package structure

```
com.rto.tracker
├── config/          # Security, rate limiting, OpenAPI, MDC, Micrometer
├── controller/      # REST controllers (Admin, Audit, Dashboard, Day, Event, User, Zone)
├── domain/          # JPA entities + enums
├── dto/             # Request/response records
├── exception/       # Domain exceptions + GlobalExceptionHandler
├── repository/      # Spring Data JPA repositories
└── service/         # Business logic (Dashboard, Event, OfficeDayCalculation, PeriodStats, Registration, User, Zone, Audit, InviteCode)
```

#### Key design decisions

- **No holiday/PTO adjustments** — the RTO target is a true calendar average; weeks are always `days / 7`
- **Event sourcing** — `ZoneEvent` rows are never mutated; all metrics are derived from them
- **On-demand + cache** — `OfficeDayRecord` is computed on first read and invalidated on new events
- **User-scoped everything** — every repository query must filter by `userId`; a missing filter is a data-leak bug

#### Compliance formula (same across all periods)

```
totalPeriodWeeks     = periodDays / 7.0
weeksElapsed         = daysSincePeriodStart / 7.0
weeksRemaining       = totalPeriodWeeks - weeksElapsed
daysStillNeeded      = max(0, ceil(required * totalPeriodWeeks - daysInOffice))
requiredAvgRemainder = daysStillNeeded / weeksRemaining  (null when period ended)
```

Periods: **week** (Mon-Sun), **month**, **quarter** (Q1-Q4), **year**.

### Frontend

**Stack:** Angular 21 · TypeScript · Tailwind CSS v4 · Chart.js (via ng2-charts) · date-fns
**Pattern:** Feature modules with standalone components; core services via `inject()`
**Auth:** API key stored in `localStorage`; sent via `AuthInterceptor` as `X-API-Key`
**Proxy:** `proxy.conf.json` forwards `/api/**` to `http://localhost:8080` in dev

#### Frontend structure

```
frontend/src/app/
├── core/
│   ├── guards/        # AuthGuard (redirects to /login if no key)
│   ├── interceptors/  # AuthInterceptor (attaches X-API-Key)
│   ├── models/        # TypeScript interfaces mirroring backend DTOs
│   ├── services/      # API service wrappers (Dashboard, Day, Event, Zone, User, Report)
│   └── utils/         # Shared helpers
├── features/
│   ├── dashboard/     # Compliance summary cards + period stats
│   ├── calendar/      # Monthly calendar view of office days
│   ├── events/        # Zone event log with bulk CSV upload
│   ├── reports/       # Quarterly historical reports
│   ├── zones/         # Zone CRUD
│   ├── settings/      # User profile + API key management
│   └── login/         # API key entry screen
└── shared/            # Reusable components (nav, stat cards, etc.)
```

---

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/v1/events` | Record a zone event (called by HA automations) |
| `GET` | `/api/v1/days/{date}` | Full daily breakdown for a date |
| `GET` | `/api/v1/dashboard/summary` | All compliance stats across all periods |
| `GET` | `/api/v1/reports/quarter/{year}/{quarter}` | Historical quarter report |
| `GET/POST/PUT/DELETE` | `/api/v1/zones` | Zone management |
| `GET/PUT` | `/api/v1/users/me` | Current user profile |
| `GET` | `/api/v1/audit` | Audit log |
| `POST` | `/api/v1/auth/register` | Register with invite code |
| `POST` | `/admin/invite-codes` | Admin — create invite code |
| `GET/PUT` | `/admin/users` | Admin — list/update users |
