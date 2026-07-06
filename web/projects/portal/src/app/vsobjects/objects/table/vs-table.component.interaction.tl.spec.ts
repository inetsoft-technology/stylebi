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
 * VSTable — Pass 1: Interaction
 *
 * Direct instantiation (no ATL render) — all 22 constructor deps mocked.
 * Covers user-triggered flows and server command handling not tested in vs-table.spec.ts:
 * ngOnInit lifecycle wiring, ngOnDestroy cleanup, actions-switch dispatch, sort/form flows,
 * loadTableData, changeCellText, rowLinkClicked, processUpdateSortInfoCommand.
 *
 *   Group 1  — ngOnInit: pagingControlService subscriptions; clearSelection; tabDeselect
 *   Group 2  — ngOnDestroy: actionSubscription + subscriptions cleanup
 *   Group 3  — set actions: key switch cases (multi-select, selection-reset/apply,
 *                            form-apply, sort-column/-aggregate, insert-row, append-row,
 *                            delete-rows, show-format-pane)
 *   Group 4  — sortClicked: sends SortColumnEvent; multi flag propagated
 *   Group 5  — sortColumn: stopPropagation; sendEvent with ctrlKey mapped to multi
 *   Group 6  — formInputChanged: sends ChangeFormTableCellInput
 *   Group 7  — changeCellText: embedded+!submitOnChange → changedTableCells; else checkFormData0
 *   Group 8  — processUpdateSortInfoCommand: updates headersSortType and sortPositions
 *   Group 9  — loadTableData: start=0 populates headers/data/fields; start>0 uses tableHeaderCells
 *   Group 10 — rowLinkClicked: guards (!viewer, no rowHyperlinks, cell.hyperlinks); happy path
 *   Group 11 — isDraggable: header→true; non-header+viewer→false; resizeCol active→false
 *   Group 12 — clearFlyover: hasFlyover+force→sendFlyover+clears; no-op when condition fails
 *   Group 13 — onOpenHighlightDialog: 'table highlight' action emits model
 */

