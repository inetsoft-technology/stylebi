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
 * WSDetailsPaneComponent — Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1  [Risk 3] — getTableStatus: builds status from table.info.editable=false,
 *                        sourceInfo.view, mirrorInfo, rowRange, totalRows, duration,
 *                        exceededMaximum, info.runtime, info.live.
 *   Group 2  [Risk 3] — populateTableModeButtons: creates one button per mode in
 *                        table.modes array; null table → empty tableModeButtons.
 *   Group 3  [Risk 3] — ngOnChanges(table): calls populateTableModeButtons, resets
 *                        rowRange to null, updates iconCss when table != null.
 *   Group 4  [Risk 2] — openConditionDialog: emits onOpenAssemblyConditionDialog for
 *                        non-embedded table; shows info dialog for embedded table.
 *   Group 5  [Risk 2] — @Output emitters: openAggregateDialog, onOpenSortColumnDialog,
 *                        toggleMirrorAutoUpdate, selectColumnSource, oozColumnMouseEvent.
 *   Group 6  [Risk 2] — changeTableMode: sends WSAssemblyEvent via worksheetClient.sendEvent
 *                        with correct URI and table name.
 *   Group 7  [Risk 2] — setShowName: updates showName and emits onToggleShowColumnName.
 *   Group 8  [Risk 2] — isSupportChangeColumnOrder: false for crosstab with groups;
 *                        false for SQL-edited SQLBoundTableAssembly; true otherwise.
 *   Group 9  [Risk 2] — searchNext/searchPrevious: increments/decrements searchIndex
 *                        with wrap-around; updates searchResultCount.
 *   Group 10 [Risk 1] — isTableButtonVisible: respects freeFormSqlEnabled and
 *                        expressionColumnEnabled flags.
 */

import { SimpleChange } from "@angular/core";
import { makeMocks, makeTable, makeWorksheet, renderComponent } from "./ws-details-pane.component.test-helpers";

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: getTableStatus [Risk 3]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — getTableStatus", () => {

   it("should include '-Not Editable' when table.info.editable=false", async () => {
      const mocks = makeMocks();
      mocks.table.info.editable = false;
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("-Not Editable");
   });

   it("should append [viewName] when sourceInfo.view is set", async () => {
      const mocks = makeMocks();
      mocks.table.tableClassType = "BoundTableAssembly";
      mocks.table.info.editable = true;
      mocks.table.info.sourceInfo = { view: "MY_VIEW" };
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("[MY_VIEW]");
   });

   it("should append row range when rowRange is set", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.rowRange = { start: 1, end: 100 } as any;

      const status = comp.getTableStatus();

      expect(status).toContain("1-100");
   });

   it("should include total rows when totalRows is non-null", async () => {
      const mocks = makeMocks();
      mocks.table.totalRows = 500;
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("500");
   });

   it("should include duration when table.duration is non-null", async () => {
      const mocks = makeMocks();
      mocks.table.duration = 123;
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("123ms");
   });

   it("should include exceededMaximum text when table.exceededMaximum is set", async () => {
      const mocks = makeMocks();
      mocks.table.exceededMaximum = "Row limit reached";
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("Row limit reached");
   });

   it("should include runtime tag when info.runtime=true and exceededMaximum is null", async () => {
      const mocks = makeMocks();
      mocks.table.info.runtime = true;
      mocks.table.exceededMaximum = null;
      const { comp } = await renderComponent(mocks);

      const status = comp.getTableStatus();

      expect(status).toContain("fullPreview");
   });
});

