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
 * EditIdentityViewComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — reset(): must restore all state layers (model, form fields, members/roles,
 *                       password controls) and emit pageChanged(false).
 *   Group 2 [Risk 3] — apply(): org-save must call beginOrgSave(), set identityEditable=false,
 *                       emit the correct typed event, and stamp model.oldName before emitting.
 *   Group 3 [Risk 2] — password form: new user keeps group always enabled;
 *                       existing user toggles via changePasswordEnabled;
 *                       mismatch produces group-level error.
 *   Group 4 [Risk 2] — updateModel(): name is trimmed; empty name leaves model.name unchanged;
 *                       role-specific fields (defaultRole, sysAdmin, description) are synced.
 *   Group 5 [Risk 2] — identityEditable: Subject emissions and model-setter reset.
 *
 * Confirmed bugs (it.fails — remove wrapper once fixed):
 *   (none currently identified)
 *
 * KEY contracts:
 *   - reset() calls restoreState() then setOriginalState() — model object reference changes.
 *   - apply() for ORGANIZATION type calls orgBusy.beginOrgSave() and sets identityEditable=false.
 *   - updateModel() only updates model.name when form value is truthy (guards against empty string).
 *   - passwordGroup is disabled by default for existing users; enabled only via updatePassword(true).
 *   - IdentityType values: USER=0, GROUP=1, ROLE=2, ORGANIZATION=4.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA, SimpleChanges } from "@angular/core";
import { HttpClientModule } from "@angular/common/http";
import {
   ControlValueAccessor,
   FormsModule,
   NG_VALUE_ACCESSOR,
   UntypedFormGroup,
   ReactiveFormsModule,
} from "@angular/forms";
import { it } from "@jest/globals"; // must import to enable it.failing
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatInputModule } from "@angular/material/input";
import { MatSelectModule } from "@angular/material/select";
import { ErrorStateMatcher } from "@angular/material/core";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { BehaviorSubject, Subject } from "rxjs";

import { EditIdentityViewComponent } from "./edit-identity-view.component";
import { IdentityType } from "../../../../../../../shared/data/identity-type";
import {
   EditGroupPaneModel,
   EditIdentityPaneModel,
   EditOrganizationPaneModel,
   EditRolePaneModel,
   EditUserPaneModel,
} from "../edit-identity-pane/edit-identity-pane.model";
import { SecurityBusyService } from "../security-busy.service";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const makeUserModel = (overrides: Partial<EditUserPaneModel> = {}): EditUserPaneModel => ({
   name: "testuser",
   organization: "DefaultOrg",
   root: false,
   identityNames: [],
   members: [],
   roles: [],
   permittedIdentities: [],
   editable: true,
   status: true,
   alias: "",
   email: "",
   locale: "",
   password: "Password1!",
   currentUser: false,
   localesList: [],
   supportChangePassword: true,
   newUser: false,
   hasPassword: true,
   ...overrides,
});

const makeRoleModel = (overrides: Partial<EditRolePaneModel> = {}): EditRolePaneModel => ({
   name: "testrole",
   organization: "DefaultOrg",
   root: false,
   identityNames: [],
   members: [],
   roles: [],
   permittedIdentities: [],
   editable: true,
   defaultRole: false,
   isSysAdmin: false,
   isOrgAdmin: false,
   description: "",
   ...overrides,
});

const makeOrgModel = (overrides: Partial<EditOrganizationPaneModel> = {}): EditOrganizationPaneModel => ({
   name: "TestOrg",
   organization: null,
   root: false,
   identityNames: [],
   members: [],
   roles: [],
   permittedIdentities: [],
   editable: true,
   id: "testorg",
   properties: [],
   locale: "",
   localesList: [],
   currentUserName: "admin",
   ...overrides,
});

const makeGroupModel = (overrides: Partial<EditIdentityPaneModel> = {}): EditIdentityPaneModel => ({
   name: "testgroup",
   organization: "DefaultOrg",
   root: false,
   identityNames: [],
   members: [],
   roles: [],
   permittedIdentities: [],
   editable: true,
   ...overrides,
});

