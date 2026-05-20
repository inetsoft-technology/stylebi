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
 * AuthorizationProviderDetailViewComponent - Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - model binding: existing provider input must populate the form and
 *                      enterprise state must control CUSTOM availability.
 *   Group 2 [Risk 2] - change tracking and reset: parent dirty state must reflect edits and
 *                      reset must clear it.
 *   Group 3 [Risk 2] - isValid: FILE and CUSTOM providers must use the correct validation path.
 *   Group 4 [Risk 2] - lifecycle: value-change subscriptions stop on destroy.
 *
 * Confirmed bugs (it.failing - remove wrapper once fixed):
 *
 *   Bug A - initial model input is not written to the form (Group 1):
 *     Angular supplies @Input values before ngOnInit. The setter records _model/_original but
 *     skips form writes because form is not created yet; initForm() then creates blank controls
 *     and never replays the model. Existing authorization providers can render as blank.
 *
 * KEY contracts:
 *   - FILE provider validity is changed && different from original.
 *   - CUSTOM provider validity is customForm.valid && changed.
 *   - changed setter emits onChanged before storing the local flag.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { ReactiveFormsModule, UntypedFormControl, Validators } from "@angular/forms";
import { MatCardModule } from "@angular/material/card";
import { MatFormFieldModule } from "@angular/material/form-field";
import { MatInputModule } from "@angular/material/input";
import { MatOptionModule } from "@angular/material/core";
import { MatSelectModule } from "@angular/material/select";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { it } from "@jest/globals";
import { render, waitFor } from "@testing-library/angular";
import { of } from "rxjs";

import { AppInfoService } from "../../../../../../../shared/util/app-info.service";
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
// Render helper
// ---------------------------------------------------------------------------

interface RenderOpts {
   model?: AuthorizationProviderModel;
   isEnterprise?: boolean;
}

async function renderComponent(opts: RenderOpts = {}) {
   const appInfoSpy = {
      isEnterprise: jest.fn().mockReturnValue(of(opts.isEnterprise ?? true)),
   };

   const result = await render(AuthorizationProviderDetailViewComponent, {
      imports: [
         ReactiveFormsModule,
         NoopAnimationsModule,
         MatCardModule,
         MatFormFieldModule,
         MatInputModule,
         MatOptionModule,
         MatSelectModule,
      ],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: AppInfoService, useValue: appInfoSpy },
      ],
      componentProperties: {
         model: opts.model,
      },
   });

   result.fixture.detectChanges();

   return {
      ...result,
      comp: result.fixture.componentInstance as AuthorizationProviderDetailViewComponent,
      appInfoSpy,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] - model binding and enterprise flag
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - model binding and enterprise flag", () => {

   // Regression-sensitive: editing an existing provider starts from the @Input model. If the
   // form stays blank, reset/save can overwrite the provider name and type.
   it.failing("should initialize providerName and providerType from an input model", async () => {
      const model = makeModel({
         providerName: "ExistingAuthz",
         providerType: SecurityProviderType.CUSTOM,
      });

      const { comp } = await renderComponent({ model });

      expect(comp.form.controls["providerName"].value).toBe("ExistingAuthz");
      expect(comp.form.controls["providerType"].value).toBe(SecurityProviderType.CUSTOM);
   });

   // Regression-sensitive baseline: after form creation, assigning model must write both
   // top-level controls used by isValid and reset.
   it("should populate the form when model is assigned after init", async () => {
      const { comp } = await renderComponent({ model: makeModel() });

      comp.model = makeModel({
         providerName: "AssignedLater",
         providerType: SecurityProviderType.FILE,
      });

      expect(comp.form.controls["providerName"].value).toBe("AssignedLater");
      expect(comp.form.controls["providerType"].value).toBe(SecurityProviderType.FILE);
   });

   // Regression-sensitive: CUSTOM authorization is enterprise-gated in the template.
   it("should load the enterprise flag from AppInfoService", async () => {
      const { comp } = await renderComponent({ isEnterprise: false });

      await waitFor(() => expect(comp.isEnterprise).toBe(false));
   });
});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] - change tracking and reset
// ---------------------------------------------------------------------------

describe("AuthorizationProviderDetailViewComponent - change tracking and reset", () => {

   // Regression-sensitive: parent apply buttons and unsaved-change guards depend on onChanged.
   it("should emit changed=true when providerName differs from the original model", async () => {
      const { comp } = await renderComponent({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" });
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      comp.form.controls["providerName"].setValue("ProviderB");

      expect(comp.changed).toBe(true);
      expect(emitted).toContain(true);
   });

   // Regression-sensitive: reset must restore the form and clear dirty state in one operation.
   it("should restore form values and emit changed=false on reset", async () => {
      const { comp } = await renderComponent({ model: makeModel() });
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
      const { comp } = await renderComponent({ model: makeModel() });
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
      const { comp } = await renderComponent({ model: makeModel() });
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
      const { comp } = await renderComponent({ model: makeModel() });
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
      const { comp, fixture } = await renderComponent({ model: makeModel() });
      comp.model = makeModel({ providerName: "ProviderA" });
      const emitted: boolean[] = [];
      comp.onChanged.subscribe(value => emitted.push(value));

      fixture.destroy();
      comp.form.controls["providerName"].setValue("AfterDestroy");

      expect(emitted).toHaveLength(0);
   });
});
