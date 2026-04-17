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
 * TaskActionPaneComponent — Testing Library style
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — set action(null): default model uses "RepletAction" type which is absent from the dropdown (it.failing — confirmed bug)
 *   Group 2 [Risk 3] — set action(value): nested bookmarks are defensively cloned
 *   Group 3 [Risk 2] — changeActionType: per-type model creation contracts
 *   Group 4 [Risk 2] — onModelChanged / fireModelChanged: valid state propagation
 *
 * Confirmed bugs (it.failing — remove wrapper once fixed):
 *
 *   Bug A — null action defaults to "RepletAction" type not offered by the dropdown (Group 1):
 *     `set action(null)` sets `selectedActionType = "RepletAction"` and creates a
 *     GeneralActionModel with `actionType: "RepletAction"`.
 *     The template mat-select only offers "ViewsheetAction", "BackupAction", "BatchAction".
 *     Result: the dropdown shows nothing selected; the scheduler form starts with an invisible
 *     action type that cannot be reached through the UI.
 *     Issue #74498 
 *
 * KEY contracts:
 *   - "ViewsheetAction" → GeneralActionModel  (actionClass: "GeneralActionModel")
 *   - "BackupAction"    → BackupActionModel   (backupPathsEnabled: true, backupPath: null, assets: [])
 *   - "BatchAction"     → BatchActionModel    (taskName: null)
 *   - changeActionType() always fires modelChanged with valid=false.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";

import { it } from "@jest/globals"; // must be import, or it.failing didn't work
import { TaskActionPaneComponent } from "./task-action-pane.component";
import { GeneralActionModel } from "../../../../../../shared/schedule/model/general-action-model";
import { BackupActionModel } from "../../../../../../shared/schedule/model/backup-action-model";
import { BatchActionModel } from "../../../../../../shared/schedule/model/batch-action-model";
import { VSBookmarkInfoModel } from "../../../../../../portal/src/app/vsobjects/model/vs-bookmark-info-model";

// ---------------------------------------------------------------------------
// Fixtures
// ---------------------------------------------------------------------------

function makeGeneralAction(overrides: Partial<GeneralActionModel> = {}): GeneralActionModel {
   return {
      actionType: "ViewsheetAction",
      actionClass: "GeneralActionModel",
      label: "My Action",
      notificationEnabled: false,
      deliverEmailsEnabled: false,
      printOnServerEnabled: false,
      saveToServerEnabled: false,
      ccAddress: "",
      bccAddress: "",
      ...overrides,
   };
}

// ---------------------------------------------------------------------------
// Render helper
// ---------------------------------------------------------------------------

async function renderComponent(action = makeGeneralAction()) {
   const result = await render(TaskActionPaneComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: { action },
   });
   return { ...result, comp: result.fixture.componentInstance };
}

// ---------------------------------------------------------------------------
// Group 1 [Risk 3] — set action(null): defaults to RepletAction (confirmed bug)
// ---------------------------------------------------------------------------

describe("TaskActionPaneComponent — set action(null): default type", () => {

   // The dropdown only offers ViewsheetAction / BackupAction / BatchAction.
   // Defaulting to "RepletAction" leaves the select showing nothing, making the form appear broken.
   // Issue #74498(fixed)
   it("should default selectedActionType to a type offered in the dropdown when action is null", async () => {
      const { comp, fixture } = await renderComponent();

      // Assign null via the public setter
      comp.action = null;
      fixture.detectChanges();

      // "RepletAction" is NOT one of the three dropdown options; the default should be
      // "ViewsheetAction" (the first real option) or any option shown in the template.
      const DROPDOWN_OPTIONS = ["ViewsheetAction", "BackupAction", "BatchAction"];
      expect(DROPDOWN_OPTIONS).toContain(comp.selectedActionType);
   });

   // Happy: existing action with a known type must preserve that type
   it("should set selectedActionType from the provided action's actionType", async () => {
      const { comp, fixture } = await renderComponent();

      comp.action = makeGeneralAction({ actionType: "ViewsheetAction" });
      fixture.detectChanges();

      expect(comp.selectedActionType).toBe("ViewsheetAction");
   });

   // 🔁 Regression-sensitive: The default model created for null must be a GeneralActionModel,
   // not left as null, so child editors receive a defined model object.
   it("should create a non-null action model when action input is null", async () => {
      const { comp, fixture } = await renderComponent();

      comp.action = null;
      fixture.detectChanges();

      expect(comp.action).not.toBeNull();
      expect(comp.action.label).toBeDefined();
   });

});

