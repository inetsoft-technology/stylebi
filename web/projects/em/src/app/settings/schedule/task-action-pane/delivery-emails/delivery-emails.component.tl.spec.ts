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
 * DeliveryEmailsComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isValid: CSV empty-assemblies check runs before !enabled guard (it.failing — confirmed bug)
 *   Group 2 [Risk 2] — sender setter: senderEnabled=false disables field, bypassing required/email validators
 *   Group 3 [Risk 2] — changeFormat: CSV+viewsheet forces bundledAsZip=true
 *   Group 4 [Risk 2] — togglePasswordForm: fipsMode suppresses zip password fields
 *   Group 5 [Risk 2] — updateEnable: emailMatchLayout=true locks emailExpandSelections
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — isValid CSV guard before !enabled (Group 1):
 *     isValid() checks `format=="CSV" && type==="viewsheet" && csvExportModel.selectedAssemblies.length==0`
 *     BEFORE it checks `!this.enabled`.
 *     Result: when delivery is disabled (enabled=false), the method still returns false if format is CSV
 *     with empty assemblies — the scheduler form is incorrectly blocked even though the user turned
 *     off email delivery.
 *     Fix: move the CSV check inside the `if(this.enabled)` branch.
 *
 * KEY contracts:
 *   - `isValid()` = `!enabled || form.valid`, with a CSV+viewsheet+empty-assemblies special case.
 *   - `sender` control is disabled (not validated) when `senderEnabled=false`.
 *   - `bundledDisabled` formats: HTML_BUNDLE, HTML_BUNDLE_NO_PAGINATION, HTML_NO_PAGINATION_EMAIL, PNG, CSV+viewsheet.
 *   - `passwordVisible` = `!fipsMode && (bundledAsZip || HTML_BUNDLE || HTML_BUNDLE_NO_PAGINATION)`.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { it } from "@jest/globals";
import { DeliveryEmailsComponent, DeliveryEmails } from "./delivery-emails.component";
import { ExportFormatModel } from "../../../../../../../shared/schedule/model/export-format-model";
import { CSVConfigModel } from "../../../../../../../shared/schedule/model/csv-config-model";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({
   selector: "em-email-picker",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EmailPickerStub), multi: true }]
})
class EmailPickerStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

/* eslint-disable @angular-eslint/component-selector */
@Component({
   selector: "mat-select",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => MatSelectStub), multi: true }]
})
class MatSelectStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}
/* eslint-enable @angular-eslint/component-selector */

@Component({
   selector: "em-mat-ckeditor",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => CkEditorStub), multi: true }]
})
class CkEditorStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

const MAIL_FORMATS: ExportFormatModel[] = [
   { type: "Excel", label: "Excel" } as ExportFormatModel,
   { type: "PDF",   label: "PDF"   } as ExportFormatModel,
   { type: "CSV",   label: "CSV"   } as ExportFormatModel,
];

function makeCSVModel(selectedCount: number): CSVConfigModel {
   return { selectedAssemblies: Array(selectedCount).fill("table") } as CSVConfigModel;
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<DeliveryEmailsComponent> = {}) {
   const result = await render(DeliveryEmailsComponent, {
      imports: [FormsModule, ReactiveFormsModule, MatCheckboxModule, NoopAnimationsModule],
      declarations: [EmailPickerStub, MatSelectStub, CkEditorStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ErrorStateMatcher, useValue: { isErrorState: () => false } },
      ],
      componentProperties: {
         mailFormats: MAIL_FORMATS,
         ...props,
      },
   });

   await result.fixture.whenStable();
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — isValid: CSV check before !enabled guard (confirmed bug)
// ---------------------------------------------------------------------------

