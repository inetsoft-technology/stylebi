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
 * AuthenticationProviderDetailViewComponent
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model binding: existing provider input must populate the form; new
 *                      provider defaults must reflect enterprise and tenant state.
 *   Group 2 [Risk 2] - change tracking and reset: parent dirty state must follow edits and
 *                      reset must clear it.
 *   Group 3 [Risk 2] - isValid: Apply is enabled only for a changed, valid provider and the
 *                      active nested provider form.
 *   Group 4 [Risk 2] - lifecycle: value-change subscriptions stop on destroy.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *
 *   Bug A - initial model input is not written to the form (Group 1):
 *     Angular supplies @Input values before ngOnInit. The setter records _model/_original but
 *     skips form writes because form is not created yet; initForm() then creates blank controls
 *     and never replays the model. Existing providers can render as blank.
 *
 * KEY contracts:
 *   - File providers require changed=true and top-level values different from _original.
 *   - LDAP/DATABASE/CUSTOM providers also require the matching nested form control to exist
 *     and be valid.
 *   - New-provider defaults: DB and custom follow enterprise; LDAP is disabled in multi-tenant.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgIf } from "@angular/common";
import { provideHttpClient } from "@angular/common/http";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { FormsModule, ReactiveFormsModule, UntypedFormControl, Validators } from "@angular/forms";
import { MatCard, MatCardContent } from "@angular/material/card";
import { MatFormField, MatLabel, MatError } from "@angular/material/form-field";
import { MatInput } from "@angular/material/input";
import { MatSelect } from "@angular/material/select";
import { MatOption } from "@angular/material/core";
import { of, EMPTY } from "rxjs";

import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
import { AuthenticationProviderModel } from "../security-provider-model/authentication-provider-model";
import { SecurityProviderType } from "../security-provider-model/security-provider-type.enum";
import { AuthenticationProviderDetailViewComponent } from "./authentication-provider-detail-view.component";
import { SecurityProviderService } from "../security-provider.service";