// ---------------------------------------------------------------------------
// Group 2 [Risk 3] — set action(value): clone contract
// ---------------------------------------------------------------------------

describe("TaskActionPaneComponent — set action(value): clone behavior", () => {

   // 🔁 Regression-sensitive: Object.assign({}) creates a NEW top-level object —
   // mutating comp.action must not affect the original reference.
   it("should store a different object reference than the input (not the same object)", async () => {
      const original = makeGeneralAction();
      const { comp } = await renderComponent(original);

      expect(comp.action).not.toBe(original);
   });

   // Regression-sensitive: nested bookmarks must be copied so child edits cannot mutate parent input.
   it("should not share nested bookmarks array reference with the input", async () => {
      const bookmarks: VSBookmarkInfoModel[] = [{ name: "(Home)", label: "(Home)" }];
      const original = makeGeneralAction({ bookmarks });
      const { comp } = await renderComponent(original);

      const copiedBookmarks = (comp.action as GeneralActionModel).bookmarks;
      expect(copiedBookmarks).not.toBe(original.bookmarks);
      expect(copiedBookmarks[0]).not.toBe(original.bookmarks[0]);
   });

   // Error path: scalar properties must be independently copied
   it("should copy scalar properties so mutating the stored action does not affect the original", async () => {
      const original = makeGeneralAction({ label: "Original" });
      const { comp } = await renderComponent(original);

      (comp.action as GeneralActionModel).label = "Modified";

      expect(original.label).toBe("Original");
   });

});

// ---------------------------------------------------------------------------
// Group 3 [Risk 2] — changeActionType: per-type model creation
// ---------------------------------------------------------------------------

describe("TaskActionPaneComponent — changeActionType: model creation contracts", () => {

   // 🔁 Regression-sensitive: BackupAction must start with backupPathsEnabled=true and empty assets.
   // A future refactor that forgets these defaults will break the backup form on first open.
   it("should create a BackupActionModel with backupPathsEnabled=true and empty assets for BackupAction", async () => {
      const { comp } = await renderComponent();

      comp.selectedActionType = "BackupAction";
      comp.changeActionType();

      const model = comp.action as BackupActionModel;
      expect(model.actionType).toBe("BackupAction");
      expect(model.backupPathsEnabled).toBe(true);
      expect(model.backupPath).toBeNull();
      expect(model.assets).toEqual([]);
   });

   // 🔁 Regression-sensitive: BatchAction must start with taskName=null (unselected).
   // Other BatchActionModel fields (queryEntry, queryParameters, embeddedParameters) are NOT
   // initialized — callers must handle undefined for those fields.
   it("should create a BatchActionModel with taskName=null for BatchAction", async () => {
      const { comp } = await renderComponent();

      comp.selectedActionType = "BatchAction";
      comp.changeActionType();

      const model = comp.action as BatchActionModel;
      expect(model.actionType).toBe("BatchAction");
      expect(model.taskName).toBeNull();
   });

   // Happy: ViewsheetAction (and other types) fall into GeneralActionModel branch
   it("should create a GeneralActionModel for ViewsheetAction type", async () => {
      const { comp } = await renderComponent();

      comp.selectedActionType = "ViewsheetAction";
      comp.changeActionType();

      expect(comp.action.actionType).toBe("ViewsheetAction");
      expect(comp.action.actionClass).toBe("GeneralActionModel");
   });

});

// ---------------------------------------------------------------------------
// Group 4 [Risk 2] — onModelChanged / fireModelChanged: valid-state propagation
// ---------------------------------------------------------------------------

describe("TaskActionPaneComponent — onModelChanged: valid state propagation", () => {

   // 🔁 Regression-sensitive: valid=true from the child must reach the parent listener unchanged.
   it("should emit modelChanged with valid=true when child reports valid state", async () => {
      const { comp } = await renderComponent();

      const emitted: { valid: boolean }[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.onModelChanged({ valid: true, model: makeGeneralAction() });

      expect(emitted).toHaveLength(1);
      expect(emitted[0].valid).toBe(true);
   });

   // 🔁 Regression-sensitive: changeActionType must always emit valid=false immediately,
   // forcing the parent to treat a mid-edit type switch as an invalid state until reconfigured.
   it("should emit modelChanged with valid=false after changeActionType is called", async () => {
      const { comp } = await renderComponent();

      const emitted: { valid: boolean }[] = [];
      comp.modelChanged.subscribe(e => emitted.push(e));

      comp.selectedActionType = "BackupAction";
      comp.changeActionType();

      expect(emitted).toHaveLength(1);
      expect(emitted[0].valid).toBe(false);
   });

});