describe("DeliveryEmailsComponent — isValid: !enabled guard vs CSV check order", () => {

   // enabled=false must ALWAYS produce valid=true.
   // The disabled state means "skip this action" — no configuration is required.
   it("should emit valid=true when enabled=false with a non-CSV format", async () => {
      const { comp } = await renderComponent({ enabled: false });

      const emitted: DeliveryEmails[] = [];
      comp.deliveryChanged.subscribe(e => emitted.push(e));

      comp.fireDeliveryChanged();

      expect(emitted[0].valid).toBe(true);
      expect(comp.enabled).toBe(false); // cause: delivery is disabled
   });

   // Bug A (Issue #74513): CSV empty-assemblies check must not override the disabled short-circuit.
   // When delivery is disabled, the user expects valid=true regardless of CSV configuration.
   // Issue #74513
   it("should emit valid=true when enabled=false even if CSV format has empty assemblies", async () => {
      const { comp } = await renderComponent({
         enabled: false,
         type: "viewsheet",
         csvExportModel: makeCSVModel(0),
      });
      comp.format = "CSV";

      const emitted: DeliveryEmails[] = [];
      comp.deliveryChanged.subscribe(e => emitted.push(e));

      comp.fireDeliveryChanged();

      expect(emitted[0].valid).toBe(true);
   });

   // 🔁 Regression-sensitive: enabled=true + valid form → valid=true.
   it("should emit valid=true when enabled=true with a valid sender and format", async () => {
      const { comp } = await renderComponent({ enabled: true, senderEnabled: true });

      comp.sender = "admin@example.com";
      comp.recipients = "user@example.com"; // [required]="true" adds Validators.required dynamically
      comp.format = "PDF";

      const emitted: DeliveryEmails[] = [];
      comp.deliveryChanged.subscribe(e => emitted.push(e));

      comp.fireDeliveryChanged();

      expect(emitted[0].valid).toBe(true);
      expect(comp.form.valid).toBe(true); // form is valid — not a format or sender issue
   });

   // Error: enabled=true + invalid sender → valid=false (form invalid).
   it("should emit valid=false when enabled=true and sender is invalid", async () => {
      const { comp } = await renderComponent({ enabled: true, senderEnabled: true });

      comp.sender = "not-an-email";
      const emitted: DeliveryEmails[] = [];
      comp.deliveryChanged.subscribe(e => emitted.push(e));

      comp.fireDeliveryChanged();

      expect(emitted[0].valid).toBe(false);
      expect(comp.form.get("sender").invalid).toBe(true);  // sender is the cause
      expect(comp.form.get("format").invalid).toBe(false); // not a format issue
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 2] — sender: senderEnabled=false disables field
// ---------------------------------------------------------------------------

describe("DeliveryEmailsComponent — sender: senderEnabled disables validation", () => {

   // 🔁 Regression-sensitive: when senderEnabled=false, the sender control is disabled —
   // disabled controls are excluded from form.valid, so required/email validators are bypassed.
   // Risk Point: a blank or invalid sender can reach the backend silently.
   it("should disable the sender control when senderEnabled is false", async () => {
      const { comp } = await renderComponent({ senderEnabled: false });

      comp.sender = "";

      expect(comp.form.get("sender").disabled).toBe(true);
   });

   // Happy: senderEnabled=true must enable the sender control so validators run.
   it("should enable the sender control and enforce email format when senderEnabled is true", async () => {
      const { comp } = await renderComponent({ senderEnabled: true });

      comp.sender = "bad-address";

      expect(comp.form.get("sender").enabled).toBe(true);
      expect(comp.form.get("sender").invalid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — changeFormat: format-driven side effects
// ---------------------------------------------------------------------------

describe("DeliveryEmailsComponent — changeFormat: format-driven side effects", () => {

   // 🔁 Regression-sensitive: CSV format on a viewsheet must auto-set bundledAsZip=true.
   // If this side effect is lost, the CSV attachment is sent un-bundled, changing download behavior.
   it("should force bundledAsZip=true when format is CSV on a viewsheet", async () => {
      const { comp } = await renderComponent({ type: "viewsheet" });

      comp.form.get("format").setValue("CSV");
      comp.changeFormat();

      expect(comp.bundledAsZip).toBe(true);
   });

   // 🔁 Regression-sensitive: HTML format must disable emailMatchLayout so layout options
   // are not configurable (HTML always matches layout by definition).
   it("should disable emailMatchLayout when format is HTML", async () => {
      const { comp } = await renderComponent();

      comp.form.get("format").setValue("Html"); // DashboardOptions.HTML
      comp.changeFormat();

      expect(comp.form.get("emailMatchLayout").disabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — togglePasswordForm: fipsMode suppresses zip fields
// ---------------------------------------------------------------------------

describe("DeliveryEmailsComponent — togglePasswordForm: fipsMode and passwordVisible", () => {

   // 🔁 Regression-sensitive: when fipsMode=true, passwordVisible=false and zip password
   // controls must remain disabled — FIPS-compliant environments prohibit zip encryption.
   it("should keep zip password controls disabled when fipsMode is true", async () => {
      const { comp } = await renderComponent({ fipsMode: true });

      comp.form.get("bundledAsZip").setValue(true, { emitEvent: false });
      comp.togglePasswordForm(false);

      expect(comp.form.get("zipPassword").disabled).toBe(true);
      expect(comp.form.get("verifyZipPassword").disabled).toBe(true);
      expect(comp.passwordVisible).toBe(false); // fipsMode suppresses the section
   });

   // Happy: bundledAsZip=true with fipsMode=false must enable zip password fields.
   it("should enable zip password controls when bundledAsZip=true and fipsMode=false", async () => {
      const { comp } = await renderComponent({ fipsMode: false });

      comp.form.get("bundledAsZip").setValue(true, { emitEvent: false });
      comp.togglePasswordForm(false);

      expect(comp.passwordVisible).toBe(true);
      expect(comp.form.get("zipPassword").enabled).toBe(true);
      expect(comp.form.get("verifyZipPassword").enabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — updateEnable: emailExpandSelections locked by emailMatchLayout
// ---------------------------------------------------------------------------

describe("DeliveryEmailsComponent — updateEnable: emailExpandSelections enable/disable", () => {

   // 🔁 Regression-sensitive: when emailMatchLayout=true, emailExpandSelections must be disabled —
   // expanding selections in match-layout mode is contradictory and the checkbox must be locked.
   it("should disable emailExpandSelections when emailMatchLayout is true", async () => {
      const { comp } = await renderComponent({ expandEnabled: true });

      comp.form.get("emailMatchLayout").setValue(true);
      // trigger updateEnable via valueChanges
      comp.form.get("emailMatchLayout").updateValueAndValidity();

      expect(comp.form.get("emailExpandSelections").disabled).toBe(true);
   });

   // Happy: emailMatchLayout=false with expandEnabled=true must re-enable emailExpandSelections.
   it("should enable emailExpandSelections when emailMatchLayout is false and expandEnabled is true", async () => {
      const { comp } = await renderComponent({ expandEnabled: true });

      comp.form.get("emailMatchLayout").setValue(false);
      comp.form.get("emailMatchLayout").updateValueAndValidity();

      expect(comp.form.get("emailExpandSelections").enabled).toBe(true);
   });

});
