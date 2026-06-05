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
 * AuthorizationProviderDetailViewComponent
 *
 * Coverage:
 *   Group 1 [Risk 3] - model binding: existing provider input must populate the form and
 *                      enterprise state must control CUSTOM availability.
 *   Group 2 [Risk 2] - change tracking and reset: parent dirty state must reflect edits and
 *                      reset must clear it.
 *   Group 3 [Risk 2] - isValid: FILE and CUSTOM providers must use the correct validation path.
 *   Group 4 [Risk 2] - lifecycle: value-change subscriptions stop on destroy.
 *
 * KEY contracts:
 *   - FILE provider validity is changed && different from original.
 *   - CUSTOM provider validity is customForm.valid && changed.
 *   - changed setter emits onChanged before storing the local flag.
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgIf } from "@angular/common";
import { provideNoopAnimations } from "@angular/platform-browser/animations";
import { FormsModule, ReactiveFormsModule, UntypedFormControl, Validators } from "@angular/forms";
import { MatCard, MatCardContent } from "@angular/material/card";
import { MatFormField, MatLabel, MatError } from "@angular/material/form-field";
import { MatInput } from "@angular/material/input";
import { MatSelect } from "@angular/material/select";
import { MatOption } from "@angular/material/core";
import { of } from "rxjs";

import { AppInfoService } from "../../../../../../../shared/util/app-info.service";

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
import { AuthorizationProviderModel } from "../security-provider-model/authorization-provider-model";
import { SecurityProviderType } from "../security-provider-model/security-provider-type.enum";
import { AuthorizationProviderDetailViewComponent } from "./authorization-provider-detail-view.component";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<AuthorizationProviderModel> = {}): AuthorizationProviderModel {
   return {
      providerName: "ProviderA",
      providerType: SecurityProviderType.FILE,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Setup helper
// ---------------------------------------------------------------------------

interface SetupOpts {
   model?: AuthorizationProviderModel;
   isEnterprise?: boolean;
}

async function createFixture(opts: SetupOpts = {}) {
   const appInfoSpy = {
      isEnterprise: vi.fn().mockReturnValue(of(opts.isEnterprise ?? true)),
   };

   const result = await render(AuthorizationProviderDetailViewComponent, {
      detectChangesOnRender: false,
      // CustomProviderViewComponent is excluded: its ngOnInit adds 'customForm' to the parent
      // form (with className: required), which would prevent the tests from controlling
      // customForm state independently to verify the parent's isValid logic.
      componentImports: [
         NgIf, FormsModule, ReactiveFormsModule,
         MatCard, MatCardContent, MatFormField, MatLabel, MatInput, MatError, MatSelect, MatOption,
         StubEditorPanelComponent,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideNoopAnimations(),
         { provide: AppInfoService, useValue: appInfoSpy },
      ],
      componentProperties: {
         ...(opts.model !== undefined ? { model: opts.model } : {}),
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
// Group 1 [Risk 3] - model binding and enterprise flag
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - model binding and enterprise flag", () => {

   // Regression-sensitive: editing an existing provider starts from the @Input model. If the
   // form stays blank, reset/save can overwrite the provider name and type.
   it("should initialize providerName and providerType from an input model", async () => {
      const model = makeModel({
         providerName: "ExistingAuthz",
         providerType: SecurityProviderType.CUSTOM,
      });

      const { comp } = await createFixture({ model });

      expect(comp.form.controls["providerName"].value).toBe("ExistingAuthz");
      expect(comp.form.controls["providerType"].value).toBe(SecurityProviderType.CUSTOM);
   });

   // Regression-sensitive baseline: after form creation, assigning model must write both
   // top-level controls used by isValid and reset.
   it("should populate the form when model is assigned after init", async () => {
      const { comp } = await createFixture({ model: makeModel() });

      comp.model = makeModel({
         providerName: "AssignedLater",
         providerType: SecurityProviderType.FILE,
      });

      expect(comp.form.controls["providerName"].value).toBe("AssignedLater");
      expect(comp.form.controls["providerType"].value).toBe(SecurityProviderType.FILE);
   });

   // Regression-sensitive: CUSTOM authorization is enterprise-gated in the template.
   it("should load the enterprise flag from AppInfoService", async () => {
      const { comp } = await createFixture({ isEnterprise: false });

      expect(comp.isEnterprise).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - change tracking and reset
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - change tracking and reset", () => {

   // Regression-sensitive: parent apply buttons and unsaved-change guards depend on onChanged.
   it("should emit changed=true when providerName differs from the original model", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" });
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      comp.form.controls["providerName"].setValue("ProviderB");

      expect(comp.changed).toBe(true);
      expect(emitted).toContain(true);
   });

   // Regression-sensitive: reset must restore the form and clear dirty state in one operation.
   it("should restore form values and emit changed=false on reset", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({
         providerName: "ProviderA",
         providerType: SecurityProviderType.FILE,
      });
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));
      comp.form.controls["providerName"].setValue("Edited");
      comp.form.controls["providerType"].setValue(SecurityProviderType.CUSTOM);

      comp.reset();

      expect(comp.form.controls["providerName"].value).toBe("ProviderA");
      expect(comp.form.controls["providerType"].value).toBe(SecurityProviderType.FILE);
      expect(comp.changed).toBe(false);
      expect(emitted[emitted.length - 1]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] - isValid
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - isValid guards", () => {

   // Regression-sensitive: unchanged FILE provider data must not enable Apply; changed valid
   // values must enable it so provider renames can be saved.
   it("should require changed, different top-level values for FILE providers", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({
         providerName: "ProviderA",
         providerType: SecurityProviderType.FILE,
      });
      comp.changed = false;

      expect(comp.isValid).toBe(false);

      comp.form.controls["providerName"].setValue("ProviderB");

      expect(comp.form.controls["providerName"].errors?.["required"]).toBeFalsy();
      expect(comp.isValid).toBe(true);
   });

   // Regression-sensitive: CUSTOM validity is delegated to the nested customForm. If the parent
   // ignores it, an invalid custom provider can be submitted.
   it("should require customForm.valid for CUSTOM providers", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerType: SecurityProviderType.FILE });
      comp.form.controls["providerName"].setValue("ProviderB");
      comp.changed = true;

      comp.form.addControl("customForm", new UntypedFormControl("ok"));
      comp.form.controls["providerType"].setValue(SecurityProviderType.CUSTOM);
      expect(comp.isValid).toBe(true);

      comp.form.setControl("customForm", new UntypedFormControl("", Validators.required));
      expect(comp.isValid).toBe(false);
   });

   // Regression-sensitive: empty names must be invalid for the name control specifically, not
   // hidden by the provider-type branch.
   it("should be invalid for an empty providerName and report the required error", async () => {
      const { comp } = await createFixture({ model: makeModel() });
      comp.model = makeModel();
      comp.form.controls["providerName"].setValue("");
      comp.form.controls["providerType"].setValue(SecurityProviderType.FILE);
      comp.changed = true;

      expect(comp.form.controls["providerName"].errors?.["required"]).toBeTruthy();
      expect(comp.isValid).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] - lifecycle
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - lifecycle cleanup", () => {

   // Regression-sensitive: value-change subscriptions must stop on destroy or a closed detail
   // view can keep toggling parent dirty state.
   it("should stop emitting changed events after destroy", async () => {
      const { comp, fixture } = await createFixture({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" });
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      fixture.destroy();
      comp.form.controls["providerName"].setValue("AfterDestroy");

      expect(emitted).toHaveLength(0);
   });
});
