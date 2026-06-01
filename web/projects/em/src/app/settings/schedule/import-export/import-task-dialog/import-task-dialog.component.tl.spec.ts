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
 * ImportTaskDialogComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3]  — finish(): only selected tasks are sent in the POST body
 *   Group 2 [Risk 2]  — model setter: auto-selects all tasks; clears previous selection
 *   Group 3 [Risk 2]  — loading setter: disables/enables the file form control
 *   Group 4 [Risk 2]  — masterToggle(): select all / deselect all toggle
 *   Group 5 [Risk 2]  — onImportComplete(): success dialog vs partial-failure warning dialog
 *
 * Confirmed bugs (it.failing until source is fixed):
 *
 *   Bug A — handleImportError() missing optional chaining on error.error.message:
 *     `error.error.message` throws TypeError when error.error is null (e.g. null JSON body on
 *     500). handleUploadError() uses a fixed message string and is unaffected.
 *     Fix: `error.error?.message` with a fallback (e.g. `error.message`).
 *
 *   Bug B — ImportTaskResponse.failedTasks typed as `[]` (empty tuple), not `string[]`:
 *     model/import-task-response.ts defines `failedTasks: []` but runtime/API returns task name
 *     strings (Java: List<String>). Tests use `as any` to assign string arrays. Component uses
 *     .length and .join() correctly. Fix: change to `failedTasks: string[]`.
 *
 * KEY contracts:
 *   - finish() collects selection.selected values and sends their `.task` strings in POST body.
 *   - model setter always calls selection.clear() then selection.select(...tasks) (all pre-selected).
 *   - loading=true  → uploadForm.get("file").disabled === true.
 *   - loading=false → uploadForm.get("file").disabled === false.
 *   - onImportComplete: failedTasks.length > 0 → WARNING dialog; else → INFO dialog.
 *   - onImportComplete: after dialog closes → dialogRef.close(true) in both branches.
 */

import { Component, forwardRef, NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { ControlValueAccessor, NG_VALUE_ACCESSOR, ReactiveFormsModule } from "@angular/forms";
import { MAT_DIALOG_DATA, MatDialog, MatDialogRef } from "@angular/material/dialog";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { HttpErrorResponse } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse } from "msw";
import { of } from "rxjs";

import { server } from "../../../../../../../../mocks/server";
import { ImportTaskDialogComponent } from "./import-task-dialog.component";
import { ImportTaskDialogModel } from "../../model/import-task-dialog-model";
import { TaskDependencyModel } from "../../model/task-dependency-model";
import { ImportTaskResponse } from "../../model/import-task-response";
import { MessageDialogType } from "../../../../common/util/message-dialog";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

// em-file-chooser appears in the upload form with formControlName="file" and must implement
// ControlValueAccessor so Angular can wire it up without the real component being declared.
@Component({
   selector: "em-file-chooser",
   template: "",
   providers: [{ provide: NG_VALUE_ACCESSOR, useExisting: forwardRef(() => FileChooserStub), multi: true }],
})
class FileChooserStub implements ControlValueAccessor {
   writeValue() {}
   registerOnChange() {}
   registerOnTouched() {}
}

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeTask(task: string, dependency = ""): TaskDependencyModel {
   return { task, dependency };
}

function makeImportModel(...taskNames: string[]): ImportTaskDialogModel {
   return { tasks: taskNames.map(t => makeTask(t)) };
}

const UPLOAD_URL = "*/api/em/content/schedule/set-task-file";
const IMPORT_URL = "*/api/em/content/schedule/import/*";

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComp(opts: { dialogClosesWith?: unknown } = {}) {
   const dialogRefSpy = { close: vi.fn() };
   const matDialogSpy = {
      open: vi.fn().mockReturnValue({
         afterClosed: () => of(opts.dialogClosesWith !== undefined ? opts.dialogClosesWith : undefined),
      }),
   };

   const result = await render(ImportTaskDialogComponent, {
      imports: [ReactiveFormsModule, NoopAnimationsModule, MatCheckboxModule],
      declarations: [FileChooserStub],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: MatDialogRef, useValue: dialogRefSpy },
         { provide: MatDialog, useValue: matDialogSpy },
         { provide: MAT_DIALOG_DATA, useValue: {} },
      ],
   });

   result.fixture.detectChanges();
   await result.fixture.whenStable();

   const comp = result.fixture.componentInstance as ImportTaskDialogComponent;
   return { comp, dialogRefSpy, matDialogSpy };
}