// em-email-picker uses formControlName="email" — provide a minimal CVA stub so
// ReactiveFormsModule can wire it up without importing the real module.
@Component({
   selector: "em-email-picker",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EmailPickerStub), multi: true }],
})
class EmailPickerStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: {
   model?: EditIdentityPaneModel;
   type?: IdentityType;
   provider?: string;
   isSysAdmin?: boolean;
} = {}) {
   const editableSubject = new Subject<boolean>();
   const orgBusySpy = {
      orgLoading$: new BehaviorSubject(false).asObservable(),
      orgLoading: false,
      beginOrgSave: jest.fn(),
      endOrgSave: jest.fn(),
   };

   const result = await render(EditIdentityViewComponent, {
      imports: [FormsModule, ReactiveFormsModule, NoopAnimationsModule, HttpClientModule,
                MatCheckboxModule, MatInputModule, MatSelectModule],
      declarations: [EmailPickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ErrorStateMatcher, useValue: { isErrorState: () => false } },
         { provide: SecurityBusyService, useValue: orgBusySpy },
      ],
      componentProperties: {
         identityEditableChanges: editableSubject,
         provider: props.provider ?? "DefaultProvider",
         type: props.type ?? IdentityType.USER,
         model: props.model,
         isSysAdmin: props.isSysAdmin ?? false,
         treeData: [],
      },
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance,
      editableSubject,
      orgBusySpy,
   };
}

// ================================================================
// Group 1 [Risk 3] — reset(): full state restoration
// ================================================================

