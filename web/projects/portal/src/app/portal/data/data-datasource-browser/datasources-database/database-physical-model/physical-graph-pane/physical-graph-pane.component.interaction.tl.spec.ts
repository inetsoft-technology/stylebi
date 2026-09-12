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
 * PhysicalGraphPane — Pass 1: Interaction
 *
 * Direct-instantiation tests (no ATL render) — 3-param constructor, no jsPlumb.
 * HTTP calls use vi.fn() mock returning synchronous observables (of(...)).
 *
 *   Group 1  — viewport: reads graphContainer width/height; null-safe fallback
 *   Group 2  — restoreGraphViewModel: copies showColumns from matching old tables
 *   Group 3  — restoreJoinEditPaneModel: copies bounds; skips when joinEdit=false
 *   Group 4  — toggleFullScreen: flips flag; emits to onFullScreen Subject
 *   Group 5  — zoomLayout: routes ZOOM_OUT/ZOOM_IN/numeric; always refreshes graph
 *   Group 6  — zoom: clamps at 0.2 (zoomOut) and 2.0 (zoomIn)
 *   Group 7  — updateScale: sets scale, emits onZoom
 *   Group 8  — isAutoLayoutSelected / isZoomItemSelected
 *   Group 9  — zoomOutEnabled / zoomInEnabled boundary conditions
 *   Group 10 — keydown: Escape in fullScreen → toggle; other cases → no-op
 *   Group 11 — autoLayout: sets option, resets scroll, PUT, then refresh + onModified
 *   Group 12 — editJoinRuntimeId: returns runtimeID; null when no graph/model
 *   Group 13 — closeJoinEditPane: GET with params; save=true → emitModelChange + onModified
 *   Group 14 — fullScreenTooltip: correct string per fullScreenView state
 */

