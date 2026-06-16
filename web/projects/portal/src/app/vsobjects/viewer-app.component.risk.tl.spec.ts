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
 * ViewerAppComponent — Pass 2: Risk
 *
 * Covers async / modal-interaction paths deferred from Pass 1:
 *   Group 1  [Risk 3] — showBookmarks: fetches isDefaultOrgAsset + bookmark list;
 *                        auto-gotos matching bookmark from queryParameters
 *   Group 2  [Risk 3] — deleteBookmark: confirm dialog → STOMP delete; goto home
 *                        if deleted bookmark was currentBookmark
 *   Group 3  [Risk 2] — addBookmark: isAddBookmarkDisabled guard; dialog → STOMP add
 *   Group 4  [Risk 3] — gotoBookmark: direct path (no check) vs. check-deleted path;
 *                        warning when deleted
 *   Group 5  [Risk 3] — closeViewsheet (non-inTabs): checkFormTables → direct close;
 *                        confirm dialog when form tables exist; cancel abort
 *   Group 6  [Risk 2] — processMessageCommand CONFIRM: confirm dialog; ok → send
 *                        events with confirmed=true; cancel → confirmed=false for
 *                        confirmEvent items
 *   Group 7  [Risk 2] — processMessageCommand PROGRESS: sends events; shows progress
 *                        dialog when checkMv URI is present
 *   Group 8  [Risk 2] — processRemoveVSObjectCommand: removes from vsObjects +
 *                        vsObjectActions; clears selectedActions; emits
 *                        onLoadingStateChanged when globalLoadingIndicator
 *   Group 9  [Risk 2] — cancelViewsheetLoading: cancelled flag; assetLoadingService;
 *                        STOMP cancel event
 *   Group 10 [baseline] — setServerUpdateInterval / clearServerUpdateInterval
 *   Group 11 [baseline] — onFullScreenChange: reads fullScreenService.fullScreenMode
 *   Group 12 [baseline] — updateScrollLeft / processEmbedErrorCommand
 */

import { Subject, of } from "rxjs";
import { waitFor } from "@testing-library/angular";
import { VSObjectModel } from "./model/vs-object-model";

import {
   VS_CLIENT_MOCK,
   MODAL_MOCK,
   DIALOG_SERVICE_MOCK,
   MODEL_SERVICE_MOCK,
   HYPERLINK_SERVICE_MOCK,
   CHECK_FORM_DATA_SERVICE_MOCK,
   PAGING_CONTROL_SERVICE_MOCK,
   FULL_SCREEN_SERVICE_MOCK,
   ASSET_LOADING_SERVICE_MOCK,
   HEARTBEAT_WORKER_SERVICE_MOCK,
   fullScreenChangeSubject,
   resetMocks,
   renderComponent,
} from "./viewer-app.test-fixtures";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — showBookmarks [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — showBookmarks()", () => {
   // 🔁 Regression-sensitive: showBookmarks fires two async calls simultaneously —
   // direct http.get for isDefaultOrgAsset and modelService.getModel for the list.
   // Both must complete before the component state is correct.
   it("should populate vsBookmarkList from getBookmarks response", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([
         { name: "BK1", label: "BK1" },
         { name: "BK2", label: "BK2" },
      ]));
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";

      comp.showBookmarks(false);

      await waitFor(() => {
         expect(comp.vsBookmarkList).toHaveLength(2);
         expect(comp.vsBookmarkList[0].name).toBe("BK1");
      });
   });

   it("should update isDefaultOrgAsset from the HTTP response (MSW returns false)", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.isDefaultOrgAsset = true; // start truthy so the change is observable

      comp.showBookmarks(false);

      await waitFor(() => {
         expect(comp.isDefaultOrgAsset).toBe(false);
      });
   });

   // 🔁 Regression-sensitive: when queryParameters contains bookmarkName, showBookmarks
   // must auto-goto that bookmark after fetching the list. If the goto is skipped, users
   // following a deep-link URL never land on the intended bookmark.
   it("should auto-goto matching bookmark when queryParameters has bookmarkName", async () => {
      const bk: any = { name: "DeepLink", owner: { name: "admin", orgID: null } };
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of([bk]));
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.queryParameters = new Map([["bookmarkName", ["DeepLink"]]]);
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.showBookmarks(true);

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/bookmark/goto-bookmark",
            expect.any(Object),
         );
      });
   });
});

