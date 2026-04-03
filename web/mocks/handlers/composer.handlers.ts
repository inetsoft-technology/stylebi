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
 * MSW handlers for Composer-related endpoints.
 *
 * Covers viewsheet CRUD operations used by the composer canvas and dialogs.
 * Override individual handlers per-test with server.use() for error scenarios.
 */
import { http, HttpResponse } from "msw";

export const composerHandlers = [
   // Load viewsheet
   http.get("*/api/composer/viewsheet/:id", ({ params }) => {
      return HttpResponse.json({
         id: params["id"],
         name: "Test Viewsheet",
         objects: [],
      });
   }),

   // Save viewsheet
   http.put("*/api/composer/viewsheet/:id/save", () => {
      return HttpResponse.json({ saved: true });
   }),

   // Delete viewsheet — default allows deletion
   http.delete("*/api/composer/viewsheet/:id", () => {
      return new HttpResponse(null, { status: 200 });
   }),

   // Open viewsheet for editing
   http.post("*/api/composer/viewsheet/open", () => {
      return HttpResponse.json({ runtimeId: "test-runtime-id" });
   }),

   // Close viewsheet
   http.post("*/api/composer/viewsheet/close/:id", () => {
      return new HttpResponse(null, { status: 200 });
   }),
];