import { ZoomOptions } from "../../../../../../vsobjects/model/layout/zoom-options";
import {
   createPhysicalGraphPaneComp,
   createMockHttpClient,
   createMockPhysicalModelService,
   makeGraphModel,
   makeGraphViewModel,
   makeJoinEditPaneModel,
   makeJoinGraphModel,
   makeTableGraphModel,
} from "./physical-graph-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ─────────────────────────────────────────────────────────────────────────────
describe("PhysicalGraphPane — Pass 1: Interaction", () => {

   // ── Group 1 — viewport ───────────────────────────────────────────────────
   describe("Group 1 — viewport", () => {
      it("should return clientWidth and clientHeight from graphContainer", () => {
         const { comp } = createPhysicalGraphPaneComp({ graphContainerDims: { width: 1024, height: 768 } });

         expect(comp.viewport).toEqual({ width: 1024, height: 768 });
      });

      it("should return {0, 0} when graphContainer is null", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).graphContainer = null;

         expect(comp.viewport).toEqual({ width: 0, height: 0 });
      });
   });

   // ── Group 2 — restoreGraphViewModel ─────────────────────────────────────
   // Private method tested directly: called inside refreshPhysicalGraphModel, but verifying
   // the matching/copy logic in isolation avoids HTTP mock setup and POST response handling.
   describe("Group 2 — restoreGraphViewModel", () => {
      it("should copy showColumns from a matching old graph to the new graph", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldGraph = makeGraphModel("orders", { showColumns: true });
         const newGraph = makeGraphModel("orders", { showColumns: false });
         const oldModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([oldGraph]) });
         const newModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([newGraph]) });

         (comp as any).restoreGraphViewModel(newModel, oldModel);

         expect(newModel.graphViewModel.graphs[0].showColumns).toBe(true);
      });

      it("should not modify new graph when no matching old table exists", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([makeGraphModel("orders")]) });
         const newModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([makeGraphModel("customers")]) });

         (comp as any).restoreGraphViewModel(newModel, oldModel);

         expect(newModel.graphViewModel.graphs[0].showColumns).toBe(false);
      });

      it("should return early when oldModel is null", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const newModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([makeGraphModel("orders")]) });

         expect(() => (comp as any).restoreGraphViewModel(newModel, null)).not.toThrow();
      });

      it("should return early when oldModel has no graphViewModel", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldModel = makeJoinGraphModel({ graphViewModel: null });
         const newModel = makeJoinGraphModel({ graphViewModel: makeGraphViewModel([makeGraphModel("orders")]) });

         expect(() => (comp as any).restoreGraphViewModel(newModel, oldModel)).not.toThrow();
      });
   });

   // ── Group 3 — restoreJoinEditPaneModel ────────────────────────────────────
   // Private method tested directly: same rationale as Group 2 — isolates table-matching
   // and bounds-copy logic without routing through the full HTTP refresh cycle.
   describe("Group 3 — restoreJoinEditPaneModel", () => {
      it("should copy bounds from old table to matching new table", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldBounds = { left: 10, top: 20, width: 100, height: 50 } as any;
         const oldTable = makeTableGraphModel("orders", [], []);
         oldTable.bounds = oldBounds;
         const oldModel = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel([oldTable]),
         });
         const newTable = makeTableGraphModel("orders", [], []);
         const newModel = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel([newTable]),
         });

         (comp as any).restoreJoinEditPaneModel(newModel, oldModel);

         expect(newModel.joinEditPaneModel.tables[0].bounds).toBe(oldBounds);
      });

      it("should skip when oldModel.joinEdit is false", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldModel = makeJoinGraphModel({
            joinEdit: false,
            joinEditPaneModel: makeJoinEditPaneModel([makeTableGraphModel("orders")]),
         });
         const newTable = makeTableGraphModel("orders");
         const originalBounds = newTable.bounds;
         const newModel = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel([newTable]),
         });

         (comp as any).restoreJoinEditPaneModel(newModel, oldModel);

         // bounds should NOT be replaced from old table when oldModel.joinEdit is false
         expect(newModel.joinEditPaneModel.tables[0].bounds).toBe(originalBounds);
      });

      it("should skip when newModel.joinEdit is false", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const oldModel = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel([makeTableGraphModel("orders")]),
         });
         const newModel = makeJoinGraphModel({ joinEdit: false });

         expect(() => (comp as any).restoreJoinEditPaneModel(newModel, oldModel)).not.toThrow();
      });

      it("should skip when oldModel is null", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const newModel = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel([]),
         });

         expect(() => (comp as any).restoreJoinEditPaneModel(newModel, null)).not.toThrow();
      });
   });

   // ── Group 4 — toggleFullScreen ────────────────────────────────────────────
   describe("Group 4 — toggleFullScreen", () => {
      it("should set fullScreenView to true when it was false", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = false;

         comp.toggleFullScreen();

         expect(comp.fullScreenView).toBe(true);
      });

      it("should set fullScreenView to false when it was true", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = true;

         comp.toggleFullScreen();

         expect(comp.fullScreenView).toBe(false);
      });

      it("should emit the new fullScreenView value to onFullScreen", () => {
         const { comp, physicalModelService } = createPhysicalGraphPaneComp();
         const received: boolean[] = [];
         physicalModelService.onFullScreen.subscribe((v: boolean) => received.push(v));
         comp.fullScreenView = false;

         comp.toggleFullScreen();

         expect(received).toContain(true);
      });
   });

   // ── Group 5 — zoomLayout ──────────────────────────────────────────────────
   describe("Group 5 — zoomLayout", () => {
      it("should call zoom(true) when ZOOM_OUT", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const zoomSpy = vi.spyOn(comp, "zoom");

         comp.zoomLayout(ZoomOptions.ZOOM_OUT);

         expect(zoomSpy).toHaveBeenCalledWith(true);
      });

      it("should call zoom() with no args when ZOOM_IN", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const zoomSpy = vi.spyOn(comp, "zoom");

         comp.zoomLayout(ZoomOptions.ZOOM_IN);

         expect(zoomSpy).toHaveBeenCalledWith();
      });

      it("should call updateScale when a numeric ZoomOption is given", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const updateSpy = vi.spyOn(comp, "updateScale");

         comp.zoomLayout(ZoomOptions.ZOOM_150);

         expect(updateSpy).toHaveBeenCalledWith(ZoomOptions.ZOOM_150);
      });

      it("should call refreshPhysicalGraphModel regardless of zoom direction", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const refreshSpy = vi.spyOn(comp, "refreshPhysicalGraphModel");

         comp.zoomLayout(ZoomOptions.ZOOM_OUT);

         // called with no arguments (zoomLayout passes nothing to refreshPhysicalGraphModel)
         expect(refreshSpy).toHaveBeenCalledWith();
      });
   });

   // ── Group 6 — zoom ────────────────────────────────────────────────────────
   describe("Group 6 — zoom", () => {
      it("should not change scale when zoomOut=true and scale is at minimum (0.2)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 0.2;
         const updateSpy = vi.spyOn(comp, "updateScale");

         comp.zoom(true);

         expect(updateSpy).not.toHaveBeenCalled();
      });

      it("should not change scale when zoomOut=false and scale is at maximum (2.0)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 2.0;
         const updateSpy = vi.spyOn(comp, "updateScale");

         comp.zoom(false);

         expect(updateSpy).not.toHaveBeenCalled();
      });

      it("should call updateScale when zooming in from 1.0", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 1.0;
         const updateSpy = vi.spyOn(comp, "updateScale");

         comp.zoom(false);

         expect(updateSpy).toHaveBeenCalled();
         expect(comp.scale).toBeGreaterThan(1.0);
      });

      it("should call updateScale when zooming out from 1.0", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 1.0;
         const updateSpy = vi.spyOn(comp, "updateScale");

         comp.zoom(true);

         expect(updateSpy).toHaveBeenCalled();
         expect(comp.scale).toBeLessThan(1.0);
      });
   });

   // ── Group 7 — updateScale ─────────────────────────────────────────────────
   describe("Group 7 — updateScale", () => {
      it("should set this.scale to the given value", () => {
         const { comp } = createPhysicalGraphPaneComp();

         comp.updateScale(1.5);

         expect(comp.scale).toBe(1.5);
      });

      it("should emit the new scale via onZoom", () => {
         const { comp, onZoomSpy } = createPhysicalGraphPaneComp();

         comp.updateScale(0.75);

         expect(onZoomSpy).toHaveBeenCalledWith(0.75);
      });
   });

   // ── Group 8 — isAutoLayoutSelected / isZoomItemSelected ──────────────────
   // Private field `option` read directly: the field has no public setter; seeding it
   // directly is the only way to set the pre-condition without invoking autoLayout's
   // full HTTP side-effect chain.
   describe("Group 8 — isAutoLayoutSelected / isZoomItemSelected", () => {
      it("isAutoLayoutSelected should return true when option matches", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).option = "horizontal";

         expect(comp.isAutoLayoutSelected("horizontal")).toBe(true);
      });

      it("isAutoLayoutSelected should return false when option does not match", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).option = "vertical";

         expect(comp.isAutoLayoutSelected("horizontal")).toBe(false);
      });

      it("isZoomItemSelected should return true when scale matches ZoomOptions value", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = ZoomOptions.ZOOM_100;

         expect(comp.isZoomItemSelected(ZoomOptions.ZOOM_100)).toBe(true);
      });

      it("isZoomItemSelected should return false when scale does not match", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = ZoomOptions.ZOOM_100;

         expect(comp.isZoomItemSelected(ZoomOptions.ZOOM_150)).toBe(false);
      });
   });

   // ── Group 9 — zoomOutEnabled / zoomInEnabled ──────────────────────────────
   describe("Group 9 — zoomOutEnabled / zoomInEnabled", () => {
      it("zoomOutEnabled should return true when scale is 1.0", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 1.0;
         expect(comp.zoomOutEnabled()).toBe(true);
      });

      it("zoomOutEnabled should return false when scale is at 0.2 (minimum)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 0.2;
         expect(comp.zoomOutEnabled()).toBe(false);
      });

      it("zoomOutEnabled should return true when scale is at 2.0 (maximum)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 2.0;
         expect(comp.zoomOutEnabled()).toBe(true);
      });

      it("zoomInEnabled should return true when scale is 1.0", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 1.0;
         expect(comp.zoomInEnabled()).toBe(true);
      });

      it("zoomInEnabled should return false when scale is at 2.0 (maximum)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 2.0;
         expect(comp.zoomInEnabled()).toBe(false);
      });

      it("zoomInEnabled should return true when scale is at 0.2 (minimum)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.scale = 0.2;
         expect(comp.zoomInEnabled()).toBe(true);
      });
   });

   // ── Group 10 — keydown ────────────────────────────────────────────────────
   describe("Group 10 — keydown", () => {
      it("should toggle full screen when Escape is pressed in fullScreen mode", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = true;
         const event = { key: "Escape", preventDefault: vi.fn(), stopPropagation: vi.fn() } as any;

         comp.keydown(event);

         expect(comp.fullScreenView).toBe(false);
         expect(event.preventDefault).toHaveBeenCalled();
         expect(event.stopPropagation).toHaveBeenCalled();
      });

      it("should toggle full screen when Esc is pressed (legacy key name) in fullScreen mode", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = true;
         const event = { key: "Esc", preventDefault: vi.fn(), stopPropagation: vi.fn() } as any;

         comp.keydown(event);

         expect(comp.fullScreenView).toBe(false);
      });

      it("should not toggle when Escape is pressed but fullScreen is false", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = false;
         const event = { key: "Escape", preventDefault: vi.fn(), stopPropagation: vi.fn() } as any;

         comp.keydown(event);

         expect(comp.fullScreenView).toBe(false);
         expect(event.preventDefault).not.toHaveBeenCalled();
      });

      it("should not toggle when a non-Escape key is pressed", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = true;
         const event = { key: "ArrowLeft", preventDefault: vi.fn(), stopPropagation: vi.fn() } as any;

         comp.keydown(event);

         expect(comp.fullScreenView).toBe(true);
      });
   });

   // ── Group 11 — autoLayout ─────────────────────────────────────────────────
   // Private fields `option` and `graphContainer.nativeElement.scrollTop/Left` read directly:
   // both have no public accessors; direct inspection is the only way to verify the
   // pre-PUT state mutations without a separate HTTP integration test.
   describe("Group 11 — autoLayout", () => {
      it("should set option to 'horizontal' when colPriority=true", () => {
         const { comp } = createPhysicalGraphPaneComp();

         comp.autoLayout(true);

         expect((comp as any).option).toBe("horizontal");
      });

      it("should set option to 'vertical' when colPriority=false (default)", () => {
         const { comp } = createPhysicalGraphPaneComp();

         comp.autoLayout(false);

         expect((comp as any).option).toBe("vertical");
      });

      it("should reset graphContainer scroll to 0", () => {
         const { comp } = createPhysicalGraphPaneComp();

         comp.autoLayout();

         expect((comp as any).graphContainer.nativeElement.scrollTop).toBe(0);
         expect((comp as any).graphContainer.nativeElement.scrollLeft).toBe(0);
      });

      it("should call HTTP PUT to the auto-layout endpoint", () => {
         const { comp, httpClient } = createPhysicalGraphPaneComp();

         comp.autoLayout(true);

         expect(httpClient.put).toHaveBeenCalledWith(
            expect.stringContaining("physicalmodel/graph/layout"),
            expect.any(Object),
         );
      });

      it("should emit onModified(true) after successful PUT", () => {
         const { comp, onModifiedSpy } = createPhysicalGraphPaneComp();

         comp.autoLayout();

         expect(onModifiedSpy).toHaveBeenCalledWith(true);
      });
   });

   // ── Group 12 — editJoinRuntimeId ──────────────────────────────────────────
   // Private field `physicalGraph` written directly: the component populates it via
   // refreshPhysicalGraphModel (HTTP POST). Writing directly lets us test the getter
   // in isolation without triggering the network call.
   describe("Group 12 — editJoinRuntimeId", () => {
      it("should return runtimeID from joinEditPaneModel", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });

         expect(comp.editJoinRuntimeId).toBe("join-runtime-456");
      });

      it("should return null when physicalGraph is null", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = null;

         expect(comp.editJoinRuntimeId).toBeNull();
      });

      it("should return null when joinEditPaneModel is null", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({ joinEdit: false, joinEditPaneModel: null });

         expect(comp.editJoinRuntimeId).toBeNull();
      });
   });

   // ── Group 13 — closeJoinEditPane ──────────────────────────────────────────
   // Private field `physicalGraph` written directly (see Group 12 for rationale).
   describe("Group 13 — closeJoinEditPane", () => {
      it("should call GET with originRuntimeId, newRuntimeId, and save params", () => {
         const { comp, httpClient } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });

         comp.closeJoinEditPane(false);

         expect(httpClient.get).toHaveBeenCalledWith(
            expect.stringContaining("physicalmodel/join-edit/close"),
            expect.objectContaining({ params: expect.anything() }),
         );
      });

      it("should call refreshPhysicalGraphModel after GET succeeds", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });
         const refreshSpy = vi.spyOn(comp, "refreshPhysicalGraphModel").mockImplementation(() => {});

         comp.closeJoinEditPane(false);

         // called with no arguments — closeJoinEditPane passes nothing to refreshPhysicalGraphModel
         expect(refreshSpy).toHaveBeenCalledWith();
      });

      it("should emit onModified(true) when save=true", () => {
         const { comp, onModifiedSpy } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });
         vi.spyOn(comp, "refreshPhysicalGraphModel").mockImplementation(() => {});

         comp.closeJoinEditPane(true);

         expect(onModifiedSpy).toHaveBeenCalledWith(true);
      });

      it("should not emit onModified when save=false", () => {
         const { comp, onModifiedSpy } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });
         vi.spyOn(comp, "refreshPhysicalGraphModel").mockImplementation(() => {});

         comp.closeJoinEditPane(false);

         expect(onModifiedSpy).not.toHaveBeenCalled();
      });

      it("should call physicalModelService.emitModelChange(true) when save=true", () => {
         const { comp, physicalModelService } = createPhysicalGraphPaneComp();
         (comp as any).physicalGraph = makeJoinGraphModel({
            joinEdit: true,
            joinEditPaneModel: makeJoinEditPaneModel(),
         });
         vi.spyOn(comp, "refreshPhysicalGraphModel").mockImplementation(() => {});

         comp.closeJoinEditPane(true);

         expect(physicalModelService.emitModelChange).toHaveBeenCalledWith(true);
      });
   });

   // ── Group 14 — fullScreenTooltip ──────────────────────────────────────────
   describe("Group 14 — fullScreenTooltip", () => {
      it("should return exit tooltip when fullScreenView is true", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = true;

         expect(comp.fullScreenTooltip).toBe("_#(js:Exit Full Screen)");
      });

      it("should return toggle tooltip when fullScreenView is false", () => {
         const { comp } = createPhysicalGraphPaneComp();
         comp.fullScreenView = false;

         expect(comp.fullScreenTooltip).toBe("_#(js:Toggle Full Screen)");
      });
   });
});