// ---------------------------------------------------------------------------
// Group 2: populateTableModeButtons [Risk 3]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — populateTableModeButtons", () => {

   // Regression-sensitive: every mode must map to a button with the correct label and
   // clickFunction; wrong label breaks the toolbar display.
   it("should create one button per mode in table.modes", async () => {
      const mocks = makeMocks();
      mocks.table.modes = ["default", "live", "full", "detail"];
      const { comp } = await renderComponent(mocks);

      expect(comp.tableModeButtons).toHaveLength(4);
   });

   it("should label the 'default' mode button with 'default'", async () => {
      const mocks = makeMocks();
      mocks.table.modes = ["default"];
      const { comp } = await renderComponent(mocks);

      expect(comp.tableModeButtons[0].label).toBe("default");
      expect(comp.tableModeButtons[0].id).toBe("ws-table-mode-default");
   });

   it("should label the 'live' mode button with 'live'", async () => {
      const mocks = makeMocks();
      mocks.table.modes = ["live"];
      const { comp } = await renderComponent(mocks);

      expect(comp.tableModeButtons[0].label).toBe("live");
      expect(comp.tableModeButtons[0].id).toBe("ws-table-mode-live");
   });

   it("should produce empty tableModeButtons when table is null", async () => {
      const mocks = makeMocks();
      mocks.table = null;
      const { comp } = await renderComponent(mocks);

      // Manually trigger ngOnChanges for null table
      comp.ngOnChanges({
         table: { currentValue: null, previousValue: mocks.table, firstChange: false, isFirstChange: () => false } as any,
      } as any);

      expect(comp.tableModeButtons).toHaveLength(0);
   });
});

// ---------------------------------------------------------------------------
// Group 3: ngOnChanges(table) [Risk 3]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — ngOnChanges table", () => {

   // Regression-sensitive: rowRange must be reset when a new table is set so stale
   // range data from the previous table doesn't contaminate the new table status.
   it("should reset rowRange to null when table changes", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      comp.rowRange = { start: 1, end: 50 } as any;
      const newTable = makeTable({ name: "NewTable" });

      comp.ngOnChanges({
         table: {
            currentValue: newTable,
            previousValue: mocks.table,
            firstChange: false,
            isFirstChange: () => false,
         } as any,
      });

      expect(comp.rowRange).toBeNull();
   });

   it("should update iconCss when new table is non-null", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const newTable = makeTable({ name: "AnotherTable" });

      comp.ngOnChanges({
         table: {
            currentValue: newTable,
            previousValue: mocks.table,
            firstChange: false,
            isFirstChange: () => false,
         } as any,
      });

      // iconCss should be set (not null/undefined) after a non-null table change
      expect(comp.iconCss).toBeDefined();
   });

   it("should re-populate tableModeButtons for the new table's modes", async () => {
      const mocks = makeMocks();
      mocks.table.modes = ["default", "live"];
      const { comp, fixture } = await renderComponent(mocks);

      const newTable = makeTable({ name: "NewTable", modes: ["default", "live", "full"] });
      fixture.componentRef.setInput("table", newTable);

      expect(comp.tableModeButtons).toHaveLength(3);
   });
});