describe("EditIdentityViewComponent — reset(): full state restoration", () => {

   // 🔁 Regression-sensitive: reset() must restore both the model AND the form controls.
   // If only one layer is restored, the UI shows the original value but the next apply()
   // submits the edited value (or vice versa), corrupting server state silently.
   it("should restore name and theme in both form controls and model after editing", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "original", theme: "theme1" }),
         type: IdentityType.USER,
      });

      comp.form.controls["name"].setValue("edited");
      comp.form.controls["theme"].setValue("theme2");
      comp.updateModel();

      comp.reset();

      expect(comp.form.controls["name"].value).toBe("original");
      expect(comp.form.controls["theme"].value).toBe("theme1");
      expect(comp.model.name).toBe("original");
      expect(comp.model.theme).toBe("theme1");
   });

   // members and roles arrays live parallel to the model —
   // reset() must restore them from originalModel, not just from the in-memory arrays.
   // Stale array state leaves removed/added identities visible after reset.
   it("should restore members and roles arrays to original empty state after mutation", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ members: [], roles: [] }),
         type: IdentityType.USER,
      });

      const fakeID = { name: "user1", orgID: "DefaultOrg" };
      comp.members = [{ identityID: fakeID, type: IdentityType.USER }];
      comp.roles  = [{ identityID: fakeID, type: IdentityType.ROLE }];

      comp.reset();

      expect(comp.members).toHaveLength(0);
      expect(comp.roles).toHaveLength(0);
   });

   // reset() for normal user (hasPassword=true) must set
   // changePasswordEnabled=false and clear the password input fields.
   // Leaving changePasswordEnabled=true would send an empty password on the next apply().
   it("should set changePasswordEnabled=false and clear password fields for user with existing password", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: true, supportChangePassword: true }),
         type: IdentityType.USER,
      });

      comp.form.get("changePasswordEnabled").setValue(true);
      comp.updatePassword(true);

      comp.reset();

      expect(comp.form.get("changePasswordEnabled").value).toBe(false);
      // reset() clears the input fields; null semantics are enforced when syncing to the model.
      expect((comp.pwForm as UntypedFormGroup).controls["password"].value).toBe("");
      expect((comp.pwForm as UntypedFormGroup).controls["confirmPassword"].value).toBe("");
   });

   // reset() must emit pageChanged(false) — the last emission
   // must be false so the parent clears the dirty indicator. If it stays true, the parent
   // shows "unsaved changes" even after the user has reverted all edits.
   it("should emit pageChanged(false) as the final event after reset", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel(),
         type: IdentityType.USER,
      });

      const emitted: boolean[] = [];
      comp.pageChanged.subscribe(v => emitted.push(v));

      comp.reset();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted[emitted.length - 1]).toBe(false);
   });

   describe.each([
      {
         name: "role",
         type: IdentityType.ROLE,
         model: makeRoleModel({ defaultRole: false, isSysAdmin: false, description: "original" }),
         edits: { defaultRole: true, sysAdmin: true, description: "edited" },
         expected: { defaultRole: false, sysAdmin: false, description: "original" },
      },
      {
         name: "user",
         type: IdentityType.USER,
         model: makeUserModel({ alias: "orig-alias", email: "orig@test.com", locale: "en_US" }),
         edits: { alias: "new-alias", email: "new@test.com", locale: "fr_FR" },
         expected: { alias: "orig-alias", email: "orig@test.com", locale: "en_US" },
      },
      {
         name: "org",
         type: IdentityType.ORGANIZATION,
         model: makeOrgModel({ id: "original-id", locale: "en_US" }),
         edits: { id: "new-id", locale: "de_DE" },
         expected: { id: "original-id", locale: "en_US" },
      },
   ] satisfies Array<{
      name: string;
      type: IdentityType;
      model: EditIdentityPaneModel;
      edits: Record<string, unknown>;
      expected: Record<string, unknown>;
   }>)("should restore %s form controls after editing", ({ type, model, edits, expected }) => {
      it("restores edited fields back to original values", async () => {
         const { comp } = await renderComponent({ model, type });

         Object.entries(edits).forEach(([key, value]) => comp.form.get(key)?.setValue(value));
         comp.updateModel();

         comp.reset();

         Object.entries(expected).forEach(([key, value]) => {
            expect(comp.form.get(key)?.value).toBe(value);
         });
      });
   });

   // reset() must also restore permittedIdentities, not just members/roles.
   // Stale permittedIdentities after reset can expose or hide dashboard access incorrectly.
   it("should restore permittedIdentities to original empty state after mutation", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ permittedIdentities: [] }),
         type: IdentityType.USER,
      });

      comp.permittedIdentities = [{ identityID: { name: "p1", orgID: "DefaultOrg" }, type: IdentityType.USER }];

      comp.reset();

      expect(comp.permittedIdentities).toHaveLength(0);
   });

   // reset() for newUser must leave pwForm enabled and password touched.
   // A disabled form skips the required validator, letting a new user save with no password.
   it("should keep password form enabled and password field touched after reset() for new user", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ newUser: true, hasPassword: false }),
         type: IdentityType.USER,
      });

      comp.pwForm.controls["password"].setValue("TempPass1!");
      comp.reset();

      expect(comp.pwForm.enabled).toBe(true);
      expect(comp.pwForm.controls["password"].value).toBe("");
      expect(comp.pwForm.controls["password"].touched).toBe(true);
   });

   // reset() for a user with no existing password must re-enable
   // the password form (changePasswordEnabled=true, pwForm enabled, password touched).
   // If skipped, the user cannot set a password after cancelling a previous attempt.
   it("should re-enable password form and set changePasswordEnabled=true after reset() for user without password", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: false, supportChangePassword: true }),
         type: IdentityType.USER,
      });

      comp.reset();

      expect(comp.form.get("changePasswordEnabled").value).toBe(true);
      expect(comp.pwForm.enabled).toBe(true);
      expect(comp.pwForm.controls["password"].touched).toBe(true);
   });

});

// ================================================================
// Group 2 [Risk 3] — apply(): org-save side effects
// ================================================================

