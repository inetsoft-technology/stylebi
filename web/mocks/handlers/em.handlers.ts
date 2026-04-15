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
 * MSW handlers for Enterprise Manager endpoints.
 *
 * Covers admin/security/monitoring APIs used by the em project.
 * Override per-test with server.use() for permission-denied or error scenarios.
 */
import { http, HttpResponse } from "msw";

export const emHandlers = [
   // Current organization info (navbar) — all callers use ../api/em/navbar/organization
   http.get("*/api/em/navbar/organization", () => {
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

   // Identity theme list — returned on every EditIdentityView init (ngOnInit)
   http.get("*/api/em/security/themes", () => {
      return HttpResponse.json({ themes: [] });
   }),

   // Identity names for the add-member dropdown — returned on every EditIdentityView init()
   http.get("*/api/em/security/providers/:provider/identities/:index", () => {
      return HttpResponse.json({ identityNames: [] });
   }),

   // Schedule task list
   http.get("*/api/em/schedule/tasks", () => {
      return HttpResponse.json({ tasks: [] });
   }),

   // Navbar admin-role flags — used by ResourcePermissionComponent.ngOnInit()
   // Defaults mirror the class-field defaults (both true) so tests that don't
   // override these handlers see behaviour consistent with the component's own defaults.
   http.get("*/api/em/navbar/isOrgAdminOnly", () => {
      return HttpResponse.json(true);
   }),
   http.get("*/api/em/navbar/isSiteAdmin", () => {
      return HttpResponse.json(true);
   }),
];