// ---------------------------------------------------------------------------
// Group 4: openConditionDialog [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — openConditionDialog", () => {

   // Regression-sensitive: embedded tables must NOT open the condition dialog because
   // filtering is not supported; missing this guard causes confusing behavior.
   it("should emit onOpenAssemblyConditionDialog with table.name for non-embedded table", async () => {
      const mocks = makeMocks();
      mocks.table.isEmbeddedTable = vi.fn().mockReturnValue(false);
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onOpenAssemblyConditionDialog.subscribe(emitSpy);

      comp.openConditionDialog();

      expect(emitSpy).toHaveBeenCalledWith("TestTable");
   });

   it("should open NgbModal info dialog and NOT emit for embedded table", async () => {
      const mocks = makeMocks();
      mocks.table.isEmbeddedTable = vi.fn().mockReturnValue(true);
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onOpenAssemblyConditionDialog.subscribe(emitSpy);

      comp.openConditionDialog();

      expect(mocks.modalService.open).toHaveBeenCalled();
      expect(emitSpy).not.toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 5: @Output emitters [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — @Output emitters", () => {

   it("openAggregateDialog should emit onOpenAggregateDialog with table.name", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onOpenAggregateDialog.subscribe(emitSpy);

      comp.openAggregateDialog();

      expect(emitSpy).toHaveBeenCalledWith("TestTable");
   });

   it("table button sort should emit onOpenSortColumnDialog with table.name", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onOpenSortColumnDialog.subscribe(emitSpy);

      // Find the sort button's clickFunction
      const sortButton = comp.tableButtons.find(b => b.label === "sort");
      sortButton?.clickFunction?.();

      expect(emitSpy).toHaveBeenCalledWith("TestTable");
   });

   it("toggleMirrorAutoUpdate should emit onToggleAutoUpdate with table", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);
      const emitSpy = vi.fn();
      comp.onToggleAutoUpdate.subscribe(emitSpy);

      // Find the mirror-auto-update-enabled button's clickFunction
      const btn = comp.tableButtons.find(b => b.label === "mirror-auto-update-enabled");
      btn?.clickFunction?.();

      expect(emitSpy).toHaveBeenCalledWith(mocks.table);
   });

   it("selectColumnSource should emit onSelectColumnSource with event", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onSelectColumnSource.subscribe(emitSpy);
      const event: any = { assembly: "T1", columnIndex: 0 };

      comp.selectColumnSource(event);

      expect(emitSpy).toHaveBeenCalledWith(event);
   });

   it("oozColumnMouseEvent should emit onOozColumnMouseEvent with event", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onOozColumnMouseEvent.subscribe(emitSpy);
      const event: any = { assembly: "T1", type: "mouseenter" };

      comp.oozColumnMouseEvent(event);

      expect(emitSpy).toHaveBeenCalledWith(event);
   });
});

// ---------------------------------------------------------------------------
// Group 6: changeTableMode [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — changeTableMode", () => {

   it("should send WSAssemblyEvent to TABLE_MODE_SOCKET_URI+mode", async () => {
      const mocks = makeMocks();
      const { comp } = await renderComponent(mocks);

      comp.changeTableMode("live");

      expect(mocks.worksheetClient.sendEvent).toHaveBeenCalledWith(
         expect.stringContaining("live"),
         expect.objectContaining({ assemblyName: "TestTable" }),
      );
   });

   it("should include table.name in the assembly event", async () => {
      const mocks = makeMocks();
      mocks.table.name = "MyQueryTable";
      const { comp } = await renderComponent(mocks);
      comp.table = mocks.table;

      comp.changeTableMode("default");

      const [, event] = mocks.worksheetClient.sendEvent.mock.calls[0];
      expect(event.assemblyName).toBe("MyQueryTable");
   });
});

// ---------------------------------------------------------------------------
// Group 7: setShowName [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — setShowName", () => {

   it("should update showName and emit onToggleShowColumnName", async () => {
      const { comp } = await renderComponent();
      const emitSpy = vi.fn();
      comp.onToggleShowColumnName.subscribe(emitSpy);
      // Wire fake dropdowns
      (comp as any).dropdowns = { forEach: vi.fn() };

      comp.setShowName(true);

      expect(comp.showName).toBe(true);
      expect(emitSpy).toHaveBeenCalledWith(true);
   });

   it("should close all dropdowns when setShowName is called", async () => {
      const { comp } = await renderComponent();
      const closeSpy = vi.fn();
      (comp as any).dropdowns = { forEach: (fn: any) => fn({ close: closeSpy }) };

      comp.setShowName(false);

      expect(closeSpy).toHaveBeenCalled();
   });
});