describe("EditIdentityViewComponent — apply(): org-save side effects", () => {

   // 🔁 Regression-sensitive: apply() for org must call beginOrgSave() and set identityEditable=false.
   // If either step is skipped, the UI stays interactive while the org is saving — concurrent
   // applies can submit stale data that overwrites the server's updated state.
   it("should call beginOrgSave() and set identityEditable=false when applying org changes", async () => {
      const { comp, orgBusySpy } = await renderComponent({
         model: makeOrgModel({ name: "TestOrg" }),
         type: IdentityType.ORGANIZATION,
      });

      comp.apply();

      expect(orgBusySpy.beginOrgSave).toHaveBeenCalledTimes(1);
      expect(comp.identityEditable).toBe(false);
   });

   // apply() for org must emit organizationSettingsChanged, not userSettingsChanged.
   // A wrong event means the parent ignores the save — changes are lost silently.
   it("should emit organizationSettingsChanged (not userSettingsChanged) for org type", async () => {
      const { comp } = await renderComponent({
         model: makeOrgModel({ name: "TestOrg", id: "testorg" }),
         type: IdentityType.ORGANIZATION,
      });

      const orgEvents: EditOrganizationPaneModel[] = [];
      const userEvents: EditUserPaneModel[] = [];
      comp.organizationSettingsChanged.subscribe(e => orgEvents.push(e));
      comp.userSettingsChanged.subscribe(e => userEvents.push(e));

      comp.apply();

      expect(orgEvents).toHaveLength(1);
      expect(userEvents).toHaveLength(0);
   });

   // apply() must stamp model.oldName from originalModel before emitting.
   // Without oldName, the server cannot distinguish a rename from an edit — renames are silently
   // dropped and the entity is saved under the new name without cleaning up the old one.
   it("should stamp model.oldName with the original name before emitting settings changed", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "original" }),
         type: IdentityType.USER,
      });

      comp.form.controls["name"].setValue("renamed");
      comp.updateModel();
      comp.apply();

      expect(comp.model.oldName).toBe("original");
   });

   // apply() for user must NOT call beginOrgSave() — incorrectly
   // locking org busy state disables the whole security settings UI for non-org saves.
   it("should emit userSettingsChanged and NOT call beginOrgSave() for user type", async () => {
      const { comp, orgBusySpy } = await renderComponent({
         model: makeUserModel(),
         type: IdentityType.USER,
      });

      const userEvents: EditUserPaneModel[] = [];
      comp.userSettingsChanged.subscribe(e => userEvents.push(e));

      comp.apply();

      expect(orgBusySpy.beginOrgSave).not.toHaveBeenCalled();
      expect(userEvents).toHaveLength(1);
   });

   // apply() for role must emit roleSettingsChanged, not other events.
   // Wrong event type means the parent ignores the save — role changes are lost silently.
   it("should emit roleSettingsChanged and NOT call beginOrgSave() for role type", async () => {
      const { comp, orgBusySpy } = await renderComponent({
         model: makeRoleModel(),
         type: IdentityType.ROLE,
      });

      const roleEvents: EditRolePaneModel[] = [];
      comp.roleSettingsChanged.subscribe(e => roleEvents.push(e));

      comp.apply();

      expect(orgBusySpy.beginOrgSave).not.toHaveBeenCalled();
      expect(roleEvents).toHaveLength(1);
   });

   // apply() for group must emit groupSettingsChanged.
   it("should emit groupSettingsChanged and NOT call beginOrgSave() for group type", async () => {
      const { comp, orgBusySpy } = await renderComponent({
         model: makeGroupModel(),
         type: IdentityType.GROUP,
      });

      const groupEvents: EditGroupPaneModel[] = [];
      comp.groupSettingsChanged.subscribe(e => groupEvents.push(e));

      comp.apply();

      expect(orgBusySpy.beginOrgSave).not.toHaveBeenCalled();
      expect(groupEvents).toHaveLength(1);
   });

   // apply() must emit pageChanged(false) as its final emission so the
   // parent clears the dirty indicator after a save (apply() calls updateModel() first which
   // emits true, then apply() must follow with false).
   it("should emit pageChanged(false) as the final event after apply()", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel(),
         type: IdentityType.USER,
      });

      const emitted: boolean[] = [];
      comp.pageChanged.subscribe(v => emitted.push(v));

      comp.apply();

      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted[emitted.length - 1]).toBe(false);
   });

});

// ================================================================
// Group 3 [Risk 2] — password form: enablement rules
// ================================================================

