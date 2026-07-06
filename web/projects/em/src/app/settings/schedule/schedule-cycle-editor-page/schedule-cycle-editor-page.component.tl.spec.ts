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
 * ScheduleCycleEditorPageComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2]  — valid getter: composite sub-validator contract
 *   Group 2 [Risk 2]  — loadModel(): success populates model; error with message opens dialog → close;
 *                        error without message closes directly
 *   Group 3 [Risk 2]  — save(): success shows snackbar + resets taskChanged; error opens dialog
 *   Group 4 [Risk 2]  — name.valueChanges: propagates to model.label and sets taskChanged
 *   Group 5 [Risk 2]  — copyCondition(): COPY_OF_PREFIX stripping for nested copies
 *   Group 6 [Risk 2]  — deleteConditions(): confirmed dialog splices condition and adjusts index
 *
 * Confirmed bugs (it.failing until source is fixed):
 *   Bug #75126:
 *   Bug A — save() missing optional chaining on error.error.message:
 *     loadModel() uses `error.error?.message` (safe); save() uses `error.error.message`
 *     (unsafe). Null JSON body makes error.error null → TypeError in the RxJS error handler.
 *     Fix: align save() with loadModel() — guard with `error.error?.message`.
 *
 * KEY contracts:
 *   - valid = conditionsValid && optionsValid && name.valid && taskChanged (ALL four required).
 *   - copyCondition() strips ALL nested COPY_OF_PREFIX occurrences before adding one.
 *   - deleteConditions() decrements selectedConditionIndex only when it equals conditionItems.length
 *     after the splice (i.e., was pointing at the last item).
 *   - name.valueChanges always propagates to model.label and sets taskChanged=true.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { HttpErrorResponse, provideHttpClient } from "@angular/common/http";
import { ReactiveFormsModule } from "@angular/forms";
import { MatDialog } from "@angular/material/dialog";
import { MatSnackBar } from "@angular/material/snack-bar";
import { ActivatedRoute, Router } from "@angular/router";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";

import { EMPTY, of } from "rxjs";
import { server } from "@test-mocks/server";
import { ScheduleCycleEditorPageComponent } from "./schedule-cycle-editor-page.component";
import { ScheduleCycleDialogModel } from "../model/schedule-cycle-dialog-model";
import { TimeZoneService } from "../../../../../../shared/schedule/time-zone.service";
import { PageHeaderService } from "../../../page-header/page-header.service";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeCycleModel(overrides: Partial<ScheduleCycleDialogModel> = {}): ScheduleCycleDialogModel {
   return {
      name: "TestCycle",
      label: "Test Cycle",
      taskDefaultTime: false,
      timeZone: "America/New_York",
      timeZoneOptions: [{ timeZoneId: "America/New_York", label: "EST" }],
      conditionPaneModel: {
         conditions: [{ label: "Cond 1", conditionType: "TimeCondition" } as any],
         timeZoneOffset: 0
      } as any,
      cycleInfo: {
         name: "TestCycle",
         startNotify: false, startEmail: "",
         endNotify: false, endEmail: "",
         failureNotify: false, failureEmail: "",
         exceedNotify: false, threshold: 0, exceedEmail: ""
      },
      permissionModel: null as any,
      startTimeEnabled: true,
      ...overrides,
   } as ScheduleCycleDialogModel;
}