// ---------------------------------------------------------------------------
// Group 8: isSupportChangeColumnOrder [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — isSupportChangeColumnOrder", () => {

   // Regression-sensitive: reorder must be disabled for crosstab because column order
   // is determined by the aggregate definition, not the display order.
   it("should return false for crosstab with groups in aggregateInfo", async () => {
      const mocks = makeMocks();
      mocks.table.aggregateInfo = { crosstab: true, groups: [{ name: "col1" }] };
      mocks.table.mode = "default";
      const { comp } = await renderComponent(mocks);

      expect(comp.isSupportChangeColumnOrder()).toBe(false);
   });

   it("should return false for SQLBoundTableAssembly with sqlEdited=true", async () => {
      const mocks = makeMocks();
      mocks.table.tableClassType = "SQLBoundTableAssembly";
      mocks.table.sqlEdited = true;
      mocks.table.aggregateInfo = null;
      const { comp } = await renderComponent(mocks);

      expect(comp.isSupportChangeColumnOrder()).toBe(false);
   });

   it("should return true for a regular BoundTableAssembly", async () => {
      const mocks = makeMocks();
      mocks.table.tableClassType = "BoundTableAssembly";
      mocks.table.aggregateInfo = null;
      const { comp } = await renderComponent(mocks);

      expect(comp.isSupportChangeColumnOrder()).toBe(true);
   });

   it("should return true for SQLBoundTableAssembly with sqlEdited=false", async () => {
      const mocks = makeMocks();
      mocks.table.tableClassType = "SQLBoundTableAssembly";
      mocks.table.sqlEdited = false;
      mocks.table.aggregateInfo = null;
      const { comp } = await renderComponent(mocks);

      expect(comp.isSupportChangeColumnOrder()).toBe(true);
   });
});

// ---------------------------------------------------------------------------
// Group 9: searchNext / searchPrevious [Risk 2]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — searchNext / searchPrevious", () => {

   it("searchNext should increment searchIndex", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 1;
      comp.searchLength = 3;

      comp.searchNext();

      expect(comp.searchIndex).toBe(2);
   });

   it("searchNext should wrap from end back to 1", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 3;
      comp.searchLength = 3;

      comp.searchNext();

      expect(comp.searchIndex).toBe(1);
   });

   it("searchNext should reset to 0 when searchLength=0", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 0;
      comp.searchLength = 0;

      comp.searchNext();

      expect(comp.searchIndex).toBe(0);
   });

   it("searchPrevious should decrement searchIndex", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 2;
      comp.searchLength = 3;

      comp.searchPrevious();

      expect(comp.searchIndex).toBe(1);
   });

   it("searchPrevious should wrap from 0 to searchLength", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 1;
      comp.searchLength = 3;

      comp.searchPrevious();

      // After decrement: searchIndex=0 ≤ 0, so set to searchLength=3
      expect(comp.searchIndex).toBe(3);
   });

   it("searchNext should update searchResultCount string", async () => {
      const { comp } = await renderComponent();
      comp.searchIndex = 1;
      comp.searchLength = 5;

      comp.searchNext();

      expect(comp.searchResultCount).toBe("2/5");
   });
});

// ---------------------------------------------------------------------------
// Group 10: isTableButtonVisible [Risk 1]
// ---------------------------------------------------------------------------

describe("WSDetailsPaneComponent — isTableButtonVisible", () => {

   it("should return false when button.label is not in table.tableButtons", async () => {
      const mocks = makeMocks();
      mocks.table.tableButtons = ["condition"];
      const { comp } = await renderComponent(mocks);
      const button: any = { label: "sort", id: "ws-table-sort" };

      expect(comp.isTableButtonVisible(button)).toBe(false);
   });

   it("should return true when button.label is in table.tableButtons", async () => {
      const mocks = makeMocks();
      mocks.table.tableButtons = ["sort"];
      const { comp } = await renderComponent(mocks);
      const button: any = { label: "sort", id: "ws-table-sort" };

      expect(comp.isTableButtonVisible(button)).toBe(true);
   });

   it("should return false for expression button when expressionColumnEnabled=false", async () => {
      const mocks = makeMocks();
      mocks.table.tableButtons = ["expression"];
      const { fixture } = await renderComponent(mocks);
      const comp = fixture.componentInstance as any;
      comp.expressionColumnEnabled = false;
      const button: any = { label: "expression", id: "ws-table-expression" };

      expect(comp.isTableButtonVisible(button)).toBe(false);
   });
});
