/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
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
 * MSW handlers for Viewer endpoints.
 *
 * Covers the ../api/vs/**, ../api/viewsheet/** API paths
 * used by components in the viewer. All defaults are the "happy path"
 * minimal responses needed for a component to finish initialisation without errors.
 *
 * Per-test overrides:
 *   import { server } from '<path>/mocks/server';
 *   import { http, HttpResponse } from 'msw';
 *   server.use(http.get('*\/api/vs/someEndpoint', () => HttpResponse.json(myData)));
 */
import { http, HttpResponse } from "msw";

export const viewerHandlers = [

   // EmailDialog.ok() — validate email addresses before sending
   http.get("*/api/vs/check-email-valid", () => {
      return HttpResponse.json({
         messageCommand: { type: "OK", message: "" },
         addressHistory: [],
      });
   }),

];
