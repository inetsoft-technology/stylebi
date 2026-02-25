# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StyleBI is a cloud-native business intelligence web application. It is a multi-module Maven monorepo with a Java backend (Spring Boot) and Angular frontend.

## Build Commands

### Prerequisites
- Java 21 SDK with `JAVA_HOME` set
- Docker (running)
- GitHub classic personal access token with `read:packages` scope configured in `~/.m2/settings.xml` (see README.md)

### Java / Full Build
```bash
./mvnw clean install              # Full build with tests
./mvnw clean install -DskipTests  # Skip tests for faster builds
./mvnw clean install -PdockerImage  # Build + Docker image in one step
./mvnw clean package jib:dockerBuild -pl docker  # Build Docker image only
```

### Frontend (Angular)
```bash
cd web
npm run build         # Development build
npm run build:prod    # Production build
npm run start         # Dev server
npm run test          # Run Jest tests
npm run test:watch    # Watch mode
npm run test:em       # Enterprise Manager tests only
npm run lint          # ESLint
npm run lint:prod     # Lint with checkstyle output
npm run verify        # lint + test
```

### Running Locally
After building, start the server from `docker/target/docker-test`:
```bash
docker compose up -d
# Access at http://localhost:8080, admin/admin credentials
docker compose down --rmi local -v  # Tear down
```

## Module Structure

```
inetsoft-stylebi (root)
├── bom/           – Dependency version management
├── build-tools/   – Custom Maven plugins (ANTLR, node, runner, etc.)
├── core/          – Core Java library (inetsoft-core.jar)
├── server/        – Spring Boot application (inetsoft-server.jar)
├── web/           – Angular frontend (compiled into inetsoft-web.jar)
├── connectors/    – 28 pluggable data source adapters (ZIP artifacts)
├── utils/         – Utility modules (SSL, MapDB storage, XML formats)
└── docker/        – Docker image assembly via JiBit
```

## Architecture

### Request Flow
```
Angular UI (Portal / Enterprise Manager)
    ↓ REST + WebSocket (SockJS)
Spring Boot Server (server/)
    ↓
Core Libraries (core/)
    ├── UQL query engine
    ├── Graph / chart rendering
    ├── Report generation
    └── Connector framework → connectors/
```

### Frontend Projects (`web/projects/`)
- **portal** – Main user-facing dashboard and visualization UI
- **em** – Enterprise Manager (admin interface)
- **shared** – Reusable Angular components and services shared between portal and em
- **elements** – Web components

### Backend Key Packages
- `inetsoft.uql.*` – Universal Query Language engine
- `inetsoft.graph.*` – Chart and graph rendering
- `inetsoft.report.*` – Report generation
- `inetsoft.web.*` – Spring MVC controllers, WebSocket handlers
- `inetsoft.sree.*` – Server-side report execution environment

### Storage Backends
The server supports multiple storage backends selected via Maven profiles:
- `mapdb` (default) – Embedded MapDB
- `awsStorage` – AWS S3
- `googleStorage` – Google Cloud Storage
- `mongoStorage` – MongoDB
- `azureStorage` – Azure Blob Storage

### Key Technology Versions
- Java 21, Spring Boot 3.5.8, Spring Framework 6.2.15
- Angular 15.2.x, TypeScript 4.9.4, RxJS 6.6.7
- Angular Material 15.2.9, Bootstrap 5.2.3
- Jest 28.1.3 (frontend testing)

## Development Notes

- The trunk uses trunk-based development; some tests on `main` may be unstable. Use `-DskipTests` when needed or check out a release tag for stable builds.
- Frontend tests use Jest (not Karma/Jasmine). Run them from the `web/` directory.
- The `web/` module is built by Maven via a custom `node-maven-plugin` that runs npm automatically during `./mvnw` builds — you only need to run npm commands directly for frontend-only iteration.
- The `connectors/` modules produce ZIP artifacts, not JARs; they are loaded at runtime by the connector framework.