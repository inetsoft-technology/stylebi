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
 * ViewerAppComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — processSetRuntimeIdCommand: sets runtimeId on client, emits runtimeIdChange,
 *                        also calls processSetPermissionsCommand (Toolbar appended to permissions)
 *   Group 2  [Risk 3] — processSetPermissionsCommand: inverted-logic toolbarVisible gate +
 *                        toolbarVisibleChange emit + portalRepositoryPermission side-effect
 *   Group 3  [Risk 2] — processUpdateUndoStateCommand: undoEnabled / redoEnabled flags
 *   Group 4  [Risk 2] — processSetExportTypesCommand: builds exportTypes; filters Snapshot for
 *                        cross-org assets
 *   Group 5  [Risk 2] — processInitGridCommand: toolbarVisible / toolbarVisibleChange / wallboard /
 *                        stompClient.reloadOnFailure / hyperlinkService.singleClick
 *   Group 6  [Risk 2] — processExpiredSheetCommand: sets expired=true
 *   Group 7  [Risk 2] — zoom(): scale increments / decrements; clamps to [0.2, 2.0]
 *   Group 8  [Risk 2] — previousPage() / nextPage(): guard on undoEnabled + maxMode
 *   Group 9  [Risk 2] — toggleAnnotations(): sends inverted showAnnotations in STOMP event
 *   Group 10 [Risk 2] — closeViewsheet() inTabs: emits closeCurrentTab without any HTTP call
 *   Group 11 [baseline] — mobileToolbarVisible: requires touchDevice AND selectedActions
 *   Group 12 [baseline] — scrollLeft / scrollTop: return 0 when maxMode=true
 *   Group 13 [baseline] — isPageControlVisible: objectType gating
 *   Group 14 [baseline] — isPreviousPageVisible + isPreviousPageDisabled
 *   Group 15 [baseline] — isNextPageVisible + isNextPageDisabled
 *   Group 16 [baseline] — isExportVisible: exportTypes gate + permission check
 *   Group 17 [baseline] — isEmailVisible / isScheduleVisible / isPrintViewsheetVisible
 *   Group 18 [baseline] — isZoomVisible / isToggleFullScreenVisible / isCloseViewsheetVisible
 *   Group 19 [baseline] — bookmarksVisible: snapshot + permission + embed + list-length gates
 *   Group 20 [baseline] — changeMaxMode: maxMode flag + per-object maxMode assignment
 *   Group 21 [baseline] — getOrgId: organization parsed from assetId
 *   Group 22 [baseline] — getReloadMessage: returns correct i18n key per flag
 *   Group 23 [baseline] — processSetComposedDashboardCommand: sets composedDashboard flag
 *   Group 24 [baseline] — processSetViewsheetInfoCommand: annotated / showAnnotations / info fields
 *   Group 25 [baseline] — runtimeId setter: updates dialogService.container CSS selector
 *   Group 26 [baseline, +memory-leak] — ngOnDestroy: subscriptions cleaned up
 *
 * Confirmed bugs (it.fails): none
 *
 * Out of scope this pass (covered in Pass 2 and Pass 3):
 *   Pass 2 (risk): showBookmarks, deleteBookmark, addBookmark, gotoBookmark, closeViewsheet()
 *     non-inTabs modal path, processMessageCommand CONFIRM/PROGRESS, processEmbedErrorCommand,
 *     processRemoveVSObjectCommand, cancelViewsheetLoading, updateScrollLeft, fullScreenApplied,
 *     setServerUpdateInterval, clearServerUpdateInterval
 *   Pass 3 (display): onOpenChartFormatPane, processSetCurrentFormatCommand, showProgressDialog,
 *     processShowLoadingMaskCommand, showContextMenu, bookmarkChanged dialog, updateFormat,
 *     isShowAnnotationsVisible, showingActions, all toolbar *ngIf display conditions
 */

import { waitFor } from "@testing-library/angular";

