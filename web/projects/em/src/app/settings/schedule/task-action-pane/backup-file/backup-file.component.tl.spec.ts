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
 * BackupFileComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — fireBackupChanged: valid output contract (enabled flag, path, selectedEntities)
 *   Group 2 [Risk 3] — set path / set serverPath: path input silently ignored when _serverPath is set
 *   Group 3 [Risk 2] — toggleFTP: credential form controls enabled/disabled based on ftp flag
 *   Group 4 [Risk 2] — remove / removeAll: entity removal fires backupPathsChanged
 *   Group 5 [Risk 2] — isSameType: bitmask comparison for DATA_SOURCE type
 *
 * KEY contracts:
 *   - `valid` output = `!enabled || (backupForm.valid && selectedEntities.length > 0)`
 *   - `set path` is a no-op once `_serverPath` has been assigned a non-null value.
 *   - FTP credential fields (useCredential, secretId, username, password) start disabled;
 *     toggleFTP enables them when ftp=true and disables when ftp=false.
 *   - `remove()` uses `Array.indexOf` (reference equality) — only removes entities that
 *     are the same object reference as those in `entitySelection`.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { FormsModule } from "@angular/forms";
import { ReactiveFormsModule } from "@angular/forms";
import { MatCheckboxModule } from "@angular/material/checkbox";
import { NoopAnimationsModule } from "@angular/platform-browser/animations";
import { render } from "@testing-library/angular";
import { EMPTY, of, Subject } from "rxjs";

import { BackupFileComponent } from "./backup-file.component";
import { ContentRepositoryService } from "../../../content/repository/content-repository-page/content-repository.service";
import { ExportAssetsService } from "../../../content/repository/import-export/export-assets.service";
import { RepositoryTreeDataSource } from "../../../content/repository/repository-tree-data-source";
import { SelectedAssetModel } from "../../../content/repository/import-export/selected-asset-model";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { ServerPathInfoModel } from "../../../../../../../portal/src/app/vsobjects/model/server-path-info-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeAsset(path: string, type = RepositoryEntryType.VIEWSHEET): SelectedAssetModel {
   return { path, type, label: path, typeName: "", typeLabel: "", user: null, description: "", icon: "" };
}