describe("EditIdentityViewComponent — password form: new user vs existing user", () => {

   // new-user password group must always be enabled so required/complexity
   // validators run. The changePasswordEnabled checkbox is hidden via *ngIf in the template
   // (not disabled via form API) — its value stays false and the control stays enabled.
   it("should keep password group enabled for new user (changePasswordEnabled hidden, value stays false)", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ newUser: true, hasPassword: false }),
         type: IdentityType.USER,
      });

      expect(comp.pwForm.enabled).toBe(true);
      // The checkbox is hidden via *ngIf="!isNewUser && hasPassword" — the control is not
      // disabled programmatically, but its value is irrelevant for new-user flows.
      expect(comp.form.get("changePasswordEnabled").value).toBe(false);
   });

   // existing user — password group must be disabled by default.
   // After toggleing changePasswordEnabled, updatePassword(true) must enable it so validators run.
   it("should toggle password group enabled/disabled via changePasswordEnabled for existing user", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: true, supportChangePassword: true }),
         type: IdentityType.USER,
      });

      expect(comp.pwForm.disabled).toBe(true);

      comp.form.get("changePasswordEnabled").setValue(true);
      comp.updatePassword(true);

      expect(comp.pwForm.enabled).toBe(true);
   });

   // Risk Point: passwordsMatch validator is on the group, not on the confirmPassword control.
   // A test asserting only form.valid=false cannot distinguish this from a required/complexity error.
   it("should produce a group-level passwordsMatch error when passwords do not match", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ newUser: true, hasPassword: false }),
         type: IdentityType.USER,
      });

      comp.pwForm.controls["password"].setValue("Password1!");
      comp.pwForm.controls["confirmPassword"].setValue("Different1!");
      comp.pwForm.controls["confirmPassword"].markAsTouched();

      expect(comp.pwForm.errors?.["passwordsMatch"]).toBeTruthy(); // group error, not control error
      expect(comp.pwForm.get("password").errors?.["passwordComplexity"]).toBeFalsy(); // not a complexity issue
      expect(comp.form.valid).toBe(false);
   });

   // a user who has never set a password (e.g. SSO account) must see
   // the password form auto-enabled on init so the required validator fires.
   // If skipped, they can save without a password set.
   it("should auto-enable password form and set changePasswordEnabled=true on init for user without password", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: false, supportChangePassword: true, newUser: false }),
         type: IdentityType.USER,
      });

      expect(comp.form.get("changePasswordEnabled").value).toBe(true);
      expect(comp.pwForm.enabled).toBe(true);
      expect(comp.pwForm.controls["password"].touched).toBe(true);
   });

   // 🔁 Regression-sensitive: updatePassword(false) must disable the group AND set the password
   // control to null so apply() sends null (server semantics: "leave password unchanged").
   // If password stays non-null, every apply() silently overwrites the existing password.
   it("should disable password form and set password to null when updatePassword(false) is called", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: true, supportChangePassword: true }),
         type: IdentityType.USER,
      });

      comp.updatePassword(true); // enable first
      expect(comp.pwForm.enabled).toBe(true);

      comp.updatePassword(false);

      expect(comp.pwForm.disabled).toBe(true);
      expect((comp.pwForm as UntypedFormGroup).controls["password"].value).toBeNull();
   });

});

// ================================================================
// Group 4 [Risk 2] — updateModel(): form-to-model sync
// ================================================================

