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
 * TaskActionPane (portal) — Pass 1: Interaction tests
 * Testing Library style.
 *
 * Covers the main public API without triggering HTTP:
 *   Group 1  — action getter: index resolution, -1 sentinel, out-of-bounds null
 *   Group 2  — actionNames getter: label mapping, null-safe empty
 *   Group 3  — isGeneralAction getter
 *   Group 4  — selectSheetError getter: required validator state
 *   Group 5  — listView initialization from model.actions.length
 *   Group 6  — addAction(): push + state reset
 *   Group 7  — copyAction(): no-op guards, deep-clone, label prefix
 *   Group 8  — deleteAction(): confirm dialog, ok removes, cancel is no-op
 *   Group 9  — editAction(): no-op guard, actionIndex + listView update
 *   Group 10 — changeView(): listView toggle
 *   Group 11 — isTabSelected(): match and non-match
 *   Group 12 — getSheetPath(): dashboardMap lookup
 *   Group 13 — isValid(): form + parentForm both required
 *
 * Mocking strategy:
 *   All tests use the renderTaskActionPane() helper from test-helpers.ts, which
 *   supplies a model carrying a single ViewsheetAction with NO sheet value. This
 *   keeps every ngOnInit HTTP call (getBookmarks / getHighlights / getParameters /
 *   getPrintLayout / getTableDataAssemblies) on the early-return path, so no MSW
 *   handler is needed. NgbModal is mocked (MODAL_MOCK) to control deleteAction()
 *   confirm dialogs. HttpClient is provided for DI only.
 */

import { UntypedFormControl, Validators } from "@angular/forms";
import { waitFor } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Subject } from "rxjs";

import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import {
   lastRenderedFixture,
   makeAction,
   makeModel,
   MODAL_MOCK,
   renderTaskActionPane,
   resetMocks,
} from "./task-action-pane.test-helpers";

// ---------------------------------------------------------------------------
// Helpers used only in deleteAction tests
// ---------------------------------------------------------------------------