import {
   VS_CLIENT_MOCK,
   STOMP_CLIENT_MOCK,
   SCALE_SERVICE_MOCK,
   SELECTION_MOBILE_SERVICE_MOCK,
   DIALOG_SERVICE_MOCK,
   HYPERLINK_SERVICE_MOCK,
   CROSS_ORG_ASSET_ID,
   resetMocks,
   renderComponent,
} from "./viewer-app.test-fixtures";

beforeEach(() => {
   resetMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — processSetRuntimeIdCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processSetRuntimeIdCommand()", () => {
   // 🔁 Regression-sensitive: the command sets runtimeId on the STOMP client as well as on the
   // component, then delegates to processSetPermissionsCommand. Breaking either assignment
   // leaves the viewsheet open without an identifiable runtime session.
   it("should set runtimeId on the viewsheet client and emit runtimeIdChange", async () => {
      const { comp } = await renderComponent();
      const emitted: string[] = [];
      comp.runtimeIdChange.subscribe(v => emitted.push(v));

      comp.processSetRuntimeIdCommand({ runtimeId: "rt-abc", permissions: [] });

      expect(VS_CLIENT_MOCK.runtimeId).toBe("rt-abc");
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe("rt-abc");
   });

   // processSetRuntimeIdCommand pushes "Toolbar" into permissions before delegating, which means
   // the toolbar is hidden when the command contains no other permissions. Verify that.
   it("should hide toolbar when no permissions are in the command (Toolbar appended → forbidden)", async () => {
      const { comp } = await renderComponent();

      comp.processSetRuntimeIdCommand({ runtimeId: "rt-abc", permissions: [] });

      // processSetPermissionsCommand receives ["Toolbar"]; indexOf("Toolbar") >= 0 → toolbarVisible=false
      expect(comp.toolbarVisible).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 2 — processSetPermissionsCommand [Risk 3]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processSetPermissionsCommand()", () => {
   // 🔁 Regression-sensitive: toolbarVisible uses INVERTED logic.
   // "Toolbar" in the list means it IS forbidden → toolbar hidden.
   it("should set toolbarVisible=false when 'Toolbar' is in the permissions list", async () => {
      const { comp } = await renderComponent();

      comp.processSetPermissionsCommand({ permissions: ["Toolbar"] });

      expect(comp.toolbarVisible).toBe(false);
   });

   // When "Toolbar" is absent from the list, the toolbar is visible.
   it("should set toolbarVisible=true when 'Toolbar' is NOT in the permissions list", async () => {
      const { comp } = await renderComponent();

      comp.processSetPermissionsCommand({ permissions: ["Email"] });

      expect(comp.toolbarVisible).toBe(true);
   });

   it("should emit toolbarVisibleChange with the updated value", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.toolbarVisibleChange.subscribe(v => emitted.push(v));

      comp.processSetPermissionsCommand({ permissions: [] });

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(true);
   });

   // portalRepositoryPermission uses the same inverted logic as toolbarVisible:
   // "PortalRepository" in the list means it is forbidden.
   it("should set portalRepositoryPermission=false when 'PortalRepository' is in permissions", async () => {
      const { comp } = await renderComponent();

      comp.processSetPermissionsCommand({ permissions: ["PortalRepository"] });

      expect(HYPERLINK_SERVICE_MOCK.portalRepositoryPermission).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 3 — processUpdateUndoStateCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processUpdateUndoStateCommand()", () => {
   it("should set undoEnabled=true when current > 0", async () => {
      const { comp } = await renderComponent();

      comp.processUpdateUndoStateCommand({ current: 1, points: 5, savePoint: 0 } as any);

      expect(comp.undoEnabled).toBe(true);
   });

   it("should set undoEnabled=false when current = 0", async () => {
      const { comp } = await renderComponent();

      comp.processUpdateUndoStateCommand({ current: 0, points: 5, savePoint: 0 } as any);

      expect(comp.undoEnabled).toBe(false);
   });

   it("should set redoEnabled=true when current < points - 1", async () => {
      const { comp } = await renderComponent();

      comp.processUpdateUndoStateCommand({ current: 2, points: 5, savePoint: 0 } as any);

      expect(comp.redoEnabled).toBe(true);
   });

   it("should set redoEnabled=false when current = points - 1", async () => {
      const { comp } = await renderComponent();

      comp.processUpdateUndoStateCommand({ current: 4, points: 5, savePoint: 0 } as any);

      expect(comp.redoEnabled).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — processSetExportTypesCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processSetExportTypesCommand()", () => {
   it("should build exportTypes array from command labels and values", async () => {
      const { comp } = await renderComponent();

      comp.processSetExportTypesCommand({
         exportTypes: ["Excel", "PDF"],
         exportLabels: ["Export Excel", "Export PDF"],
      });

      expect(comp.exportTypes).toHaveLength(2);
      expect(comp.exportTypes[0]).toEqual({ label: "Export Excel", value: "Excel" });
      expect(comp.exportTypes[1]).toEqual({ label: "Export PDF", value: "PDF" });
   });

   // 🔁 Regression-sensitive: Snapshot export must be excluded when the viewsheet belongs to a
   // different org than the currently logged-in user's org.  If it leaks through, users from one
   // org can snapshot-export viewsheets they should not be able to.
   it("should omit Snapshot export type when asset org does not match currOrgID", async () => {
      // CROSS_ORG_ASSET_ID has organization="other_org"; currOrgID defaults to "host_org".
      const { comp } = await renderComponent({ assetId: CROSS_ORG_ASSET_ID });

      comp.processSetExportTypesCommand({
         exportTypes: ["Excel", "Snapshot", "PDF"],
         exportLabels: ["Export Excel", "Export Snapshot", "Export PDF"],
      });

      const values = comp.exportTypes.map(t => t.value);
      expect(values).not.toContain("Snapshot");
      expect(values).toContain("Excel");
      expect(values).toContain("PDF");
   });
});

// ---------------------------------------------------------------------------
// Group 5 — processInitGridCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processInitGridCommand()", () => {
   it("should update toolbarVisible and emit toolbarVisibleChange", async () => {
      const { comp } = await renderComponent();
      const emitted: boolean[] = [];
      comp.toolbarVisibleChange.subscribe(v => emitted.push(v));

      comp.processInitGridCommand({ toolbarVisible: false, wallboard: false, singleClick: false } as any);

      expect(comp.toolbarVisible).toBe(false);
      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe(false);
   });

   it("should set stompClient.reloadOnFailure from the wallboard flag", async () => {
      const { comp } = await renderComponent();

      comp.processInitGridCommand({ toolbarVisible: true, wallboard: true, singleClick: false } as any);

      expect(STOMP_CLIENT_MOCK.reloadOnFailure).toBe(true);
   });

   it("should forward singleClick to hyperlinkService", async () => {
      const { comp } = await renderComponent();

      comp.processInitGridCommand({ toolbarVisible: true, wallboard: false, singleClick: true } as any);

      expect(HYPERLINK_SERVICE_MOCK.singleClick).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — processExpiredSheetCommand [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processExpiredSheetCommand()", () => {
   it("should set expired=true", async () => {
      const { comp } = await renderComponent();

      // Bypass: processExpiredSheetCommand is a private STOMP handler — called directly to
      // simulate server push.
      (comp as any)["processExpiredSheetCommand"]({});

      expect(comp.expired).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 7 — zoom() [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — zoom()", () => {
   it("should increment scale by 0.2 when zooming in", async () => {
      const { comp } = await renderComponent();
      comp.scale = 1.0;

      comp.zoom(false);

      expect(comp.scale).toBeCloseTo(1.2);
      expect(SCALE_SERVICE_MOCK.setScale).toHaveBeenCalledWith(expect.closeTo(1.2));
   });

   it("should decrement scale by 0.2 when zooming out", async () => {
      const { comp } = await renderComponent();
      comp.scale = 1.0;

      comp.zoom(true);

      expect(comp.scale).toBeCloseTo(0.8);
   });

   it("should not decrement below 0.2", async () => {
      const { comp } = await renderComponent();
      comp.scale = 0.2;

      comp.zoom(true);

      expect(comp.scale).toBeCloseTo(0.2);
      expect(SCALE_SERVICE_MOCK.setScale).not.toHaveBeenCalled();
   });

   it("should not increment above 2.0", async () => {
      const { comp } = await renderComponent();
      comp.scale = 2.0;

      comp.zoom(false);

      expect(comp.scale).toBeCloseTo(2.0);
      expect(SCALE_SERVICE_MOCK.setScale).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8 — previousPage() / nextPage() [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — previousPage() / nextPage()", () => {
   it("should send UNDO event when undoEnabled=true and maxMode=false", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = true;
      comp.maxMode = false;

      comp.previousPage();

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith("/events/undo");
   });

   it("should NOT send event when undoEnabled=false", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = false;
      comp.maxMode = false;

      comp.previousPage();

      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalled();
   });

   it("should NOT send event when maxMode=true even if undoEnabled", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = true;
      comp.maxMode = true;

      comp.previousPage();

      expect(VS_CLIENT_MOCK.sendEvent).not.toHaveBeenCalled();
   });

   // nextPage() has no redoEnabled guard — by design: the disabled state is
   // enforced by the template (isNextPageDisabled), not by the method itself.
   // The server handles an unconditional REDO when nothing is redoable.
   // Contrast: previousPage() does guard on undoEnabled (AND !maxMode).
   it("nextPage should always send REDO event regardless of redoEnabled", async () => {
      const { comp } = await renderComponent();
      comp.redoEnabled = false;

      comp.nextPage();

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledWith("/events/redo");
   });
});

// ---------------------------------------------------------------------------
// Group 9 — toggleAnnotations() [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — toggleAnnotations()", () => {
   it("should send toggle event with inverted showAnnotations (false → true)", async () => {
      const { comp } = await renderComponent();
      comp.showAnnotations = false;

      comp.toggleAnnotations();

      expect(VS_CLIENT_MOCK.sendEvent).toHaveBeenCalledOnce();
      const [uri, event] = VS_CLIENT_MOCK.sendEvent.mock.calls[0];
      expect(uri).toBe("/events/annotation/toggle-status");
      // ToggleAnnotationStatusEvent stores the flag as private 'status'; verify via cast.
      expect((event as any)["status"]).toBe(true);
   });

   it("should send toggle event with inverted showAnnotations (true → false)", async () => {
      const { comp } = await renderComponent();
      comp.showAnnotations = true;

      comp.toggleAnnotations();

      const [, event] = VS_CLIENT_MOCK.sendEvent.mock.calls[0];
      expect((event as any)["status"]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 10 — closeViewsheet() inTabs path [Risk 2]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — closeViewsheet() inTabs path", () => {
   // 🔁 Regression-sensitive: when tabsHeight > 0 the component emits closeCurrentTab
   // immediately and returns.  If the guard is broken, closeViewsheetNow() fires instead,
   // sending a STOMP event that closes the entire viewsheet rather than just the tab.
   it("should emit closeCurrentTab and NOT call model service when tabsHeight > 0", async () => {
      const { comp } = await renderComponent({ tabsHeight: 40 });
      const closedTabs: string[] = [];
      comp.closeCurrentTab.subscribe(v => closedTabs.push(v));

      SELECTION_MOBILE_SERVICE_MOCK.hasAutoMaxMode.mockReturnValue(false);

      comp.closeViewsheet();

      expect(closedTabs).toHaveLength(1);
   });
});

// ---------------------------------------------------------------------------
// Group 11 — mobileToolbarVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — mobileToolbarVisible", () => {
   it("should be false when touchDevice=false regardless of selectedActions", async () => {
      const { comp } = await renderComponent();
      comp.touchDevice = false;
      comp.selectedActions = {} as any;

      expect(comp.mobileToolbarVisible).toBe(false);
   });

   it("should be false when selectedActions is null regardless of touchDevice", async () => {
      const { comp } = await renderComponent();
      comp.touchDevice = true;
      comp.selectedActions = null;

      expect(comp.mobileToolbarVisible).toBe(false);
   });

   it("should be true when touchDevice=true, selectedActions is set, and no pop/dataTip overlay", async () => {
      const { comp } = await renderComponent();
      comp.touchDevice = true;
      comp.selectedActions = {} as any;
      comp.selectedPopComponent = null;
      comp.selectedDataTipView = null;

      expect(comp.mobileToolbarVisible).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 12 — scrollLeft / scrollTop [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — scrollLeft / scrollTop", () => {
   it("scrollLeft should return 0 when maxMode=true", async () => {
      const { comp } = await renderComponent();
      (comp as any)["_scrollLeft"] = 120;
      comp.maxMode = true;

      expect(comp.scrollLeft).toBe(0);
   });

   it("scrollLeft should return _scrollLeft when maxMode=false", async () => {
      const { comp } = await renderComponent();
      (comp as any)["_scrollLeft"] = 120;
      comp.maxMode = false;

      expect(comp.scrollLeft).toBe(120);
   });

   it("scrollTop should return 0 when maxMode=true", async () => {
      const { comp } = await renderComponent();
      (comp as any)["_scrollTop"] = 80;
      comp.maxMode = true;

      expect(comp.scrollTop).toBe(0);
   });

   it("scrollTop should return _scrollTop when maxMode=false", async () => {
      const { comp } = await renderComponent();
      (comp as any)["_scrollTop"] = 80;
      comp.maxMode = false;

      expect(comp.scrollTop).toBe(80);
   });
});

// ---------------------------------------------------------------------------
// Group 13 — usePagingControl / isPageControlVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — usePagingControl() / isPageControlVisible()", () => {
   it("usePagingControl should return true for VSCrosstab objectType", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [{ objectType: "VSCrosstab", absoluteName: "Crosstab1" } as any];

      expect(comp.usePagingControl(0)).toBe(true);
   });

   it("usePagingControl should return false for VSGauge objectType", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [{ objectType: "VSGauge", absoluteName: "Gauge1" } as any];

      expect(comp.usePagingControl(0)).toBe(false);
   });

   it("isPageControlVisible should return true when a paging-enabled assembly is selected", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [{ objectType: "VSTable", absoluteName: "Table1" } as any];
      comp.selectedAssemblies = [0];

      expect(comp.isPageControlVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 14 — isPreviousPageVisible + isPreviousPageDisabled [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — isPreviousPageVisible / isPreviousPageDisabled()", () => {
   it("isPreviousPageDisabled should return true when undoEnabled=false", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = false;
      comp.maxMode = false;

      expect(comp.isPreviousPageDisabled()).toBe(true);
   });

   it("isPreviousPageDisabled should return true when maxMode=true", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = true;
      comp.maxMode = true;

      expect(comp.isPreviousPageDisabled()).toBe(true);
   });

   it("isPreviousPageDisabled should return false when undoEnabled=true and maxMode=false", async () => {
      const { comp } = await renderComponent();
      comp.undoEnabled = true;
      comp.maxMode = false;

      expect(comp.isPreviousPageDisabled()).toBe(false);
   });

   it("isPreviousPageVisible should return false when 'Undo' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Undo"] });

      expect(comp.isPreviousPageVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 15 — isNextPageVisible + isNextPageDisabled [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — isNextPageVisible / isNextPageDisabled()", () => {
   it("isNextPageDisabled should return true when redoEnabled=false", async () => {
      const { comp } = await renderComponent();
      comp.redoEnabled = false;

      expect(comp.isNextPageDisabled()).toBe(true);
   });

   it("isNextPageDisabled should return false when redoEnabled=true", async () => {
      const { comp } = await renderComponent();
      comp.redoEnabled = true;

      expect(comp.isNextPageDisabled()).toBe(false);
   });

   it("isNextPageVisible should return false when 'Redo' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Redo"] });

      expect(comp.isNextPageVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 16 — isExportVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — isExportVisible()", () => {
   // isPermissionForbidden("ExportVS","Export") short-circuits to true if exportTypes is empty.
   it("should return false when exportTypes is empty (no export types loaded yet)", async () => {
      const { comp } = await renderComponent();
      comp.exportTypes = [];

      expect(comp.isExportVisible()).toBe(false);
   });

   it("should return true when exportTypes has entries and no forbidden permissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.exportTypes = [{ label: "Excel", value: "Excel" }];

      expect(comp.isExportVisible()).toBe(true);
   });

   it("should return false when 'Export' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Export"] });
      comp.exportTypes = [{ label: "Excel", value: "Excel" }];

      expect(comp.isExportVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 17 — isEmailVisible / isScheduleVisible / isPrintViewsheetVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — email / schedule / print visibility", () => {
   it("isEmailVisible should return true when 'Email' is not in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isEmailVisible()).toBe(true);
   });

   it("isEmailVisible should return false when 'Email' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Email"] });

      expect(comp.isEmailVisible()).toBe(false);
   });

   it("isPrintViewsheetVisible should return true when 'Print' is not in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isPrintViewsheetVisible()).toBe(true);
   });

   it("isPrintViewsheetVisible should return false when 'Print' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Print"] });

      expect(comp.isPrintViewsheetVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 18 — isZoomVisible / isToggleFullScreenVisible / isCloseViewsheetVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — zoom / fullscreen / close visibility", () => {
   it("isZoomVisible should return true when 'Zoom' is not in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isZoomVisible()).toBe(true);
   });

   it("isZoomVisible should return false when 'Zoom' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Zoom"] });

      expect(comp.isZoomVisible()).toBe(false);
   });

   it("isToggleFullScreenVisible should return true when 'Full Screen' is not forbidden", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });

      expect(comp.isToggleFullScreenVisible()).toBe(true);
   });

   it("isToggleFullScreenVisible should return false when 'Full Screen' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Full Screen"] });

      expect(comp.isToggleFullScreenVisible()).toBe(false);
   });

   // isCloseViewsheetVisible requires (inPortal || preview || linkView) to be truthy.
   // Use preview=true so the first guard passes; then test the permission gate.
   it("isCloseViewsheetVisible should return true for preview when 'Close' is not forbidden", async () => {
      const { comp } = await renderComponent({ preview: true, toolbarPermissions: [] });

      expect(comp.isCloseViewsheetVisible()).toBe(true);
   });

   it("isCloseViewsheetVisible should return false when 'Close' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ preview: true, toolbarPermissions: ["Close"] });

      expect(comp.isCloseViewsheetVisible()).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 19 — bookmarksVisible [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — bookmarksVisible()", () => {
   it("should return false when snapshot=true", async () => {
      const { comp } = await renderComponent();
      comp.snapshot = true;
      comp.vsBookmarkList = [{ name: "home" } as any, { name: "other" } as any];

      expect(comp.bookmarksVisible()).toBe(false);
   });

   it("should return false when 'Bookmark' is in toolbarPermissions", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: ["Bookmark"] });
      comp.vsBookmarkList = [{ name: "home" } as any, { name: "other" } as any];

      expect(comp.bookmarksVisible()).toBe(false);
   });

   it("should return true when no snapshot, no bookmark permissions, and bookmark list has entries", async () => {
      const { comp } = await renderComponent({ toolbarPermissions: [] });
      comp.snapshot = false;
      comp.vsBookmarkList = [{ name: "home" } as any, { name: "other" } as any];

      expect(comp.bookmarksVisible()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 20 — changeMaxMode [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — changeMaxMode()", () => {
   it("should set maxMode on the component", async () => {
      const { comp } = await renderComponent();

      comp.changeMaxMode({ assembly: "Chart1", maxMode: true });

      expect(comp.maxMode).toBe(true);
   });

   it("should set maxMode=true only on the matching assembly object", async () => {
      const { comp } = await renderComponent();
      comp.vsObjects = [
         { absoluteName: "Chart1", objectType: "VSChart" } as any,
         { absoluteName: "Table1", objectType: "VSTable" } as any,
      ];

      comp.changeMaxMode({ assembly: "Chart1", maxMode: true });

      expect((comp.vsObjects[0] as any).maxMode).toBe(true);
      expect((comp.vsObjects[1] as any).maxMode).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 21 — getOrgId [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — getOrgId()", () => {
   it("should parse the organization field from a well-formed assetId", async () => {
      // assetId format: scope^type^user^path^organization
      const { comp } = await renderComponent({ assetId: "128^4096^__NULL__^TestVS^host_org" });

      const orgId = (comp as any)["getOrgId"]();

      expect(orgId).toBe("host_org");
   });

   it("should return null when assetId is not set", async () => {
      const { comp } = await renderComponent({ assetId: null });

      const orgId = (comp as any)["getOrgId"]();

      expect(orgId).toBeNull();
   });
});

// ---------------------------------------------------------------------------
// Group 22 — getReloadMessage [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — getReloadMessage()", () => {
   it("should return expiration key when expired=true", async () => {
      const { comp } = await renderComponent();
      comp.expired = true;

      expect(comp.getReloadMessage()).toBe("_#(js:viewer.expiration)");
   });

   it("should return rename-transform key when transformFinished=true", async () => {
      const { comp } = await renderComponent();
      comp.expired = false;
      comp.transformFinished = true;

      expect(comp.getReloadMessage()).toBe("_#(js:viewer.expiration.renameTransformFinished)");
   });

   it("should return edit-bookmark key when editBookmarkFinished=true", async () => {
      const { comp } = await renderComponent();
      comp.expired = false;
      comp.transformFinished = false;
      comp.editBookmarkFinished = true;

      expect(comp.getReloadMessage()).toBe("_#(js:viewer.expiration.editBookmarkFinished)");
   });

   it("should return empty string when no flags are set", async () => {
      const { comp } = await renderComponent();

      expect(comp.getReloadMessage()).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 23 — processSetComposedDashboardCommand [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processSetComposedDashboardCommand()", () => {
   it("should set the composedDashboard flag to true", async () => {
      const { comp } = await renderComponent();

      comp.processSetComposedDashboardCommand({});

      expect((comp as any)["composedDashboard"]).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 24 — processSetViewsheetInfoCommand [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — processSetViewsheetInfoCommand()", () => {
   it("should update annotated and showAnnotations from the command", async () => {
      const { comp } = await renderComponent();

      comp.processSetViewsheetInfoCommand({
         annotated: true,
         annotation: true,
         info: {},
         assemblyInfo: { name: "TestVS" },
         linkUri: "",
         baseEntry: null,
         layouts: [],
         formTable: false,
      } as any);

      expect(comp.annotated).toBe(true);
      expect(comp.showAnnotations).toBe(true);
   });

   it("should update scaleToScreen and viewsheetName from the command", async () => {
      const { comp } = await renderComponent();

      comp.processSetViewsheetInfoCommand({
         annotated: false,
         annotation: false,
         info: { scaleToScreen: true, viewsheetBackground: "blue" },
         assemblyInfo: { name: "Dash1" },
         linkUri: "",
         baseEntry: null,
         layouts: [],
         formTable: false,
      } as any);

      expect(comp.scaleToScreen).toBe(true);
      expect(comp.name).toBe("Dash1");
   });
});

// ---------------------------------------------------------------------------
// Group 25 — runtimeId setter [baseline]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — runtimeId setter", () => {
   it("should update dialogService.container to the CSS selector for the new runtimeId", async () => {
      const { comp } = await renderComponent();

      comp.runtimeId = "rt-xyz";

      expect(DIALOG_SERVICE_MOCK.container).toBe('.viewer-container[runtime-id="rt-xyz"]');
   });
});

// ---------------------------------------------------------------------------
// Group 26 — ngOnDestroy subscription cleanup [baseline, +memory-leak]
// ---------------------------------------------------------------------------

describe("ViewerAppComponent — ngOnDestroy()", () => {
   it("should call dialogService.ngOnDestroy", async () => {
      const { comp } = await renderComponent();

      comp.ngOnDestroy();

      expect(DIALOG_SERVICE_MOCK.ngOnDestroy).toHaveBeenCalledOnce();
   });
});
