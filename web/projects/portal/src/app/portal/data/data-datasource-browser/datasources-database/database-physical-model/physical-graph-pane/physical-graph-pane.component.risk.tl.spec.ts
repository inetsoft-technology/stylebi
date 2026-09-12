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
 * PhysicalGraphPane — Pass 2: Risk
 *
 * Covers async flows, race conditions, and state-management edge cases.
 *
 *   Group 1 — modelInitializing: reflects physicalModelService.loadingModel
 *   Group 2 — ngAfterViewChecked: delegates to updateGraphPaneSize
 *   Group 3 — updateGraphPaneSize: skips when viewport unchanged; debounces PUT when changed; updates oldViewport after PUT
 *   Group 4 — refreshPhysicalGraphModel: POST → sets physicalGraph, emits events; refreshWarning on joinEdit; error path
 */

import { throwError } from "rxjs";
import {
   createMockDebounceService,
   createMockHttpClient,
   createMockPhysicalModelService,
   createPhysicalGraphPaneComp,
   makeJoinEditPaneModel,
   makeJoinGraphModel,
} from "./physical-graph-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("PhysicalGraphPane — Pass 2: Risk", () => {

   // ── Group 1 — modelInitializing ───────────────────────────────────────────
   describe("Group 1 — modelInitializing", () => {
      it("should return true when physicalModelService.loadingModel is true", () => {
         const physicalModelService = createMockPhysicalModelService();
         physicalModelService.loadingModel = true;
         const { comp } = createPhysicalGraphPaneComp({ physicalModelService });

         expect(comp.modelInitializing).toBe(true);
      });

      it("should return false when physicalModelService.loadingModel is false", () => {
         const { comp } = createPhysicalGraphPaneComp();

         expect(comp.modelInitializing).toBe(false);
      });
   });

   // ── Group 2 — ngAfterViewChecked ──────────────────────────────────────────
   // Private field `graphContainer` and method `updateGraphPaneSize` accessed directly:
   // graphContainer is set by Angular DI via @ViewChild — bypassed here to test the
   // null/non-null branching without a full Angular lifecycle; updateGraphPaneSize is
   // private and tested in Group 3, so it is stubbed here to avoid double-testing.
   describe("Group 2 — ngAfterViewChecked", () => {
      it("should call updateGraphPaneSize when graphContainer is set", () => {
         const { comp } = createPhysicalGraphPaneComp();
         const spy = vi.spyOn(comp as any, "updateGraphPaneSize").mockImplementation(() => {});

         comp.ngAfterViewChecked();

         expect(spy).toHaveBeenCalledWith();
      });

      it("should still call updateGraphPaneSize when graphContainer is null (early-return inside)", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).graphContainer = null;
         const spy = vi.spyOn(comp as any, "updateGraphPaneSize").mockImplementation(() => {});

         comp.ngAfterViewChecked();

         expect(spy).toHaveBeenCalledWith();
      });
   });

   // ── Group 3 — updateGraphPaneSize ─────────────────────────────────────────
   // Private method and fields tested directly: `updateGraphPaneSize` is private and
   // called only from ngAfterViewChecked (tested in Group 2). `oldViewport` is private
   // state with no public accessor — written directly to set the "changed/unchanged"
   // precondition without triggering a real change-detection cycle.
   describe("Group 3 — updateGraphPaneSize", () => {
      it("should skip PUT when graphContainer is null", () => {
         const { comp, httpClient } = createPhysicalGraphPaneComp();
         (comp as any).graphContainer = null;

         (comp as any).updateGraphPaneSize();

         expect(httpClient.put).not.toHaveBeenCalledWith(
            expect.stringContaining("graph/size"), expect.anything()
         );
      });

      it("should skip debounce/PUT when viewport has not changed (isEquals)", () => {
         const debounceService = createMockDebounceService(false);
         const { comp, httpClient } = createPhysicalGraphPaneComp({ debounceService });
         (comp as any).oldViewport = comp.viewport;

         (comp as any).updateGraphPaneSize();

         expect(debounceService.debounce).not.toHaveBeenCalled();
      });

      it("should call debounce and PUT when viewport has changed", () => {
         const { comp, httpClient } = createPhysicalGraphPaneComp({ graphContainerDims: { width: 800, height: 600 } });
         (comp as any).oldViewport = { width: 400, height: 300 };

         (comp as any).updateGraphPaneSize();

         expect(httpClient.put).toHaveBeenCalledWith(
            expect.stringContaining("physicalmodel/graph/size"),
            expect.anything(),
         );
      });

      it("should update oldViewport to current viewport after PUT succeeds", () => {
         const { comp } = createPhysicalGraphPaneComp({ graphContainerDims: { width: 800, height: 600 } });
         (comp as any).oldViewport = { width: 100, height: 100 };

         (comp as any).updateGraphPaneSize();

         expect((comp as any).oldViewport).toEqual({ width: 800, height: 600 });
      });
   });

   // ── Group 4 — refreshPhysicalGraphModel ───────────────────────────────────
   // Private fields `loadingGraphPane` and `physicalGraph` accessed directly: both have
   // no public accessors. Written/read directly to verify the state transitions that
   // happen inside the POST subscribe callback (success and error paths).
   describe("Group 4 — refreshPhysicalGraphModel", () => {
      it("should POST to graph endpoint and set physicalGraph on success", () => {
         const pgm = makeJoinGraphModel({ joinEdit: false });
         const httpClient = createMockHttpClient({ postResponse: pgm });
         const { comp } = createPhysicalGraphPaneComp({ httpClient });

         comp.refreshPhysicalGraphModel();

         expect((comp as any).physicalGraph).toBe(pgm);
      });

      it("should set loadingGraphPane to false after POST succeeds", () => {
         const { comp } = createPhysicalGraphPaneComp();
         (comp as any).loadingGraphPane = true;

         comp.refreshPhysicalGraphModel();

         expect((comp as any).loadingGraphPane).toBe(false);
      });

      it("should emit onPhysicalGraph with graphViewModel", () => {
         const pgm = makeJoinGraphModel({ joinEdit: false });
         const httpClient = createMockHttpClient({ postResponse: pgm });
         const { comp, onPhysicalGraphSpy } = createPhysicalGraphPaneComp({ httpClient });

         comp.refreshPhysicalGraphModel();

         expect(onPhysicalGraphSpy).toHaveBeenCalledWith(pgm.graphViewModel);
      });

      it("should emit onJoinEditing with joinEdit value", () => {
         const pgm = makeJoinGraphModel({ joinEdit: true, joinEditPaneModel: makeJoinEditPaneModel() });
         const httpClient = createMockHttpClient({ postResponse: pgm });
         const { comp, onJoinEditingSpy } = createPhysicalGraphPaneComp({ httpClient });

         comp.refreshPhysicalGraphModel();

         expect(onJoinEditingSpy).toHaveBeenCalledWith(true);
      });

      it("should call refreshWarning with runtimeId when joinEdit is true", () => {
         const pgm = makeJoinGraphModel({ joinEdit: true, joinEditPaneModel: makeJoinEditPaneModel() });
         const httpClient = createMockHttpClient({ postResponse: pgm });
         const { comp, physicalModelService } = createPhysicalGraphPaneComp({ httpClient });

         comp.refreshPhysicalGraphModel();

         expect(physicalModelService.refreshWarning).toHaveBeenCalledWith("runtime-123");
      });

      it("should not call refreshWarning when joinEdit is false", () => {
         const pgm = makeJoinGraphModel({ joinEdit: false });
         const httpClient = createMockHttpClient({ postResponse: pgm });
         const { comp, physicalModelService } = createPhysicalGraphPaneComp({ httpClient });

         comp.refreshPhysicalGraphModel();

         expect(physicalModelService.refreshWarning).not.toHaveBeenCalled();
      });

      it("should set loadingGraphPane to false when POST errors", () => {
         const httpClient = createMockHttpClient();
         vi.spyOn(httpClient, "post").mockReturnValue(throwError(() => new Error("network error")));
         const { comp } = createPhysicalGraphPaneComp({ httpClient });
         (comp as any).loadingGraphPane = true;

         comp.refreshPhysicalGraphModel();

         expect((comp as any).loadingGraphPane).toBe(false);
      });

      it("should use joinEditInfo.runtimeId when joinEditInfo is provided", () => {
         const { comp, httpClient } = createPhysicalGraphPaneComp();
         const joinEditInfo = { runtimeId: "join-rt-789", sourceTable: "a", targetTable: "b" } as any;

         comp.refreshPhysicalGraphModel(joinEditInfo);

         // GetGraphModelEvent stores the runtime id in field runtimeID (capital D)
         const postCall = httpClient.post.mock.calls[0];
         expect(postCall[1].runtimeID).toBe("join-rt-789");
      });
   });
});
