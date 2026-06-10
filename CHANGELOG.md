# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added
- Reports: export an RTO compliance report as PDF for week, month, quarter,
  year, or a custom date range (up to 365 days). The PDF includes a cover
  block with employee name and period, a compliance summary, a weekly bar
  chart of office days, and a dated log of each qualifying day with the
  first work-area entry time and location. Suitable for management review.

## [1.1.0] - 2026-06-10

UX polish release. Smoothes out friction points in three frequently-used
parts of the UI — Settings, Zones, and Events.

### Added
- Settings: copy-to-clipboard button on the post-regeneration API key alert,
  with a "✓ Copied" confirmation that reverts after 2 seconds.

### Changed
- Zones: redesigned empty state with a prominent "Add your first zone" CTA
  and a short explanation of what zones are for, so first-time users have
  an obvious next action instead of hunting for the top-right button.
- Events: added quick date-range chips (Today, Last 7 days, Last 30 days,
  This month, This quarter) above the start/end pickers. Editing either
  picker clears the active chip.
- Events CSV upload: added a collapsible "Expected CSV format" section
  documenting the column order, date/time format, and Arrived/Departed
  event types, plus a "Download sample CSV" button.

## [1.0.0] - 2026-06-10

First production release. Stable after two weeks of live use against a real
Home Assistant deployment.

### Features

#### Backend
- Spring Boot 3.4 service with PostgreSQL + Flyway migrations.
- `POST /api/v1/events` webhook endpoint for Home Assistant zone enter/exit events.
- Compliance computation across week, month, quarter, and year periods using a
  single uniform formula (`weeks = days / 7`).
- Event-sourced design — `ZoneEvent` rows are immutable; metrics derive from them.
- `OfficeDayRecord` cache, computed on demand and invalidated on new events for
  the same day.
- Bulk range fetch for dashboard summary — collapses ~160 per-day queries into 2.
- API key authentication with SHA-256 hashed storage; invite-code gated registration.
- Per-user rate limiting via Bucket4j.
- Soft-delete for zone events; GPS-bounce events dropped by debounce window.
- Observability: Micrometer + Prometheus, Brave/Zipkin tracing, Loki log
  appender, MDC request IDs.
- OpenAPI / Swagger UI with reverse-proxy and CORS support.
- `/api/v1/version` endpoint exposing app version + git commit.

#### Frontend
- Angular 21 SPA with Tailwind v4 and Chart.js.
- Dashboard with compliance summary across all periods, single-request load.
- Calendar view with monthly grid, in-month average/week, and per-day detail view.
- Day detail: subway-map journey rail bounded by Home, office as phase connector,
  commute gap annotations for non-commute time (e.g. happy hour), ongoing-office
  indicator, timeline bar.
- Events log with sortable Timestamp / Zone / Type columns, CSV bulk upload,
  and event soft-delete.
- Zone management CRUD.
- Quarterly historical reports.
- Settings: profile, required-days-per-week, API key management.
- Dark mode.
- App version and commit hash surfaced in the sidebar, with mismatch detection
  when frontend and backend versions diverge.

#### Deployment
- Multi-stage Dockerfiles for backend (Temurin 21 JRE) and frontend
  (Node 22 build + Nginx).
- `docker-compose.yml` orchestrating both services with env-driven config.
- GitHub Actions CI: backend tests, frontend build, tag- and main-triggered
  Docker Hub publish with semver image tags.

### Known limitations

- No holiday / PTO awareness — RTO target is a true calendar average.
- Single-tenant in practice; multi-user supported via API key but no admin UI
  beyond invite codes.

[Unreleased]: https://github.com/mikerada6/rto-tracker/compare/v1.1.0...HEAD
[1.1.0]: https://github.com/mikerada6/rto-tracker/releases/tag/v1.1.0
[1.0.0]: https://github.com/mikerada6/rto-tracker/releases/tag/v1.0.0
