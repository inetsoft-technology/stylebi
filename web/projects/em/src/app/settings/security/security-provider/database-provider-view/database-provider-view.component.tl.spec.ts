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
 * DatabaseProviderViewComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - login credential validators and canTestConnection must agree.
 *   Group 2 [Risk 2] - hash algorithm: only supported exact algorithms are valid.
 *   Group 3 [Risk 2] - service callbacks: test connection, query preview, and admin-role editor
 *                      must pass the parent provider form and write returned state back.
 *   Group 4 [Risk 2] - change emission: dbForm edits must notify the parent.
 *
 * KEY contracts:
 *   - requiresLogin=false clears secretId, user, and password validators.
 *   - useCredential=true requires secretId and clears user/password validators.
 *   - HASH_ALGORITHMS validation is exact and case-sensitive.
 */

import { CommonModule } from "@angular/common";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule, UntypedFormGroup } from "@angular/forms";
import { MatAutocompleteModule } from "@angular/material/autocomplete";
import { MatButtonModule } from "@angular/material/button";
import { MatCardModule } from "@angular/material/card";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatIconModule } from "@angular/material/icon";
import { MatInputModule } from "@angular/material/input";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render, waitFor } from "@testing-library/angular";
import { of } from "rxjs";

import { DatabaseAuthenticationProviderModel } from "../security-provider-model/database-authentication-provider-model";
import { SecurityProviderService } from "../security-provider.service";
import { DatabaseProviderViewComponent } from "./database-provider-view.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const makeModel = (
   overrides: Partial<DatabaseAuthenticationProviderModel> = {},
): DatabaseAuthenticationProviderModel => ({
   driver: "org.h2.Driver",
   url: "jdbc:h2:mem:test",
   requiresLogin: true,
   useCredential: false,
   secretId: "",
   user: "db-user",
   password: "db-pass",
   hashAlgorithm: "SHA-256",
   userQuery: "select * from users where name = ?",
   groupListQuery: "select name from groups",
   userListQuery: "select name from users",
   groupUsersQuery: "select user from group_users",
   roleListQuery: "select name from roles",
   organizationListQuery: "select name from organizations",
   organizationNameQuery: "select name from organizations where id = ?",
   organizationMembersQuery: "select member from organization_members",
   organizationRolesQuery: "select role from organization_roles",
   userRolesQuery: "select role from user_roles",
   userRoleListQuery: "select role from user_roles",
   appendSalt: false,
   userEmailsQuery: "select email from users",
   sysAdminRoles: "sys-admin",
   orgAdminRoles: "org-admin",
   ...overrides,
});

function makeServiceMock() {
   return {
      triggerUserListQuery: jest.fn(),
      triggerGroupListQuery: jest.fn(),
      triggerRoleListQuery: jest.fn(),
      triggerOrganizationListQuery: jest.fn(),
      triggerUsersQuery: jest.fn(),
      triggerUserRolesQuery: jest.fn(),
      triggerUserRoleListQuery: jest.fn(),
      triggerUserEmailsQuery: jest.fn(),
      triggerGroupUsersQuery: jest.fn(),
      triggerOrganizationMembersQuery: jest.fn(),
      triggerOrganizationNameQuery: jest.fn(),
      testDatabaseConnection: jest.fn().mockReturnValue(of({ status: "Connected" })),
      getAdminRoles: jest.fn().mockImplementation((_current: string, _form: UntypedFormGroup, sysAdmin: boolean) =>
         of(sysAdmin ? "sys-a, sys-b" : "org-a, org-b"),
      ),
   };
}

interface RenderOpts {
   model?: DatabaseAuthenticationProviderModel;
   isMultiTenant?: boolean;
   isCloudSecrets?: boolean;
}

async function renderComponent(opts: RenderOpts = {}) {
   const form = new UntypedFormGroup({});
   const serviceMock = makeServiceMock();

   const result = await render(DatabaseProviderViewComponent, {
      imports: [
         CommonModule,
         ReactiveFormsModule,
         NoopAnimationsModule,
         MatAutocompleteModule,
         MatButtonModule,
         MatCardModule,
         MatCheckboxModule,
         MatFormFieldModule,
         MatIconModule,
         MatInputModule,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: SecurityProviderService, useValue: serviceMock }],
      componentProperties: {
         form,
         isMultiTenant: opts.isMultiTenant ?? false,
         isCloudSecrets: opts.isCloudSecrets ?? false,
      },
   });

   const comp = result.fixture.componentInstance as DatabaseProviderViewComponent;
   comp.model = opts.model ?? makeModel();
   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return { ...result, comp, form, serviceMock };
}

