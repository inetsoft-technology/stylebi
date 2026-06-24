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
 * TaskActionPane (portal) — Pass 2: Risk tests
 * Testing Library style.
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — ngOnInit HTTP population: bookmarks/highlights/hasPrintLayout
 *   Group 2 [Risk 3] — getBookmarks() filter + default logic
 *   Group 3 [Risk 2] — updateValues() with empty model.actions → addAction(true)
 *   Group 4 [Risk 2] — deleteAction() multi-select: reverse-index ordering
 *   Group 5 [Risk 2] — action setter index padding beyond model.actions.length
 *
 * KEY contracts:
 *   - ngOnInit fires up to 5 HTTP calls when action.sheet is set;
 *     all 5 handlers must be registered before render.
 *   - getBookmarks() filters existing generalActionModel.bookmarks to the
 *     server response, defaulting to [(Home)] when the result is empty.
 *   - deleteAction() sorts selected indices and splices in reverse to avoid
 *     shifting errors when removing multiple entries.
 *
 * Mocking strategy:
 *   MSW handlers are added per describe group in beforeEach via server.use().
 *   Groups 3–5 require no HTTP and use the default no-sheet model from
 *   renderTaskActionPane(). NgbModal is mocked (MODAL_MOCK) for deleteAction.
 */

import { http, HttpResponse } from "msw";
import { waitFor } from "@testing-library/angular";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { Subject } from "rxjs";

import { server } from "@test-mocks/server";
import { MessageDialog } from "../../../../widget/dialog/message-dialog/message-dialog.component";
import { GeneralActionModel } from "../../../../../../../shared/schedule/model/general-action-model";
import { VSBookmarkInfoModel } from "../../../../vsobjects/model/vs-bookmark-info-model";
import {
   lastRenderedFixture,
   makeAction,
   makeModel,
   MODAL_MOCK,
   renderTaskActionPane,
   resetMocks,
} from "./task-action-pane.test-helpers";

// ---------------------------------------------------------------------------
// Shared MSW fixture helpers
// ---------------------------------------------------------------------------

const HOME_BOOKMARK: VSBookmarkInfoModel = {
   name: "(Home)",
   label: "(Home)",
   owner: { name: "admin", orgID: "host_org" },
};

const SHEET_ID = "1^128^__NULL__^table1";

/**
 * Registers all 5 MSW handlers that ngOnInit fires when action.sheet is set.
 * Must be called before renderTaskActionPane() with a sheet-bearing action.
 */
function setupAllActionHandlers(overrides: {
   bookmarks?: VSBookmarkInfoModel[];
   highlights?: any[];
   hasPrintLayout?: boolean;
   parameters?: string[];
   tableAssemblies?: string[];
} = {}): void {
   server.use(
      http.get("*/api/portal/schedule/task/action/bookmarks", () =>
         HttpResponse.json(overrides.bookmarks ?? [HOME_BOOKMARK])
      ),
      http.get("*/api/portal/schedule/task/action/viewsheet/highlights", () =>
         HttpResponse.json(overrides.highlights ?? [])
      ),
      http.get("*/api/portal/schedule/task/action/hasPrintLayout", () =>
         HttpResponse.json(overrides.hasPrintLayout ?? false)
      ),
      http.get("*/api/portal/schedule/task/action/viewsheet/parameters", () =>
         HttpResponse.json(overrides.parameters ?? [])
      ),
      http.get("*/api/portal/schedule/task/action/viewsheet/tableDataAssemblies", () =>
         HttpResponse.json(overrides.tableAssemblies ?? [])
      ),
   );
}

// ---------------------------------------------------------------------------
// Specs
// ---------------------------------------------------------------------------

