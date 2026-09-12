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
 * VSCrosstab — Pass 2: Risk
 *
 * Risk-first coverage of async race conditions and subscription leak paths.
 *
 *   Group 1 — showCrosstabDateComparisonDialog: dual-subscribe chain (getModel → dialog.onClear)
 *   Group 2 — scaleService subscription: constructor wires scale updates
 *   Group 3 — actionSubscription teardown: replaced without leaking on rapid set actions calls
 *   Group 4 — keepScroll state: set by drillClicked, reset by loadTableData
 *   Group 5 — showAnnotateDialog: richTextService.showAnnotationDialog subscription
 */

import { Subject, of } from "rxjs";
import {
   createCrosstabComponent,
   createMockActions,
   makeLoadTableDataCommand,
} from "./vs-crosstab.component.test-helpers";
import { ComponentTool } from "../../../common/util/component-tool";

afterEach(() => vi.restoreAllMocks());

describe("VSCrosstab — Pass 2: Risk", () => {
   // Group 1: showCrosstabDateComparisonDialog is private. Direct access via (comp as any) is
   // necessary because the method is only triggered via the action switch — calling it directly
   // isolates the two chained subscriptions (getModel → dialog.onClear) without mock-routing
   // through the action event infrastructure.
   describe("Group 1 — showCrosstabDateComparisonDialog dual-subscribe", () => {
      it("should call modelService.getModel to fetch the DateComparisonDialogModel", () => {
         const modelService = {
            getModel: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            sendModel: vi.fn(),
         };
         const { comp } = createCrosstabComponent({ modelService });

         (comp as any).showCrosstabDateComparisonDialog(comp.model);

         expect(modelService.getModel).toHaveBeenCalledWith(
            expect.stringContaining("composer/vs/date-comparison-dialog-model"),
         );
      });

      it("should open dialog after getModel resolves", () => {
         const dialogData = { standardPeriods: null, customPeriods: null };
         const modelSubject = new Subject<any>();
         const modelService = {
            getModel: vi.fn().mockReturnValue(modelSubject.asObservable()),
            sendModel: vi.fn(),
         };
         const dialogRef = {
            onClear: new Subject<void>(),
            close: vi.fn(),
            dateComparisonDialogModel: null,
            variableValues: null,
            assemblyName: null,
            runtimeId: null,
            assemblyType: null,
            scriptTreeModel: null,
         };
         // try/finally: ComponentTool.showDialog is a static method — spy must be restored
         // before afterEach so it cannot bleed into the remaining tests of this group.
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef as any);
         try {
            const { comp } = createCrosstabComponent({ modelService });

            (comp as any).showCrosstabDateComparisonDialog(comp.model);
            modelSubject.next(dialogData);

            expect(showDialogSpy).toHaveBeenCalled();
            expect(dialogRef.dateComparisonDialogModel).toBe(dialogData);
         } finally {
            showDialogSpy.mockRestore();
         }
      });

      it("should send clear event and close dialog when onClear fires", () => {
         const modelSubject = new Subject<any>();
         const modelService = {
            getModel: vi.fn().mockReturnValue(modelSubject.asObservable()),
            sendModel: vi.fn(),
         };
         const dialogRef = {
            onClear: new Subject<void>(),
            close: vi.fn(),
            dateComparisonDialogModel: null,
            variableValues: null,
            assemblyName: null,
            runtimeId: null,
            assemblyType: null,
            scriptTreeModel: null,
         };
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef as any);
         try {
            const { comp, viewsheetClient } = createCrosstabComponent({ modelService });

            (comp as any).showCrosstabDateComparisonDialog(comp.model);
            modelSubject.next({});
            dialogRef.onClear.next();

            expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
               expect.stringContaining("date-comparison-dialog-model/clear"),
            );
            expect(dialogRef.close).toHaveBeenCalled();
         } finally {
            showDialogSpy.mockRestore();
         }
      });

      it("should fetch scriptTreeModel via second getModel call after dialog opens", () => {
         const dialogData = {};
         const modelSubject = new Subject<any>();
         const modelService = {
            getModel: vi.fn()
               .mockReturnValueOnce(modelSubject.asObservable())
               .mockReturnValue(new Subject<any>().asObservable()),
            sendModel: vi.fn(),
         };
         const dialogRef = {
            onClear: new Subject<void>(),
            close: vi.fn(),
            dateComparisonDialogModel: null,
            variableValues: null,
            assemblyName: null,
            runtimeId: null,
            assemblyType: null,
            scriptTreeModel: null,
         };
         const showDialogSpy = vi.spyOn(ComponentTool, "showDialog").mockReturnValue(dialogRef as any);
         try {
            const { comp } = createCrosstabComponent({ modelService });

            (comp as any).showCrosstabDateComparisonDialog(comp.model);
            modelSubject.next(dialogData);

            expect(modelService.getModel).toHaveBeenCalledTimes(2);
            expect(modelService.getModel).toHaveBeenCalledWith(
               expect.stringContaining("vsscriptable/scriptTree"),
               expect.any(Object),
            );
         } finally {
            showDialogSpy.mockRestore();
         }
      });
   });

   describe("Group 2 — scaleService subscription", () => {
      it("should update scale field when scaleService emits a new scale", () => {
         const scaleSubject = new Subject<number>();
         const scaleService = { getScale: vi.fn(() => scaleSubject.asObservable()) };
         const { comp } = createCrosstabComponent({ scaleService });

         scaleSubject.next(2);

         expect((comp as any).scale).toBe(2);
      });

      it("should reflect latest scale value after multiple emissions", () => {
         const scaleSubject = new Subject<number>();
         const scaleService = { getScale: vi.fn(() => scaleSubject.asObservable()) };
         const { comp } = createCrosstabComponent({ scaleService });

         scaleSubject.next(1.5);
         scaleSubject.next(3);

         expect((comp as any).scale).toBe(3);
      });
   });

   // Group 3: actionSubscription is a private field on VSCrosstab; it is read via (comp as any)
   // to verify the old Subscription instance is replaced without leaking. There is no public API
   // that exposes the current subscription reference.
   describe("Group 3 — actionSubscription replacement without leak", () => {
      it("should unsubscribe prior actionSubscription when actions is replaced", () => {
         const { comp } = createCrosstabComponent();
         const firstActions = createMockActions();
         comp.actions = firstActions as any;
         const firstSubscription = (comp as any).actionSubscription;
         const unsubscribeSpy = vi.spyOn(firstSubscription, "unsubscribe");

         comp.actions = createMockActions() as any;

         expect(unsubscribeSpy).toHaveBeenCalled();
      });

      it("should not dispatch to old subscription after replacement", () => {
         const { comp } = createCrosstabComponent();
         const firstActions = createMockActions();
         const secondActions = createMockActions();
         comp.actions = firstActions as any;
         comp.actions = secondActions as any;

         const exportSpy = vi.spyOn(comp as any, "exportTable").mockImplementation(() => {});

         firstActions.onAssemblyActionEvent.next({ id: "crosstab export", event: null, model: comp.model });

         expect(exportSpy).not.toHaveBeenCalled();
      });

      it("should null out actionSubscription when actions is set to null", () => {
         const { comp } = createCrosstabComponent();
         comp.actions = createMockActions() as any;

         comp.actions = null;

         expect((comp as any).actionSubscription).toBeNull();
      });
   });

   // Group 4: keepScroll is a private field on VSCrosstab; direct access is the only way to
   // seed its initial value and verify the flip. Both state directions are covered here.
   // The set-to-true path is also covered in Pass 1 Interaction Group 5 (drillClicked);
   // the reset-to-false path is also covered in Pass 1 Interaction Group 3 (loadTableData).
   describe("Group 4 — keepScroll state", () => {
      it("should set keepScroll to true when drillClicked is called", () => {
         const { comp } = createCrosstabComponent();
         const cell = { drillOp: "+", field: "col1" } as any;
         (comp as any).keepScroll = false;

         comp.drillClicked(0, 0, cell, "X");

         expect((comp as any).keepScroll).toBe(true);
      });

      it("should reset keepScroll to false after loadTableData completes", () => {
         const { comp } = createCrosstabComponent();
         (comp as any).keepScroll = true;

         (comp as any).loadTableData(makeLoadTableDataCommand());

         expect((comp as any).keepScroll).toBe(false);
      });

      it("should not reset keepScroll if loadTableData is never called after drill", () => {
         const { comp } = createCrosstabComponent();
         const cell = { drillOp: "+", field: "col1" } as any;

         comp.drillClicked(0, 0, cell, "X");

         expect((comp as any).keepScroll).toBe(true);
      });
   });

   // Group 5: showAnnotateDialog is private; (comp as any) access exposes it so we can verify
   // the richTextService subscription contract without routing through the action event plumbing.
   describe("Group 5 — showAnnotateDialog richTextService subscription", () => {
      it("should call richTextService.showAnnotationDialog", () => {
         const dialogSubject = new Subject<any>();
         const richTextService = {
            showAnnotationDialog: vi.fn().mockReturnValue(dialogSubject.asObservable()),
         };
         const { comp } = createCrosstabComponent({ richTextService } as any);
         // getSelectedCell accesses @ViewChildren(VSTableCell) which is undefined without
         // Angular rendering; stub it so the subscription chain under test is reachable.
         vi.spyOn(comp as any, "getSelectedCell").mockReturnValue(null);

         (comp as any).showAnnotateDialog({ target: null, clientX: 0, clientY: 0 } as any);

         expect(richTextService.showAnnotationDialog).toHaveBeenCalled();
      });
   });
});
