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
 * SecurityProviderService — logic-layer tests (pure computation, no HTTP/side effects)
 *
 * Risk-first coverage (7 groups, 16 cases, 1 failing):
 *   Group 1 [Risk 3, 2]         — getAuthorizationModel (2 cases)
 *   Group 2 [Risk 3, 3, 3]      — getAuthenticationModel (3 cases)
 *   Group 3 [Risk 3, 2, Bug]    — parseAdminRoles (2 cases + 1 it.failing)
 *   Group 4 [Risk 3, 2]         — formatAdminRolesString (2 cases)
 *   Group 5 [Risk 3, 2]         — getDistinctIdentityIDLabels (2 cases)
 *   Group 6 [Risk 2, 2]         — getDistinctIdentityNames (2 cases)
 *   Group 7 [Risk 2, 2]         — getOrganizationIdentityNames (2 cases)
 *
 * Confirmed bugs (test.failing — remove .failing wrapper once fixed):
 *   - parseAdminRoles("   "): whitespace-only string is truthy so it bypasses the !rolesString guard,
 *     split+trim produces [""] instead of []; blank role sent to server on every whitespace-only input.
 *     Same root cause: "admin," → ["admin",""], ",admin" → ["","admin"].
 *     Fix: add .filter(Boolean) after .map(role => role.trim())
 *
 * KEY contracts:
 *   - parseAdminRoles(null) and parseAdminRoles("") both return [] without throwing
 *   - formatAdminRolesString(null) returns "" without throwing
 *   - getDistinctIdentityIDLabels uses "__GLOBAL__" sentinel when orgID is null or empty string
 *   - getAuthenticationModel LDAP branch converts sysAdminRoles string → string[] via parseAdminRoles
 *   - getAuthenticationModel DATABASE branch preserves sysAdminRoles / orgAdminRoles as raw strings
 *
 * Design gaps:
 *   - getAuthenticationModel FILE / ACTIVE_DIRECTORY / GENERIC types fall through the switch
 *     with no sub-model extension — intentional; no provider-specific fields needed for these types
 */

import { it as jestIt } from "@jest/globals";
import { UntypedFormControl, UntypedFormGroup } from "@angular/forms";
import { IdentityId } from "../users/identity-id";
import { SecurityProviderService } from "./security-provider.service";
import { SecurityProviderType } from "./security-provider-model/security-provider-type.enum";

// ---------------------------------------------------------------------------
// Form builder helpers
// ---------------------------------------------------------------------------

function makeFileAuthzForm(name = "MyProvider"): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl(name),
      providerType: new UntypedFormControl(SecurityProviderType.FILE)
   });
}

function makeCustomAuthzForm(): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl("  Custom  "),
      providerType: new UntypedFormControl(SecurityProviderType.CUSTOM),
      customForm: new UntypedFormGroup({
         className: new UntypedFormControl("com.example.CustomProvider"),
         jsonConfiguration: new UntypedFormControl('{"key":"value"}')
      })
   });
}

function makeLdapAuthnForm(): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl("LdapProvider"),
      oldName: new UntypedFormControl("OldLdap"),
      providerType: new UntypedFormControl(SecurityProviderType.LDAP),
      ldapForm: new UntypedFormGroup({
         host: new UntypedFormControl("ldap.example.com"),
         sysAdminRoles: new UntypedFormControl("admin, superuser")
      }),
      userSearch: new UntypedFormGroup({ userBase: new UntypedFormControl("ou=users") }),
      groupSearch: new UntypedFormGroup({ groupBase: new UntypedFormControl("ou=groups") }),
      roleSearch: new UntypedFormGroup({ roleBase: new UntypedFormControl("ou=roles") })
   });
}

function makeDbAuthnForm(): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl("DbProvider"),
      oldName: new UntypedFormControl(""),
      providerType: new UntypedFormControl(SecurityProviderType.DATABASE),
      dbForm: new UntypedFormGroup({
         driver: new UntypedFormControl("com.mysql.Driver"),
         sysAdminRoles: new UntypedFormControl("sysadmin"),
         orgAdminRoles: new UntypedFormControl("orgadmin")
      })
   });
}