describe("TaskActionPane (portal) — risk tests", () => {
   beforeEach(() => {
      resetMocks();
      MessageDialog.lastMessage = null;
      MessageDialog.lastMessageTS = 0;
   });
   afterEach(() => vi.restoreAllMocks());
   afterEach(() => { lastRenderedFixture?.destroy(); });

   // -------------------------------------------------------------------------
   // Group 1 [Risk 3] — ngOnInit HTTP population
   // -------------------------------------------------------------------------

   describe("ngOnInit with a sheet — HTTP state population", () => {
      beforeEach(() => {
         setupAllActionHandlers({
            bookmarks: [HOME_BOOKMARK],
            highlights: [{ element: "Chart1", highlight: "H1", condition: "> 100", count: 0 }],
            hasPrintLayout: true,
            parameters: ["param1", "param2"],
            tableAssemblies: ["Table1"],
         });
      });

      it("populates comp.bookmarks from the server response", async () => {
         const model = makeModel({ actions: [makeAction({ sheet: SHEET_ID })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.bookmarks).toHaveLength(1);
         expect(comp.bookmarks[0].name).toBe("(Home)");
      });

      it("sets selectedBookmark to the (Home) entry from the server response", async () => {
         const model = makeModel({ actions: [makeAction({ sheet: SHEET_ID })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.selectedBookmark).toEqual(expect.objectContaining({ name: "(Home)" }));
      });

      it("sets hasPrintLayout from the server response", async () => {
         const model = makeModel({ actions: [makeAction({ sheet: SHEET_ID })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.hasPrintLayout).toBe(true);
      });

      it("populates comp.highlights from the server response", async () => {
         const model = makeModel({ actions: [makeAction({ sheet: SHEET_ID })] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.highlights).toHaveLength(1);
         expect(comp.highlights[0].element).toBe("Chart1");
      });
   });

   // -------------------------------------------------------------------------
   // Group 2 [Risk 3] — getBookmarks() filter + default logic
   // -------------------------------------------------------------------------

   describe("getBookmarks() — bookmark filter and default logic", () => {
      it("removes bookmarks absent from the server response", async () => {
         const staleBookmark: VSBookmarkInfoModel = {
            name: "Stale",
            label: "Stale",
            owner: { name: "admin", orgID: "host_org" },
         };
         server.use(
            http.get("*/api/portal/schedule/task/action/bookmarks", () =>
               HttpResponse.json([HOME_BOOKMARK])
            ),
         );
         // Render with no sheet so ngOnInit doesn't fire HTTP; set sheet + call manually.
         const action = makeAction({ bookmarks: [HOME_BOOKMARK, staleBookmark] });
         const model = makeModel({ actions: [action] });
         const { comp } = await renderTaskActionPane(model);

         (comp.model.actions[0] as GeneralActionModel).sheet = SHEET_ID;
         comp.getBookmarks();

         await waitFor(() => {
            const bookmarks = (comp.model.actions[0] as GeneralActionModel).bookmarks;
            expect(bookmarks).toHaveLength(1);
            expect(bookmarks[0].name).toBe("(Home)");
         });
      });

      it("defaults generalActionModel.bookmarks to [(Home)] when existing list is empty after filter", async () => {
         const staleBookmark: VSBookmarkInfoModel = {
            name: "Gone",
            label: "Gone",
            owner: { name: "admin", orgID: "host_org" },
         };
         server.use(
            http.get("*/api/portal/schedule/task/action/bookmarks", () =>
               HttpResponse.json([HOME_BOOKMARK])
            ),
         );
         const action = makeAction({ bookmarks: [staleBookmark] });
         const model = makeModel({ actions: [action] });
         const { comp } = await renderTaskActionPane(model);

         (comp.model.actions[0] as GeneralActionModel).sheet = SHEET_ID;
         comp.getBookmarks();

         // After filter: "Gone" not in server response → list becomes empty →
         // default branch runs and inserts (Home).
         await waitFor(() => {
            const bookmarks = (comp.model.actions[0] as GeneralActionModel).bookmarks;
            expect(bookmarks).toHaveLength(1);
            expect(bookmarks[0].name).toBe("(Home)");
         });
      });

      it("leaves generalActionModel.bookmarks empty when server has no (Home) and list is already empty", async () => {
         const customBookmark: VSBookmarkInfoModel = {
            name: "Custom",
            label: "Custom",
            owner: { name: "admin", orgID: "host_org" },
         };
         server.use(
            http.get("*/api/portal/schedule/task/action/bookmarks", () =>
               HttpResponse.json([customBookmark])
            ),
         );
         // action.bookmarks starts as [] (empty, truthy) — filter runs but result is still [].
         const action = makeAction({ bookmarks: [] });
         const model = makeModel({ actions: [action] });
         const { comp } = await renderTaskActionPane(model);

         (comp.model.actions[0] as GeneralActionModel).sheet = SHEET_ID;
         comp.getBookmarks();

         // comp.bookmarks receives the server response (1 entry);
         // but generalActionModel.bookmarks stays [] because server has no (Home).
         await waitFor(() => expect(comp.bookmarks).toHaveLength(1));
         expect((comp.model.actions[0] as GeneralActionModel).bookmarks).toHaveLength(0);
      });
   });

   // -------------------------------------------------------------------------
   // Group 3 [Risk 2] — updateValues() with empty model.actions
   // -------------------------------------------------------------------------

   describe("updateValues() — empty model.actions triggers addAction(true)", () => {
      it("creates a default ViewsheetAction when model.actions is initially empty", async () => {
         const model = makeModel({ actions: [] });
         const { comp } = await renderTaskActionPane(model);
         expect(comp.model.actions).toHaveLength(1);
         expect(comp.model.actions[0].actionType).toBe("ViewsheetAction");
         expect(comp.model.actions[0].actionClass).toBe("GeneralActionModel");
      });

      it("keeps listView false when a default action was auto-created", async () => {
         const model = makeModel({ actions: [] });
         const { comp } = await renderTaskActionPane(model);
         // Only 1 action after auto-create → listView must remain false.
         expect(comp.listView).toBe(false);
      });
   });

   // -------------------------------------------------------------------------
   // Group 4 [Risk 2] — deleteAction() multi-select: reverse-index ordering
   // -------------------------------------------------------------------------

   describe("deleteAction() — multi-select removes in reverse index order", () => {
      it("removes all selected actions, leaving only unselected actions in order", async () => {
         const model = makeModel({
            actions: [
               makeAction({ label: "A" }),
               makeAction({ label: "B" }),
               makeAction({ label: "C" }),
            ],
         });
         const { comp, fixture } = await renderTaskActionPane(model);
         comp.selectedActions = [0, 2];

         // Confirm with "ok".
         MODAL_MOCK.open.mockImplementationOnce(() => ({
            result: Promise.resolve("ok"),
            componentInstance: { onCommit: new Subject<string>() },
            close: vi.fn(),
            dismiss: vi.fn(),
         }));
         comp.deleteAction();

         await waitFor(() => expect(comp.model.actions).toHaveLength(1));
         expect(comp.model.actions[0].label).toBe("B");
         await fixture.whenStable();
      });

      it("clears selectedActions after successful multi-delete", async () => {
         const model = makeModel({
            actions: [makeAction({ label: "A" }), makeAction({ label: "B" })],
         });
         const { comp, fixture } = await renderTaskActionPane(model);
         comp.selectedActions = [0, 1];

         MODAL_MOCK.open.mockImplementationOnce(() => ({
            result: Promise.resolve("ok"),
            componentInstance: { onCommit: new Subject<string>() },
            close: vi.fn(),
            dismiss: vi.fn(),
         }));
         comp.deleteAction();

         await waitFor(() => {
            expect(comp.model.actions).toHaveLength(0);
            expect(comp.selectedActions).toHaveLength(0);
         });
         await fixture.whenStable();
      });
   });

   // -------------------------------------------------------------------------
   // Group 5 [Risk 2] — action setter: index padding beyond model.actions.length
   // -------------------------------------------------------------------------

   describe("action setter — out-of-bounds index padding", () => {
      it("inserts null entries when actionIndex is set beyond model.actions.length", async () => {
         const { comp } = await renderTaskActionPane();
         // model.actions has 1 entry at index 0. Force actionIndex to 3.
         // Bypass changeActionType() chain — set directly to isolate setter behavior.
         comp.actionIndex = 3;
         const newAction = makeAction({ label: "Far Action" });
         comp.action = newAction;

         expect(comp.model.actions).toHaveLength(4);
         expect(comp.model.actions[0]).toEqual(makeAction()); // original preserved
         expect(comp.model.actions[1]).toBeNull();
         expect(comp.model.actions[2]).toBeNull();
         expect(comp.model.actions[3]).toBe(newAction);
      });
   });
});