// ---------------------------------------------------------------------------
// Group 2 — deleteBookmark [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — deleteBookmark()", () => {
   it("should open a confirm dialog when called", async () => {
      const { comp } = await renderComponent();
      const bk: any = { name: "TestBK", readOnly: false };

      comp.deleteBookmark(bk);

      expect(MODAL_MOCK.open).toHaveBeenCalledOnce();
   });

   // 🔁 Regression-sensitive: clicking "yes" MUST send the STOMP delete event.
   // If the dialog result is not awaited or the event URI is wrong, bookmarks
   // are never removed server-side.
   it("should send STOMP delete-bookmark event when user clicks Yes", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("yes"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      VS_CLIENT_MOCK.sendEvent.mockClear();
      const bk: any = { name: "TestBK", readOnly: false, currentBookmark: false };

      comp.deleteBookmark(bk);

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/bookmark/delete-bookmark",
            expect.any(Object),
         );
      });
   });

   it("should NOT send any STOMP event when user clicks No", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("no"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent();
      VS_CLIENT_MOCK.sendEvent.mockClear();
      const bk: any = { name: "TestBK", readOnly: false, currentBookmark: false };

      comp.deleteBookmark(bk);

      // Positive gate: wait until the modal has opened, which lets the
      // Promise.resolve("no") microtask settle before asserting the negative.
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalledWith(
         "/events/vs/bookmark/delete-bookmark",
         expect.any(Object),
      );
   });

   // 🔁 Regression-sensitive: when the deleted bookmark was currentBookmark, the
   // component must immediately goto the home bookmark so the viewer is not left
   // in a broken state showing a non-existent bookmark.
   it("should goto home bookmark when deleted bookmark was currentBookmark", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("yes"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const home: any = { name: "(Home)", label: "(Home)", currentBookmark: false };
      const deleted: any = { name: "TestBK", currentBookmark: true };
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.vsBookmarkList = [home, deleted];
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.deleteBookmark(deleted);

      await waitFor(() => {
         // Two sendEvent calls expected: delete then goto
         const uris = VS_CLIENT_MOCK.sendEvent.mock.calls.map((c: any[]) => c[0]);
         expect(uris).toContain("/events/vs/bookmark/delete-bookmark");
         expect(uris).toContain("/events/vs/bookmark/goto-bookmark");
      });
   });
});

// ---------------------------------------------------------------------------
// Group 3 — addBookmark [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — addBookmark()", () => {
   // isAddBookmarkDisabled() returns true when securityEnabled=false (default)
   it("should NOT open dialog when isAddBookmarkDisabled() returns true", async () => {
      const { comp } = await renderComponent();
      comp.securityEnabled = false; // default; isAddBookmarkDisabled() → true

      comp.addBookmark();

      expect(DIALOG_SERVICE_MOCK.open).not.toHaveBeenCalled();
   });

   // 🔁 Regression-sensitive: when the dialog resolves with a bookmark model, the
   // component must send the STOMP add event — if it uses the wrong URI or skips the
   // result handler, new bookmarks are silently lost.
   it("should open dialog and send STOMP add-bookmark when dialog confirms", async () => {
      const result: any = { name: "NewBK", readOnly: false, type: 1 };
      DIALOG_SERVICE_MOCK.open.mockReturnValue({
         closed: of(null),
         close: vi.fn(),
         result: Promise.resolve(result),
      });
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.securityEnabled = true;
      comp.principal = "admin^^^host_org";
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.addBookmark();

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/bookmark/add-bookmark",
            expect.any(Object),
         );
      });
   });
});

