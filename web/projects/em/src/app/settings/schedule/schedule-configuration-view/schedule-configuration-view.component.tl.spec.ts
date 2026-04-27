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
 * ScheduleConfigurationViewComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — emailAddress control: enabled only when notifyIfDown || notifyIfTaskFailed (model setter + valueChanges)
 *   Group 2 [Risk 3] — fireChange: emitted model completeness (emailAddress, logFile from non-form fields)
 *   Group 3 [Risk 3] — form validators: concurrency/rmiPort boundaries; minMemory > maxMemory cross-field error
 *   Group 4 [Risk 2] — editClassPath: dialog result updates model.classpath and form classpath control
 *   Group 5 [Risk 2] — onTimeRangesChanged / onServerLocationsChanged: value propagation and fireChange
 *
 * KEY contracts:time-condition-editor.component
 *   - emailAddress is disabled by default; enabled iff notifyIfDown || notifyIfTaskFailed is true.
 *   - When emailAddress is disabled but holds a value, fireChange() preserves that value via
 *     form.get("emailAddress").value (bypasses the disabled-control exclusion in form.value).
 *   - logFile is not a form control but is injected into the emitted model from this.model.logFile.
 *   - smallerThan("minMemory", "maxMemory", false) adds {greaterThan: true} on the form group
 *     when minMemory.value > maxMemory.value (equal values are allowed).
 *   - fireChange() is a no-op when this.model is null/undefined.
 *   - Emitted {model, valid}.valid mirrors this.form.valid at the moment of emission.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { ControlValueAccessor, FormsModule, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { ErrorStateMatcher } from "@angular/material/core";
import { MatDialog } from "@angular/material/dialog";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { of } from "rxjs";

import { it } from "@jest/globals";
import { ScheduleConfigurationViewComponent, ScheduleConfiguration } from "./schedule-configuration-view.component";
import { ScheduleUsersService } from "../../../../../../shared/schedule/schedule-users.service";
import { ScheduleConfigurationModel } from "../model/schedule-configuration-model";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

// em-email-picker uses formControlName="emailAddress" — needs a CVA stub so
// ReactiveFormsModule can wire it up without the real component being declared.
@Component({
   selector: "em-email-picker",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => EmailPickerStub), multi: true }]
})
class EmailPickerStub implements ControlValueAccessor {
   writeValue() {} registerOnChange() {} registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeModel(overrides: Partial<ScheduleConfigurationModel> = {}): ScheduleConfigurationModel {
   return {
      concurrency: 4,
      logFile: "/var/log/scheduler.log",
      rmiPort: 1099,
      classpath: "/usr/share/lib/a.jar:/usr/share/lib/b.jar",
      pathSeparator: ":",
      notificationEmail: true,
      saveToDisk: false,
      emailDelivery: false,
      enableEmailBrowser: false,
      maxMemory: 2048,
      minMemory: 512,
      emailAddress: "admin@example.com",
      emailSubject: "Scheduler Alert",
      emailMessage: "The scheduler has an issue.",
      notifyIfDown: false,
      notifyIfTaskFailed: false,
      shareTaskInSameGroup: false,
      deleteTaskOnlyByOwner: false,
      timeRanges: [],
      serverLocations: [],
      saveAutoSuffix: "",
      securityEnable: false,
      cloudSecrets: false,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(modelOverrides: Partial<ScheduleConfigurationModel> = {}) {
   const dialogMock = {
      open: jest.fn(),
   };

   const usersServiceMock = {
      getEmailUsers: jest.fn(() => of([])),
      getGroups: jest.fn(() => of([])),
   };

   const result = await render(ScheduleConfigurationViewComponent, {
      imports: [FormsModule, ReactiveFormsModule, MatCheckboxModule, NoopAnimationsModule],
      declarations: [EmailPickerStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         { provide: ErrorStateMatcher, useValue: { isErrorState: () => false } },
         { provide: ScheduleUsersService, useValue: usersServiceMock },
         { provide: MatDialog, useValue: dialogMock },
      ],
      componentProperties: {
         model: makeModel(modelOverrides),
      },
   });

   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance,
      dialogMock,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — emailAddress control: enable/disable via notification flags
// ---------------------------------------------------------------------------

describe("ScheduleConfigurationViewComponent — emailAddress: enabled by notifyIfDown || notifyIfTaskFailed", () => {

   // 🔁 Regression-sensitive: model setter must enable the emailAddress control when notifyIfDown=true.
   // If enable() is not called, emailAddress is permanently disabled regardless of checkbox state,
   // and the saved notification address will always be empty.
   it("should enable emailAddress and set emailEditable when model.notifyIfDown is true", async () => {
      const { comp } = await renderComponent({ notifyIfDown: true });

      expect(comp.form.get("emailAddress").enabled).toBe(true);
      expect(comp.emailEditable).toBe(true);
   });

   // 🔁 Regression-sensitive: notifyIfTaskFailed alone must also enable the email field.
   // This is the other branch of the OR condition — missing it means task-failure emails are silently lost.
   it("should enable emailAddress when only model.notifyIfTaskFailed is true", async () => {
      const { comp } = await renderComponent({ notifyIfTaskFailed: true });

      expect(comp.form.get("emailAddress").enabled).toBe(true);
      expect(comp.emailEditable).toBe(true);
   });

   // 🔁 Regression-sensitive: when BOTH flags are false the email field must stay disabled.
   // An enabled-but-untriggered address control would cause blank notification emails to be saved.
   it("should disable emailAddress and clear emailEditable when both flags are false", async () => {
      const { comp } = await renderComponent({ notifyIfDown: false, notifyIfTaskFailed: false });

      expect(comp.form.get("emailAddress").disabled).toBe(true);
      expect(comp.emailEditable).toBe(false);
   });

   // 🔁 Regression-sensitive: toggling notifyIfDown via valueChanges (checkbox click) must enable
   // emailAddress even when notifyIfTaskFailed stays false — the checkbox path must go through the
   // same enable/disable logic as the model setter path.
   it("should enable emailAddress when notifyIfDown checkbox changes to true with notifyIfTaskFailed still false", async () => {
      const { comp } = await renderComponent({ notifyIfDown: false, notifyIfTaskFailed: false });

      expect(comp.form.get("emailAddress").disabled).toBe(true); // precondition

      comp.form.get("notifyIfDown").setValue(true);

      expect(comp.form.get("emailAddress").enabled).toBe(true);
      expect(comp.emailEditable).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — fireChange: emitted model completeness
// ---------------------------------------------------------------------------

describe("ScheduleConfigurationViewComponent — fireChange: emitted model completeness", () => {

   // 🔁 Regression-sensitive: when emailAddress is disabled, Angular's form.value excludes it.
   // fireChange() bypasses this by reading form.get("emailAddress").value directly, so the
   // existing address is preserved in the emitted model.  If this read is removed during
   // refactoring, callers silently lose the notification address.
   it("should preserve emailAddress in emitted model even when the control is disabled", async () => {
      const { comp } = await renderComponent({
         notifyIfDown: false,
         notifyIfTaskFailed: false,
         emailAddress: "admin@example.com",
      });

      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onTimeRangesChanged([]); // triggers fireChange() directly

      // setValue inside onTimeRangesChanged may fire once via valueChanges subscription
      // (set up after setTimeout(200)) and once from the explicit fireChange() call — so
      // we check the last emission rather than asserting an exact count.
      expect(emitted.length).toBeGreaterThan(0);
      expect(emitted.at(-1).model.emailAddress).toBe("admin@example.com");
   });

   // 🔁 Regression-sensitive: logFile is not a form control — it is injected into the emitted
   // model from this.model.logFile.  If fireChange() is refactored to emit a plain form.value
   // snapshot without this merge, logFile is silently dropped and the UI loses the log path.
   it("should include logFile from the original model in the emitted model", async () => {
      const { comp } = await renderComponent({ logFile: "/var/log/scheduler.log" });

      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onTimeRangesChanged([]);

      expect(emitted[0].model.logFile).toBe("/var/log/scheduler.log");
   });

   // Happy: when form is fully valid, emitted valid must be true.
   it("should emit valid=true when all required fields are within bounds", async () => {
      const { comp } = await renderComponent({
         concurrency: 4,
         rmiPort: 1099,
         classpath: "/usr/share/lib",
         minMemory: 512,
         maxMemory: 2048,
      });

      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onTimeRangesChanged([]);

      expect(emitted[0].valid).toBe(true);
      expect(comp.form.valid).toBe(true); // form agrees — not a mismatch
   });

   // Error: when an invalid control exists, emitted valid must be false so the parent can block save.
   it("should emit valid=false when a required field is invalid", async () => {
      const { comp } = await renderComponent({ concurrency: 0 });

      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onTimeRangesChanged([]);

      expect(emitted[0].valid).toBe(false);
      expect(comp.form.get("concurrency").invalid).toBe(true); // concurrency is the cause
      expect(comp.form.get("rmiPort").invalid).toBe(false);    // no false-positive
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 3] — form validators: numeric boundary and cross-field
// ---------------------------------------------------------------------------

describe("ScheduleConfigurationViewComponent — form validators: numeric boundaries and cross-field memory check", () => {

   // 🔁 Regression-sensitive: concurrency=0 violates min(1) — the scheduler would accept
   // zero threads, making the service unresponsive to all tasks.
   it("should invalidate concurrency when set to 0", async () => {
      const { comp } = await renderComponent({ concurrency: 1 });

      comp.form.get("concurrency").setValue(0);

      expect(comp.form.get("concurrency").invalid).toBe(true);
      expect(comp.form.get("rmiPort").invalid).toBe(false); // isolated to concurrency
   });

   // 🔁 Regression-sensitive: rmiPort=65536 exceeds max(65535) — saving would persist an
   // impossible port number that silently breaks the RMI listener on restart.
   it("should invalidate rmiPort when set to 65536 (above max)", async () => {
      const { comp } = await renderComponent({ rmiPort: 1099 });

      comp.form.get("rmiPort").setValue(65536);

      expect(comp.form.get("rmiPort").invalid).toBe(true);
      expect(comp.form.get("concurrency").invalid).toBe(false); // isolated to rmiPort
   });

   // 🔁 Regression-sensitive: minMemory > maxMemory must trigger the form-level greaterThan
   // error that the errorStateMatcher surfaces on both memory inputs.  If the cross-field validator
   // is dropped during refactoring, invalid memory configurations are silently accepted and the
   // scheduler JVM will fail at startup.
   it("should set form greaterThan error when minMemory exceeds maxMemory", async () => {
      const { comp } = await renderComponent({ minMemory: 512, maxMemory: 2048 });

      comp.form.get("minMemory").setValue(4096);
      comp.form.get("maxMemory").setValue(1024);

      expect(comp.form.errors).toBeTruthy();
      expect(comp.form.errors["greaterThan"]).toBe(true);
      expect(comp.form.valid).toBe(false);
   });

   // Happy: equal minMemory and maxMemory must NOT trigger the greaterThan error
   // (smallerThan was called with orEqualTo=false, so equal values are permissible).
   it("should NOT produce greaterThan error when minMemory equals maxMemory", async () => {
      const { comp } = await renderComponent({ minMemory: 1024, maxMemory: 1024 });

      expect(comp.form.errors).toBeFalsy();
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — editClassPath: dialog interaction
// ---------------------------------------------------------------------------

describe("ScheduleConfigurationViewComponent — editClassPath: dialog result updates classpath", () => {

   // 🔁 Regression-sensitive: when the dialog is confirmed with a new classpath string, both
   // model.classpath (source of truth for re-opening the dialog) and the form classpath control
   // (used for validation and emission) must be updated atomically.  If one is missed, the next
   // dialog open shows stale data while the form has already changed.
   it("should update model.classpath and form classpath control when dialog closes with data", async () => {
      const { comp, dialogMock } = await renderComponent();

      const newClasspath = "/opt/lib/new.jar:/opt/lib/extra.jar";
      dialogMock.open.mockReturnValue({ afterClosed: () => of(newClasspath) });

      comp.editClassPath();

      expect(comp.model.classpath).toBe(newClasspath);
      expect(comp.form.get("classpath").value).toBe(newClasspath);
   });

   // Error: when the dialog is dismissed (data=null/undefined), classpath must remain unchanged.
   it("should leave model.classpath and form unchanged when dialog is dismissed without data", async () => {
      const { comp, dialogMock } = await renderComponent({ classpath: "/original/path.jar" });

      dialogMock.open.mockReturnValue({ afterClosed: () => of(null) });

      comp.editClassPath();

      expect(comp.model.classpath).toBe("/original/path.jar");
      expect(comp.form.get("classpath").value).toBe("/original/path.jar");
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — onTimeRangesChanged / onServerLocationsChanged
// ---------------------------------------------------------------------------

describe("ScheduleConfigurationViewComponent — onTimeRangesChanged / onServerLocationsChanged: propagation", () => {

   // 🔁 Regression-sensitive: onTimeRangesChanged must write new ranges into the form control
   // AND immediately call fireChange() so the parent receives an updated model.
   // If fireChange() is not called here, time-range edits are silently dropped until the next
   // unrelated form change triggers the subscription.
   it("should update the timeRanges form control and emit onChange when time ranges change", async () => {
      const { comp } = await renderComponent();

      const newRanges = [{ name: "Business Hours" } as any];
      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onTimeRangesChanged(newRanges);

      expect(comp.form.get("timeRanges").value).toEqual(newRanges);
      expect(emitted.length).toBeGreaterThan(0);
   });

   // 🔁 Regression-sensitive: onServerLocationsChanged must update the serverLocations form value
   // and trigger onChange.  Missing this means the saved config never reflects server-location edits.
   it("should update the serverLocations form control and emit onChange when server locations change", async () => {
      const { comp } = await renderComponent();

      const newLocations = [{ serverName: "node-1" } as any];
      const emitted: ScheduleConfiguration[] = [];
      comp.onChange.subscribe(e => emitted.push(e));

      comp.onServerLocationsChanged(newLocations);

      expect(comp.form.get("serverLocations").value).toEqual(newLocations);
      expect(emitted.length).toBeGreaterThan(0);
   });

});
