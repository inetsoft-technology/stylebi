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
 * MSW handlers for ModelService-related endpoints.
 *
 * Covers the ../api/ paths used by ModelService (getModel, sendModel, putModel, etc.).
 * In JSDOM, relative paths resolve to http://localhost/, so "../api/foo" becomes
 * "http://localhost/api/foo". The wildcard prefix "*" handles any origin.
 */
import { http, HttpResponse } from "msw";

export const modelHandlers = [
   // Portal data-source browser
   http.get("*/api/portal/content/data-source/:name", () => {
      return HttpResponse.json({ tables: [], columns: [] });
   }),

   // Materialized-view global resource check
   http.get("*/api/portal/content/materialized-view/isOrgAccessGlobalMV/:org", () => {
      return HttpResponse.json(false);
   }),

   // Generic POST binding save (composer → vs/binding)
   http.post("*/api/composer/vs/binding/:id", () => {
      return HttpResponse.json({ message: "Saved successfully" });
   }),

   // AI assistant endpoints — ModalHeaderComponent requests these on init.
   // Return null for all of them so the feature is gracefully disabled in tests.
   http.get("*/api/assistant/*", () => {
      return new HttpResponse(null, { status: 200 });
   }),
];
