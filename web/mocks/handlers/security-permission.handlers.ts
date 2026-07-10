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
 * AuthorizationService.getPermissions(parent) returns permissions keyed by bare child
 * name relative to `parent` (see its own doc comment); real callers (sidenav/tab-bar
 * components, authorization-guard.service.ts) always read `p.permissions[child]`, never
 * a compound "parent/child" string. ORG_ADMIN_CHILD_PERMISSIONS below is therefore keyed
 * by parent path, each value a full child map, so the mock can respond correctly no matter
 * which parent path a given component requests. `false` entries are the For-Org-× items in
 * the permission-matrix-actions.md capability matrix; everything else is For-Org-√ (true).
 */

import { http, HttpResponse } from "msw";
import type { RequestHandler } from "msw";

const ORG_ADMIN_CHILD_PERMISSIONS: Record<string, Record<string, boolean>> = {
   "": { auditing: true, monitoring: true, settings: true, notification: false },
   "monitoring": {
      summary: false, cluster: false, log: false, cache: false,
      viewsheets: true, queries: true, users: true,
   },
   "settings": {
      general: false, logging: false, properties: false,
      security: true, content: true, schedule: true, presentation: true,
   },
   "settings/content": {
      "drivers-and-plugins": false, "data-space": false,
      "materialized-views": true, "repository": true,
   },
   "settings/presentation": {
      "org-settings": false, "settings": true, "themes": true,
   },
   "settings/schedule": {
      "settings": false, "status": false, "tasks": true, "cycles": true,
   },
   "settings/security": {
      provider: false, sso: false, googleSignIn: false, actions: true, users: true,
   },
};

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
         const childPermissions = ORG_ADMIN_CHILD_PERMISSIONS[path];
         const permissions = childPermissions ? { ...childPermissions } : { [path]: true };
         const multiTenancyHiddenComponents: Record<string, boolean> = {};

         if(childPermissions) {
            Object.keys(childPermissions).forEach(child => {
               if(!childPermissions[child]) {
                  multiTenancyHiddenComponents[child] = true;
               }
            });
         }

         return HttpResponse.json({ permissions, labels: {}, multiTenancyHiddenComponents });
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
