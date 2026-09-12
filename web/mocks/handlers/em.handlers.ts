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

   // Security tree root — UsersSettingsPageComponent.refreshTree()
   http.get("*/api/em/security/user/get-security-tree-root/*", () => {
      return HttpResponse.json({
         users: { identityID: { name: "Users", orgID: "" }, type: 1, children: [], readOnly: false, organization: "" },
         groups: { identityID: { name: "Groups", orgID: "" }, type: 2, children: [], readOnly: false, organization: "" },
         roles: { identityID: { name: "Roles", orgID: "" }, type: 3, children: [], readOnly: false, organization: "" },
         organizations: { identityID: { name: "Organizations", orgID: "" }, type: 4, children: [], readOnly: false, organization: "" },
         editable: true,
         isMultiTenant: false,
         namedUsers: false,
      });
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

   // Security settings page — defaults: security off, multi-tenancy off, no LDAP
   http.get("*/api/em/security/get-enable-security", () => {
      return HttpResponse.json({ enable: false, toggleDisabled: false, ldapProviderUsed: false });
   }),
   http.get("*/api/em/security/get-multi-tenancy", () => {
      return HttpResponse.json({
         enable: false, toggleDisabled: false, ldapProviderUsed: false, passOrgIdAs: "domain"
      });
   }),
   http.get("*/api/em/security/get-enable-self-signup", () => {
      return HttpResponse.json({ enable: false, toggleDisabled: false, ldapProviderUsed: false });
   }),

   // Authorization permissions — used by AuthorizationService.getPermissions() on every
   // component that injects it. Returns empty permissions so no feature is blocked by default.
   http.get("*/api/em/authz", () => {
      return HttpResponse.json({ permissions: {}, labels: {}, multiTenancyHiddenComponents: {} });
   }),

   // Multi-tenancy flag — used by several security/SSO components on init.
   http.get("*/api/em/navbar/isMultiTenant", () => {
      return HttpResponse.json(false);
   }),

   // Cloud secrets flag — used by SSO settings page on init.
   http.get("*/api/em/security/isCloudSecrets", () => {
      return HttpResponse.json(false);
   }),

   // Page header model — PageHeaderComponent calls this on init for breadcrumb/title.
   http.get("*/api/em/pageheader/get-pageheader-model", () => {
      return HttpResponse.json({ title: "", currentOrgId: null, orgList: [] });
   }),

   // Email-browser-enabled flag — schedule task email action checks this on init.
   http.get("*/api/em/schedule/task/action/email-browser-enabled", () => {
      return HttpResponse.json(false);
   }),

   // Viewsheet folder tree — ViewsheetDataSource constructor calls this immediately on init.
   // Returning an empty node list is safe: transform() produces no nodes, setData() succeeds,
   // and the catchError/snackBar path (which triggers NG0205 on a destroyed injector) is never reached.
   http.get("*/api/em/schedule/task/action/viewsheet/folders", () => {
      return HttpResponse.json({ nodes: [] });
   }),

   // Current user info — security components use this to display logged-in user context.
   http.get("*/api/em/security/get-current-user", () => {
      return HttpResponse.json({ name: "admin", orgID: "host_org" });
   }),

   // Authentication provider queries — OrganizationDropdownService calls one of these on init
   // depending on whether the current user is a system admin. The default user returned above
   // has no isSysAdmin flag, so the non-admin path (get-current-authentication-provider) fires.
   // configured-authentication-providers covers the sys-admin path for tests that override the
   // current-user response.
   http.get("*/api/em/security/get-current-authentication-provider", () => {
      return HttpResponse.json(null);
   }),
   http.get("*/api/em/security/configured-authentication-providers", () => {
      return HttpResponse.json({ providers: [] });
   }),

   // First day of week preference — date/time condition editors request this on init.
   http.get("*/api/first-day-of-week", () => {
      return HttpResponse.json(0);
   }),

   // Organization info — org-related components call this on init.
   http.get("*/api/org/info", () => {
      return HttpResponse.json({ orgID: "host_org", name: "Default Organization" });
   }),

   // SSO settings — fallback default so specs that don't set up their own handler don't error.
   http.get("*/api/sso/settings", () => {
      return HttpResponse.json({
         activeFilterType: "NONE",
         roles: [],
         selectedRoles: [],
         fallbackLogin: false,
         logoutUrl: "",
         logoutPath: "",
         samlAttributesModel: {
            spEntityId: "", assertionUrl: "", idpEntityId: "",
            idpSignOnUrl: "", idpLogoutUrl: "", idpPublicKey: "",
            roleClaim: "", groupClaim: "", orgIDClaim: "",
         },
         openIdAttributesModel: null,
         customAttributesModel: null,
      });
   }),

   // Context help link directive default.
   http.get("*/api/em/help-url", () => {
      return HttpResponse.json("");
   }),
   http.get("*/api/em/help-links", () => {
      return HttpResponse.json({ links: [] });
   }),

   // Common organization list used by audit filters.
   http.get("*/api/em/security/users/get-all-organization-names/", () => {
      return HttpResponse.json([]);
   }),

   // Audit components often render shared table/filter children that immediately
   // request parameters and the first page of data. Individual tests override
   // these defaults when the payload matters.
   http.get("*/api/em/monitoring/audit/:endpoint", ({ params }) => {
      const endpoint = String(params.endpoint);

      if(endpoint.endsWith("Parameters")) {
         return HttpResponse.json({
            types: [],
            actions: [],
            states: [],
            hosts: [],
            users: [],
            groups: [],
            assets: [],
            targetAssets: [],
            dependentAssets: [],
            organizations: [],
            organizationFilter: true,
            systemAdministrator: false,
            startTime: 0,
            endTime: 1000,
         });
      }

      return HttpResponse.json({ totalRowCount: 0, rows: [] });
   }),
   http.post("*/api/em/monitoring/audit/:endpoint", () => {
      return HttpResponse.json({ totalRowCount: 0, rows: [] });
   }),
];