const CYCLE_GET_URL = "*/api/em/schedule/cycle-dialog-model/*";
const CYCLE_SAVE_URL = "*/api/em/schedule/edit-cycle";

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(opts: {
   model?: ScheduleCycleDialogModel;
   dialogConfirms?: boolean;
   /** Override the GET handler for the cycle-dialog-model endpoint. When provided, the
    *  helper skips waitFor(model) because the model will never be set on error paths. */
   getHandler?: Parameters<typeof http.get>[1];
} = {}) {
   const model = opts.model ?? makeCycleModel();

   server.use(
      http.get(CYCLE_GET_URL, opts.getHandler ?? (() => HttpResponse.json(model)))
   );

   const dialogMock = {
      open: vi.fn().mockReturnValue({ afterClosed: () => of(opts.dialogConfirms ?? false) })
   };
   const snackBarMock = { open: vi.fn() };
   const routerMock = { navigate: vi.fn(), events: EMPTY };

   const result = await render(ScheduleCycleEditorPageComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: MatDialog, useValue: dialogMock },
         { provide: MatSnackBar, useValue: snackBarMock },
         { provide: Router, useValue: routerMock },
         { provide: ActivatedRoute, useValue: { params: of({ cycle: "TestCycle" }) } },
         { provide: TimeZoneService, useValue: { updateTimeZoneOptions: vi.fn((opts: any) => opts) } },
         { provide: PageHeaderService, useValue: { title: "" } },
      ],
   });

   const comp = result.fixture.componentInstance;

   if(!opts.getHandler) {
      await waitFor(() => expect(comp.model).toBeDefined());
   }

   return { ...result, comp, dialogMock, snackBarMock, routerMock };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 2] — valid getter: composite sub-validator contract
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — valid getter: all sub-validators required", () => {

   // 🔁 Regression-sensitive: all four sub-validators (conditionsValid, optionsValid, name.valid,
   // taskChanged) must be true simultaneously. Removing any one silently allows saving with
   // incomplete or unchanged state.
   it("should return false when taskChanged is false even if name is valid and all else is true", async () => {
      const { comp } = await renderComponent();

      comp.conditionsValid = true;
      comp.optionsValid = true;
      comp.name.setValue("ValidCycleName"); // triggers valueChanges → taskChanged=true
      comp.taskChanged = false;             // reset AFTER setValue to isolate the flag

      expect(comp.valid).toBe(false);
      expect(comp.taskChanged).toBe(false); // taskChanged is the cause
      expect(comp.name.valid).toBe(true);   // name is not the cause
   });

   // Risk Point/Contract: invalid name control must block the save button independently of taskChanged.
   it("should return false when name is empty (required error) even when taskChanged is true", async () => {
      const { comp } = await renderComponent();

      comp.conditionsValid = true;
      comp.optionsValid = true;
      comp.name.setValue(""); // empty string → required error; also sets taskChanged=true via subscription
      comp.taskChanged = true; // ensure taskChanged=true to isolate name validity as the cause
      comp.name.markAsTouched();

      expect(comp.valid).toBe(false);
      expect(comp.name.valid).toBe(false); // name is the cause
      expect(comp.taskChanged).toBe(true);
   });

   // Happy: all four sub-validators pass → valid is true.
   it("should return true when taskChanged, valid name, conditionsValid, and optionsValid are all true", async () => {
      const { comp } = await renderComponent();

      comp.taskChanged = true;
      comp.conditionsValid = true;
      comp.optionsValid = true;
      comp.name.setValue("ValidCycleName");

      expect(comp.valid).toBe(true);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — loadModel(): success and error paths
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — loadModel(): HTTP success and error paths", () => {

   // 🔁 Regression-sensitive: loadModel must populate model and sync name control without
   // triggering taskChanged. loadModel calls name.setValue with {emitEvent:false} to prevent
   // the valueChanges subscription from setting taskChanged=true on initial load.
   it("should populate model and sync name control without setting taskChanged", async () => {
      const { comp } = await renderComponent();

      expect(comp.model.name).toBe("TestCycle");
      expect(comp.name.value).toBe("TestCycle"); // model.name (set via loadModel with emitEvent:false)
      expect(comp.taskChanged).toBe(false);
   });

   // Risk Point/Contract: error WITH error.error?.message → dialog shown, then router.navigate on close.
   it("should open error dialog and navigate away when loadModel fails with a message", async () => {
      const { dialogMock, routerMock } = await renderComponent({
         getHandler: () => HttpResponse.json({ message: "Cycle not found" }, { status: 404 })
      });

      await waitFor(() => expect(dialogMock.open).toHaveBeenCalledTimes(1));
      const dialogData = dialogMock.open.mock.calls[0][1].data;
      expect(dialogData.content).toContain("Cycle not found");
      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalledWith(["/settings/schedule/cycles"]));
   });

   // Risk Point/Contract: error WITHOUT error.error?.message → skip dialog, close immediately.
   it("should navigate away directly when loadModel fails without an error message body", async () => {
      const { dialogMock, routerMock } = await renderComponent({
         getHandler: () => new Response(null, { status: 500 })
      });

      await waitFor(() => expect(routerMock.navigate).toHaveBeenCalledWith(["/settings/schedule/cycles"]));
      expect(dialogMock.open).not.toHaveBeenCalled();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — save(): success and error paths
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — save(): success and error paths", () => {

   // 🔁 Regression-sensitive: taskChanged must reset to false ONLY inside the success callback.
   // If reset synchronously before the HTTP response, a subsequent error leaves taskChanged=false,
   // permanently disabling the Save button.
   it("should set taskChanged=false and open snackbar after a successful save", async () => {
      server.use(
         http.post(CYCLE_SAVE_URL, () => HttpResponse.json(makeCycleModel()))
      );

      const { comp, snackBarMock } = await renderComponent();
      comp.taskChanged = true;
      comp.save();

      await waitFor(() => expect(comp.taskChanged).toBe(false));
      expect(snackBarMock.open).toHaveBeenCalledTimes(1);
   });

   // Risk Point/Contract: save error with a message → dialog.open() called with error content.
   it("should open error dialog when save fails with an error message in the response body", async () => {
      server.use(
         http.post(CYCLE_SAVE_URL, () =>
            HttpResponse.json({ message: "Cycle name already exists" }, { status: 400 })
         )
      );

      const { comp, dialogMock } = await renderComponent();
      comp.taskChanged = true;
      comp.save();

      await waitFor(() => expect(dialogMock.open).toHaveBeenCalledTimes(1));
      const dialogData = dialogMock.open.mock.calls[0][1].data;
      expect(dialogData.content).toContain("Cycle name already exists");
   });

   // Bug A — save() error handler reads error.error.message without optional chaining (loadModel is safe).
   // Null JSON body → TypeError in save() subscribe; no dialog. Fix: use error.error?.message like loadModel().
   // Spy http.post so the real save() subscribe error callback runs with a null body (same as MSW 500/null).
   it.fails("should not throw when save error body is null", async () => {
      const { comp, dialogMock } = await renderComponent();
      const httpError = new HttpErrorResponse({
         error: null,
         status: 500,
         statusText: "Internal Server Error",
      });

      let errorHandlerThrew = false;
      vi.spyOn(comp["http"], "post").mockReturnValue({
         subscribe(_success: unknown, error: (err: HttpErrorResponse) => void) {
            try {
               error(httpError);
            }
            catch {
               errorHandlerThrew = true;
            }
            return { unsubscribe: () => {} };
         },
      } as any);

      comp.taskChanged = true;
      comp.save();

      expect(errorHandlerThrew).toBe(false);
      expect(dialogMock.open).not.toHaveBeenCalled();
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — name.valueChanges: model.label and taskChanged propagation
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — name valueChanges: label and taskChanged", () => {

   // 🔁 Regression-sensitive: name control changes must propagate to model.label so the
   // displayed name on the list page reflects the user's edits after save.
   it("should update model.label when the name control value changes", async () => {
      const { comp } = await renderComponent();

      comp.name.setValue("Updated Cycle Name");

      expect(comp.model.label).toBe("Updated Cycle Name");
   });

   // Risk Point/Contract: name change must set taskChanged=true so the Save button becomes active.
   it("should set taskChanged=true when the name control changes", async () => {
      const { comp } = await renderComponent();
      comp.taskChanged = false;

      comp.name.setValue("New Name");

      expect(comp.taskChanged).toBe(true);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — copyCondition(): COPY_OF_PREFIX stripping
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — copyCondition(): COPY_OF_PREFIX handling", () => {

   // 🔁 Regression-sensitive: copying a condition must prepend COPY_OF_PREFIX to the base label.
   it("should prepend the COPY_OF_PREFIX to the condition label on copy", async () => {
      const { comp } = await renderComponent();

      comp.selectedConditionIndex = 0;
      comp.model.conditionPaneModel.conditions[0].label = "Morning Run";
      comp.conditionItems[0].label = "Morning Run";

      comp.copyCondition();

      const copiedLabel = comp.conditionItems[comp.selectedConditionIndex].label;
      expect(copiedLabel).toMatch(/^_#\(js:Copy of\) Morning Run$/);
   });

   // 🔁 Regression-sensitive: copying a copy must NOT nest the prefix — the while-loop strips all
   // existing prefixes before adding one. If stripped incorrectly, repeated copies create
   // deeply nested labels that don't fit in the UI list.
   it("should strip all nested COPY_OF_PREFIX occurrences and add only one fresh prefix on copy-of-copy", async () => {
      const { comp } = await renderComponent();

      const prefix = "_#(js:Copy of) ";
      const nestedLabel = `${prefix}${prefix}Original`;
      comp.selectedConditionIndex = 0;
      comp.model.conditionPaneModel.conditions[0].label = nestedLabel;
      comp.conditionItems[0].label = nestedLabel;

      comp.copyCondition();

      const copiedLabel = comp.conditionItems[comp.selectedConditionIndex].label;
      expect(copiedLabel).toBe(`${prefix}Original`);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 6 [Risk 2] — deleteConditions(): confirmation + index adjustment
// ════════════════════════════════════════════════════════════════════════════

describe("ScheduleCycleEditorPageComponent — deleteConditions(): splice and index adjustment", () => {

   // Risk Point/Contract: dialog cancel (false) must not modify conditions.
   it("should not splice conditions when the confirmation dialog is cancelled", async () => {
      const { comp, dialogMock } = await renderComponent({ dialogConfirms: false });

      comp.addCondition(); // now 2 conditions
      const countBefore = comp.conditionItems.length;
      comp.selectedConditionIndex = 0;

      comp.deleteConditions();

      await waitFor(() => expect(dialogMock.open).toHaveBeenCalled());
      expect(comp.conditionItems.length).toBe(countBefore);
   });

   // 🔁 Regression-sensitive: deleting the last item in the list must decrement
   // selectedConditionIndex so it points at the new last item, not an out-of-bounds index.
   it("should decrement selectedConditionIndex when the last-indexed item is deleted", async () => {
      const { comp } = await renderComponent({ dialogConfirms: true });

      comp.addCondition(); // now 2 conditions (indices 0, 1)
      comp.selectedConditionIndex = 1; // select the last

      comp.deleteConditions();

      await waitFor(() => expect(comp.conditionItems.length).toBe(1));
      expect(comp.selectedConditionIndex).toBeLessThan(comp.conditionItems.length);
      expect(comp.selectedConditionIndex).toBe(0);
   });

});