// ---------------------------------------------------------------------------
// Group 4 — gotoBookmark [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — gotoBookmark()", () => {
   it("should send STOMP goto-bookmark directly when check=false (no deletion check)", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.vsBookmarkList = [];
      VS_CLIENT_MOCK.sendEvent.mockClear();
      const bk: any = { name: "BK1", owner: { name: "admin", orgID: null } };

      comp.gotoBookmark(bk, false);

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vs/bookmark/goto-bookmark",
         expect.any(Object),
      );
   });

   // 🔁 Regression-sensitive: gotoBookmark(check=true) must query the server before
   // navigating. If the check is skipped, navigating to a deleted bookmark throws a
   // server-side error with no user feedback.
   it("should send STOMP goto-bookmark when check=true and bookmark is NOT deleted", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of("false"));
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.vsBookmarkList = [];
      VS_CLIENT_MOCK.sendEvent.mockClear();
      const bk: any = { name: "BK1", owner: { name: "admin", orgID: null } };

      comp.gotoBookmark(bk, true);

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/bookmark/goto-bookmark",
            expect.any(Object),
         );
      });
   });

   it("should show warning dialog and NOT send goto when check=true and bookmark IS deleted", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of("true"));
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      VS_CLIENT_MOCK.sendEvent.mockClear();
      const bk: any = { name: "Gone", owner: { name: "admin", orgID: null } };

      comp.gotoBookmark(bk, true);

      await waitFor(() => {
         expect(MODAL_MOCK.open).toHaveBeenCalled();
      });
      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalledWith(
         "/events/vs/bookmark/goto-bookmark",
         expect.any(Object),
      );
   });

   it("should mark the target bookmark as currentBookmark", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      const bk1: any = { name: "BK1", currentBookmark: true };
      const bk2: any = { name: "BK2", currentBookmark: false };
      comp.vsBookmarkList = [bk1, bk2];
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.gotoBookmark(bk2, false);

      expect(bk1.currentBookmark).toBe(false);
      expect(bk2.currentBookmark).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — closeViewsheet (non-inTabs path) [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — closeViewsheet() non-inTabs path", () => {
   // 🔁 Regression-sensitive: the guard must use tabsHeight===0 (not inPortal or
   // preview) to determine the non-tabs path. If the condition is inverted,
   // closeViewsheet closes the ENTIRE session rather than the tab.
   it("should send STOMP close event when checkFormTables returns false", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(false));
      const { comp } = await renderComponent({ tabsHeight: 0 });
      comp.runtimeId = "rt-test";
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.closeViewsheet();

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/close",
         );
      });
   });

   it("should emit closeClicked(true) after closing viewsheet", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(false));
      const { comp } = await renderComponent({ tabsHeight: 0 });
      comp.runtimeId = "rt-test";
      const clicked: boolean[] = [];
      comp.closeClicked.subscribe((v: boolean) => clicked.push(v));
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.closeViewsheet();

      await waitFor(() => {
         expect(clicked).toContain(true);
      });
   });

   // 🔁 Regression-sensitive: when form tables exist, the user MUST be asked to
   // confirm. Skipping this dialog causes unsaved form data to be silently discarded.
   it("should show confirm dialog when checkFormTables returns true", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(true));
      const { comp } = await renderComponent({ tabsHeight: 0 });
      comp.runtimeId = "rt-test";

      comp.closeViewsheet();

      await waitFor(() => {
         expect(MODAL_MOCK.open).toHaveBeenCalled();
      });
   });

   it("should close viewsheet after user confirms form-table dialog", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(true));
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent({ tabsHeight: 0 });
      comp.runtimeId = "rt-test";
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.closeViewsheet();

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/close",
         );
      });
   });

   it("should NOT close viewsheet when user cancels form-table dialog", async () => {
      MODEL_SERVICE_MOCK.getModel.mockReturnValue(of(true));
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("cancel"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent({ tabsHeight: 0 });
      comp.runtimeId = "rt-test";
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.closeViewsheet();

      // Positive gate: wait until the modal has opened, which lets the
      // Promise.resolve("cancel") microtask settle before asserting the negative.
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalledWith(
         "/events/composer/viewsheet/close",
      );
   });
});