function setConnectionFields(comp: DatabaseProviderViewComponent): void {
   comp.dbForm.patchValue({
      driver: "org.h2.Driver",
      url: "jdbc:h2:mem:test",
      hashAlgorithm: "SHA-256",
      user: "",
      password: "",
      secretId: "",
   });
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - login credential validators and canTestConnection
// ---------------------------------------------------------------------------

describe("DatabaseProviderViewComponent - login credential validators", () => {

   // Regression-sensitive: disabling login must clear all credential validators; otherwise
   // a database that does not require login cannot be tested or saved.
   it("should clear credential validators when requiresLogin is false", async () => {
      const { comp } = await renderComponent();
      setConnectionFields(comp);

      comp.dbForm.controls["requiresLogin"].setValue(false);

      expect(comp.dbForm.controls["secretId"].errors?.["required"]).toBeFalsy();
      expect(comp.dbForm.controls["user"].errors?.["required"]).toBeFalsy();
      expect(comp.dbForm.controls["password"].errors?.["required"]).toBeFalsy();
      expect(comp.canTestConnection).toBe(true);
   });

   // Regression-sensitive: secretId is the credential when useCredential=true.
   it("should keep Test Connection disabled when useCredential=true and secretId is empty", async () => {
      const { comp } = await renderComponent({ isCloudSecrets: true });
      setConnectionFields(comp);

      comp.dbForm.controls["useCredential"].setValue(true);

      expect(comp.dbForm.controls["secretId"].errors?.["required"]).toBeTruthy();
      expect(comp.dbForm.controls["user"].errors?.["required"]).toBeFalsy();
      expect(comp.dbForm.controls["password"].errors?.["required"]).toBeFalsy();
      expect(comp.canTestConnection).toBe(false);

      comp.dbForm.controls["secretId"].setValue("db-secret");

      expect(comp.canTestConnection).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - hash algorithm
// ---------------------------------------------------------------------------

describe("DatabaseProviderViewComponent - hash algorithm validation", () => {

   // Regression-sensitive: algorithm validation is exact; a case-only mismatch must not be
   // accepted because the backend receives the string as an algorithm identifier.
   it("should reject unsupported or case-mismatched hash algorithms and accept exact names", async () => {
      const { comp } = await renderComponent();

      comp.dbForm.controls["hashAlgorithm"].setValue("sha-256");
      expect(comp.dbForm.controls["hashAlgorithm"].errors?.["unsupported"]).toBe(true);
      expect(comp.dbForm.controls["hashAlgorithm"].errors?.["required"]).toBeFalsy();

      comp.dbForm.controls["hashAlgorithm"].setValue("SHA-256");
      expect(comp.dbForm.controls["hashAlgorithm"].errors).toBeNull();
   });

   // Regression-sensitive: autocomplete should remain case-insensitive and prefix-based.
   // Losing this behavior makes common SHA searches look empty even though valid options exist.
   it("should filter hash algorithm suggestions case-insensitively by prefix", async () => {
      const { comp } = await renderComponent();
      const emissions: string[][] = [];
      const sub = comp.filteredAlgorithms.subscribe(values => emissions.push(values));

      comp.dbForm.controls["hashAlgorithm"].setValue("sha-2");

      await waitFor(() => expect(emissions[emissions.length - 1]).toContain("SHA-256"));
      expect(emissions[emissions.length - 1]).toContain("SHA-224");
      expect(emissions[emissions.length - 1]).not.toContain("SHA-1");

      sub.unsubscribe();
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - service callbacks
// ---------------------------------------------------------------------------

describe("DatabaseProviderViewComponent - service callbacks", () => {

   // Regression-sensitive: Test Connection must use the parent provider form, not only dbForm,
   // because provider name/type live outside the nested database form.
   it("should call testDatabaseConnection with the parent form and update connectionStatus", async () => {
      const { comp, form, serviceMock } = await renderComponent();

      comp.testConnection();

      expect(serviceMock.testDatabaseConnection).toHaveBeenCalledWith(form);
      expect(comp.connectionStatus).toBe("Connected");
   });

   // Regression-sensitive: query preview callbacks carry multi-tenant context for user/group
   // lookups, while organization list uses the parent form without the flag.
   it("should route query item callbacks to SecurityProviderService with the expected arguments", async () => {
      const { comp, form, serviceMock } = await renderComponent({ isMultiTenant: true });

      comp.dbFormItems.find(item => item.formControlName === "userListQuery").callback();
      comp.dbFormItems.find(item => item.formControlName === "organizationListQuery").callback();

      expect(serviceMock.triggerUserListQuery).toHaveBeenCalledWith(form, true);
      expect(serviceMock.triggerOrganizationListQuery).toHaveBeenCalledWith(form);
   });

   // Regression-sensitive: editRoles writes the dialog result back to the correct control.
   // A swapped sys/org target silently grants the wrong admin scope.
   it("should update sysAdminRoles and orgAdminRoles from getAdminRoles results", async () => {
      const { comp, form, serviceMock } = await renderComponent();
      comp.dbForm.controls["sysAdminRoles"].setValue("old-sys");
      comp.dbForm.controls["orgAdminRoles"].setValue("old-org");

      comp.editRoles(true);
      comp.editRoles(false);

      await waitFor(() => {
         expect(comp.dbForm.controls["sysAdminRoles"].value).toBe("sys-a, sys-b");
         expect(comp.dbForm.controls["orgAdminRoles"].value).toBe("org-a, org-b");
      });
      expect(serviceMock.getAdminRoles).toHaveBeenCalledWith("old-sys", form, true);
      expect(serviceMock.getAdminRoles).toHaveBeenCalledWith("old-org", form, false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] - change emission
// ---------------------------------------------------------------------------

describe("DatabaseProviderViewComponent - change emission", () => {

   // Regression-sensitive: parent detail pages rely on changed to enable Apply. If the
   // subscription is lost when model is patched, edits become unsavable.
   it("should emit changed when dbForm values diverge from the input model", async () => {
      const { comp } = await renderComponent();
      const emitted: void[] = [];
      comp.changed.subscribe(() => emitted.push(undefined));

      comp.dbForm.controls["driver"].setValue("org.postgresql.Driver");

      await waitFor(() => expect(emitted.length).toBeGreaterThan(0));
   });

   // Regression-sensitive: rebinding the input model should replace the old subscription instead
   // of emitting while patching the new model or emitting duplicate changes for one user edit.
   it("should not duplicate changed emissions after model is rebound", async () => {
      const { comp } = await renderComponent();
      const emitted: void[] = [];
      comp.changed.subscribe(() => emitted.push(undefined));

      comp.model = makeModel({ url: "jdbc:h2:mem:next" });

      expect(emitted).toHaveLength(0);

      comp.dbForm.controls["driver"].setValue("org.postgresql.Driver");

      expect(emitted).toHaveLength(1);
   });
});
