# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

StyleBI is a cloud-native, open-source business intelligence web application (AGPL-3.0) built by InetSoft Technology. It is a Maven multi-module project with a Java 21 / Spring Boot 3.5 backend and an Angular 15 frontend.

GitHub requires a [classic personal access token](https://docs.github.com/en/authentication/keeping-your-account-and-data-secure/managing-your-personal-access-tokens) with `read:packages` scope configured in `~/.m2/settings.xml` as server id `gh-stylebi` to resolve dependencies from the GitHub Packages Maven repository.

## Build Commands

### Java (Maven)

```shell
# Full build (all modules)
./mvnw clean install

# Skip tests to speed up
./mvnw clean install -DskipTests

# Force re-download of remote dependencies
./mvnw clean install -U

# Build a specific module (e.g., core)
./mvnw install -pl core

# Run tests for a specific module
./mvnw test -pl core

# Run a single test class
./mvnw test -pl core -Dtest=MyTestClass

# Run a single test method
./mvnw test -pl core -Dtest=MyTestClass#myMethod

# Build Docker image (requires prior Java build)
./mvnw clean package jib:dockerBuild -pl docker

# Build Java libraries and Docker image together
./mvnw clean install -PdockerImage
```

### Frontend (Angular — run from `web/` directory)

```shell
# Install dependencies
npm install

# Development build
npm run build

# Production build
npm run build:prod

# Watch mode (dev)
npm run build:watch

# Run all tests
npm test

# Run Enterprise Manager tests only
npm run test:em

# Lint
npm run lint
```

## Architecture

### Maven Modules

| Module | Artifact | Purpose |
|--------|----------|---------|
| `bom` | inetsoft-stylebi-bom | Dependency version management (BOM) |
| `build-tools` | — | Custom Maven plugins: `antlr2-maven-plugin`, `node-maven-plugin`, `path-utils-maven-plugin`, `tern-annotations`, etc. |
| `core` | inetsoft-core | Core BI engine library (see below) |
| `server` | inetsoft-server | Spring Boot application entry point |
| `connectors` | — | Optional pluggable data connectors (see below) |
| `utils` | — | Utility JARs: SSL helpers, MapDB storage, XML formats |
| `web` | stylebi-portal | Angular frontend (see below) |
| `docker` | inetsoft-docker-community | Docker image packaging via Jib |

### Core Module (`core/src/main/java/inetsoft/`)

The vast majority of business logic lives in `inetsoft-core`.

- **`graph/`** — Charting/visualization engine (`EGraph` is the main entry point; sub-packages for `aesthetic`, `coord`, `data`, `element`, `scale`, `schema`)
- **`uql/`** — Universal Query Layer: data source abstraction, query execution
  - **`uql/asset/`** — Asset model: Worksheets (data transformation) and Viewsheets (dashboards)
  - **`uql/viewsheet/`** — Viewsheet assembly types (charts, tables, selection lists, etc.)
  - **`uql/jdbc/`** — JDBC connection pooling and driver management
  - **`uql/tabular/`** — Tabular (REST/structured) data sources
- **`report/`** — Report engine and output formats (PDF, etc.)
- **`sree/`** — Server Runtime Environment: `RepletEngine`, repository, scheduling, security
- **`web/`** — Spring MVC controllers, WebSocket messaging, REST API endpoints, and Thymeleaf views organized by feature area (`viewsheet/`, `composer/`, `portal/`, `binding/`, etc.)
- **`util/`** — Core utilities, configuration (`SreeEnv`), storage abstractions, Groovy scripting support
- **`analytic/`** — AnalyticAssistant and composition layer
- **`storage/`** — Storage abstraction layer (blob, indexed, etc.)

The `core` module uses annotation processors at compile time: Immutables (`@Value`), `auto-service`, `record-builder-processor`, and a custom `tern-annotations` processor that generates JS function metadata for the scripting engine.

### Server Module (`server/`)

Thin Spring Boot launcher (`InetsoftApplication`/`BaseInetsoftApplication`) plus health checks and metrics. At runtime it wires together everything from `inetsoft-core`.

### Connectors (`connectors/`)

Pluggable data source connectors packaged as ZIP archives:
- NoSQL: `inetsoft-aerospike`, `inetsoft-cassandra`, `inetsoft-elastic`, `inetsoft-mongodb`, `inetsoft-orientdb`
- Cloud/SaaS: `inetsoft-googledoc`, `inetsoft-onedrive`, `inetsoft-sharepoint-online`, `inetsoft-odata`, `inetsoft-datagov`
- Big Data: `inetsoft-hive`, `inetsoft-r`
- JDBC wrappers: `jdbc-access`, `jdbc-derby`, `jdbc-h2`, `jdbc-mysql`, `jdbc-oracle`, `jdbc-postgresql`, `jdbc-snowflake`, `jdbc-sqlserver`, `jdbc-drill`, `jdbc-hpcc`
- Other: `inetsoft-rest`, `inetsoft-serverfile`, `inetsoft-tabular-util`

### Frontend (`web/`)

Angular 15 workspace with multiple projects (`web/projects/`):

- **`portal`** — Main user-facing application: viewer, composer (dashboard/worksheet editor), binding UI, VS wizard, graph editor
- **`em`** — Enterprise Manager: server administration UI (auditing, authorization, monitoring, scheduling, etc.)
- **`shared`** — Shared Angular library used by both `portal` and `em`: STOMP/WebSocket client, CKEditor wrapper, CodeMirror wrapper, security utilities, schedule models

The `gulpfile.js` handles font generation and bundling of `elements` and `viewer-element` builds. Angular output is written to `target/generated-resources/ng/` and consumed by the Maven build.

### WebSocket Communication

The frontend communicates with the backend primarily over STOMP-over-SockJS WebSockets (configured in `WebSocketConfig.java` in core). Commands and events flow between Angular controllers and Java controller event handlers in `inetsoft.web.viewsheet.` and `inetsoft.web.composer.`.

### Test Configuration

Default surefire exclusion groups: `rest-api-doc`, `penetration`, `integration`, `slow`. Tests run with locale/timezone fixed to `en_US` / `America/New_York`. The `SreeHomeExtension` JUnit extension auto-initializes the `sree.home` directory for tests.