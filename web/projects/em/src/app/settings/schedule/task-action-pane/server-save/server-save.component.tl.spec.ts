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
 * ServerSaveComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isValid / fireServerSaveChanged: valid output contract
 *   Group 2 [Risk 3] — updatePath: ftp auto-detection is sticky after ftp: true
 *   Group 3 [Risk 3] — findDuplicate / findDuplicateFormat: duplicate detection contracts
 *   Group 4 [Risk 2] — directoryPathValidator / relativePathValidator: path rejection rules
 *   Group 5 [Risk 2] — removeFile: removes by reference and fires serverSaveChanged
 *
 * KEY contracts:
 *   - `isValid()` returns false when: enabled+no files, duplicate entries, invalid form,
 *     or CSV format with no selected assemblies.
 *   - `updatePath(file, fireByFtp=false)` auto-sets `file.ftp = true` when the path starts
 *     with "ftp:" or "sftp:". Once set, the ftp flag is STICKY — it stays true even if
 *     the path is later changed to a non-ftp value (because the OR is not reset).
 *   - `findDuplicateFormat` catches same-format/different-path (parallel saves in same format).
 *   - `findDuplicate` catches same-format/same-path (exact duplicate entries).
 *   - `directoryPathValidator` rejects paths ending with `/`, `\`, or `:`.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule, ReactiveFormsModule } from "@angular/forms";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { MatRadioModule } from "@angular/material/radio";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";

import { ServerSaveComponent, ServerSave, ServerSaveFile } from "./server-save.component";
import { ExportFormatModel } from "../../../../../../../shared/schedule/model/export-format-model";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeFormat(type: string, label: string): ExportFormatModel {
   return { type, label } as ExportFormatModel;
}

function makePathModel(overrides: Partial<ServerPathInfoModel> = {}): ServerPathInfoModel {
   return {
      path: "",
      ftp: false,
      useCredential: false,
      secretId: "",
      username: "",
      password: "",
      oldFormat: -1,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<ServerSaveComponent> = {}) {
   const result = await render(ServerSaveComponent, {
      imports: [FormsModule, ReactiveFormsModule, MatCheckboxModule, MatRadioModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         formats: [makeFormat("0", "Excel"), makeFormat("3", "CSV"), makeFormat("1", "PDF")],
         ...props,
      },
   });

   await result.fixture.whenStable();

   return { ...result, comp: result.fixture.componentInstance };
}

/** Set up the component with N files by pushing matching formats/paths/pathModels. */
function addFileEntry(comp: ServerSaveComponent, format: string, path: string, ftp = false) {
   const formats = comp.saveFormats.concat(format);
   const paths = comp.savePaths.concat(path);
   const pathModels = comp.serverPaths.concat(makePathModel({ path, ftp }));
   // Assign all three together — each setter calls updateFiles() but initForm()
   // rebuilds from all three arrays, so set the internal state in one pass.
   comp.saveFormats = formats;
   comp.savePaths = paths;
   comp.serverPaths = pathModels;
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — isValid: valid output contract
// ---------------------------------------------------------------------------

describe("ServerSaveComponent — isValid: valid output contract", () => {

   // 🔁 Regression-sensitive: when save-to-disk is disabled, valid must always be true —
   // blocking the scheduler on a disabled action would be a false error for the user.
   it("should emit valid=true when enabled is false, regardless of files", async () => {
      const { comp } = await renderComponent({ enabled: false });

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.fireServerSaveChanged();

      expect(emitted[0].valid).toBe(true);
   });

   // 🔁 Regression-sensitive: empty files list with enabled=true must be invalid —
   // allowing save-to-disk without destinations would produce a silent no-op.
   it("should emit valid=false when enabled=true and no files are configured", async () => {
      const { comp } = await renderComponent({ enabled: true });

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.fireServerSaveChanged();

      expect(emitted[0].valid).toBe(false);
      // Warning semantics: no destinations → filesRequired warning should be active,
      // duplicate warnings are not.
      expect(comp.files.length).toBe(0);
      expect(comp.findDuplicate).toBeUndefined();
      expect(comp.findDuplicateFormat).toBeUndefined();
   });

   // 🔁 Regression-sensitive: exact duplicate (same format + same path) must produce valid=false.
   it("should emit valid=false when two files have the same format and same path", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/reports/output.xlsx");
      addFileEntry(comp, "0", "/reports/output.xlsx");

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.fireServerSaveChanged();

      expect(emitted[0].valid).toBe(false);
      // Duplicate warning semantics: same format + same path → findDuplicate=true, findDuplicateFormat=false
      expect(comp.findDuplicate).toBeTruthy();
      expect(comp.findDuplicateFormat).toBeFalsy();
   });

   // 🔁 Regression-sensitive: same format but different paths should hit the duplicate-format warning
   // (findDuplicateFormat) rather than the exact-duplicate warning (findDuplicate).
   it("should trigger duplicate-format warning when two files share the same format but different paths", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/reports/output-A.xlsx");
      addFileEntry(comp, "0", "/reports/output-B.xlsx");

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.fireServerSaveChanged();

      expect(emitted[0].valid).toBe(false);
      expect(comp.findDuplicateFormat).toBeTruthy();
      expect(comp.findDuplicate).toBeFalsy();
   });

   // Happy: single file with a valid path → valid=true.
   it("should emit valid=true when enabled=true and one file with a non-empty path is set", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/reports/output.xlsx");

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.fireServerSaveChanged();

      expect(emitted[0].valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — updatePath: ftp sticky flag
// ---------------------------------------------------------------------------

describe("ServerSaveComponent — updatePath: ftp auto-detection stickiness", () => {

   // 🔁 Regression-sensitive: a path beginning with "ftp:" must auto-enable the ftp flag
   // so the backend knows to use FTP transport even if the user didn't check the box.
   it("should set file.ftp to true when path starts with 'ftp:'", async () => {
      const { comp } = await renderComponent();

      // updatePath(else branch) does `file.path = file.filePath` first, so filePath drives the check
      const file: ServerSaveFile = { format: "0", path: "", filePath: "ftp://server/output.xlsx", ftp: false };
      comp.updatePath(file, false);

      expect(file.ftp).toBe(true);
   });

   // 🔁 Regression-sensitive: once ftp=true (auto-set from path), changing the path to a
   // non-ftp URL does NOT reset ftp because `file.ftp = file.ftp || condition` short-circuits.
   // The only way to clear ftp is through the checkbox (updateFtp → fireByFtp=true).
   // Risk Point: user types "ftp://..." then edits to "/local/path" — FTP flag silently stays on.
   it("should NOT clear file.ftp when path changes away from ftp prefix (sticky behavior)", async () => {
      const { comp } = await renderComponent();

      const file: ServerSaveFile = { format: "0", path: "", filePath: "ftp://server/output.xlsx", ftp: false };
      // First call sets ftp=true via path detection
      comp.updatePath(file, false);
      expect(file.ftp).toBe(true);

      // Second call with a non-ftp filePath: ftp should STILL be true (sticky)
      file.filePath = "/local/output.xlsx";
      comp.updatePath(file, false);

      expect(file.ftp).toBe(true);
   });

   // Happy: fireByFtp=true must skip the auto-detection entirely — the FTP checkbox
   // controls the flag directly via ngModel, not updatePath.
   it("should not modify file.ftp when fireByFtp is true", async () => {
      const { comp } = await renderComponent();

      const file: ServerSaveFile = { format: "0", path: "", filePath: "ftp://server/output.xlsx", ftp: false };
      comp.updatePath(file, true);

      expect(file.ftp).toBe(false);
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 3] — findDuplicate / findDuplicateFormat
// ---------------------------------------------------------------------------

describe("ServerSaveComponent — findDuplicate / findDuplicateFormat: duplicate detection", () => {

   // 🔁 Regression-sensitive: same format + same path must be detected by findDuplicate.
   it("should detect exact duplicates (same format and same path) via findDuplicate", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report.xlsx");
      addFileEntry(comp, "0", "/out/report.xlsx");

      expect(comp.findDuplicate).toBeDefined();
   });

   // 🔁 Regression-sensitive: same format + different path must be detected by findDuplicateFormat
   // (two outputs in the same format to different locations — likely unintentional).
   it("should detect format-only duplicates (same format, different path) via findDuplicateFormat", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report-a.xlsx");
      addFileEntry(comp, "0", "/out/report-b.xlsx");

      expect(comp.findDuplicateFormat).toBeDefined();
   });

   // Boundary: different format + same path is NOT a duplicate — valid to export the same
   // path in multiple formats (e.g., PDF and Excel side by side).
   it("should return undefined for different formats at the same path (not a duplicate)", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report.xlsx");
      addFileEntry(comp, "1", "/out/report.xlsx");

      expect(comp.findDuplicate).toBeUndefined();
      expect(comp.findDuplicateFormat).toBeUndefined();
   });

   // Boundary: single file — no duplicate possible.
   it("should return undefined for both checks when only one file is configured", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report.xlsx");

      expect(comp.findDuplicate).toBeUndefined();
      expect(comp.findDuplicateFormat).toBeUndefined();
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — directoryPathValidator / relativePathValidator
// ---------------------------------------------------------------------------

describe("ServerSaveComponent — path validators: rejection rules", () => {

   // 🔁 Regression-sensitive: a path ending with "/" must be rejected as a directory reference —
   // saving to a directory (not a file) is not a valid destination.
   it("should return a directoryPath error when path ends with '/'", async () => {
      const { comp } = await renderComponent();

      const errors = comp.directoryPathValidator({ value: "/output/folder/" } as any);

      expect(errors).toEqual({ directoryPath: true });
   });

   // Happy: a normal file path must pass both validators without errors.
   it("should return null for a valid relative file path", async () => {
      const { comp } = await renderComponent();

      expect(comp.directoryPathValidator({ value: "output/report.xlsx" } as any)).toBeNull();
      expect(comp.relativePathValidator({ value: "output/report.xlsx" } as any)).toBeNull();
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — removeFile: entity removal
// ---------------------------------------------------------------------------

describe("ServerSaveComponent — removeFile: removes by reference and fires changed", () => {

   // 🔁 Regression-sensitive: removeFile uses `findIndex(f => f === file)` (reference equality).
   // Passing the same object reference from files[] must remove it; the files array must shrink.
   it("should remove the matching file and emit serverSaveChanged", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report.xlsx");
      const file = comp.files[0]; // same reference

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      comp.removeFile(file);

      expect(comp.files).toHaveLength(0);
      expect(emitted).toHaveLength(1);
   });

   // Boundary: removing a file that is not in the list (different reference) must be a no-op.
   it("should not emit serverSaveChanged when the file reference is not found", async () => {
      const { comp } = await renderComponent({ enabled: true });

      addFileEntry(comp, "0", "/out/report.xlsx");

      const emitted: ServerSave[] = [];
      comp.serverSaveChanged.subscribe(e => emitted.push(e));

      // Different object — not the same reference as files[0]
      comp.removeFile({ format: "0", path: "/out/report.xlsx" });

      expect(comp.files).toHaveLength(1);
      expect(emitted).toHaveLength(0);
   });

});