function makeServerPath(overrides: Partial<ServerPathInfoModel> = {}): ServerPathInfoModel {
   return {
      path: "/backup/output",
      ftp: false,
      useCredential: false,
      secretId: "",
      username: "",
      password: "",
      oldFormat: 0,
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(props: Partial<BackupFileComponent> = {}) {
   const changesMock = new Subject<void>();
   const selectedNodesMock = new Subject<any[]>();

   const serviceMock = {
      changes: changesMock.asObservable(),
      selectedNodeChanges: selectedNodesMock.asObservable(),
      get selectedNodes() { return []; },
      set selectedNodes(_: any) {},
      clearSelectedNodes: jest.fn(),
      selectNode: jest.fn(),
      hideTrash: jest.fn((data: any[]) => data),
   };

   const dataSourceMock = {
      dataSubject: EMPTY,
      treeControl: { expansionModel: { selected: [] } },
      loading: of(false),
      data: [],
      refresh: jest.fn(() => of([])),
   };

   const result = await render(BackupFileComponent, {
      imports: [FormsModule, ReactiveFormsModule, MatCheckboxModule, NoopAnimationsModule],
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: ContentRepositoryService, useValue: serviceMock },
         { provide: ExportAssetsService, useValue: { getUsers: jest.fn(() => []), loadUserNode: jest.fn(() => of({ nodes: [] })) } },
         { provide: "MatSnackBar", useValue: { open: jest.fn() } },
      ],
      componentProviders: [
         { provide: RepositoryTreeDataSource, useValue: dataSourceMock },
      ],
      componentProperties: props,
   });

   await result.fixture.whenStable();

   return {
      ...result,
      comp: result.fixture.componentInstance,
      serviceMock,
   };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — fireBackupChanged: valid output contract
// ---------------------------------------------------------------------------

describe("BackupFileComponent — fireBackupChanged: valid output contract", () => {

   // 🔁 Regression-sensitive: when backup is disabled (enabled=false), the parent must receive
   // valid=true — otherwise the scheduler form is blocked even though backup is turned off.
   it("should emit valid=true when enabled is false, regardless of form state", async () => {
      const { comp } = await renderComponent({ enabled: false, selectedEntities: [] });

      const emitted: { valid: boolean }[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.fireBackupChanged();

      expect(emitted).toHaveLength(1);
      expect(emitted[0].valid).toBe(true);
   });

   // 🔁 Regression-sensitive: path is `Validators.required` — empty path must produce valid=false.
   it("should emit valid=false when enabled=true and path is empty", async () => {
      const { comp } = await renderComponent({ enabled: true, selectedEntities: [makeAsset("a/report")] });

      // path starts as "" (required validator → invalid)
      const emitted: { valid: boolean }[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.fireBackupChanged();

      expect(emitted[0].valid).toBe(false);
      expect(comp.backupForm.get("path").invalid).toBe(true);   // path is the cause
      expect(comp.selectedEntities.length).toBeGreaterThan(0);  // not caused by empty entities
   });

   // 🔁 Regression-sensitive: even with a valid path, empty selectedEntities must still produce
   // valid=false — the backup needs at least one asset to be meaningful.
   it("should emit valid=false when enabled=true, path is set, but selectedEntities is empty", async () => {
      const { comp } = await renderComponent({ enabled: true, selectedEntities: [] });

      comp.backupForm.get("path").setValue("/some/path");
      const emitted: { valid: boolean }[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.fireBackupChanged();

      expect(emitted[0].valid).toBe(false);
      expect(comp.backupForm.valid).toBe(true);         // form is valid — path is set
      expect(comp.selectedEntities).toHaveLength(0);   // entities are the cause
   });

   // Happy path: all conditions met → valid=true.
   it("should emit valid=true when enabled=true, path is non-empty, and selectedEntities is non-empty", async () => {
      const { comp } = await renderComponent({ enabled: true, selectedEntities: [makeAsset("a/report")] });

      comp.backupForm.get("path").setValue("/backup/path");
      const emitted: { valid: boolean }[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.fireBackupChanged();

      expect(emitted[0].valid).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — set path / set serverPath: silent path override
// ---------------------------------------------------------------------------

describe("BackupFileComponent — set path / set serverPath: path input blocked by serverPath", () => {

   // 🔁 Regression-sensitive: before serverPath is assigned, the path input must update the form.
   it("should update the path form field when path input is set and _serverPath is null", async () => {
      const { comp } = await renderComponent();

      comp.path = "/new/path";

      expect(comp.backupForm.get("path").value).toBe("/new/path");
   });

   // 🔁 Regression-sensitive: once serverPath is assigned, subsequent path input changes must NOT
   // overwrite the form — serverPath is the authoritative source.
   // Risk Point: parent component may still push path updates after serverPath is set,
   // silently replacing user-visible path with a stale value.
   it("should ignore path input once _serverPath has been set", async () => {
      const { comp } = await renderComponent();

      comp.serverPath = makeServerPath({ path: "/ftp/destination" });
      comp.path = "/local/override";

      expect(comp.backupForm.get("path").value).toBe("/ftp/destination");
   });

   // Happy: serverPath populates all form fields correctly.
   it("should populate all form fields from serverPath when serverPath is set", async () => {
      const { comp } = await renderComponent();

      comp.serverPath = makeServerPath({
         path: "/ftp/dest",
         ftp: true,
         username: "ftpuser",
         password: "secret",
      });

      expect(comp.backupForm.get("path").value).toBe("/ftp/dest");
      expect(comp.backupForm.get("ftp").value).toBe(true);
      expect(comp.backupForm.get("username").value).toBe("ftpuser");
      expect(comp.backupForm.get("password").value).toBe("secret");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — toggleFTP: credential form controls
// ---------------------------------------------------------------------------

describe("BackupFileComponent — toggleFTP: credential field enable/disable", () => {

   // 🔁 Regression-sensitive: setting ftp=true must enable username/password so the user can type.
   // If these controls stay disabled, form values are not included in the submitted model.
   it("should enable username, password, useCredential and secretId when ftp is true", async () => {
      const { comp } = await renderComponent();

      comp.backupForm.get("ftp").setValue(true, { emitEvent: false });
      comp.toggleFTP();

      expect(comp.backupForm.get("username").enabled).toBe(true);
      expect(comp.backupForm.get("password").enabled).toBe(true);
      expect(comp.backupForm.get("useCredential").enabled).toBe(true);
      expect(comp.backupForm.get("secretId").enabled).toBe(true);
   });

   // 🔁 Regression-sensitive: re-disabling credentials after ftp is unchecked prevents
   // stale credentials from reaching the server when FTP is not in use.
   it("should disable credential controls when ftp is false", async () => {
      const { comp } = await renderComponent();

      // Enable first, then disable
      comp.backupForm.get("ftp").setValue(true, { emitEvent: false });
      comp.toggleFTP();
      comp.backupForm.get("ftp").setValue(false, { emitEvent: false });
      comp.toggleFTP();

      expect(comp.backupForm.get("username").disabled).toBe(true);
      expect(comp.backupForm.get("password").disabled).toBe(true);
      expect(comp.backupForm.get("useCredential").disabled).toBe(true);
      expect(comp.backupForm.get("secretId").disabled).toBe(true);
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — remove / removeAll: entity management
// ---------------------------------------------------------------------------

describe("BackupFileComponent — remove / removeAll: entity management", () => {

   // 🔁 Regression-sensitive: remove() uses Array.indexOf (reference equality) to find entities.
   // Only entities that are the SAME object reference as those in entitySelection are removed.
   it("should remove the selected entity and emit backupPathsChanged", async () => {
      const asset = makeAsset("reports/Dashboard");
      const { comp } = await renderComponent({ selectedEntities: [asset], enabled: true });

      comp.backupForm.get("path").setValue("/backup");
      comp.entitySelection = [asset]; // same reference

      const emitted: { assets: SelectedAssetModel[] }[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.remove();

      expect(comp.selectedEntities).toHaveLength(0);
      expect(emitted).toHaveLength(1);
      expect(emitted[0].assets).toHaveLength(0);
   });

   // 🔁 Regression-sensitive: removeAll() must clear BOTH entitySelection and selectedEntities,
   // then fire backupPathsChanged — ensuring valid=false propagates to the parent.
   it("should clear all entities and reset entitySelection on removeAll", async () => {
      const assets = [makeAsset("a"), makeAsset("b")];
      const { comp } = await renderComponent({ selectedEntities: assets, enabled: true });

      comp.entitySelection = [assets[0]];

      const emitted: any[] = [];
      comp.backupPathsChanged.subscribe(e => emitted.push(e));

      comp.removeAll();

      expect(comp.selectedEntities).toHaveLength(0);
      expect(comp.entitySelection).toHaveLength(0);
      expect(emitted).toHaveLength(1);
   });

});

// ---------------------------------------------------------------------------
// Group 5 [Risk 2] — isSameType: bitmask vs exact-match logic
// ---------------------------------------------------------------------------

describe("BackupFileComponent — isSameType: DATA_SOURCE bitmask contract", () => {

   // 🔁 Regression-sensitive: two DATA_SOURCE entities with different sub-bits must still match —
   // the deduplication check uses bitmask presence, not exact type equality.
   it("should return true when both types have the DATA_SOURCE bit set, even with differing sub-bits", async () => {
      const { comp } = await renderComponent();

      // DATA_SOURCE folder (DATA_SOURCE | FOLDER) vs plain DATA_SOURCE
      const dsFolder = RepositoryEntryType.DATA_SOURCE | RepositoryEntryType.FOLDER;
      expect(comp.isSameType(RepositoryEntryType.DATA_SOURCE, dsFolder)).toBe(true);
   });

   // Happy: non-DATA_SOURCE types use exact equality — same type matches, different does not.
   it("should return false when non-DATA_SOURCE types differ", async () => {
      const { comp } = await renderComponent();

      expect(comp.isSameType(RepositoryEntryType.VIEWSHEET, RepositoryEntryType.WORKSHEET)).toBe(false);
   });

});
