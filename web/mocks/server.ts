/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */

/**
 * MSW server entry point for Jest (Node.js environment).
 *
 * Aggregates all domain handlers and exports a single `server` instance.
 * This server is started in setup-jest.ts and shared across all test files.
 *
 * Per-test overrides:
 *   import { server } from '<path-to>/mocks/server';
 *   import { http, HttpResponse } from 'msw';
 *
 *   server.use(http.get('*\/api/some/endpoint', () => HttpResponse.json({ ... })));
 *
 * The override is automatically reset after each test (server.resetHandlers()
 * is called in afterEach inside setup-jest.ts).
 */
import { setupServer } from "msw/node";
import { modelHandlers } from "./handlers/model.handlers";
import { composerHandlers } from "./handlers/composer.handlers";
import { emHandlers } from "./handlers/em.handlers";

export const server = setupServer(
   ...modelHandlers,
   ...composerHandlers,
   ...emHandlers,
);
