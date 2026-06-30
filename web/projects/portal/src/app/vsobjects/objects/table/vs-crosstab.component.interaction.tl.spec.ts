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
 * VSCrosstab — Pass 1: Interaction
 *
 * Covers observable wiring, lifecycle, command dispatch, and action-switch routing.
 *
 *   Group 1 — ngOnInit: subscribes to scrollTop/scrollLeft, clearSelection, tabDeselect
 *   Group 2 — ngOnDestroy: unsubscribes actionSubscription
 *   Group 3 — loadTableData (LoadTableDataCommand): populates table, loadedRows, keepScroll reset
 *   Group 4 — set actions: key action-switch cases dispatch to correct handlers;
 *                          onOpenConditionDialog emit (Bug #17211)
 *   Group 5 — drillClicked: sets keepScroll=true, sends CROSSTAB_DRILL_URI
 *   Group 6 — drillAction: sends CROSSTAB_ACTION_DRILL
 *   Group 7 — hideColumn / showColumns: send ShowHideCrosstabColumnsEvent
 *   Group 8 — processClearSelectionCommand: delegates to clearSelection
 *   Group 9 — showPagingControl: calls pagingControlService on mobile; no-op otherwise
 */

import { Subject } from "rxjs";
import { ViewsheetCommandMessage } from "../../../common/viewsheet-client/viewsheet-command-message";
import {
   createCrosstabComponent,
   createMockActions,
   makeLoadTableDataCommand,
   makeMockCrosstabModel,
   makeTableCell,
} from "./vs-crosstab.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSCrosstab — Pass 1: Interaction", () => {
   describe("Group 1 — ngOnInit", () => {
      it("should subscribe to pagingControlService.scrollTop and scrollLeft", () => {
         const pagingControlService = {
            setPagingControlModel: vi.fn(),
            scrollTop: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            scrollLeft: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            getCurrentAssembly: vi.fn().mockReturnValue("Crosstab1"),
         };
         const { comp } = createCrosstabComponent({ pagingControlService });

         comp.ngOnInit();

         expect(pagingControlService.scrollTop).toHaveBeenCalled();
         expect(pagingControlService.scrollLeft).toHaveBeenCalled();
      });

      it("should clear selection on ngOnInit", () => {
         const { comp } = createCrosstabComponent();
         comp.model.selectedHeaders = new Map([[0, [0]]]);
         comp.model.firstSelectedRow = 0;
         comp.model.firstSelectedColumn = 0;

         comp.ngOnInit();

         expect(comp.model.firstSelectedRow).toBe(-1);
         expect(comp.model.firstSelectedColumn).toBe(-1);
      });

      it("should clear selection when tabDeselected fires with matching assembly name", () => {
         const tabDeselectedSubject = new Subject<string>();
         const tabService = { tabDeselected: tabDeselectedSubject.asObservable() };
         const { comp } = createCrosstabComponent({ tabService });
         comp.model.firstSelectedRow = 2;

         comp.ngOnInit();
         tabDeselectedSubject.next("Crosstab1");

         expect(comp.model.firstSelectedRow).toBe(-1);
      });

      it("should not clear selection when tabDeselected fires with different name", () => {
         const tabDeselectedSubject = new Subject<string>();
         const tabService = { tabDeselected: tabDeselectedSubject.asObservable() };
         const { comp } = createCrosstabComponent({ tabService });
         comp.ngOnInit();

         const selectedHeadersBefore = new Map([[0, [0]]]);
         comp.model.selectedHeaders = selectedHeadersBefore;
         comp.model.firstSelectedRow = 2;

         tabDeselectedSubject.next("OtherAssembly");

         expect(comp.model.firstSelectedRow).toBe(2);
      });
   });

   describe("Group 2 — ngOnDestroy", () => {
      it("should unsubscribe actionSubscription on ngOnDestroy", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const actionSubscription = (comp as any).actionSubscription;
         const unsubscribeSpy = vi.spyOn(actionSubscription, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubscribeSpy).toHaveBeenCalled();
      });

      it("should set actionSubscription to null after ngOnDestroy", () => {
         const { comp } = createCrosstabComponent();
         comp.actions = createMockActions() as any;

         comp.ngOnDestroy();

         expect((comp as any).actionSubscription).toBeNull();
      });

      it("should tolerate ngOnDestroy when no actions set", () => {
         const { comp } = createCrosstabComponent();

         expect(() => comp.ngOnDestroy()).not.toThrow();
      });
   });

   // Group 3: loadTableData is protected — accessed via (comp as any) because the command
   // handler is called by name from CommandProcessor at runtime with no public wrapper.
   // keepScroll is a private field on VSCrosstab; there is no public setter, so it is seeded
   // directly to isolate the reset-to-false behavior from the drill trigger path.
   // The complementary set-to-true path is tested in Group 5 of this file.
   describe("Group 3 — loadTableData (LoadTableDataCommand)", () => {
      it("should set loadedRows from command start/end", () => {
         const { comp } = createCrosstabComponent();
         const cmd = makeLoadTableDataCommand({ start: 5, end: 20 });

         (comp as any).loadTableData(cmd);

         expect((comp as any).loadedRows).toEqual({ start: 5, end: 20 });
      });

      it("should populate comp.table from command.tableCells", () => {
         const { comp } = createCrosstabComponent();
         const cell = makeTableCell({ row: 1, col: 1, cellData: "42" });
         const cmd = makeLoadTableDataCommand({
            tableCells: [
               [makeTableCell({ row: 0, col: 0 }), makeTableCell({ row: 0, col: 1 }), makeTableCell({ row: 0, col: 2 })],
               [makeTableCell({ row: 1, col: 0 }), cell, makeTableCell({ row: 1, col: 2 })],
            ],
         });

         (comp as any).loadTableData(cmd);

         expect(comp.table[1][1].cellData).toBe("42");
      });

      it("should populate ltTable with header rows when command.start == 0", () => {
         const { comp } = createCrosstabComponent({
            model: { headerRowCount: 1, headerColCount: 1 },
         });
         const headerCell = makeTableCell({ row: 0, col: 0, cellData: "Header" });
         const cmd = makeLoadTableDataCommand({
            start: 0,
            tableCells: [
               [headerCell, makeTableCell({ row: 0, col: 1 }), makeTableCell({ row: 0, col: 2 })],
               [makeTableCell({ row: 1, col: 0 }), makeTableCell({ row: 1, col: 1 }), makeTableCell({ row: 1, col: 2 })],
            ],
         });

         (comp as any).loadTableData(cmd);

         expect(comp.ltTable[0][0].cellData).toBe("Header");
      });

      it("should set keepScroll to false after loading data", () => {
         const { comp } = createCrosstabComponent();
         (comp as any).keepScroll = true;

         (comp as any).loadTableData(makeLoadTableDataCommand());

         expect((comp as any).keepScroll).toBe(false);
      });

      it("should set model.cells to the loaded table", () => {
         const { comp } = createCrosstabComponent();
         const cmd = makeLoadTableDataCommand();

         (comp as any).loadTableData(cmd);

         expect(comp.model.cells).toEqual(comp.table);
      });

      // E6: verify CommandProcessor routing wiring — commandsSubject → processLoadTableDataCommand.
      // Subscription is established in the constructor (not ngOnInit), so no lifecycle call is needed.
      // processLoadTableDataCommand uses setTimeout(0) internally, so we spy on it rather than
      // asserting on the async end-result (loadedRows), to keep the test synchronous.
      it("should route LoadTableDataCommand via commandsSubject to processLoadTableDataCommand", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();
         const spy = vi.spyOn(comp as any, "processLoadTableDataCommand").mockImplementation(() => {});
         try {
            const cmd = makeLoadTableDataCommand({ start: 3, end: 7 });
            viewsheetClient.commandsSubject.next(
               new ViewsheetCommandMessage("Crosstab1", "LoadTableDataCommand", cmd),
            );
            expect(spy).toHaveBeenCalledWith(cmd);
         } finally {
            spy.mockRestore();
         }
      });
   });

   describe("Group 4 — set actions: action-switch routing", () => {
      it("should toggle multiSelect when 'crosstab multi-select' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.model.multiSelect = false;
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "crosstab multi-select", event: null, model: comp.model });

         expect(comp.model.multiSelect).toBe(true);
      });

      it("should call exportTable when 'crosstab export' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "exportTable").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab export", event: null, model: comp.model });

         expect(spy).toHaveBeenCalled();
      });

      it("should call drillAction when 'crosstab drilldown' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "drillAction").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab drilldown", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith();
      });

      it("should call drillAction(true) when 'crosstab drillup' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "drillAction").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab drillup", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(true);
      });

      it("should call showCrosstabDateComparisonDialog when 'crosstab date-comparison' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "showCrosstabDateComparisonDialog").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab date-comparison", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(comp.model);
      });

      it("should call hideColumn when 'crosstab hide column' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "hideColumn").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab hide column", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(comp.model);
      });

      it("should call showColumns when 'crosstab show columns' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "showColumns").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "crosstab show columns", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(comp.model);
      });

      it("should emit onOpenFormatPane when 'crosstab show-format-pane' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const emitSpy = vi.spyOn(comp.onOpenFormatPane, "emit");

         actions.onAssemblyActionEvent.next({ id: "crosstab show-format-pane", event: null, model: comp.model });

         expect(emitSpy).toHaveBeenCalledWith(comp.model);
      });

      it("should call drillHandle when 'expand all' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "drillHandle").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "expand all", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(comp.model);
      });

      it("should call drillHandle(model, true) when 'collapse all' fires", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const spy = vi.spyOn(comp as any, "drillHandle").mockImplementation(() => {});

         actions.onAssemblyActionEvent.next({ id: "collapse all", event: null, model: comp.model });

         expect(spy).toHaveBeenCalledWith(comp.model, true);
      });

      it("should replace prior actionSubscription when actions is set twice", () => {
         const { comp } = createCrosstabComponent();
         const firstActions = createMockActions();
         const secondActions = createMockActions();
         const multiSelectSpy = vi.spyOn(comp as any, "showDetails").mockImplementation(() => {});

         comp.actions = firstActions as any;
         comp.actions = secondActions as any;

         firstActions.onAssemblyActionEvent.next({ id: "crosstab show-details", event: null, model: comp.model });

         expect(multiSelectSpy).not.toHaveBeenCalled();
      });

      // Bug #17211 — condition action must emit onOpenConditionDialog with the model.
      it("should emit onOpenConditionDialog when 'crosstab conditions' fires (Bug #17211)", () => {
         const { comp } = createCrosstabComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const emitSpy = vi.spyOn(comp.onOpenConditionDialog, "emit");

         actions.onAssemblyActionEvent.next({ id: "crosstab conditions", event: null, model: comp.model });

         expect(emitSpy).toHaveBeenCalledWith(comp.model);
      });
   });

   // Group 5: keepScroll set-to-true is tested here; the complementary reset-to-false path
   // is in Group 3 (loadTableData) of this file and in Pass 2 Risk Group 4.
   describe("Group 5 — drillClicked", () => {
      it("should set keepScroll to true before sending drill event", () => {
         const { comp } = createCrosstabComponent();
         const cell = makeTableCell({ drillOp: "+", field: "col1" });
         (comp as any).keepScroll = false;

         comp.drillClicked(1, 0, cell, "X");

         expect((comp as any).keepScroll).toBe(true);
      });

      it("should send event to CROSSTAB_DRILL_URI with correct assembly name", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();
         const cell = makeTableCell({ drillOp: "+", field: "col1" });

         comp.drillClicked(1, 0, cell, "X");

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/drill",
            expect.objectContaining({ assemblyName: "Crosstab1" }),
         );
      });
   });

   describe("Group 6 — drillAction", () => {
      it("should send event to CROSSTAB_ACTION_DRILL", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();

         (comp as any).drillAction();

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/crosstab/action/drill",
            expect.any(Object),
         );
      });

      it("should pass isDrillUp=true when called with true", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();

         (comp as any).drillAction(true);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/crosstab/action/drill",
            expect.objectContaining({ drillUp: true }),
         );
      });
   });

   // Group 7: hideColumn/showColumns are private — accessed via (comp as any) because the
   // public API surface is via the actions switch; private access avoids routing through the
   // full action plumbing for the lower-level event contracts.
   // loadedRows is a protected field on BaseTable with no public setter; it is seeded directly
   // because hideColumn reads it to derive the list of selected column indices.
   describe("Group 7 — hideColumn / showColumns", () => {
      it("should send ShowHideCrosstabColumnsEvent(show=false) when hideColumn is called with selectedData", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();
         const model = makeMockCrosstabModel();
         model.selectedData = new Map([[0, [1, 2]]]);
         model.selectedHeaders = null;
         (comp as any).loadedRows = { start: 0, end: 2 };

         (comp as any).hideColumn(model);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/table/showHideColumns",
            expect.objectContaining({ showColumns: false }),
         );
      });

      it("should send ShowHideCrosstabColumnsEvent(show=true) when showColumns is called", () => {
         const { comp, viewsheetClient } = createCrosstabComponent();
         const model = makeMockCrosstabModel();

         (comp as any).showColumns(model);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/composer/viewsheet/table/showHideColumns",
            expect.objectContaining({ showColumns: true }),
         );
      });

      it("should clear selectedRegions/selectedHeaders/selectedData after hideColumn", () => {
         const { comp } = createCrosstabComponent();
         const model = makeMockCrosstabModel();
         model.selectedData = new Map([[0, [1]]]);
         (comp as any).loadedRows = { start: 0, end: 2 };

         (comp as any).hideColumn(model);

         expect(model.selectedRegions).toBeNull();
         expect(model.selectedHeaders).toBeNull();
         expect(model.selectedData).toBeNull();
      });
   });

   describe("Group 8 — processClearSelectionCommand", () => {
      it("should call clearSelection when processClearSelectionCommand is called", () => {
         const { comp } = createCrosstabComponent();
         comp.model.firstSelectedRow = 2;
         comp.model.firstSelectedColumn = 1;

         comp.processClearSelectionCommand({} as any);

         expect(comp.model.firstSelectedRow).toBe(-1);
         expect(comp.model.firstSelectedColumn).toBe(-1);
      });
   });

   describe("Group 9 — showPagingControl", () => {
      it("should call setPagingControlModel when mobileDevice is true", () => {
         const { comp, pagingControlService } = createCrosstabComponent();
         comp.mobileDevice = true;

         comp.showPagingControl();

         expect(pagingControlService.setPagingControlModel).toHaveBeenCalledWith(
            expect.objectContaining({ assemblyName: "Crosstab1", enabled: true }),
         );
      });

      it("should not call setPagingControlModel when mobileDevice is false", () => {
         const { comp, pagingControlService } = createCrosstabComponent();
         comp.mobileDevice = false;

         comp.showPagingControl();

         expect(pagingControlService.setPagingControlModel).not.toHaveBeenCalled();
      });
   });
});
