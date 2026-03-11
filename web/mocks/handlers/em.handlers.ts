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
 * MSW handlers for Enterprise Manager endpoints.
 *
 * Covers admin/security/monitoring APIs used by the em project.
 * Override per-test with server.use() for permission-denied or error scenarios.
 */
import { http, HttpResponse } from "msw";

export const emHandlers = [
   // Current organization info (navbar)
   http.get("*/em/navbar/organization", () => {
      return HttpResponse.json("host_org");
   }),

   // Repository folder list
   http.get("*/api/em/content/repository/tree", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // Create repository folder
   http.post("*/api/em/content/repository/folder", () => {
      return HttpResponse.json({ path: "/test-folder", name: "test-folder" });
   }),

   // Security provider list
   http.get("*/api/em/security/providers", () => {
      return HttpResponse.json({ providers: [] });
   }),

   // User list
   http.get("*/api/em/security/users", () => {
      return HttpResponse.json({ users: [] });
   }),

   // Schedule task list
   http.get("*/api/em/schedule/tasks", () => {
      return HttpResponse.json({ tasks: [] });
   }),
];