// ---------------------------------------------------------------------------
// Group 6 — processMessageCommand CONFIRM [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processMessageCommand() CONFIRM type", () => {
   // 🔁 Regression-sensitive: CONFIRM messages must open a dialog — they are used
   // to ask permission before overwriting shared resources. Ignoring them silently
   // bypasses server-enforced user consent.
   it("should open confirm dialog for CONFIRM type", async () => {
      const { comp } = await renderComponent();

      comp.processMessageCommand({ type: "CONFIRM", message: "Overwrite?" } as any);

      expect(MODAL_MOCK.open).toHaveBeenCalled();
   });

   it("should send events with confirmed=true when user clicks ok", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("ok"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent();
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.processMessageCommand({
         type: "CONFIRM",
         message: "Are you sure?",
         events: { "/events/vs/someAction": { someData: true } },
      } as any);

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/someAction",
            expect.objectContaining({ someData: true, confirmed: true }),
         );
      });
   });

   // 🔁 Regression-sensitive: cancelling a CONFIRM must send confirmed=false for
   // events that have confirmEvent=true. If this is skipped, the server waits
   // forever for the cancelled confirmation reply.
   it("should send events with confirmed=false when user clicks cancel (confirmEvent=true)", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("cancel"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent();
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.processMessageCommand({
         type: "CONFIRM",
         message: "Are you sure?",
         events: { "/events/vs/someAction": { someData: true, confirmEvent: true } },
      } as any);

      await waitFor(() => {
         expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
            "/events/vs/someAction",
            expect.objectContaining({ confirmed: false }),
         );
      });
   });

   it("should NOT send events on cancel when confirmEvent is falsy", async () => {
      MODAL_MOCK.open.mockImplementation(() => ({
         result: Promise.resolve("cancel"),
         componentInstance: { onCommit: new Subject<string>() },
         close: vi.fn(),
         dismiss: vi.fn(),
      }));
      const { comp } = await renderComponent();
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.processMessageCommand({
         type: "CONFIRM",
         message: "Are you sure?",
         events: { "/events/vs/someAction": { someData: true, confirmEvent: false } },
      } as any);

      // Positive gate: wait until the modal has opened, which lets the
      // Promise.resolve("cancel") microtask settle before asserting the negative.
      await waitFor(() => expect(MODAL_MOCK.open).toHaveBeenCalled());
      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalledWith(
         "/events/vs/someAction",
         expect.any(Object),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 7 — processMessageCommand PROGRESS [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processMessageCommand() PROGRESS type", () => {
   it("should send all events from the command immediately", async () => {
      const { comp } = await renderComponent();
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.processMessageCommand({
         type: "PROGRESS",
         message: "Loading…",
         events: { "/events/vs/checkMV": { id: "mv1" } },
      } as any);

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/vs/checkMV",
         expect.objectContaining({ id: "mv1" }),
      );
   });

   it("should open progress dialog when event key contains 'checkMV'", async () => {
      const { comp } = await renderComponent();

      comp.processMessageCommand({
         type: "PROGRESS",
         message: "Building MV…",
         events: { "/events/vs/checkMV": {} },
      } as any);

      expect(MODAL_MOCK.open).toHaveBeenCalled();
   });

   it("should NOT open progress dialog when no event key contains 'checkMV'", async () => {
      const { comp } = await renderComponent();

      comp.processMessageCommand({
         type: "PROGRESS",
         message: "Loading…",
         events: { "/events/vs/someOther": {} },
      } as any);

      expect(MODAL_MOCK.open).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8 — processRemoveVSObjectCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processRemoveVSObjectCommand()", () => {
   function makeObj(name: string): VSObjectModel {
      return {
         absoluteName: name,
         objectType: "VSChart",
         visible: true,
         objectFormat: { top: 0, left: 0, width: 100, height: 100 } as any,
      } as unknown as VSObjectModel;
   }

   // 🔁 Regression-sensitive: vsObjects and vsObjectActions must be kept in sync.
   // If one array is spliced without the other, subsequent action lookups by index
   // return the wrong action for the wrong object.
   it("should remove the named object from both vsObjects and vsObjectActions", async () => {
      const { comp } = await renderComponent();
      const chart = makeObj("Chart1");
      const table = makeObj("Table1");
      comp.vsObjects = [chart, table];
      comp.vsObjectActions = [{ label: "chart-actions" }, { label: "table-actions" }] as any[];

      comp.processRemoveVSObjectCommand({ name: "Table1" });

      expect(comp.vsObjects).toHaveLength(1);
      expect(comp.vsObjects[0].absoluteName).toBe("Chart1");
      expect(comp.vsObjectActions).toHaveLength(1);
   });

   it("should NOT remove any object when the name is not found", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeObj("Chart1")];
      comp.vsObjectActions = [{} as any];

      comp.processRemoveVSObjectCommand({ name: "NoSuchObject" });

      expect(comp.vsObjects).toHaveLength(1);
   });

   // 🔁 Regression-sensitive: selectedActions must be cleared when the selected
   // assembly is removed. If not cleared, the mini-toolbar renders stale action
   // buttons for a non-existent object.
   it("should clear selectedActions when the removed object was selected", async () => {
      const { comp } = await renderComponent();
      const chart = makeObj("Chart1");
      comp.vsObjects = [chart];
      comp.vsObjectActions = [{} as any];
      comp.selectedActions = { getModel: () => ({ absoluteName: "Chart1" }) } as any;

      comp.processRemoveVSObjectCommand({ name: "Chart1" });

      expect(comp.selectedActions).toBeNull();
   });

   it("should NOT clear selectedActions when a different object is removed", async () => {
      const { comp } = await renderComponent();
      const chart = makeObj("Chart1");
      const table = makeObj("Table1");
      comp.vsObjects = [chart, table];
      comp.vsObjectActions = [{} as any, {} as any];
      const actions: any = { getModel: () => ({ absoluteName: "Chart1" }) };
      comp.selectedActions = actions;

      comp.processRemoveVSObjectCommand({ name: "Table1" });

      expect(comp.selectedActions).toBe(actions);
   });

   it("should emit onLoadingStateChanged(false) when globalLoadingIndicator=true", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [makeObj("Chart1")];
      comp.vsObjectActions = [{} as any];
      comp.globalLoadingIndicator = true;
      const states: any[] = [];
      comp.onLoadingStateChanged.subscribe((v: any) => states.push(v));

      comp.processRemoveVSObjectCommand({ name: "Chart1" });

      expect(states).toHaveLength(1);
      expect(states[0]).toEqual({ name: "Chart1", loading: false });
   });
});

