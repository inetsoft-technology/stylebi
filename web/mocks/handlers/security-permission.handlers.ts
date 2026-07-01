/*
 * This file is part of StyleBI.
 * Copyright (C) 2026  InetSoft Technology
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 */

/**
 * MSW handler factory functions for permission persona scenarios.
 *
 * Usage (in a tl.spec.ts):
 *   server.use(...SecurityMswHandlers.asSiteAdmin());
 *
 * Each function returns a RequestHandler[] that overrides the default emHandlers
 * for the relevant permission endpoints. Handlers are reset after each test by
 * the vitest-setup-tl.ts afterEach hook (server.resetHandlers()).
 *
 * Paths in SITE_ADMIN_ONLY_PATHS correspond to the For-Org-× items in the
 * permission-test-architecture-design.md 区二（Actions）矩阵。
 */

import { http, HttpResponse } from "msw";
import type { RequestHandler } from "msw";

const SITE_ADMIN_ONLY_PATHS = new Set([
   "monitoring/cache",
   "monitoring/cluster",
   "monitoring/log",
   "monitoring/summary",
   "settings/content/drivers",
   "settings/content/storage",
   "settings/presentation/orgSettings",
   "settings/schedule/settings",
   "settings/schedule/status",
   "settings/security/providers",
   "settings/security/sso",
   "settings/security/googleSignIn",
   "settings/general",
   "settings/logging",
   "settings/allProperties",
   "notification",
]);

function navbarHandlers(isSiteAdmin: boolean, isOrgAdminOnly: boolean, isMultiTenant: boolean): RequestHandler[] {
   return [
      http.get("*/api/em/navbar/isSiteAdmin",    () => HttpResponse.json(isSiteAdmin)),
      http.get("*/api/em/navbar/isOrgAdminOnly", () => HttpResponse.json(isOrgAdminOnly)),
      http.get("*/api/em/navbar/isMultiTenant",  () => HttpResponse.json(isMultiTenant)),
   ];
}

export const SecurityMswHandlers = {
   /**
    * Site admin: all EM components accessible, no multi-tenancy hiding.
    * authz returns a permission entry for whatever path is requested.
    */
   asSiteAdmin: (): RequestHandler[] => [
      http.get("*/api/em/authz", ({ request }) => {
         const path = new URL(request.url).searchParams.get("path") ?? "";
         return HttpResponse.json({
            permissions: { [path]: true },
            labels: {},
            multiTenancyHiddenComponents: {},
         });
      }),
      ...navbarHandlers(true, false, true),
   ],

   /**
    * Org admin: For-Org-√ paths accessible; For-Org-× paths denied and hidden.
    */
   asOrgAdmin: (): RequestHandler[] => [
      http.get("*/api/em/authz", ({ request }) => {
         const path = new URL(request.url).searchParams.get("path") ?? "";
         const denied = SITE_ADMIN_ONLY_PATHS.has(path);
         return HttpResponse.json({
            permissions: denied ? {} : { [path]: true },
            labels: {},
            multiTenancyHiddenComponents: denied ? { [path]: true } : {},
         });
      }),
      ...navbarHandlers(false, true, true),
   ],

   /**
    * Viewer / regular user: EM not accessible (empty permissions, not an admin).
    */
   asViewer: (): RequestHandler[] => [
      http.get("*/api/em/authz", () =>
         HttpResponse.json({ permissions: {}, labels: {}, multiTenancyHiddenComponents: {} })
      ),
      ...navbarHandlers(false, false, false),
   ],

   /**
    * Anonymous / unauthenticated: 401 on all EM auth checks.
    */
   asAnonymous: (): RequestHandler[] => [
      http.get("*/api/em/authz", () => HttpResponse.json(null, { status: 401 })),
      ...navbarHandlers(false, false, false),
   ],

   /**
    * Deny all: empty permissions map. Use when testing a component's denied/locked state.
    */
   denyAll: (): RequestHandler[] => [
      http.get("*/api/em/authz", () =>
         HttpResponse.json({ permissions: {}, labels: {}, multiTenancyHiddenComponents: {} })
      ),
   ],
};
