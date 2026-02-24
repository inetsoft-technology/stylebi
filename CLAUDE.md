# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StyleBI Community is a cloud-native business intelligence web application. This is the open-source community edition used as a Git submodule inside the enterprise repository at `../` (parent directory).

**Requirements**: Java 21 (`JAVA_HOME` must be set), Maven 3.9+ (use `./mvnw` wrapper), Node.js 18+, Docker

## Build Commands

```bash
# Build all Java modules
./mvnw clean install -DskipTests

# Build single module
./mvnw clean install -pl core -DskipTests

# Build Docker image (requires Java libraries already built)
./mvnw clean package jib:dockerBuild -pl docker

# Build Java + Docker image together
./mvnw clean install -PdockerImage
```

## Running Locally

Start the server via Docker Compose after building:
```bash
cd docker/target/docker-test
docker compose up -d
docker compose logs -f server
# Stop: docker compose down --rmi local -v
```

Access at http://localhost:8080 (admin/admin).

## Frontend (web/)

```bash
cd web
npm install

npm run build          # Dev build: portal, em, elements, viewer-element
npm run build:prod     # Production build
npm run build:watch    # Watch mode (portal + em only)
npm run test           # Jest tests (portal project)
npm run test:em        # Jest tests (em project)
npm run test:watch     # Jest watch mode
npm run lint           # ESLint
npm run verify         # Lint + tests
```

### Running a single test file

```bash
cd web
npx jest path/to/spec.ts
# Or with Angular CLI:
npx ng test --include='**/my-component.spec.ts'
```

## Maven Module Structure

| Module | Purpose |
|--------|---------|
| `bom/` | Bill of materials (dependency versions) |
| `build-tools/` | Maven build plugins and configurations |
| `utils/` | Shared utilities |
| `core/` | BI engine, REST controllers, data pipeline, graph rendering |
| `server/` | Spring Boot entry point, health/metrics endpoints |
| `connectors/` | 25+ data source connectors (JDBC, REST, cloud, NoSQL) |
| `web/` | Angular frontend (compiled into `core` resources) |
| `docker/` | Docker image and Docker Compose configuration |

## Backend Architecture (core/)

All Java source lives under `core/src/main/java/inetsoft/`:

| Package | Purpose |
|---------|---------|
| `analytic/` | Analytic engine, composition pipeline |
| `graph/` | Chart/graph rendering engine (EGraph, GGraph, visual elements) |
| `mv/` | Materialized views for query caching and acceleration |
| `report/` | Report engine, layouts, cell bindings |
| `setup/` | Server initialization and configuration |
| `sree/` | Repository engine — asset storage, security (AuthenticationProvider, AuthorizationProvider), scheduling |
| `staging/` | Cloud staging providers |
| `storage/` | Blob storage abstraction (BlobEngine, BlobStorage) |
| `uql/` | Universal Query Language — data source definitions, conditions, query model |
| `util/` | Core utilities |
| `web/` | REST controllers, WebSocket messaging, Spring configuration |

### Key web sub-packages (core/src/main/java/inetsoft/web/)

| Package | Purpose |
|---------|---------|
| `adhoc/` | Ad-hoc query controllers |
| `admin/` | Admin API endpoints |
| `binding/` | Data binding controllers |
| `composer/` | Viewsheet/worksheet composer controllers |
| `messaging/` | WebSocket message handling (STOMP) |
| `portal/` | Portal page controllers |
| `security/` | Spring Security integration, SSO |
| `viewsheet/` | Viewsheet runtime controllers |
| `vswizard/` | Visualization wizard controllers |

### Security Model

Security providers implement `AuthenticationProvider` and `AuthorizationProvider` in `inetsoft.sree.security`. The `RepletEngine` / `AnalyticRepository` in `inetsoft.sree` manage asset repository operations.

## Frontend Architecture (web/)

Four Angular projects built independently:

| Project | Entry point | Purpose |
|---------|------------|---------|
| `portal` | `projects/portal/` | Main end-user portal (dashboards, reports, data) |
| `em` | `projects/em/` | Enterprise Manager admin UI |
| `elements` | (Angular Elements) | Embeddable web components |
| `viewer-element` | (Angular Elements) | Embeddable viewsheet viewer |

### Portal app structure (`projects/portal/src/app/`)

| Directory | Purpose |
|-----------|---------|
| `portal/` | Top-level portal routes (dashboard, report, data, schedule) |
| `composer/` | Viewsheet/worksheet design canvas |
| `vsobjects/` | Viewsheet visual object components (charts, tables, gauges, etc.) |
| `vsview/` | Viewsheet viewer runtime |
| `binding/` | Data binding UI |
| `widget/` | Reusable UI components (tree, dialogs, color-picker, formula-editor, etc.) |
| `graph/` | Chart configuration UI |
| `format/` | Format/style editors |
| `common/` | Shared services and models |

### EM app structure (`projects/em/src/app/`)

Admin features: auditing, authorization, monitoring, scheduling, security configuration.

### Shared library (`projects/shared/`)

Reusable Angular modules: `ai-assistant`, `codemirror`, `data`, `schedule`, `stomp`, `util`.

### WebSocket / Real-time

The frontend communicates with the backend via STOMP over SockJS for real-time viewsheet updates. The `stomp` shared module and `messaging/` backend package handle this.

## Data Connectors (connectors/)

JDBC connectors (MySQL, PostgreSQL, Oracle, SQL Server, Snowflake, etc.) and non-JDBC connectors (MongoDB, Elasticsearch, REST, OData, Google Docs, OneDrive, SharePoint, Cassandra, etc.). Each connector is a separate Maven module.

## Testing

**Java**: JUnit tests alongside source. Run with Maven:
```bash
./mvnw test -pl core          # Run core tests
./mvnw test -pl server        # Run server tests
```

**TypeScript**: Jest via Angular CLI. Test files are `*.spec.ts` colocated with source.