// ---------------------------------------------------------------------------
// Group 9 — cancelViewsheetLoading [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — cancelViewsheetLoading()", () => {
   it("should set cancelled=true", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-cancel";

      comp.cancelViewsheetLoading();

      expect(comp.cancelled).toBe(true);
   });

   it("should call assetLoadingService.setLoading with false", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-cancel";
      comp.assetId = "128^4096^__NULL__^TestVS^host_org";

      comp.cancelViewsheetLoading();

      expect(ASSET_LOADING_SERVICE_MOCK.setLoading).toHaveBeenCalledWith(
         expect.any(String),
         false,
      );
   });

   // 🔁 Regression-sensitive: the STOMP cancel event must include the runtimeId.
   // A missing runtimeId causes the server to cancel the wrong viewsheet loading.
   it("should send STOMP cancelViewsheet event with runtimeId", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-cancel";
      VS_CLIENT_MOCK.sendEvent.mockClear();

      comp.cancelViewsheetLoading();

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/viewsheet/cancelViewsheet",
         expect.objectContaining({ runtimeViewsheetId: "rt-cancel" }),
      );
   });
});

// ---------------------------------------------------------------------------
// Group 10 — setServerUpdateInterval / clearServerUpdateInterval [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — setServerUpdateInterval() / clearServerUpdateInterval()", () => {
   it("should NOT create a heartbeat when updateEnabled=false", async () => {
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-test";
      comp.updateEnabled = false;

      comp.setServerUpdateInterval();

      expect(HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat).not.toHaveBeenCalled();
   });

   it("should create a heartbeat keyed on runtimeId when updateEnabled=true", async () => {
      const heartbeatSubject = new Subject<void>();
      HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat.mockReturnValue(heartbeatSubject);
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-upd";
      comp.updateEnabled = true;

      comp.setServerUpdateInterval();

      expect(HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat).toHaveBeenCalledWith(
         "rt-upd-viewsheet-update",
         expect.any(Number),
      );
   });

   it("should send TOUCH_ASSET event when heartbeat ticks", async () => {
      const heartbeatSubject = new Subject<void>();
      HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat.mockReturnValue(heartbeatSubject);
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-upd";
      comp.updateEnabled = true;
      comp.setServerUpdateInterval();
      VS_CLIENT_MOCK.sendEvent.mockClear();

      heartbeatSubject.next();

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith(
         "/events/composer/touch-asset",
         expect.any(Object),
      );
   });

   it("should unsubscribe existing heartbeat when clearServerUpdateInterval is called", async () => {
      const unsubscribeSpy = vi.fn();
      const heartbeatSubject = new Subject<void>();
      HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat.mockReturnValue(heartbeatSubject);
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-upd";
      comp.updateEnabled = true;
      comp.setServerUpdateInterval();
      // private field — bypass needed to intercept the subscription
      const sub = (comp as any)["serverUpdateSubscription"];
      const origUnsubscribe = sub.unsubscribe.bind(sub);
      (comp as any)["serverUpdateSubscription"].unsubscribe = unsubscribeSpy;

      comp.clearServerUpdateInterval();

      expect(unsubscribeSpy).toHaveBeenCalledOnce();
      expect((comp as any)["serverUpdateSubscription"]).toBeNull();
   });

   it("should use touchInterval (seconds) if set", async () => {
      const heartbeatSubject = new Subject<void>();
      HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat.mockReturnValue(heartbeatSubject);
      const { comp } = await renderComponent();
      comp.runtimeId = "rt-upd";
      comp.updateEnabled = true;
      comp.touchInterval = 30; // 30 seconds

      comp.setServerUpdateInterval();

      expect(HEARTBEAT_WORKER_SERVICE_MOCK.createHeartbeat).toHaveBeenCalledWith(
         "rt-upd-viewsheet-update",
         30000, // 30 * 1000
      );
   });
});