// EditorPanelComponent.ngAfterContentChecked() does DOM layout queries every cycle, causing
// NG0100 (ExpressionChangedAfterItHasBeenCheckedError) on MatFormField/MatOption host bindings.
@Component({ selector: "em-editor-panel", standalone: true, template: "<ng-content />" })
class StubEditorPanelComponent {
   @Input() applyVisible = true;
   @Input() applyLabel = "_#(js:Apply)";
   @Input() resetLabel = "_#(js:Reset)";
   @Input() cancelLabel = "_#(js:Cancel)";
   @Input() applyDisabled = false;
   @Input() resetDisabled = false;
   @Input() contentClass = "";
   @Input() contentStyle: { [key: string]: string } = {};
   @Input() actionsClass = "";
   @Input() actionsStyle: { [key: string]: string } = {};
   @Input() resetVisible = true;
   @Output() applyClicked = new EventEmitter<any>();
   @Output() resetClicked = new EventEmitter<any>();
   @Output() unsavedChanges = new EventEmitter<boolean>();
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<AuthenticationProviderModel> = {}): AuthenticationProviderModel {
   return {
      providerName: "ProviderA",
      oldName: "ProviderA",
      providerType: SecurityProviderType.FILE,
      dbProviderEnabled: true,
      customProviderEnabled: true,
      ldapProviderEnabled: true,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

interface SetupOpts {
   model?: AuthenticationProviderModel;
   isEnterprise?: boolean;
   isMultiTenant?: boolean;
}

const SECURITY_PROVIDER_STUB = {
   testDatabaseConnection: () => EMPTY,
   testConnection: () => EMPTY,
   getUsers: () => EMPTY,
   getGroups: () => EMPTY,
   getRoles: () => EMPTY,
   getOrganizations: () => EMPTY,
   getDefaultOrganization: () => EMPTY,
   getAdminRoles: () => EMPTY,
   updateAuthenticationProvider: () => EMPTY,
   routeToListView: vi.fn(),
   triggerUserListQuery: vi.fn(),
   triggerGroupListQuery: vi.fn(),
   triggerRoleListQuery: vi.fn(),
   triggerOrganizationListQuery: vi.fn(),
   triggerUsersQuery: vi.fn(),
   triggerUserRolesQuery: vi.fn(),
   triggerUserRoleListQuery: vi.fn(),
   triggerUserEmailsQuery: vi.fn(),
   triggerGroupUsersQuery: vi.fn(),
   triggerOrganizationMembersQuery: vi.fn(),
   triggerOrganizationNameQuery: vi.fn(),
};

async function createFixture(opts: SetupOpts = {}) {
   const appInfoSpy = {
      isEnterprise: vi.fn().mockReturnValue(of(opts.isEnterprise ?? true)),
   };

   const result = await render(AuthenticationProviderDetailViewComponent, {
      detectChangesOnRender: false,
      // Child view components (CustomProviderViewComponent, LdapProviderViewComponent,
      // DatabaseProviderViewComponent) are excluded: they each add nested form groups to the parent
      // form in ngOnInit, preventing independent control of those groups in the isValid tests.
      componentImports: [
         NgIf, FormsModule, ReactiveFormsModule,
         MatCard, MatCardContent, MatFormField, MatLabel, MatInput, MatError, MatSelect, MatOption,
         StubEditorPanelComponent,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         provideNoopAnimations(),
         { provide: AppInfoService, useValue: appInfoSpy },
         { provide: SecurityProviderService, useValue: SECURITY_PROVIDER_STUB },
      ],
      componentProperties: {
         isCloudSecrets: false,
         ...(opts.model !== undefined ? { model: opts.model as any } : {}),
         ...(opts.isMultiTenant !== undefined ? { isMultiTenant: opts.isMultiTenant } : {}),
      },
   });

   // MatSelect with a reactive form pre-set value causes NG0100 on the first detectChanges because
   // the selection model is populated in ngAfterContentInit (after the initial binding evaluation).
   // First pass without strict check lets lifecycle hooks stabilize the selection model.
   // Second pass with strict check then sees a consistent state.
   result.fixture.detectChanges(false);
   result.fixture.detectChanges();
   await result.fixture.whenStable();

   return { fixture: result.fixture, comp: result.fixture.componentInstance, appInfoSpy };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - model binding and new-provider defaults
// ---------------------------------------------------------------------------

describe("AuthenticationProviderDetailViewComponent - model binding and new-provider defaults", () => {

   // Regression-sensitive: editing an existing provider starts from the @Input model. If the
   // form stays blank, a reset/save can overwrite the provider name and type.
   it.fails("should initialize providerName, oldName, and providerType from an input model", async () => {
      const model = makeModel({
         providerName: "Existing",
         oldName: "Original",
         providerType: SecurityProviderType.DATABASE,
      });

      const { comp } = await createFixture({ model });

      expect(comp.providerName).toBe("Existing");
      expect(comp.oldName).toBe("Original");
      expect(comp.providerType).toBe(SecurityProviderType.DATABASE);
   });

   // Regression-sensitive baseline: once the form exists, assigning model must populate every
   // top-level control used by save/reset.
   it("should populate the form when model is assigned after init", async () => {
      const { comp } = await createFixture({ model: makeModel() });

      comp.model = makeModel({
         providerName: "AssignedLater",
         oldName: "OldAssigned",
         providerType: SecurityProviderType.LDAP,
      }) as any;

      expect(comp.providerName).toBe("AssignedLater");
      expect(comp.oldName).toBe("OldAssigned");
      expect(comp.providerType).toBe(SecurityProviderType.LDAP);
   });

   // Regression-sensitive: available provider types are security product-surface flags.
   // Enterprise controls DB/custom; multi-tenant suppresses LDAP.
   it("should create new-provider defaults from enterprise and multi-tenant flags", async () => {
      const { comp } = await createFixture({
         model: null as any,
         isEnterprise: true,
         isMultiTenant: true,
      });

      // initNewProvider() uses setTimeout(0) to set the default model
      await vi.waitFor(() => expect(comp.model).toBeTruthy(), { timeout: 1000 });
      expect(comp.model.dbProviderEnabled).toBe(true);
      expect(comp.model.customProviderEnabled).toBe(true);
      expect(comp.model.ldapProviderEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - change tracking and reset
// ---------------------------------------------------------------------------

describe("AuthenticationProviderDetailViewComponent - change tracking and reset", () => {

   // Regression-sensitive: parent apply buttons and unsaved-change guards depend on onChanged.
   it("should emit changed=true when providerName differs from the original model", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" }) as any;
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      comp.providerName = "ProviderB";

      expect(comp.changed).toBe(true);
      expect(emitted).toContain(true);
   });

   // Regression-sensitive: reset must put the top-level form controls back to the model and clear
   // dirty state; otherwise Apply remains enabled after the visible fields are restored.
   it("should restore top-level fields and emit changed=false on reset", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({
         providerName: "ProviderA",
         providerType: SecurityProviderType.FILE,
      }) as any;
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));
      comp.providerName = "Edited";
      comp.providerType = SecurityProviderType.CUSTOM;

      comp.reset();

      expect(comp.providerName).toBe("ProviderA");
      expect(comp.providerType).toBe(SecurityProviderType.FILE);
      expect(comp.changed).toBe(false);
      expect(emitted[emitted.length - 1]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - isValid
// ---------------------------------------------------------------------------

describe("AuthenticationProviderDetailViewComponent - isValid guards", () => {

   // Regression-sensitive: unchanged file providers must not enable Apply; changed valid values
   // must enable it so users can save provider renames/type changes.
   it("should require a changed, valid top-level model for FILE providers", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({
         providerName: "ProviderA",
         providerType: SecurityProviderType.FILE,
      }) as any;
      comp.changed = false;

      expect(comp.isValid).toBe(false);

      comp.providerName = "ProviderB";

      expect(comp.form.controls["providerName"].errors?.["required"]).toBeFalsy();
      expect(comp.isValid).toBe(true);
   });

   // Regression-sensitive: nested provider panels own type-specific validators. The parent must
   // block Apply until the active nested form exists and is valid.
   it("should require a valid nested form for DATABASE providers", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerType: SecurityProviderType.DATABASE }) as any;
      comp.providerType = SecurityProviderType.DATABASE;
      comp.providerName = "ProviderB";
      comp.changed = true;

      expect(comp.isValid).toBe(false);

      comp.form.addControl("dbForm", new UntypedFormControl("ok"));
      expect(comp.isValid).toBe(true);

      comp.form.setControl("dbForm", new UntypedFormControl("", Validators.required));
      expect(comp.isValid).toBe(false);
   });

   // Regression-sensitive: invalid provider names must be attributed to the name control, not to
   // a missing nested form, so the UI shows the correct error reason.
   it("should be invalid for an empty providerName without activating nested-form errors", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel() as any;
      comp.providerName = "";
      comp.providerType = SecurityProviderType.FILE;
      comp.changed = true;

      expect(comp.form.controls["providerName"].errors?.["required"]).toBeTruthy();
      expect(comp.form.get("dbForm")).toBeNull();
      expect(comp.isValid).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] - lifecycle
// ---------------------------------------------------------------------------

describe("AuthenticationProviderDetailViewComponent - lifecycle cleanup", () => {

   // Regression-sensitive: value-change subscriptions must stop on destroy or a closed detail
   // view can keep toggling parent dirty state.
   it("should stop emitting changed events after destroy", async () => {
      const { comp, fixture } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" }) as any;
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      fixture.destroy();
      comp.providerName = "AfterDestroy";

      expect(emitted).toHaveLength(0);
   });
});