function mockConfirmOk(): void {
   MODAL_MOCK.open.mockImplementationOnce(() => ({
      result: Promise.resolve("ok"),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

function mockConfirmCancel(): void {
   MODAL_MOCK.open.mockImplementationOnce(() => ({
      result: Promise.resolve("cancel"),
      componentInstance: { onCommit: new Subject<string>() },
      close: vi.fn(),
      dismiss: vi.fn(),
   }));
}

// ---------------------------------------------------------------------------
// Specs
// ---------------------------------------------------------------------------

describe("TaskActionPane (portal) — interaction tests", () => {
   beforeEach(() => {
      resetMocks();
      MessageDialog.lastMessage = null;
      MessageDialog.lastMessageTS = 0;
   });
   afterEach(() => vi.restoreAllMocks());
   afterEach(() => { lastRenderedFixture?.destroy(); });

   // -------------------------------------------------------------------------
   // Group 1 — action getter
   // -------------------------------------------------------------------------

   describe("action getter", () => {
      it("returns model.actions[actionIndex] when actionIndex is 0", async () => {
         const { comp } = await renderTaskActionPane();
         expect(comp.actionIndex).toBe(0);
         expect(comp.action).toBe(comp.model.actions[0]);
      });

      it("returns the last action when actionIndex is -1", async () => {
         const model = makeModel({ actions: [makeAction({ label: "First" }), makeAction({ label: "Last" })] });
         const { comp } = await renderTaskActionPane(model);
         // Bypass setter side-effects — set actionIndex directly to avoid changeActionType() chain.
         comp.actionIndex = -1;
         expect(comp.action).toBe(comp.model.actions[1]);
      });

      it("returns null when actionIndex exceeds model.actions.length", async () => {
         const { comp } = await renderTaskActionPane();
         // Bypass setter — model has 1 action; index 5 is out-of-bounds.
         comp.actionIndex = 5;
         expect(comp.action).toBeNull();
      });
   });

   // -------------------------------------------------------------------------
   // Group 2 — actionNames getter
   // -------------------------------------------------------------------------

   describe("actionNames getter", () => {
      it("returns labels from all actions in order", async () => {
         const model = makeModel({ actions: [makeAction({ label: "First" }), makeAction({ label: "Second" })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.actionNames).toEqual(["First", "Second"]);
      });

      it("returns empty array when model.actions is null", async () => {
         const { comp } = await renderTaskActionPane();
         // Bypass model setter to avoid updateValues() side-effects — null out actions directly.
         (comp as any)._model.actions = null;
         expect(comp.actionNames).toEqual([]);
      });
   });

   // -------------------------------------------------------------------------
   // Group 3 — isGeneralAction getter
   // -------------------------------------------------------------------------

   describe("isGeneralAction getter", () => {
      it("returns true when the current action is a ViewsheetAction", async () => {
         const { comp } = await renderTaskActionPane();
         expect(comp.action.actionType).toBe("ViewsheetAction");
         expect(comp.isGeneralAction).toBe(true);
      });
   });

   // -------------------------------------------------------------------------
   // Group 4 — selectSheetError getter
   // -------------------------------------------------------------------------

   describe("selectSheetError getter", () => {
      it("returns true when the dashboard control has no value (Validators.required fails)", async () => {
         const { comp } = await renderTaskActionPane();
         // Default action has no sheet → dashboard control is null → required validator fails.
         expect(comp.selectSheetError).toBe(true);
      });

      it("returns false after the dashboard control receives a non-empty value", async () => {
         const { comp } = await renderTaskActionPane();
         comp.form.controls["dashboard"].setValue("1^128^__NULL__^table1");
         expect(comp.selectSheetError).toBe(false);
      });
   });

   // -------------------------------------------------------------------------
   // Group 5 — listView initialization
   // -------------------------------------------------------------------------

   describe("listView initialization", () => {
      it("is false when model has a single action", async () => {
         const { comp } = await renderTaskActionPane(makeModel({ actions: [makeAction()] }));
         expect(comp.listView).toBe(false);
      });

      it("is true when model has more than one action", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.listView).toBe(true);
      });
   });

   // -------------------------------------------------------------------------
   // Group 6 — addAction()
   // -------------------------------------------------------------------------

   describe("addAction()", () => {
      it("appends a new ViewsheetAction to model.actions", async () => {
         const { comp } = await renderTaskActionPane();
         const before = comp.model.actions.length;
         comp.addAction();
         expect(comp.model.actions).toHaveLength(before + 1);
         expect(comp.model.actions[comp.model.actions.length - 1].actionType).toBe("ViewsheetAction");
      });

      it("sets listView to false and actionIndex to -1 after adding", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.listView).toBe(true);
         comp.addAction();
         expect(comp.listView).toBe(false);
         expect(comp.actionIndex).toBe(-1);
      });
   });

   // -------------------------------------------------------------------------
   // Group 7 — copyAction()
   // -------------------------------------------------------------------------

   describe("copyAction()", () => {
      it("is a no-op when no action is selected", async () => {
         const { comp } = await renderTaskActionPane();
         comp.selectedActions = [];
         const before = comp.model.actions.length;
         comp.copyAction();
         expect(comp.model.actions).toHaveLength(before);
      });

      it("is a no-op when more than one action is selected", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         comp.selectedActions = [0, 1];
         comp.copyAction();
         expect(comp.model.actions).toHaveLength(2);
      });

      it("pushes a copy with a 'Copy of' prefix on the label", async () => {
         const { comp } = await renderTaskActionPane(makeModel({ actions: [makeAction({ label: "My Action" })] }));
         comp.selectedActions = [0];
         comp.copyAction();
         expect(comp.model.actions).toHaveLength(2);
         expect(comp.model.actions[1].label).toBe("_#(js:Copy of) My Action");
      });

      it("updates selectedActions to the index of the newly added copy", async () => {
         const { comp } = await renderTaskActionPane(makeModel({ actions: [makeAction({ label: "My Action" })] }));
         comp.selectedActions = [0];
         comp.copyAction();
         expect(comp.selectedActions).toEqual([1]);
      });

      it("strips repeated 'Copy of' prefix so copying a copy keeps a single prefix", async () => {
         const { comp } = await renderTaskActionPane(
            makeModel({ actions: [makeAction({ label: "_#(js:Copy of) My Action" })] })
         );
         comp.selectedActions = [0];
         comp.copyAction();
         expect(comp.model.actions[1].label).toBe("_#(js:Copy of) My Action");
      });

      it("deep-clones the action so mutating the copy does not affect the original", async () => {
         const { comp } = await renderTaskActionPane();
         comp.model.actions = [makeAction({ label: "Orig", ccAddress: "a@b.com" })];
         comp.selectedActions = [0];
         comp.copyAction();
         (comp.model.actions[1] as GeneralActionModel).ccAddress = "changed@b.com";
         expect((comp.model.actions[0] as GeneralActionModel).ccAddress).toBe("a@b.com");
      });
   });

   // -------------------------------------------------------------------------
   // Group 8 — deleteAction()
   // -------------------------------------------------------------------------

   describe("deleteAction()", () => {
      it("opens a confirm dialog via ComponentTool when called", async () => {
         const { comp } = await renderTaskActionPane();
         comp.selectedActions = [0];
         comp.deleteAction();
         await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object)));
      });

      it("removes the selected action from model.actions when the user confirms", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         comp.selectedActions = [0];
         mockConfirmOk();
         comp.deleteAction();
         await waitFor(() => expect(comp.model.actions).toHaveLength(1));
         expect(comp.model.actions[0].label).toBe("B");
      });

      it("does not modify model.actions when the user cancels the confirm dialog", async () => {
         const { comp } = await renderTaskActionPane();
         comp.selectedActions = [0];
         mockConfirmCancel();
         comp.deleteAction();
         // Gate: wait for the dialog to have opened, proving the async path settled.
         await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalledWith(MessageDialog, expect.any(Object)));
         expect(comp.model.actions).toHaveLength(1);
      });
   });

   // -------------------------------------------------------------------------
   // Group 9 — editAction()
   // -------------------------------------------------------------------------

   describe("editAction()", () => {
      it("is a no-op when selectedActions is empty", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         comp.listView = true;
         comp.selectedActions = [];
         comp.editAction();
         expect(comp.listView).toBe(true);
         expect(comp.actionIndex).toBe(0);
      });

      it("sets listView to false and actionIndex to selectedActions[0]", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         comp.listView = true;
         comp.selectedActions = [1];
         comp.editAction();
         expect(comp.listView).toBe(false);
         expect(comp.actionIndex).toBe(1);
      });
   });

   // -------------------------------------------------------------------------
   // Group 10 — changeView()
   // -------------------------------------------------------------------------

   describe("changeView()", () => {
      it("sets listView to true when called with true", async () => {
         const { comp } = await renderTaskActionPane();
         expect(comp.listView).toBe(false);
         comp.changeView(true);
         expect(comp.listView).toBe(true);
      });

      it("sets listView to false when called with false", async () => {
         const model = makeModel({ actions: [makeAction({ label: "A" }), makeAction({ label: "B" })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.listView).toBe(true);
         comp.changeView(false);
         expect(comp.listView).toBe(false);
      });
   });

   // -------------------------------------------------------------------------
   // Group 11 — isTabSelected()
   // -------------------------------------------------------------------------

   describe("isTabSelected()", () => {
      it("returns true for the index whose option type matches the current action type", async () => {
         const { comp } = await renderTaskActionPane();
         // options[0] = "ViewsheetAction"; action.actionType = "ViewsheetAction"
         expect(comp.isTabSelected(0)).toBe(true);
      });

      it("returns false for an index whose option type does not match", async () => {
         const { comp } = await renderTaskActionPane();
         // options[1] is undefined in the portal version (only one option exists)
         expect(comp.isTabSelected(1)).toBe(false);
      });
   });

   // -------------------------------------------------------------------------
   // Group 12 — getSheetPath()
   // -------------------------------------------------------------------------

   describe("getSheetPath()", () => {
      it("returns the display path from model.dashboardMap by sheet identifier", async () => {
         const model = makeModel({
            dashboardMap: { "1^128^__NULL__^table1": "My Reports/table1" },
         });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.getSheetPath("1^128^__NULL__^table1")).toBe("My Reports/table1");
      });

      it("returns undefined when the sheet identifier is not in the map", async () => {
         const { comp } = await renderTaskActionPane();
         expect(comp.getSheetPath("nonexistent-id")).toBeUndefined();
      });
   });

   // -------------------------------------------------------------------------
   // Group 13 — isValid()
   // -------------------------------------------------------------------------

   describe("isValid()", () => {
      it("returns false when the dashboard form control is invalid (no sheet selected)", async () => {
         const { comp } = await renderTaskActionPane();
         // Default action has no sheet → required validator fails → form invalid.
         expect(comp.isValid()).toBe(false);
      });

      it("returns true when both the local form and parentForm are valid", async () => {
         const { comp } = await renderTaskActionPane();
         comp.form.controls["dashboard"].setValue("1^128^__NULL__^table1");
         expect(comp.isValid()).toBe(true);
      });

      it("returns false when parentForm is invalid even if the local form is valid", async () => {
         const { comp, parentForm } = await renderTaskActionPane();
         comp.form.controls["dashboard"].setValue("1^128^__NULL__^table1");
         // Add a required control without a value to make parentForm invalid.
         parentForm.addControl("required", new UntypedFormControl(null, Validators.required));
         expect(comp.isValid()).toBe(false);
      });
   });
});