import { Subject } from "rxjs";
import {
   createMockActions,
   createTableComponent,
   makeLoadTableDataCommand,
   makeMockTableModel,
   makeTableCell,
} from "./vs-table.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("VSTable — Pass 1: Interaction", () => {

   // ── Group 1 — ngOnInit ────────────────────────────────────────────────────
   describe("Group 1 — ngOnInit", () => {
      it("should subscribe to pagingControlService.scrollTop and scrollLeft", () => {
         const pagingControlService = {
            setPagingControlModel: vi.fn(),
            scrollTop: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            scrollLeft: vi.fn().mockReturnValue(new Subject<any>().asObservable()),
            getCurrentAssembly: vi.fn().mockReturnValue("Table1"),
            hasDropdownOrTooltip: false,
         };
         const { comp } = createTableComponent({ pagingControlService });

         comp.ngOnInit();

         expect(pagingControlService.scrollTop).toHaveBeenCalled();
         expect(pagingControlService.scrollLeft).toHaveBeenCalled();
      });

      it("should set firstSelectedRow and firstSelectedColumn to -1 on ngOnInit", () => {
         const { comp } = createTableComponent();
         comp.model.firstSelectedRow = 3;
         comp.model.firstSelectedColumn = 2;

         comp.ngOnInit();

         expect(comp.model.firstSelectedRow).toBe(-1);
         expect(comp.model.firstSelectedColumn).toBe(-1);
      });

      it("should clear selection when tabDeselected fires with matching assembly name", () => {
         const tabDeselectedSubject = new Subject<string>();
         const tabService = { tabDeselected: tabDeselectedSubject.asObservable() };
         const { comp } = createTableComponent({ tabService });

         comp.ngOnInit();
         comp.model.firstSelectedRow = 5;
         tabDeselectedSubject.next("Table1");

         expect(comp.model.firstSelectedRow).toBe(-1);
      });

      it("should not clear selection when tabDeselected fires with different assembly name", () => {
         const tabDeselectedSubject = new Subject<string>();
         const tabService = { tabDeselected: tabDeselectedSubject.asObservable() };
         const { comp } = createTableComponent({ tabService });

         comp.ngOnInit();
         comp.model.firstSelectedRow = 5;
         tabDeselectedSubject.next("OtherTable");

         expect(comp.model.firstSelectedRow).toBe(5);
      });
   });

   // ── Group 2 — ngOnDestroy ─────────────────────────────────────────────────
   describe("Group 2 — ngOnDestroy", () => {
      it("should unsubscribe actionSubscription on destroy", () => {
         const { comp } = createTableComponent();
         const actions = createMockActions();
         comp.actions = actions as any;

         const sub = (comp as any).actionSubscription;
         const unsubSpy = vi.spyOn(sub, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubSpy).toHaveBeenCalled();
         expect((comp as any).actionSubscription).toBeNull();
      });

      it("should unsubscribe subscriptions on destroy", () => {
         const { comp } = createTableComponent();
         const unsubSpy = vi.spyOn((comp as any).subscriptions, "unsubscribe");

         comp.ngOnDestroy();

         expect(unsubSpy).toHaveBeenCalled();
      });
   });

   // ── Group 3 — set actions ─────────────────────────────────────────────────
   describe("Group 3 — set actions: key switch cases", () => {
      it("should toggle model.multiSelect when 'table multi-select' fires", () => {
         const { comp } = createTableComponent();
         comp.model.multiSelect = false;
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table multi-select", event: new MouseEvent("click") });

         expect(comp.model.multiSelect).toBe(true);
      });

      it("should call resetTable when 'table selection-reset' fires and there are pending changes", () => {
         const { comp } = createTableComponent({ model: { embedded: true } });
         // changedTableCells is private — seeded directly; the public path (changeCellText)
         // requires embedded+!submitOnChange which would need additional model state.
         (comp as any).changedTableCells = [{ row: 1, col: 0, text: "x" }];
         // resetTable calls updateData when changedTableCells > 0 — mock it
         vi.spyOn(comp as any, "updateData").mockImplementation(() => {});
         // loadedRows is private and unset until loadTableData fires — resetTable reads
         // loadedRows.start as the argument to updateData() before calling the mock,
         // so the read must not throw even though updateData itself is stubbed out.
         (comp as any).loadedRows = { start: 0, end: 10 };

         const actions = createMockActions();
         comp.actions = actions as any;
         actions.onAssemblyActionEvent.next({ id: "table selection-reset", event: new MouseEvent("click") });

         expect((comp as any).changedTableCells).toHaveLength(0);
      });

      it("should call applyFormChanges (send event) when 'table form-apply' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table form-apply", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/formTable/apply",
            expect.anything(),
         );
      });

      it("should send sort-column event with multi=false when 'table sort-column' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.sortInfo = { row: 0, col: 1, field: "Col B", sortable: true };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table sort-column", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/sort-column",
            expect.objectContaining({ multi: false }),
         );
      });

      it("should send sort-column event with multi=true when 'table sort-column-aggregate' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.sortInfo = { row: 0, col: 2, field: "Col C", sortable: true };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table sort-column-aggregate", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/sort-column",
            expect.objectContaining({ multi: true }),
         );
      });

      it("should send addRow event (insert=true) when 'table insert-row' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.firstSelectedRow = 2;
         // loadedRows is private — seeded directly; the addRow path reads loadedRows.start
         // to compute the absolute row index before sending the event.
         (comp as any).loadedRows = { start: 0, end: 10 };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table insert-row", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/formTable/addRow",
            expect.objectContaining({ insert: true }),
         );
      });

      it("should send addRow event (insert=false) when 'table append-row' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.lastSelected = { row: 3, column: 0 };
         // loadedRows private — seeded so addRow can compute the absolute row offset.
         (comp as any).loadedRows = { start: 0, end: 10 };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table append-row", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/formTable/addRow",
            expect.objectContaining({ insert: false }),
         );
      });

      it("should send deleteRows event when 'table delete-rows' fires", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.selectedData = new Map([[2, [0]], [4, [1]]]);
         // loadedRows private — deleteRows reads it to compute absolute row indices.
         (comp as any).loadedRows = { start: 0, end: 10 };
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table delete-rows", event: new MouseEvent("click") });

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/formTable/deleteRows",
            expect.anything(),
         );
      });

      it("should emit onOpenFormatPane when 'table show-format-pane' fires", () => {
         const { comp } = createTableComponent();
         const emitted: any[] = [];
         comp.onOpenFormatPane.subscribe(v => emitted.push(v));
         const actions = createMockActions();
         comp.actions = actions as any;

         actions.onAssemblyActionEvent.next({ id: "table show-format-pane", event: new MouseEvent("click") });

         expect(emitted.length).toBe(1);
         expect(emitted[0]).toBe(comp.model);
      });

      it("should unsubscribe previous actionSubscription when actions setter is called again", () => {
         const { comp } = createTableComponent();
         const actions1 = createMockActions();
         comp.actions = actions1 as any;
         const firstSub = (comp as any).actionSubscription;
         const unsubSpy = vi.spyOn(firstSub, "unsubscribe");

         const actions2 = createMockActions();
         comp.actions = actions2 as any;

         expect(unsubSpy).toHaveBeenCalled();
      });
   });

   // ── Group 4 — sortClicked ─────────────────────────────────────────────────
   describe("Group 4 — sortClicked", () => {
      it("should send sort-column event with multi=false when sortClicked(false)", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.sortInfo = { row: 0, col: 1, field: "Col B", sortable: true };

         (comp as any).sortClicked(false);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/sort-column",
            expect.objectContaining({ multi: false, col: 1, row: 0 }),
         );
      });

      it("should send sort-column event with multi=true when sortClicked(true)", () => {
         const { comp, viewsheetClient } = createTableComponent();
         comp.model.sortInfo = { row: 0, col: 2, field: "Col C", sortable: true };

         (comp as any).sortClicked(true);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/sort-column",
            expect.objectContaining({ multi: true }),
         );
      });
   });

   // ── Group 5 — sortColumn ──────────────────────────────────────────────────
   describe("Group 5 — sortColumn", () => {
      it("should stop event propagation when sortColumn is called", () => {
         const { comp } = createTableComponent();
         const cell = makeTableCell({ row: 0, col: 1, field: "Col B" });
         const event = { ctrlKey: false, stopPropagation: vi.fn() } as any;

         comp.sortColumn(event, cell);

         expect(event.stopPropagation).toHaveBeenCalled();
      });

      it("should send event with ctrlKey=true mapped to multi=true", () => {
         const { comp, viewsheetClient } = createTableComponent();
         const cell = makeTableCell({ row: 0, col: 0, field: "Col A" });
         const event = { ctrlKey: true, stopPropagation: vi.fn() } as any;

         comp.sortColumn(event, cell);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/table/sort-column",
            expect.objectContaining({ multi: true, row: 0, col: 0 }),
         );
      });
   });

   // ── Group 6 — formInputChanged ────────────────────────────────────────────
   describe("Group 6 — formInputChanged", () => {
      it("should send ChangeFormTableCellInput event to /events/formTable/edit", () => {
         const { comp, viewsheetClient } = createTableComponent();
         // loadedRows private — formInputChanged reads it to compute the absolute row offset.
         (comp as any).loadedRows = { start: 5, end: 15 };
         const cell = makeTableCell({ row: 3, col: 1 });

         comp.formInputChanged("new value", cell);

         expect(viewsheetClient.sendEvent).toHaveBeenCalledWith(
            "/events/formTable/edit",
            expect.objectContaining({ row: 3, col: 1 }),
         );
      });
   });

   // ── Group 7 — changeCellText ──────────────────────────────────────────────
   describe("Group 7 — changeCellText", () => {
      it("should push to changedTableCells and update cellLabel when embedded+!submitOnChange", () => {
         const { comp } = createTableComponent({ model: { embedded: true, submitOnChange: false } });
         const cell = makeTableCell({ row: 1, col: 0, cellLabel: "old" });

         comp.changeCellText("new", cell);

         expect((comp as any).changedTableCells).toHaveLength(1);
         expect(cell.cellLabel).toBe("new");
      });

      it("should call formDataService.checkFormData0 when not embedded", () => {
         const { comp, formDataService } = createTableComponent({ model: { embedded: false } });
         const cell = makeTableCell({ row: 1, col: 0 });

         comp.changeCellText("value", cell);

         expect(formDataService.checkFormData0).toHaveBeenCalled();
      });
   });

   // ── Group 8 — processUpdateSortInfoCommand ────────────────────────────────
   // processUpdateSortInfoCommand is protected — accessed via (comp as any) because the command
   // handler is dispatched by name from CommandProcessor at runtime; there is no public wrapper.
   describe("Group 8 — processUpdateSortInfoCommand", () => {
      it("should update model.headersSortType from command.sortOrders", () => {
         const { comp } = createTableComponent();
         const command = { sortOrders: [1, 2, 0], sortPositions: [-1, 0, 1] };

         (comp as any).processUpdateSortInfoCommand(command);

         expect(comp.model.headersSortType).toEqual([1, 2, 0]);
      });

      it("should update sortPositions from command.sortPositions", () => {
         const { comp } = createTableComponent();
         const command = { sortOrders: [0, 0, 0], sortPositions: [2, -1, 0] };

         (comp as any).processUpdateSortInfoCommand(command);

         expect(comp.model.sortPositions).toEqual([2, -1, 0]);
      });

      it("should skip update when model is null", () => {
         const { comp } = createTableComponent();
         (comp as any)._model = null;
         const command = { sortOrders: [1], sortPositions: [0] };

         // no throw expected
         expect(() => (comp as any).processUpdateSortInfoCommand(command)).not.toThrow();
      });
   });

   // ── Group 9 — loadTableData ───────────────────────────────────────────────
   // loadTableData is protected — accessed via (comp as any) because the command handler is
   // dispatched by name from CommandProcessor at runtime; there is no public wrapper.
   describe("Group 9 — loadTableData", () => {
      it("should set tableHeaders from first headerRowCount rows when start=0", () => {
         const { comp } = createTableComponent();
         const command = makeLoadTableDataCommand({ start: 0 });

         (comp as any).loadTableData(command);

         expect(comp.tableHeaders).toHaveLength(1);
         expect(comp.tableHeaders[0]).toBe(command.tableCells[0]);
      });

      it("should assign colNames as field on header cells when start=0", () => {
         const { comp } = createTableComponent();
         const command = makeLoadTableDataCommand({ start: 0 });

         (comp as any).loadTableData(command);

         expect(comp.tableHeaders[0][0].field).toBe("Col A");
         expect(comp.tableHeaders[0][1].field).toBe("Col B");
      });

      it("should update loadedRows with command start and end", () => {
         const { comp } = createTableComponent();
         const command = makeLoadTableDataCommand({ start: 0, end: 5 });

         (comp as any).loadTableData(command);

         // loadedRows private — read directly; no public getter exposes the loaded range.
         expect((comp as any).loadedRows).toEqual({ start: 0, end: 5 });
      });

      it("should replace tableHeaders from command.tableHeaderCells when start>0", () => {
         const { comp } = createTableComponent();
         // First load to set up initial state
         (comp as any).loadTableData(makeLoadTableDataCommand({ start: 0, end: 2 }));

         // Scrolled-down command with explicit header cells
         const newHeaders = [
            [makeTableCell({ row: 0, col: 0, cellLabel: "X" }),
             makeTableCell({ row: 0, col: 1, cellLabel: "Y" }),
             makeTableCell({ row: 0, col: 2, cellLabel: "Z" })],
         ];
         const command2 = makeLoadTableDataCommand({ start: 5, end: 10, tableHeaderCells: newHeaders });

         (comp as any).loadTableData(command2);

         expect(comp.tableHeaders[0][0].cellLabel).toBe("X");
      });

      it("should update rowHyperlinks from command", () => {
         const { comp } = createTableComponent();
         const links = [{ tooltip: "Row link", label: "link", url: "", linkType: 0 } as any];
         const command = makeLoadTableDataCommand({ start: 0, rowHyperlinks: links });

         (comp as any).loadTableData(command);

         expect(comp.rowHyperlinks).toBe(links);
      });
   });

   // ── Group 10 — rowLinkClicked ─────────────────────────────────────────────
   describe("Group 10 — rowLinkClicked", () => {
      it("should return early when not in viewer context", () => {
         const { comp, hyperlinkService } = createTableComponent({
            contextProvider: { viewer: false, preview: false, binding: false, composer: true, vsWizard: false, vsWizardPreview: false, embedAssembly: false },
         });
         const cell = makeTableCell();

         comp.rowLinkClicked(cell, 0);

         expect(hyperlinkService.clickLink).not.toHaveBeenCalled();
      });

      it("should return early when rowHyperlinks is empty", () => {
         const { comp, hyperlinkService } = createTableComponent();
         comp.rowHyperlinks = [];
         const cell = makeTableCell();

         comp.rowLinkClicked(cell, 0);

         expect(hyperlinkService.clickLink).not.toHaveBeenCalled();
      });

      it("should return early when cell has its own hyperlinks (autodrill guard)", () => {
         const { comp, hyperlinkService } = createTableComponent();
         comp.rowHyperlinks = [{ tooltip: "row", label: "link", url: "", linkType: 0 } as any];
         const cell = makeTableCell({ hyperlinks: [{ tooltip: "cell", label: "c", url: "", linkType: 0 } as any] });

         comp.rowLinkClicked(cell, 0);

         expect(hyperlinkService.clickLink).not.toHaveBeenCalled();
      });

      it("should call hyperlinkService.clickLink with row hyperlink when all guards pass", () => {
         const { comp, hyperlinkService } = createTableComponent();
         const link = { tooltip: "Go", label: "link", url: "http://example.com", linkType: 0 } as any;
         comp.rowHyperlinks = [link];
         const cell = makeTableCell({ hyperlinks: [] });

         comp.rowLinkClicked(cell, 0);

         expect(hyperlinkService.clickLink).toHaveBeenCalledWith(
            link,
            "viewsheet1",
            null,
         );
      });
   });

   // ── Group 11 — isDraggable ────────────────────────────────────────────────
   describe("Group 11 — isDraggable", () => {
      it("should return true for a header cell when not resizing (default component state)", () => {
         const { comp } = createTableComponent();
         // header=true → (true || !viewer) → true; initialMousePos=-1, resizeCol=-1 by default

         expect(comp.isDraggable(true)).toBe(true);
      });

      it("should return false for a data cell in viewer context (header=false, viewer=true)", () => {
         const { comp } = createTableComponent();
         // (false || !true) → false → entire AND expression is false

         expect(comp.isDraggable(false)).toBe(false);
      });

      it("should return false when resizeCol != -1 (column resize in progress)", () => {
         const { comp } = createTableComponent();
         // resizeCol is private — set directly; only a mousedown-on-column-edge event writes it.
         (comp as any).resizeCol = 0;

         expect(comp.isDraggable(true)).toBe(false);
      });
   });

   // ── Group 12 — clearFlyover ───────────────────────────────────────────────
   // clearFlyover calls sendFlyover (which internally uses debounceService) and synchronously
   // sets model.selectedData=null. Spy on sendFlyover to avoid the debounce chain.
   describe("Group 12 — clearFlyover", () => {
      it("should call sendFlyover with empty map when force=true and hasFlyover=true", () => {
         const { comp } = createTableComponent({ model: { hasFlyover: true } });
         const sendFlyoverSpy = vi.spyOn(comp, "sendFlyover").mockImplementation(() => {});

         comp.clearFlyover(true);

         expect(sendFlyoverSpy).toHaveBeenCalledWith(new Map());
      });

      it("should set model.selectedData to null when force=true and flyoverCellSelected=false", () => {
         const { comp } = createTableComponent({ model: { hasFlyover: true } });
         vi.spyOn(comp, "sendFlyover").mockImplementation(() => {});
         comp.model.selectedData = new Map([[0, [0]]]);

         comp.clearFlyover(true);

         expect(comp.model.selectedData).toBeNull();
      });

      it("should not call sendFlyover when hasFlyover=false", () => {
         const { comp } = createTableComponent({ model: { hasFlyover: false } });
         const sendFlyoverSpy = vi.spyOn(comp, "sendFlyover").mockImplementation(() => {});

         comp.clearFlyover(true);

         expect(sendFlyoverSpy).not.toHaveBeenCalled();
      });

      it("should not call sendFlyover when isFlyOnClick=true and force=false", () => {
         // condition: force || (!isFlyOnClick && !flyoverCellSelected) = false||(false&&true)=false
         const { comp } = createTableComponent({ model: { hasFlyover: true, isFlyOnClick: true } });
         const sendFlyoverSpy = vi.spyOn(comp, "sendFlyover").mockImplementation(() => {});

         comp.clearFlyover();

         expect(sendFlyoverSpy).not.toHaveBeenCalled();
      });
   });

   // ── Group 13 — onOpenHighlightDialog ─────────────────────────────────────
   describe("Group 13 — onOpenHighlightDialog", () => {
      it("should emit onOpenHighlightDialog with the current model when 'table highlight' fires", () => {
         const { comp } = createTableComponent();
         const actions = createMockActions();
         comp.actions = actions as any;
         const emitted: any[] = [];
         comp.onOpenHighlightDialog.subscribe(m => emitted.push(m));

         actions.onAssemblyActionEvent.next({ id: "table highlight", event: new MouseEvent("click") });

         expect(emitted).toHaveLength(1);
         expect(emitted[0]).toBe(comp.model);
      });
   });
});