describe("EditIdentityViewComponent — updateModel(): form-to-model sync", () => {

   // name must be trimmed before writing to model.
   // Untrimmed names cause duplicate-detection failures on the server because
   // "  alice  " and "alice" are treated as different identities.
   it("should trim leading and trailing whitespace from name in the model", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "original" }),
         type: IdentityType.USER,
      });

      comp.form.controls["name"].setValue("  spaced  ");
      comp.updateModel();

      expect(comp.model.name).toBe("spaced");
   });

   // Risk Point: updateModel() guards with `!!form.value["name"]` — empty string is falsy,
   // so model.name is NOT updated. This prevents applying a blank rename, but also means
   // model.name retains the last-valid name (verified here so refactors don't remove the guard).
   // theme is unconditional (line 547) so it IS synced even when name is skipped.
   it("should NOT update model.name when form name field is empty, but still sync theme", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "keepme", theme: "oldtheme" }),
         type: IdentityType.USER,
      });

      comp.form.controls["name"].setValue("");
      comp.form.controls["theme"].setValue("newtheme");
      comp.updateModel();

      expect(comp.model.name).toBe("keepme");    // name guard: not overwritten
      expect(comp.model.theme).toBe("newtheme"); // theme is always synced
   });

   describe.each([
      {
         name: "role",
         type: IdentityType.ROLE,
         model: makeRoleModel({ defaultRole: false, isSysAdmin: false, description: "" }),
         edits: { defaultRole: true, sysAdmin: true, description: "Admin role description" },
         expectedModel: { defaultRole: true, isSysAdmin: true, description: "Admin role description" },
      },
      {
         name: "user",
         type: IdentityType.USER,
         model: makeUserModel({ status: true, alias: "", email: "", locale: "" }),
         edits: { active: false, alias: "myalias", email: "user@example.com", locale: "fr_FR" },
         expectedModel: { status: false, alias: "myalias", email: "user@example.com", locale: "fr_FR" },
      },
      {
         name: "org",
         type: IdentityType.ORGANIZATION,
         model: makeOrgModel({ id: "old-id", locale: "" }),
         edits: { id: "new-id", locale: "de_DE" },
         expectedModel: { id: "new-id", locale: "de_DE" },
      },
      {
         name: "group",
         type: IdentityType.GROUP,
         model: makeGroupModel({ name: "oldgroup" }),
         edits: { name: "newgroup" },
         expectedModel: { name: "newgroup" },
      },
   ] satisfies Array<{
      name: string;
      type: IdentityType;
      model: EditIdentityPaneModel;
      edits: Record<string, unknown>;
      expectedModel: Record<string, unknown>;
   }>)("should sync fields to model for %s type", ({ type, model, edits, expectedModel }) => {
      it("syncs expected fields", async () => {
         const { comp } = await renderComponent({ model, type });

         Object.entries(edits).forEach(([key, value]) => comp.form.get(key)?.setValue(value));
         comp.updateModel();

         Object.entries(expectedModel).forEach(([key, value]) => {
            expect((comp.model as any)[key]).toBe(value);
         });
      });
   });

   // when changePasswordEnabled=false for an existing user,
   // model.password must be null so the server knows not to change the password.
   // If this regresses, every apply() sends a stale non-null password and overwrites it.
   it("should set model.password to null when changePasswordEnabled is false for existing user", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ hasPassword: true, supportChangePassword: true }),
         type: IdentityType.USER,
      });

      expect(comp.form.get("changePasswordEnabled").value).toBe(false);
      comp.updateModel();

      expect((comp.model as EditUserPaneModel).password).toBeNull();
   });

   // updateModel() must emit pageChanged(true) on every call.
   // If missing, the parent's dirty indicator never activates and users can navigate away
   // without any unsaved-changes warning.
   it("should emit pageChanged(true) on every updateModel() call", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel(),
         type: IdentityType.USER,
      });

      const emitted: boolean[] = [];
      comp.pageChanged.subscribe(v => emitted.push(v));

      comp.updateModel();

      expect(emitted).toContain(true);
      expect(emitted[emitted.length - 1]).toBe(true);
   });

   // Bug: the top-level empty-name guard (line 543 `if(!!form.value["name"])`) is bypassed by the
   // ROLE and GROUP type branches which unconditionally write `form.value["name"].trim()` to the
   // model. For USER/ORG types the guard works correctly (see test above); for ROLE/GROUP it does
   // not — clearing the name field writes "" into model.name, corrupting in-memory state even
   // though the form is invalid and Apply is blocked.
   it.failing.each([
      { name: "role", type: IdentityType.ROLE, model: makeRoleModel({ name: "keepme" }) },
      { name: "group", type: IdentityType.GROUP, model: makeGroupModel({ name: "keepme" }) },
   ] satisfies Array<{ name: string; type: IdentityType; model: EditIdentityPaneModel }>)(
      "BUG: should NOT update model.name when form name field is empty for %s type (guard bypassed)",
      async ({ type, model }) => {
         const { comp } = await renderComponent({ model, type });

         comp.form.controls["name"].setValue("");
         comp.updateModel();

         expect(comp.model.name).toBe("keepme"); // currently gets ""
      }
   );

});

// ================================================================
// Group 5 [Risk 2] — identityEditable: org-save lock
// ================================================================