function makeCustomAuthnForm(): UntypedFormGroup {
   return new UntypedFormGroup({
      providerName: new UntypedFormControl("CustomProvider"),
      oldName: new UntypedFormControl(""),
      providerType: new UntypedFormControl(SecurityProviderType.CUSTOM),
      customForm: new UntypedFormGroup({
         className: new UntypedFormControl("com.example.Custom"),
         jsonConfiguration: new UntypedFormControl("{}")
      })
   });
}

// ---------------------------------------------------------------------------
// Tests
// ---------------------------------------------------------------------------

describe("SecurityProviderService — logic layer", () => {
   let service: SecurityProviderService;

   beforeEach(() => {
      // Pure logic methods use no injected dependencies; null is safe here.
      service = new SecurityProviderService(null as any, null as any, null as any, null as any, null as any);
   });

   // ---------------------------------------------------------------------------
   // Group 1 [Risk 3, 2] — getAuthorizationModel
   // ---------------------------------------------------------------------------
   describe("getAuthorizationModel", () => {
      it("[Risk 3] should populate customProviderModel when type is CUSTOM", () => {
         // 🔁 Regression-sensitive: customProviderModel is read on the server side only for CUSTOM;
         //    dropping this branch means custom class config is silently ignored
         const model = service.getAuthorizationModel(makeCustomAuthzForm());

         expect(model.providerType).toBe(SecurityProviderType.CUSTOM); // (a)
         expect(model.customProviderModel).toEqual({
            className: "com.example.CustomProvider",
            jsonConfiguration: '{"key":"value"}'
         }); // (b)
      });

      it("[Risk 2] should trim providerName and omit customProviderModel for non-CUSTOM type", () => {
         const model = service.getAuthorizationModel(makeFileAuthzForm("  MyProvider  "));

         expect(model.providerName).toBe("MyProvider"); // (a) trimmed
         expect(model.customProviderModel).toBeUndefined(); // (b)
      });
   });

   // ---------------------------------------------------------------------------
   // Group 2 [Risk 3, 3, 3] — getAuthenticationModel
   // ---------------------------------------------------------------------------
   describe("getAuthenticationModel", () => {
      it("[Risk 3] LDAP: should populate ldapProviderModel and parse sysAdminRoles to array", () => {
         // 🔁 Regression-sensitive: sysAdminRoles arrives as a string but the server expects string[];
         //    skipping parseAdminRoles here causes a type mismatch that breaks role assignment silently
         const model = service.getAuthenticationModel(makeLdapAuthnForm());

         expect(model.ldapProviderModel).toBeDefined(); // (a)
         expect(model.ldapProviderModel!.sysAdminRoles).toEqual(["admin", "superuser"]); // (b) parsed + trimmed
         expect(model.dbProviderModel).toBeUndefined(); // (c) no cross-contamination
      });

      it("[Risk 3] DATABASE: should populate dbProviderModel with sysAdminRoles and orgAdminRoles", () => {
         // 🔁 Regression-sensitive: both admin role fields are required for multi-tenant DB provider;
         //    losing either field silently removes org-admin access for that provider
         const model = service.getAuthenticationModel(makeDbAuthnForm());

         expect(model.dbProviderModel).toBeDefined(); // (a)
         expect((model.dbProviderModel as any).sysAdminRoles).toBe("sysadmin"); // (b)
         expect((model.dbProviderModel as any).orgAdminRoles).toBe("orgadmin"); // (c)
      });

      it("[Risk 3] CUSTOM: should populate customProviderModel with className and jsonConfiguration", () => {
         // 🔁 Regression-sensitive: className is required for server-side class instantiation;
         //    missing it causes a NullPointerException at provider initialisation time
         const model = service.getAuthenticationModel(makeCustomAuthnForm());

         expect(model.customProviderModel).toEqual({
            className: "com.example.Custom",
            jsonConfiguration: "{}"
         });
      });
   });

   // ---------------------------------------------------------------------------
   // Group 3 [Risk 3, 2] — parseAdminRoles
   // ---------------------------------------------------------------------------
   describe("parseAdminRoles", () => {
      it("[Risk 3] should return [] for null and empty string without throwing", () => {
         // 🔁 Regression-sensitive: callers pass the raw form value which may be null on first render;
         //    a crash here prevents the LDAP / DATABASE provider form from loading at all
         expect(service.parseAdminRoles(null!)).toEqual([]); // (a)
         expect(service.parseAdminRoles("")).toEqual([]); // (b)
      });

      it("[Risk 2] should split on commas and trim whitespace from each role", () => {
         expect(service.parseAdminRoles("admin, superuser , devops")).toEqual(["admin", "superuser", "devops"]);
      });

      it("[Risk 2] should filter blank entries after trim for defensive parsing", () => {
         // NOTE: current UI dialog validation blocks manual entry of whitespace-only / leading-trailing comma roles.
         // This is still kept as a service-level guard so non-UI callers and future UI changes cannot reintroduce blanks.
         expect(service.parseAdminRoles("   ")).toEqual([]);
         expect(service.parseAdminRoles("admin,")).toEqual(["admin"]);
         expect(service.parseAdminRoles(",admin")).toEqual(["admin"]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 4 [Risk 3, 2] — formatAdminRolesString
   // ---------------------------------------------------------------------------
   describe("formatAdminRolesString", () => {
      it("[Risk 3] should return empty string for null without throwing", () => {
         // 🔁 Regression-sensitive: result is written back to a form control via binding;
         //    null would throw "Cannot set property 'value' of null" at runtime
         expect(service.formatAdminRolesString(null!)).toBe("");
      });

      it("[Risk 2] should join roles with ', ' and return '' for empty array", () => {
         expect(service.formatAdminRolesString(["admin", "superuser"])).toBe("admin, superuser"); // (a)
         expect(service.formatAdminRolesString([])).toBe(""); // (b)
      });
   });

   // ---------------------------------------------------------------------------
   // Group 5 [Risk 3, 2] — getDistinctIdentityIDLabels
   // ---------------------------------------------------------------------------
   describe("getDistinctIdentityIDLabels", () => {
      it("[Risk 3] should deduplicate entries and use __GLOBAL__ for null or empty orgID", () => {
         // 🔁 Regression-sensitive: __GLOBAL__ is the server-side sentinel for org-less identities;
         //    passing null or "" directly would fail org-scoped lookups on the backend
         const ids: IdentityId[] = [
            { name: "alice", orgID: "org1" },
            { name: "alice", orgID: "org1" }, // duplicate → removed
            { name: "bob",   orgID: null! },  // null → __GLOBAL__
            { name: "carol", orgID: "" },     // empty → __GLOBAL__
         ];
         const result = service.getDistinctIdentityIDLabels(ids);

         expect(result).toHaveLength(3); // (a)
         expect(result).toContain("alice : org1"); // (b)
         expect(result).toContain("bob : __GLOBAL__"); // (c)
         expect(result).toContain("carol : __GLOBAL__"); // (d)
      });

      it("[Risk 2] should return empty array for empty input", () => {
         expect(service.getDistinctIdentityIDLabels([])).toEqual([]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 6 [Risk 2, 2] — getDistinctIdentityNames
   // ---------------------------------------------------------------------------
   describe("getDistinctIdentityNames", () => {
      it("[Risk 2] should return distinct names regardless of orgID differences", () => {
         const ids: IdentityId[] = [
            { name: "alice", orgID: "org1" },
            { name: "alice", orgID: "org2" }, // same name, different org → deduped
            { name: "bob",   orgID: "org1" }
         ];
         const result = service.getDistinctIdentityNames(ids);

         expect(result).toHaveLength(2); // (a)
         expect(result).toContain("alice"); // (b)
         expect(result).toContain("bob"); // (c)
      });

      it("[Risk 2] should return empty array for empty input", () => {
         expect(service.getDistinctIdentityNames([])).toEqual([]);
      });
   });

   // ---------------------------------------------------------------------------
   // Group 7 [Risk 2, 2] — getOrganizationIdentityNames
   // ---------------------------------------------------------------------------
   describe("getOrganizationIdentityNames", () => {
      it("[Risk 2] should return only names matching the specified org, deduplicated", () => {
         const ids: IdentityId[] = [
            { name: "alice", orgID: "org1" },
            { name: "alice", orgID: "org1" }, // duplicate → removed
            { name: "bob",   orgID: "org2" }  // different org → excluded
         ];

         expect(service.getOrganizationIdentityNames(ids, "org1")).toEqual(["alice"]);
      });

      it("[Risk 2] should return empty array when no identity matches the org", () => {
         const ids: IdentityId[] = [{ name: "alice", orgID: "org1" }];
         expect(service.getOrganizationIdentityNames(ids, "org2")).toEqual([]);
      });
   });
});
