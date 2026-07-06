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
 * VSCalcTable - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - input setters and action dispatch fan-out.
 *   Group 2 [Risk 3] - selection paths, title selection, and viewer guards.
 *   Group 3 [Risk 2] - table command loading, annotation dispatch, and scroll state.
 *   Group 4 [Risk 2] - layout helpers, width/scroll calculations, and teardown cleanup.
 *
 * Out of scope:
 *   Template render fidelity and shared BaseTable behaviors already covered by VSTable specs.
 *
 * Mocking strategy:
 *   - direct instantiation for the deep constructor and inherited BaseTable wiring
 *   - ViewChild refs are seeded before assigning model so updateLayout() can run safely
 */

import { of } from "rxjs";
import { VSUtil } from "../../util/vs-util";
import {
   createCalcTableComponent,
   createMockCalcTableActions,
   makeCalcTableCell,
   makeCalcTableLoadCommand,
} from "./vs-calctable.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSCalcTable - Pass 1: Interaction", () => {
   describe("Group 1 - setters and actions", () => {
      it("should call updateLayout(false) when model setter receives a value", () => {
         const { comp } = createCalcTableComponent();
         // updateLayout is private — spy via any cast to verify the model setter triggers it
         const updateLayoutSpy = vi.spyOn(comp as any, "updateLayout").mockImplementation(() => {});

         comp.model = { ...comp.model, absoluteName: "CalcTable2" } as any;

         expect(updateLayoutSpy).toHaveBeenCalledWith(false);
      });

      it("should return toolbar actions from getToolbarActions", () => {
         const { comp } = createCalcTableComponent();
         const actions = createMockCalcTableActions();
         comp.actions = actions as any;

         expect(comp.getToolbarActions()).toEqual(actions.toolbarActions);
      });

      it("should unsubscribe the previous actions subscription when actions setter runs twice", () => {
         const { comp } = createCalcTableComponent();
         const firstActions = createMockCalcTableActions();
         comp.actions = firstActions as any;
         // actionSubscription is private — cast needed to verify the prior subscription is torn down on replacement
         const unsubscribeSpy = vi.spyOn((comp as any).actionSubscription, "unsubscribe");

         comp.actions = createMockCalcTableActions() as any;

         expect(unsubscribeSpy).toHaveBeenCalled();
      });

      it("should fan out action events to the expected handlers", () => {
         const { comp } = createCalcTableComponent();
         const actions = createMockCalcTableActions();
         const event = new MouseEvent("click", { clientX: 90, clientY: 110 });
         // openEditPane, exportTable, showAnnotateDialog, addFilter, openHighlightDialog,
         // openConditionDialog are all private — spy via any cast to verify action routing
         const openEditPaneSpy = vi.spyOn(comp as any, "openEditPane").mockImplementation(() => {});
         const exportTableSpy = vi.spyOn(comp as any, "exportTable").mockImplementation(() => {});
         const showDetailsSpy = vi.spyOn(comp, "showDetails").mockImplementation(() => {});
         const showAnnotateDialogSpy = vi.spyOn(comp as any, "showAnnotateDialog")
            .mockReturnValue(of({} as any));
         const addFilterSpy = vi.spyOn(comp as any, "addFilter").mockImplementation(() => {});
         const openHighlightDialogSpy = vi.spyOn(comp as any, "openHighlightDialog")
            .mockImplementation(() => {});
         const openConditionDialogSpy = vi.spyOn(comp as any, "openConditionDialog")
            .mockImplementation(() => {});
         const openMaxModeSpy = vi.spyOn(comp, "openMaxMode").mockImplementation(() => {});
         const closeMaxModeSpy = vi.spyOn(comp, "closeMaxMode").mockImplementation(() => {});
         const openCellSizeDialogSpy = vi.spyOn(comp, "openCellSizeDialog").mockImplementation(() => {});
         const showDropdownMenusSpy = vi.spyOn(VSUtil, "showDropdownMenus").mockImplementation(() => {});
         const emittedModels: any[] = [];
         comp.onOpenFormatPane.subscribe(model => emittedModels.push(model));
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "calc-table edit", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table export", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table show-details", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table annotate", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table filter", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table highlight", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table conditions", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table open-max-mode", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table close-max-mode", event });
         actions.onAssemblyActionEvent.next({ id: "menu actions", event });
         actions.onAssemblyActionEvent.next({ id: "table cell size", event });
         actions.onAssemblyActionEvent.next({ id: "calc-table show-format-pane", event });
         actions.onAssemblyActionEvent.next({ id: "more actions", event });

         expect(openEditPaneSpy).toHaveBeenCalled();
         expect(exportTableSpy).toHaveBeenCalled();
         expect(showDetailsSpy).toHaveBeenCalled();
         expect(showAnnotateDialogSpy).toHaveBeenCalledWith(event);
         expect(addFilterSpy).toHaveBeenCalledWith(event, expect.objectContaining({ left: 20, top: 30 }));
         expect(openHighlightDialogSpy).toHaveBeenCalled();
         expect(openConditionDialogSpy).toHaveBeenCalled();
         expect(openMaxModeSpy).toHaveBeenCalled();
         expect(closeMaxModeSpy).toHaveBeenCalled();
         expect(showDropdownMenusSpy).toHaveBeenNthCalledWith(
            1,
            event,
            actions.menuActions,
            expect.anything(),
         );
         expect(openCellSizeDialogSpy).toHaveBeenCalledWith(expect.anything());
         expect(emittedModels).toEqual([comp.model]);
         expect(showDropdownMenusSpy).toHaveBeenNthCalledWith(
            2,
            event,
            [{ id: "more-action" }],
            expect.anything(),
            [],
         );
      });

      it("should toggle model.multiSelect from false to true when calc-table multi-select fires", () => {
         const { comp } = createCalcTableComponent();
         const actions = createMockCalcTableActions();
         comp.model.multiSelect = false;
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({
            id: "calc-table multi-select",
            event: new MouseEvent("click"),
         });

         expect(comp.model.multiSelect).toBe(true);
      });

      it("should toggle model.multiSelect from true to false when calc-table multi-select fires again", () => {
         const { comp } = createCalcTableComponent();
         const actions = createMockCalcTableActions();
         comp.model.multiSelect = true;
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({
            id: "calc-table multi-select",
            event: new MouseEvent("click"),
         });

         expect(comp.model.multiSelect).toBe(false);
      });
   });

   describe("Group 2 - selection flows", () => {
      it("should add a selected cell on plain click and emit onRefreshFormat", () => {
         const { comp, dataTipService, changeDetectorRef } = createCalcTableComponent({
            model: { dataTip: "tip1" },
         });
         const cell = makeCalcTableCell({ row: 2, col: 1, dataPath: { path: ["A"] } as any });
         const refreshEvents: MouseEvent[] = [];
         comp.selected = true;
         comp.onRefreshFormat.subscribe(evt => refreshEvents.push(evt));

         const event = {
            button: 0,
            ctrlKey: false,
            shiftKey: false,
            stopPropagation: vi.fn(),
         } as any;

         comp.selectCell(event, cell);

         expect(comp.model.selectedData).toEqual(new Map([[2, [1]]]));
         expect(comp.model.firstSelectedRow).toBe(2);
         expect(comp.model.firstSelectedColumn).toBe(1);
         expect(comp.model.lastSelected).toEqual({ row: 2, column: 1 });
         expect(comp.model.selectedRegions).toEqual([
            expect.objectContaining({ path: ["A"], bindingType: 1 }),
         ]);
         expect((event as any).ignoreClick).toBe(true);
         expect(refreshEvents).toEqual([event]);
         expect(dataTipService.freeze).toHaveBeenCalled();
         expect(changeDetectorRef.detectChanges).toHaveBeenCalled();
      });

      it("should deselect an already-selected cell on ctrl click", () => {
         const { comp } = createCalcTableComponent();
         const cell = makeCalcTableCell({ row: 1, col: 1 });
         comp.selected = true;
         comp.model.selectedData = new Map([[1, [1]]]);

         comp.selectCell({
            button: 0,
            ctrlKey: true,
            shiftKey: false,
            stopPropagation: vi.fn(),
         } as any, cell);

         expect(comp.model.selectedData).toEqual(new Map());
      });

      it("should select a rectangular range on shift click", () => {
         const { comp } = createCalcTableComponent();
         comp.selected = true;
         // loadedRows is private — tracks the server-loaded row window; seed directly to skip loadTableData
         (comp as any).loadedRows = { start: 0, end: 2 };
         comp.table = [
            [
               makeCalcTableCell({ row: 0, col: 0, dataPath: { path: ["r0c0"] } as any }),
               makeCalcTableCell({ row: 0, col: 1, dataPath: { path: ["r0c1"] } as any }),
            ],
            [
               makeCalcTableCell({ row: 1, col: 0, dataPath: { path: ["r1c0"] } as any }),
               makeCalcTableCell({ row: 1, col: 1, dataPath: { path: ["r1c1"] } as any }),
            ],
         ];
         comp.model.lastSelected = { row: 0, column: 0 };

         comp.selectCell({
            button: 0,
            ctrlKey: false,
            shiftKey: true,
            stopPropagation: vi.fn(),
         } as any, comp.table[1][1]);

         expect(comp.model.selectedData).toEqual(new Map([
            [0, [0, 1]],
            [1, [0, 1]],
         ]));
         expect(comp.model.selectedRegions).toHaveLength(4);
      });

      it("should send flyover for selections when the model supports flyover", () => {
         const { comp } = createCalcTableComponent({
            model: { hasFlyover: true },
         });
         const sendFlyoverSpy = vi.spyOn(comp, "sendFlyover").mockImplementation(() => {});
         comp.selected = true;

         comp.selectCell({
            button: 0,
            ctrlKey: false,
            shiftKey: false,
            stopPropagation: vi.fn(),
         } as any, makeCalcTableCell({ row: 0, col: 0 }));

         expect((comp as any).flyoverCellSelected).toBe(true);
         expect(sendFlyoverSpy).toHaveBeenCalledWith(new Map([[0, [0]]]));
      });

      it("should return early for viewer-disabled cells", () => {
         const { comp, changeDetectorRef } = createCalcTableComponent({
            model: { enabled: false },
         });
         comp.selected = true;
         const cell = makeCalcTableCell({ row: 0, col: 0 });

         comp.selectCell({
            button: 0,
            ctrlKey: false,
            shiftKey: false,
            stopPropagation: vi.fn(),
         } as any, cell);

         expect(comp.model.selectedData).toBeNull();
         expect(changeDetectorRef.detectChanges).not.toHaveBeenCalled();
      });

      it("should clear cell selection and add the title data path on selectTitle", () => {
         const { comp, dataTipService } = createCalcTableComponent({
            contextProvider: {
               viewer: false,
               preview: false,
               binding: false,
               composer: true,
               vsWizardPreview: false,
               vsWizard: false,
               embedAssembly: false,
            },
         });
         comp.selected = true;
         comp.model.selectedData = new Map([[1, [1]]]);

         comp.selectTitle(new MouseEvent("click"));

         expect(comp.model.selectedData).toBeNull();
         expect(comp.model.titleSelected).toBe(true);
         expect(comp.model.selectedRegions).toEqual([
            expect.objectContaining({
               row: true,
               type: 16384,
            }),
         ]);
         expect(dataTipService.unfreeze).toHaveBeenCalled();
      });
   });

   describe("Group 3 - command loading and annotation", () => {
      it("should send max-mode events and reload data from the current loaded row", () => {
         const { comp, viewsheetClient, dataTipService } = createCalcTableComponent();
         const updateDataSpy = vi.spyOn(comp as any, "updateData").mockImplementation(() => {});
         (comp as any).loadedRows = { start: 5, end: 10 };

         comp.openMaxMode();
         comp.closeMaxMode();

         expect(viewsheetClient.sendEvent).toHaveBeenNthCalledWith(
            1,
            "/events/vstable/toggle-max-mode",
            expect.objectContaining({ tableName: "CalcTable1" }),
         );
         expect(viewsheetClient.sendEvent).toHaveBeenNthCalledWith(
            2,
            "/events/vstable/toggle-max-mode",
            expect.objectContaining({ tableName: "CalcTable1" }),
         );
         expect(updateDataSpy).toHaveBeenNthCalledWith(1, 5);
         expect(updateDataSpy).toHaveBeenNthCalledWith(2, 5);
         expect(dataTipService.hideDataTip).toHaveBeenCalledTimes(2);
      });

      it("should load table rows, header rows, and transpose data from loadTableData", () => {
         const { comp } = createCalcTableComponent();
         const command = makeCalcTableLoadCommand();
         comp.currentRow = 5;
         // loadedRows is private — tracks the server-loaded row window
         (comp as any).loadedRows = { start: 0, end: 0 };
         (comp as any).verticalScrollWrapper.nativeElement.scrollTop = 33;

         // loadTableData is private — it is the STOMP handler for LoadTableDataCommand
         (comp as any).loadTableData(command);

         expect((comp as any).loadedRows).toEqual({ start: 0, end: 1 });
         expect(comp.table).toBe(command.tableCells);
         expect(comp.tableHeaderRows).toEqual([command.tableCells[0]]);
         expect(comp.tableTranspose).toEqual([
            [command.tableCells[0][0], command.tableCells[1][0]],
         ]);
         expect((comp as any).verticalScrollWrapper.nativeElement.scrollTop).toBe(0);
      });

      it("should replace tableHeaderRows from command.tableHeaderCells for non-zero starts", () => {
         const { comp } = createCalcTableComponent();
         const nextHeaders = [[
            makeCalcTableCell({ row: 0, col: 0, cellLabel: "X" }),
            makeCalcTableCell({ row: 0, col: 1, cellLabel: "Y" }),
         ]];

         // loadTableData is private — it is the STOMP handler for LoadTableDataCommand
         (comp as any).loadTableData(makeCalcTableLoadCommand({ start: 1, end: 2, tableHeaderCells: nextHeaders }));

         expect(comp.tableHeaderRows).toEqual(nextHeaders);
      });

      it("should compute the header max row from row spans", () => {
         const { comp } = createCalcTableComponent();
         comp.table = [[
            makeCalcTableCell({ row: 0, col: 0, rowSpan: 2 }),
            makeCalcTableCell({ row: 0, col: 1, rowSpan: 1 }),
         ]];

         // getHeaderMaxRow is private — no public wrapper; invoked to verify span-based row count
         expect((comp as any).getHeaderMaxRow()).toBe(2);
      });

      it("should send a cell annotation event when a rendered selected cell exists", () => {
         const { comp, viewsheetClient } = createCalcTableComponent();
         comp.model.selectedData = new Map([[2, [1]]]);
         // cells is private — represents rendered cell DOM refs; seed directly to simulate a selected cell
         (comp as any).cells = [{
            isRendered: true,
            selected: true,
            cell: { row: 2, col: 1 },
            boundingClientRect: { left: 60, top: 80, width: 40, height: 20 },
         }];

         // addAnnotation is private — it is dispatched via the annotate action; call directly to test branching
         (comp as any).addAnnotation("note", new MouseEvent("click"));

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/annotation/add-data-annotation",
            expect.objectContaining({ content: "note", row: 2, col: 1 }),
         );
      });

      it("should send an assembly annotation event when no selected cell is available", () => {
         const { comp, viewsheetClient } = createCalcTableComponent();
         comp.model.selectedData = null;

         // addAnnotation is private — call directly to test the fallback (no selected cell) branch
         (comp as any).addAnnotation("note", new MouseEvent("click", { clientX: 140, clientY: 150 }));

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/annotation/add-assembly-annotation",
            expect.objectContaining({ content: "note" }),
         );
      });
   });

   describe("Group 4 - layout and helper calculations", () => {
      it("should partition annotations and refresh layout state in updateLayout", () => {
         const { comp } = createCalcTableComponent({
            model: {
               shrink: true,
               dataAnnotationModels: [
                  { row: 0, col: 0 },
                  { row: 2, col: 0 },
                  { row: 0, col: 2 },
                  { row: 2, col: 2 },
               ] as any,
            },
         });
         const setupVerticalScrollWrapperSpy = vi.spyOn(comp, "setupVerticalScrollWrapper");
         // updateVisibleRows, updateVisibleCols, positionDataAnnotations, sumColWidths, resetRowResize
         // are all private — spy via any cast to verify updateLayout orchestrates them
         const updateVisibleRowsSpy = vi.spyOn(comp as any, "updateVisibleRows").mockImplementation(() => {});
         const updateVisibleColsSpy = vi.spyOn(comp as any, "updateVisibleCols").mockImplementation(() => {});
         const positionDataAnnotationsSpy = vi.spyOn(comp as any, "positionDataAnnotations")
            .mockReturnValue(true);
         const sumColWidthsSpy = vi.spyOn(comp as any, "sumColWidths").mockImplementation(() => {});
         const resetRowResizeSpy = vi.spyOn(comp as any, "resetRowResize");

         // updateLayout is private — call directly to test its orchestration
         (comp as any).updateLayout(true);

         expect(setupVerticalScrollWrapperSpy).toHaveBeenCalled();
         expect(comp.model.sortInfo).toEqual({ sortable: false });
         expect(updateVisibleRowsSpy).toHaveBeenCalledWith(true);
         expect(updateVisibleColsSpy).toHaveBeenCalled();
         expect(positionDataAnnotationsSpy).toHaveBeenCalled();
         expect(comp.model.leftTopAnnotations).toHaveLength(1);
         expect(comp.model.leftBottomAnnotations).toHaveLength(1);
         expect(comp.model.rightTopAnnotations).toHaveLength(1);
         expect(comp.model.rightBottomAnnotations).toHaveLength(1);
         expect(sumColWidthsSpy).toHaveBeenCalled();
         expect(resetRowResizeSpy).toHaveBeenCalled();
      });

      it("should calculate vertical scroll wrapper metrics from the table dimensions", () => {
         const { comp } = createCalcTableComponent();

         comp.setupVerticalScrollWrapper();

         expect(comp.verticalScrollWrapperHeight).toBe(136);
         expect(comp.verticalScrollWrapperTop).toBe(44);
      });

      it("should update horizontal and vertical wrappers in wheelScrollHandler", () => {
         const { comp } = createCalcTableComponent();
         const preventDefault = vi.fn();

         comp.wheelScrollHandler({ shiftKey: true, deltaY: 12, preventDefault });
         comp.wheelScrollHandler({ shiftKey: false, deltaX: 7, deltaY: 9, preventDefault });

         expect((comp as any).horizontalScrollWrapper.nativeElement.scrollLeft).toBe(19);
         expect((comp as any).verticalScrollWrapper.nativeElement.scrollTop).toBe(9);
         expect(preventDefault).toHaveBeenCalledTimes(2);
      });

      it("should derive cell widths and visible colspan from column widths", () => {
         const { comp } = createCalcTableComponent({
            model: { colWidths: [80, 0, 100], colCount: 3, headerColCount: 1 },
         });
         comp.displayColWidths = [80, 0, 100];

         const hiddenCell = makeCalcTableCell({ col: 1, colSpan: 1 });
         const spanningCell = makeCalcTableCell({ col: 0, colSpan: 3 });

         expect(comp.getCellWidth(1, hiddenCell)).toBe(0);
         expect(comp.getCellColSpan(spanningCell, 0)).toBe(2);
         expect(comp.getColGroupColWidths()).toEqual([80, 0, 100]);
      });

      it("should return true from isSelected when the cell row/col are in selectedData", () => {
         const { comp } = createCalcTableComponent();
         comp.model.selectedData = new Map([[1, [1]]]);
         expect(comp.isSelected(makeCalcTableCell({ row: 1, col: 1 }))).toBe(true);
      });

      it("should return true from isHyperlink when the cell has hyperlinks and underline set", () => {
         const { comp } = createCalcTableComponent();
         expect(comp.isHyperlink(makeCalcTableCell({
            hyperlinks: [{ label: "x" } as any],
            underline: true,
         }))).toBe(true);
      });

      it("should compute horizontalScrollWidth as the sum of data-column widths (excluding header columns)", () => {
         const { comp } = createCalcTableComponent({ model: { headerColCount: 1 } });
         comp.displayColWidths = [80, 90, 100];
         expect(comp.horizontalScrollWidth).toBe(190);
      });

      it("should report horizontalScrollbarWidth and isFullHorizontalWrapper from scroll wrapper dimensions", () => {
         const { comp } = createCalcTableComponent();
         comp.displayColWidths = [80, 90, 100];
         // checkScroll is private — it synchronises scroll metrics from the DOM refs seeded in test-helpers
         (comp as any).checkScroll();
         expect(comp.horizontalScrollbarWidth).toBe(220);
         expect(comp.isFullHorizontalWrapper).toBe(false);
      });

      it("should compute getDetailTableTopPosition as topRowHeight minus scrollY when rows are loaded mid-scroll", () => {
         const { comp } = createCalcTableComponent();
         comp.topRowHeight = 40;
         comp.scrollY = 15;
         // loadedRows is private — when start > 0 the formula is: -scrollY + topRowHeight = 25
         (comp as any).loadedRows = { start: 2, end: 3 };
         expect(comp.getDetailTableTopPosition()).toBe(25);
      });

      it("should return the cell data path from decorateDataPath", () => {
         const { comp } = createCalcTableComponent();
         // decorateDataPath is private — no public wrapper; verify via any cast
         expect((comp as any).decorateDataPath(makeCalcTableCell({ bindingType: 7 })))
            .toEqual(expect.objectContaining({ bindingType: 7 }));
      });

      it("should call setPagingControlModel with the correct shape on showPagingControl", () => {
         const { comp, pagingControlService } = createCalcTableComponent({
            model: { scrollHeight: 200 },
         });
         comp.displayColWidths = [80, 90, 100];
         // loadedRows is private — seed directly to skip loadTableData round-trip
         (comp as any).loadedRows = { start: 2, end: 3 };
         comp.topRowHeight = 40;
         comp.scrollX = 10;
         comp.scrollY = 15;
         // checkScroll is private — it synchronises scroll metrics from the DOM refs seeded in test-helpers
         (comp as any).checkScroll();
         comp.mobileDevice = true;

         comp.showPagingControl();

         expect(pagingControlService.setPagingControlModel).toHaveBeenCalledWith(
            expect.objectContaining({
               assemblyName: "CalcTable1",
               viewportWidth: 220,
               viewportHeight: comp.verticalScrollWrapperHeight,
               contentWidth: 190,
               contentHeight: 200,
               scrollTop: 15,
               scrollLeft: 10,
            }),
         );
      });

      it("should unsubscribe owned subscriptions on destroy", () => {
         const { comp } = createCalcTableComponent();
         const actions = createMockCalcTableActions();
         comp.actions = actions as any;
         // subscriptions and actionSubscription are private — cast needed to verify teardown on destroy
         const subscriptionsUnsubscribeSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");
         const actionUnsubscribeSpy = vi.spyOn((comp as any).actionSubscription, "unsubscribe");

         comp.ngOnDestroy();

         expect(subscriptionsUnsubscribeSpy).toHaveBeenCalled();
         expect(actionUnsubscribeSpy).toHaveBeenCalled();
         expect((comp as any).actionSubscription).toBeNull();
      });
   });
});