// ════════════════════════════════════════════════════════════════════════════
// Group 1 [Risk 3] — finish(): selected tasks sent in POST body
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — finish(): POST body contains only selected task names", () => {

   // 🔁 Regression-sensitive: finish() must send only the task NAMES (string[]) for the
   // user-selected rows, not all tasks. Sending unselected tasks silently imports unwanted content.
   it("should POST only the names of selected tasks", async () => {
      let capturedBody: string[] | null = null;
      server.use(
         http.post(IMPORT_URL, async ({ request }) => {
            capturedBody = await request.json() as string[];
            return HttpResponse.json({ failedTasks: [], failed: false } as ImportTaskResponse);
         })
      );

      const { comp } = await renderComp({ dialogClosesWith: undefined });
      comp.model = makeImportModel("Task1", "Task2", "Task3");

      // Deselect Task2 — only Task1 and Task3 remain selected
      comp.selection.deselect(comp.model.tasks[1]);

      comp.finish();

      await waitFor(() => expect(capturedBody).not.toBeNull());
      expect(capturedBody).toContain("Task1");
      expect(capturedBody).toContain("Task3");
      expect(capturedBody).not.toContain("Task2");
   });

   // Risk Point/Contract: sending an empty selection posts an empty array — backend should
   // handle it gracefully. The UI allows finish() even with no selection via hasSelected()=false
   // (hasSelected only guards the button disabled state, not the method itself).
   it("should POST an empty array when no tasks are selected", async () => {
      let capturedBody: string[] | null = null;
      server.use(
         http.post(IMPORT_URL, async ({ request }) => {
            capturedBody = await request.json() as string[];
            return HttpResponse.json({ failedTasks: [], failed: false } as ImportTaskResponse);
         })
      );

      const { comp } = await renderComp({ dialogClosesWith: undefined });
      comp.model = makeImportModel("Task1");
      comp.selection.clear();

      comp.finish();

      await waitFor(() => expect(capturedBody).not.toBeNull());
      expect(capturedBody).toEqual([]);
   });

   // 🔁 Regression-sensitive: overwrite flag is read from importForm.get("overwrite") and
   // placed in the URI path. Default overwrite=true must be sent as "true" in the URL segment.
   it("should include the overwrite flag from importForm in the request URI", async () => {
      let capturedUrl = "";
      server.use(
         http.post(IMPORT_URL, ({ request }) => {
            capturedUrl = request.url;
            return HttpResponse.json({ failedTasks: [], failed: false } as ImportTaskResponse);
         })
      );

      const { comp } = await renderComp({ dialogClosesWith: undefined });
      comp.model = makeImportModel("Task1");

      comp.finish();

      await waitFor(() => expect(capturedUrl).toBeTruthy());
      expect(capturedUrl).toMatch(/\/import\/true$/);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 2 [Risk 2] — model setter: auto-select all tasks
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — model setter: selection and dataSource", () => {

   // 🔁 Regression-sensitive: assigning model must auto-select ALL tasks so the user can
   // proceed with a complete import without manually clicking every row.
   it("should auto-select all tasks when model is assigned", async () => {
      const { comp } = await renderComp();

      comp.model = makeImportModel("TaskA", "TaskB", "TaskC");

      expect(comp.selection.selected.length).toBe(3);
      expect(comp.isAllSelected()).toBe(true);
   });

   // Risk Point/Contract: assigning a new model must clear any previous selection first
   // to prevent stale rows from a prior upload from contaminating the new selection.
   it("should clear the previous selection when a new model is assigned", async () => {
      const { comp } = await renderComp();

      comp.model = makeImportModel("OldTask1", "OldTask2");
      comp.selection.deselect(comp.model.tasks[0]); // deselect one

      comp.model = makeImportModel("NewTask1"); // replace model

      expect(comp.selection.selected.length).toBe(1);
      expect(comp.selection.selected[0].task).toBe("NewTask1");
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 3 [Risk 2] — loading setter: file control enable/disable
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — loading setter: file control state", () => {

   // 🔁 Regression-sensitive: while loading, the file control must be disabled so the user
   // cannot trigger a second upload while one is in flight. If not disabled, concurrent uploads
   // can produce race conditions in the task list.
   it("should disable the file control when loading is set to true", async () => {
      const { comp } = await renderComp();

      comp.loading = true;

      expect(comp.uploadForm.get("file")!.disabled).toBe(true);
   });

   // Risk Point/Contract: after loading completes, the file control must be re-enabled so
   // the user can upload another file or retry a failed upload.
   it("should re-enable the file control when loading is set back to false", async () => {
      const { comp } = await renderComp();

      comp.loading = true;
      comp.loading = false;

      expect(comp.uploadForm.get("file")!.disabled).toBe(false);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 4 [Risk 2] — masterToggle(): select all / deselect all
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — masterToggle(): select all and deselect all", () => {

   // 🔁 Regression-sensitive: when all tasks are already selected, masterToggle() must
   // deselect all. Using it as a "select all" when everything is already selected would
   // block the user from clearing a large import set quickly.
   it("should deselect all tasks when masterToggle is called and all are currently selected", async () => {
      const { comp } = await renderComp();

      comp.model = makeImportModel("T1", "T2");
      expect(comp.isAllSelected()).toBe(true);

      comp.masterToggle();

      expect(comp.selection.selected.length).toBe(0);
   });

   // Risk Point/Contract: when not all tasks are selected, masterToggle() must select all.
   it("should select all tasks when masterToggle is called and some are currently deselected", async () => {
      const { comp } = await renderComp();

      comp.model = makeImportModel("T1", "T2", "T3");
      comp.selection.deselect(comp.model.tasks[0]);

      comp.masterToggle();

      expect(comp.selection.selected.length).toBe(3);
      expect(comp.isAllSelected()).toBe(true);
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Group 5 [Risk 2] — onImportComplete(): success vs partial-failure dialog
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — onImportComplete(): dialog type and close behavior", () => {

   // 🔁 Regression-sensitive: a partial failure (some tasks failed) must show a WARNING dialog
   // listing the failed task names. Showing a SUCCESS dialog on partial failure misleads the user.
   it("should open a WARNING dialog listing failed task names when failedTasks is non-empty", async () => {
      const { comp, matDialogSpy, dialogRefSpy } = await renderComp({ dialogClosesWith: undefined });

      // Bug B — `as any` required until failedTasks is string[] in import-task-response.ts
      const response: ImportTaskResponse = { failedTasks: ["Task1", "Task2"] as any, failed: true };
      comp.onImportComplete(response);

      expect(matDialogSpy.open).toHaveBeenCalledTimes(1);
      const dialogData = matDialogSpy.open.mock.calls[0][1].data;
      expect(dialogData.type).toBe(MessageDialogType.WARNING);
      expect(dialogData.content).toContain("Task1");
      expect(dialogData.content).toContain("Task2");

      await waitFor(() => expect(dialogRefSpy.close).toHaveBeenCalledWith(true));
   });

   // Risk Point/Contract: when all tasks import successfully, an INFO dialog must be shown
   // (not WARNING). Showing a warning on success creates false alarm.
   it("should open an INFO dialog on full success and close the outer dialog with true", async () => {
      const { comp, matDialogSpy, dialogRefSpy } = await renderComp({ dialogClosesWith: undefined });

      const response: ImportTaskResponse = { failedTasks: [] as any, failed: false };
      comp.onImportComplete(response);

      expect(matDialogSpy.open).toHaveBeenCalledTimes(1);
      const dialogData = matDialogSpy.open.mock.calls[0][1].data;
      expect(dialogData.type).toBe(MessageDialogType.INFO);
      expect(dialogData.type).not.toBe(MessageDialogType.WARNING); // right output, right reason

      await waitFor(() => expect(dialogRefSpy.close).toHaveBeenCalledWith(true));
   });

});

// ════════════════════════════════════════════════════════════════════════════
// Confirmed bug — handleImportError() null error.error body
// ════════════════════════════════════════════════════════════════════════════

describe("ImportTaskDialogComponent — handleImportError(): null error body", () => {

   // Bug A — handleImportError() reads error.error.message without optional chaining.
   // Null JSON body (network/500) makes error.error null → TypeError before dialog opens.
   // Fix: error.error?.message ?? error.message (or equivalent fallback).
   it.fails("should open error dialog from handleImportError when error.error is null", async () => {
      const { comp, matDialogSpy } = await renderComp();
      const error = new HttpErrorResponse({
         error: null,
         status: 500,
         statusText: "Internal Server Error",
         url: "/api/em/content/schedule/import/true",
      });

      let threw = false;
      try {
         (comp as any).handleImportError(error);
      }
      catch {
         threw = true;
      }

      expect(threw).toBe(false);
      expect(matDialogSpy.open).toHaveBeenCalledTimes(1);
      const dialogData = matDialogSpy.open.mock.calls[0][1].data;
      expect(dialogData.type).toBe(MessageDialogType.ERROR);
      expect(dialogData.content).toBeTruthy();
      expect(comp.loading).toBe(false);
   });

});
