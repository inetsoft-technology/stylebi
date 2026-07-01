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
 * VSTable — Pass 2: Risk
 *
 * Covers async/zone/subscription risks not caught by static unit tests:
 *   positionDataAnnotations — zone.run wrapper + setTimeout deferred detectChanges;
 *   selectCell mobile dataTip — fire-and-forget setTimeout that calls dataTipService;
 *   richTextService showAnnotationDialog — subscription lifecycle (add + callback chain).
 *
 *   Group 1  — positionDataAnnotations: setTimeout deferred inside zone.run
 *   Group 2  — selectCell mobile dataTip: setTimeout fires dataTipService calls
 *   Group 3  — richTextService subscription: annotate-title/cell subscription lifecycle
 */

import { Subject } from "rxjs";
import { GuiTool } from "../../../common/util/gui-tool";
import {
   createMockActions,
   createTableComponent,
   makeTableCell,
} from "./vs-table.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSTable — Pass 2: Risk", () => {

   // ── Group 1 — positionDataAnnotations ────────────────────────────────────────
   // positionDataAnnotations is protected — accessed via (comp as any) because it is
   // dispatched from updateLayout() when model.dataAnnotationModels is non-null;
   // there is no public entry point.
   describe("Group 1 — positionDataAnnotations: zone.run + setTimeout", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => vi.useRealTimers());

      it("should call detectChanges after setTimeout when lt annotation is positioned", () => {
         const { comp, changeDetectorRef } = createTableComponent();
         vi.spyOn(comp as any, "positionAnnotationsToCell").mockReturnValue(true);
         comp.model.leftTopAnnotations = [{}] as any;
         comp.model.leftBottomAnnotations = [];

         (comp as any).positionDataAnnotations();
         vi.runAllTimers();

         expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
      });

      it("should call detectChanges when only lb annotation is positioned (lt=false, lb=true)", () => {
         const { comp, changeDetectorRef } = createTableComponent();
         vi.spyOn(comp as any, "positionAnnotationsToCell")
            .mockReturnValueOnce(false)  // lt
            .mockReturnValueOnce(true);  // lb
         comp.model.leftTopAnnotations = [] as any;
         comp.model.leftBottomAnnotations = [{}] as any;

         (comp as any).positionDataAnnotations();
         vi.runAllTimers();

         expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
      });

      it("should NOT call detectChanges when both positionAnnotationsToCell calls return false", () => {
         const { comp, changeDetectorRef } = createTableComponent();
         vi.spyOn(comp as any, "positionAnnotationsToCell").mockReturnValue(false);
         comp.model.leftTopAnnotations = [] as any;
         comp.model.leftBottomAnnotations = [] as any;

         (comp as any).positionDataAnnotations();
         vi.runAllTimers();

         expect(changeDetectorRef.detectChanges).not.toHaveBeenCalled();
      });

      it("should return false (does not delegate annotation positioning to the parent)", () => {
         const { comp } = createTableComponent();
         vi.spyOn(comp as any, "positionAnnotationsToCell").mockReturnValue(false);
         comp.model.leftTopAnnotations = [] as any;
         comp.model.leftBottomAnnotations = [] as any;

         expect((comp as any).positionDataAnnotations()).toBe(false);
      });
   });

   // ── Group 2 — selectCell mobile dataTip ──────────────────────────────────────
   // The mobile-dataTip path in selectCell schedules a fire-and-forget setTimeout(cb, 0)
   // when mobileDevice=true. The callback is never cancelled, creating a lifecycle risk
   // if the component is destroyed before the timeout fires.
   describe("Group 2 — selectCell mobile dataTip: fire-and-forget setTimeout", () => {
      beforeEach(() => vi.useFakeTimers());
      afterEach(() => vi.useRealTimers());

      it("should call dataTipService.showDataTip after setTimeout fires (mobileDevice + dataTip)", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const { comp, dataTipService } = createTableComponent({
            model: { dataTip: "TipName", dataTipAlpha: 0.8 },
         });
         const cell = makeTableCell({ row: 1, col: 0 });
         const event = {
            button: 0, ctrlKey: false, shiftKey: false,
            stopPropagation: vi.fn(), ignoreClick: false,
            targetTouches: [{ clientX: 100, clientY: 200 }],
         } as any;

         comp.selectCell(event, cell, false);
         vi.runAllTimers();

         expect(dataTipService.showDataTip).toHaveBeenCalledWith(
            "Table1", "TipName", 100, 200, "1X0", 0.8,
         );
      });

      it("should call dataTipService.hideDataTip inside setTimeout when service is not frozen", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const { comp, dataTipService } = createTableComponent({
            model: { dataTip: "TipName" },
         });
         dataTipService.isFrozen.mockReturnValue(false);
         const cell = makeTableCell({ row: 0, col: 0 });
         const event = {
            button: 0, ctrlKey: false, shiftKey: false,
            stopPropagation: vi.fn(), ignoreClick: false,
            targetTouches: [{ clientX: 50, clientY: 50 }],
         } as any;

         comp.selectCell(event, cell, false);
         vi.runAllTimers();

         expect(dataTipService.hideDataTip).toHaveBeenCalled();
      });

      it("should NOT call dataTipService.showDataTip before the setTimeout fires", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const { comp, dataTipService } = createTableComponent({
            model: { dataTip: "TipName" },
         });
         const cell = makeTableCell({ row: 0, col: 0 });
         const event = {
            button: 0, ctrlKey: false, shiftKey: false,
            stopPropagation: vi.fn(), ignoreClick: false,
            targetTouches: [{ clientX: 50, clientY: 50 }],
         } as any;

         comp.selectCell(event, cell, false);
         // Intentionally do NOT run timers

         expect(dataTipService.showDataTip).not.toHaveBeenCalled();
      });

      it("should NOT queue a setTimeout when mobileDevice=false", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(false);
         const { comp, dataTipService } = createTableComponent({
            model: { dataTip: "TipName" },
         });
         const cell = makeTableCell({ row: 0, col: 0 });
         const event = {
            button: 0, ctrlKey: false, shiftKey: false,
            stopPropagation: vi.fn(), ignoreClick: false,
            targetTouches: [],
         } as any;

         comp.selectCell(event, cell, false);
         vi.runAllTimers();

         expect(dataTipService.showDataTip).not.toHaveBeenCalled();
      });

      it("should NOT call hideDataTip inside setTimeout when service is already frozen", () => {
         vi.spyOn(GuiTool, "isMobileDevice").mockReturnValue(true);
         const { comp, dataTipService } = createTableComponent({
            model: { dataTip: "TipName" },
         });
         dataTipService.isFrozen.mockReturnValue(true);
         const cell = makeTableCell({ row: 0, col: 0 });
         const event = {
            button: 0, ctrlKey: false, shiftKey: false,
            stopPropagation: vi.fn(), ignoreClick: false,
            targetTouches: [{ clientX: 50, clientY: 50 }],
         } as any;

         comp.selectCell(event, cell, false);
         vi.runAllTimers();

         expect(dataTipService.hideDataTip).not.toHaveBeenCalled();
      });
   });

   // ── Group 3 — richTextService subscription lifecycle ──────────────────────────
   // "table annotate title/cell" actions add the showAnnotationDialog subscription to
   // this.subscriptions so it is cleaned up in ngOnDestroy — preventing an Observable
   // that keeps a reference to the component from leaking after destroy.
   // subscriptions is protected — spied on via (comp as any) because there is no
   // public accessor to verify whether a new subscription was tracked.
   describe("Group 3 — richTextService subscription lifecycle", () => {
      it("'table annotate title' should add showAnnotationDialog subscription to this.subscriptions", () => {
         const { comp } = createTableComponent({
            richTextService: {
               showAnnotationDialog: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            },
         });
         const actions = createMockActions();
         comp.actions = actions as any;
         const addSpy = vi.spyOn((comp as any).subscriptions, "add");

         actions.onAssemblyActionEvent.next({ id: "table annotate title", event: new MouseEvent("click") });

         expect(addSpy).toHaveBeenCalled();
      });

      it("'table annotate title' callback should send add-assembly-annotation event when invoked", () => {
         let capturedCb: ((content: string) => void) | null = null;
         const richTextService = {
            showAnnotationDialog: vi.fn().mockImplementation((cb: (content: string) => void) => {
               capturedCb = cb;
               return new Subject<any>().asObservable();
            }),
         };
         const { comp, viewsheetClient } = createTableComponent({ richTextService });
         // addAssemblyAnnotation reads tableContainer for position calculation
         (comp as any).tableContainer = {
            nativeElement: { getBoundingClientRect: vi.fn().mockReturnValue({ left: 0, top: 0 }) },
         };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table annotate title", event: new MouseEvent("click") });
         capturedCb?.("annotation text");

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/annotation/add-assembly-annotation",
            expect.anything(),
         );
      });

      it("'table annotate cell' should add showAnnotationDialog subscription to this.subscriptions", () => {
         const { comp } = createTableComponent({
            richTextService: {
               showAnnotationDialog: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            },
         });
         const actions = createMockActions();
         comp.actions = actions as any;
         const addSpy = vi.spyOn((comp as any).subscriptions, "add");

         actions.onAssemblyActionEvent.next({ id: "table annotate cell", event: new MouseEvent("click") });

         expect(addSpy).toHaveBeenCalled();
      });
   });
});