describe("EditIdentityViewComponent — identityEditable: org-save disable/re-enable", () => {

   // identityEditableChanges false must lock; true must re-enable.
   it("should toggle identityEditable when identityEditableChanges emits false then true", async () => {
      const { comp, editableSubject } = await renderComponent();

      editableSubject.next(false);
      expect(comp.identityEditable).toBe(false);

      editableSubject.next(true);

      expect(comp.identityEditable).toBe(true);
   });

   // the model @Input() setter always resets identityEditable to true.
   // This ensures that after an org save completes and a new model is loaded, the panel is editable.
   // Render with a model so the form is initialized; setting a second model only tests the setter.
   it("should reset identityEditable=true when the model input is replaced", async () => {
      const { comp, editableSubject } = await renderComponent({
         model: makeUserModel(),
         type: IdentityType.USER,
      });

      editableSubject.next(false);
      expect(comp.identityEditable).toBe(false);

      // Directly invoke the setter — simulates Angular binding a new @Input value.
      // The form is already initialized so detectChanges does not throw.
      comp.model = makeUserModel({ name: "replacement" });

      expect(comp.identityEditable).toBe(true);
   });

});

// ================================================================
// Group 6 [Risk 2] — isModelChanged(), form-disabled guard, ngOnChanges
// ================================================================

describe("EditIdentityViewComponent — isModelChanged() and structural guards", () => {

   // isModelChanged() false → true → false cycle validates that original-state bookkeeping
   // is correct across the full edit/reset lifecycle.
   it("should return false initially, true after a name edit, and false again after reset", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "original" }),
         type: IdentityType.USER,
      });

      expect(comp.isModelChanged()).toBe(false);

      comp.form.controls["name"].setValue("changed");
      comp.updateModel();
      expect(comp.isModelChanged()).toBe(true);

      comp.reset();
      expect(comp.isModelChanged()).toBe(false);
   });

   // when model.editable=false the entire form must be disabled
   // so read-only users cannot modify any field.
   it("should disable the entire form when model.editable is false", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ editable: false }),
         type: IdentityType.USER,
      });

      expect(comp.form.disabled).toBe(true);
   });

   // 🔁 Regression-sensitive: ngOnChanges only calls init() when the model transitions from
   // null to a non-null value. If the guard is removed, any @Input re-binding mid-edit resets
   // the form and discards unsaved changes the user is actively making.
   it("should not re-initialize the form when model changes from non-null to another non-null value", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ name: "first" }),
         type: IdentityType.USER,
      });

      comp.form.controls["name"].setValue("edited");
      const formRef = comp.form;

      comp.ngOnChanges({
         model: {
            previousValue: makeUserModel({ name: "first" }),
            currentValue: makeUserModel({ name: "second" }),
            firstChange: false,
            isFirstChange: () => false,
         } as SimpleChanges["model"],
      });

      // form must be the same instance (not rebuilt) and still hold the edited value
      expect(comp.form).toBe(formRef);
      expect(comp.form.controls["name"].value).toBe("edited");
   });

});

// ================================================================
// Group 7 [Risk 2] — add(): member/role insertion and deduplication
// ================================================================

describe("EditIdentityViewComponent — add(): member and role insertion", () => {

   // selecting a group identity type and calling add() must push to
   // members (not roles). Wrong target means the saved model has members/roles swapped.
   it("should push group identity to members list and not to roles", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ members: [], roles: [] }),
         type: IdentityType.USER,
      });

      comp.identity = "Groups";
      comp.subIdentity = { name: "group1", orgID: "DefaultOrg" };
      comp.add();

      expect(comp.members).toHaveLength(1);
      expect(comp.members[0].identityID.name).toBe("group1");
      expect(comp.roles).toHaveLength(0);
   });

   // selecting a role identity type must push to roles, and a second
   // add() with the same identity reference must be silently rejected (deduplication).
   it("should push role identity to roles list and reject duplicate add attempts", async () => {
      const { comp } = await renderComponent({
         model: makeUserModel({ members: [], roles: [] }),
         type: IdentityType.USER,
      });

      const roleId = { name: "role1", orgID: "DefaultOrg" };
      comp.identity = "Roles";
      comp.subIdentity = roleId;
      comp.add();
      comp.add(); // same reference — must be deduplicated

      expect(comp.roles).toHaveLength(1);
      expect(comp.members).toHaveLength(0);
   });

});
