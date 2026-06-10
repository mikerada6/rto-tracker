# RTO Tracker

A self-hosted Return-to-Office compliance tracking system. Receives zone entry/exit webhooks from [Home Assistant](https://www.home-assistant.io/) and calculates RTO compliance metrics across weekly, monthly, quarterly, and yearly periods.

## Features

- Automatic office day detection via Home Assistant zone events
- Commute time tracking (home departure to office arrival)
- Compliance dashboard with week/month/quarter/year breakdowns
- Calendar view of office days
- Historical quarterly reports
- PDF report export (week / month / quarter / year / custom range) for management review
- Multi-user support with invite-code registration
- Admin tools for user and invite management

## Tech Stack

| Layer | Technology |
|-------|-----------|
| Backend | Spring Boot 3 · Java 21 · Maven |
| Frontend | Angular 21 · TypeScript · Tailwind CSS v4 |
| Database | PostgreSQL 16 · Flyway migrations |
| Auth | API key (SHA-256 hashed) |
| Containers | Docker multi-stage builds · Nginx |

## Quick Start

### Prerequisites

- Docker and Docker Compose
- PostgreSQL 16 (external)

### 1. Configure environment

```bash
cp .env.example .env
# Edit .env with your PostgreSQL credentials
```

### 2. Run with Docker Compose

```bash
docker-compose up -d
```

The frontend is available at `http://localhost:8880` and proxies API requests to the backend.

### 3. Set up Home Assistant

See the `home-assistant/` directory for automation and REST command configs that send zone events to the API.

## Local Development

### Backend

```bash
cd backend
mvn spring-boot:run    # starts on port 8080
mvn test               # run tests
```

### Frontend

```bash
cd frontend
npm install
npm start              # dev server on port 4200 (proxies /api to localhost:8080)
```

## Architecture

The backend follows a layered pattern (Controller -> Service -> Repository) with event-sourced zone data. `ZoneEvent` rows are immutable; `OfficeDayRecord` is a computed cache that is invalidated when new events arrive. All data is user-scoped.

See [CLAUDE.md](CLAUDE.md) for detailed architecture documentation.

## License

Private - All rights reserved.