// ---------------------------------------------------------------------------
// Group 11 — onFullScreenChange [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — onFullScreenChange()", () => {
   it("should update fullScreen from fullScreenService.fullScreenMode", async () => {
      const { comp } = await renderComponent();
      FULL_SCREEN_SERVICE_MOCK.fullScreenMode = true;

      fullScreenChangeSubject.next();

      expect(comp.fullScreen).toBe(true);
   });

   it("should emit fullScreenChange with the new value", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.fullScreenChange.subscribe((v: boolean) => emitted.push(v));
      FULL_SCREEN_SERVICE_MOCK.fullScreenMode = false;

      fullScreenChangeSubject.next();

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — updateScrollLeft / processEmbedErrorCommand [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — updateScrollLeft() / processEmbedErrorCommand()", () => {
   it("updateScrollLeft should delegate to pagingControlService.scrollLeftChange", async () => {
      const { comp } = await renderComponent();

      comp.updateScrollLeft(250);

      expect(PAGING_CONTROL_SERVICE_MOCK.scrollLeftChange).toHaveBeenCalledWith(250);
   });

   it("processEmbedErrorCommand should emit the error message via onEmbedError", async () => {
      const { comp } = await renderComponent();
      const errors: string[] = [];
      comp.onEmbedError.subscribe((msg: string) => errors.push(msg));

      comp.processEmbedErrorCommand({ message: "embed-failed" } as any);

      expect(errors).toHaveLength(1);
      expect(errors[0]).toBe("embed-failed");
   });
});
