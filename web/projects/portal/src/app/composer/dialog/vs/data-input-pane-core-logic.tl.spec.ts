/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
 * DataInputPane — Angular Testing Library style
 *
 * Risk-first coverage (core logic NOT covered by validate-date-format or existing spec):
 *   Group 1 [Risk 3]  — selectRow: variable/expression stored directly; normal rows use indexOf+1
 *   Group 2 [Risk 3]  — updateSelectedRow: defaultValue extracted after ":"; stale when no ":" present
 *   Group 3 [Risk 3]  — updateColumns HTTP response: existing column reset vs. preserved; error path clears all
 *   Group 4 [Risk 3]  — updateRows HTTP response: out-of-bounds rowValue reset; variable type preserved; error path clears
 *   Group 5 [Risk 2]  — selectColumnType: sets first column in VALUE mode only when columnValue empty
 *   Group 6 [Risk 2]  — isChooseCellEnabled + isSelected: state predicates
 *
 * Confirmed bugs (it.fails — remove wrapper once fixed): none
 *
 * KEY contracts:
 *   selectRow stores 1-based index via indexOf+1; a row absent from the list → "0" (invalid).
 *   updateSelectedRow takes vals[1].trim() after split(":"); no ":" → defaultValue is NOT updated (stale).
 *   updateColumns: "$"/"=" prefix on columnValue bypasses the "not-in-list" reset check.
 *   updateRows server response: data[0] is skipped; real rows start at data[1].
 *   MSW URL pattern uses wildcard prefix "asterisk/vs/dataInput/..." — relative "../vs/..." resolves to /vs/... in jsdom.
 */

import { CommonModule, DatePipe } from "@angular/common";
import { HttpClientTestingModule } from "@angular/common/http/testing";
import { provideHttpClient } from "@angular/common/http";
import { NO_ERRORS_SCHEMA } from "@angular/core";
import { FormsModule } from "@angular/forms";
import { render, waitFor } from "@testing-library/angular";
import { http, HttpResponse as MswHttpResponse } from "msw";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { DataInputPaneModel } from "../../data/vs/data-input-pane-model";
import { DataInputPane, PopupEmbeddedTable } from "./data-input-pane.component";
import { server } from "../../../../../../../mocks/server";

// ---------------------------------------------------------------------------
// Shared helpers
// ---------------------------------------------------------------------------

function createModel(overrides: Partial<DataInputPaneModel> = {}): DataInputPaneModel {
   return {
      table: "",          // empty → updateColumns() skips HTTP in ngOnInit
      tableLabel: "",
      rowValue: "",
      columnValue: "",
      defaultValue: "",
      targetTree: null,
      variable: false,
      writeBackDirectly: false,
      ...overrides,
   };
}

/** Pure-logic render: uses HttpClientTestingModule so no real HTTP leaves the process. */
async function renderPure(modelOverrides: Partial<DataInputPaneModel> = {}) {
   return render(DataInputPane, {
      imports: [CommonModule, FormsModule, HttpClientTestingModule],
      providers: [DatePipe],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         model: createModel(modelOverrides),
         comboBox: false,
         checkBox: false,
         dataType: "string",
         runtimeId: "vs1",
         variableValues: [],
      },
   });
}

/**
 * HTTP render: uses provideHttpClient() so MSW can intercept real XHR.
 * Start with table="" so ngOnInit's updateColumns() takes the no-HTTP else-branch.
 */
async function renderHttp() {
   return render(DataInputPane, {
      imports: [CommonModule, FormsModule],
      providers: [DatePipe, provideHttpClient()],
      schemas: [NO_ERRORS_SCHEMA],
      componentProperties: {
         model: createModel(),   // table="" → no HTTP during init
         comboBox: false,
         checkBox: false,
         dataType: "string",
         runtimeId: "vs1",
         variableValues: [],
      },
   });
}

// ---------------------------------------------------------------------------
// Group 1 — selectRow: row index / value storage [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — selectRow — row index calculation and direct storage [Group 1, Risk 3]", () => {

   // 🔁 Regression-sensitive: variable rows must bypass indexOf so "$var" is stored verbatim, not as "0"
   // Risk Point/Contract: charAt(0)=="$" check must run BEFORE any indexOf conversion
   it("should store variable row '$var' directly as rowValue", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.rows = ["1 : city", "2 : town"];
      comp.selectRow("$var");

      expect(comp.model.rowValue).toBe("$var");
      expect(comp.selectedRow).toBe("$var");
   });

   // Risk Point/Contract: "=" prefix triggers expression mode — stored verbatim, not as indexOf index
   it("should store expression row '=expr' directly as rowValue", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.rows = ["1 : city"];
      comp.selectRow("=expr");

      expect(comp.model.rowValue).toBe("=expr");
   });

   // 🔁 Regression-sensitive: normal rows use 1-based indexOf; indexOf=0 must become "1" not "0"
   // Why High Value: off-by-one error in this formula would corrupt every row submission
   it("should store '1' when row '1 : city' is first element (indexOf=0, +1=1)", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.rows = ["1 : city", "2 : town"];
      comp.selectRow("1 : city");

      expect(comp.model.rowValue).toBe("1");
   });

   // Risk Point/Contract: row not in list → indexOf=-1, so rowValue="0" (SA≠SB: 0 is invalid 1-based index)
   it("should store '0' when row is not found in this.rows (indexOf=-1 + 1)", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.rows = ["1 : city"];
      comp.selectRow("ghost row");

      expect(comp.model.rowValue).toBe("0");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — updateSelectedRow: defaultValue extraction [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — updateSelectedRow — defaultValue extraction [Group 2, Risk 3]", () => {

   // 🔁 Regression-sensitive: defaultValue drives the cell value shown to the user; wrong extraction → wrong submission
   // Risk Point/Contract: splits on ":" and trims vals[1] — must match the "N : value" row format
   it("should set defaultValue to the trimmed part after ':' for a normal row string", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;
      comp.model.defaultValue = "old";

      comp.updateSelectedRow("1 : New York");

      expect(comp.selectedRow).toBe("1 : New York");
      expect(comp.model.defaultValue).toBe("New York");
   });

   // Boundary: null val sets selectedRow=null but must not crash and must leave defaultValue unchanged
   it("should set selectedRow=null and leave defaultValue unchanged when val is null", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;
      comp.model.defaultValue = "stale";

      comp.updateSelectedRow(null);

      expect(comp.selectedRow).toBeNull();
      expect(comp.model.defaultValue).toBe("stale");   // stale value preserved
   });

   // 🔁 Regression-sensitive: variable/expression rows have no ":" so defaultValue is silently left stale
   // Why High Value: if defaultValue is used for form submission, stale value corrupts the payload silently
   it("should NOT update defaultValue when val has no ':' (e.g. variable '$var')", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;
      comp.model.defaultValue = "stale";

      comp.updateSelectedRow("$var");

      expect(comp.selectedRow).toBe("$var");
      expect(comp.model.defaultValue).toBe("stale");   // unchanged — no colon in "$var"
   });
});

// ---------------------------------------------------------------------------
// Group 3 — updateColumns HTTP response: column selection logic [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — updateColumns HTTP response — column selection [Group 3, Risk 3]", () => {

   // 🔁 Regression-sensitive: when a table changes, the old column may no longer exist; it must be reset to first
   // Risk Point/Contract: getIndex(columnValue) < 0 triggers reset only when column is NOT "$" or "="
   it("should reset columnValue to first column when existing column is absent from server response", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      // Pre-condition: table set, old column not in the incoming response
      comp.model.table = "Query1";
      comp.model.columnValue = "old_col";

      server.use(
         http.get("*/vs/dataInput/columns/vs1/Query1", () =>
            MswHttpResponse.json({ columnlist: ["col1", "col2"], descriptionlist: ["", ""] })
         ),
         http.get("*/vs/dataInput/rows/vs1/Query1/col1", () =>
            MswHttpResponse.json(["header", "row1", "row2"])
         )
      );

      comp.updateColumns();
      await waitFor(() => expect(comp.model.columnValue).toBe("col1"));
   });

   // 🔁 Regression-sensitive: "$" prefix marks a variable column and must survive a table refresh unchanged
   // Risk Point/Contract: the charAt(0) == "$" guard must run before getIndex() — bypassing the reset
   it("should preserve a variable column ('$col') even when it is absent from server response", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.columnValue = "$varCol";

      server.use(
         http.get("*/vs/dataInput/columns/vs1/Query1", () =>
            MswHttpResponse.json({ columnlist: ["col1"], descriptionlist: [""] })
         ),
         http.get("*/vs/dataInput/rows/vs1/Query1/$varCol", () =>
            MswHttpResponse.json(["header"])
         )
      );

      comp.updateColumns();
      await waitFor(() => expect(comp.columns.length).toBe(1));  // columns loaded

      expect(comp.model.columnValue).toBe("$varCol");   // variable preserved
   });

   // Risk Point/Contract: on HTTP error the error handler must clear all four fields atomically
   it("should clear columnValue, columns, rowValue, and rows on HTTP error", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.columnValue = "col1";
      comp.model.rowValue = "1";
      comp.rows = ["1 : city"];

      server.use(
         http.get("*/vs/dataInput/columns/vs1/Query1", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      comp.updateColumns();
      await waitFor(() => expect(comp.model.columnValue).toBe(""));

      expect(comp.columns).toEqual([]);
      expect(comp.model.rowValue).toBe("");
      expect(comp.rows).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 4 — updateRows HTTP response: row bounds and type preservation [Risk 3]
// ---------------------------------------------------------------------------

describe("DataInputPane — updateRows HTTP response — row bounds and type [Group 4, Risk 3]", () => {

   // 🔁 Regression-sensitive: out-of-bounds rowValue must be corrected to "1", not left as a dangling pointer
   // Risk Point/Contract: condition is rows.length >= Number(rowValue); if false → rowValue = "1"
   it("should reset rowValue to '1' when stored numeric rowValue exceeds rows in response", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.columnValue = "col1";
      comp.model.rowValue = "5";    // out of bounds: response has only 3 rows

      server.use(
         http.get("*/vs/dataInput/rows/vs1/Query1/col1", () =>
            // data[0] is skipped by the loop; real rows are data[1..3]
            MswHttpResponse.json(["header", "row1", "row2", "row3"])
         )
      );

      comp.updateRows();
      await waitFor(() => expect(comp.rows.length).toBe(3));

      expect(comp.model.rowValue).toBe("1");
      expect(comp.selectedRow).toBe("1 : row1");
   });

   // 🔁 Regression-sensitive: variable rowValue must survive a row refresh with its type intact
   // Risk Point/Contract: "$" prefix routes to VARIABLE branch — not treated as a numeric index
   it("should preserve variable rowValue '$var' as VARIABLE type after row fetch", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.columnValue = "col1";
      comp.model.rowValue = "$var";

      server.use(
         http.get("*/vs/dataInput/rows/vs1/Query1/col1", () =>
            MswHttpResponse.json(["header", "row1", "row2"])
         )
      );

      comp.updateRows();
      await waitFor(() => expect(comp.rows.length).toBe(2));

      expect(comp.model.rowValue).toBe("$var");
      expect(comp.rowType).toBe(ComboMode.VARIABLE);
   });

   // Risk Point/Contract: HTTP error handler must clear both rows and rowValue atomically
   it("should clear rows and rowValue on HTTP error", async () => {
      const { fixture } = await renderHttp();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.columnValue = "col1";
      comp.model.rowValue = "1";
      comp.rows = ["1 : city"];

      server.use(
         http.get("*/vs/dataInput/rows/vs1/Query1/col1", () =>
            new MswHttpResponse(null, { status: 500 })
         )
      );

      comp.updateRows();
      await waitFor(() => expect(comp.model.rowValue).toBe(""));

      expect(comp.rows).toEqual([]);
   });
});

// ---------------------------------------------------------------------------
// Group 5 — selectColumnType: column default on mode switch [Risk 2]
// ---------------------------------------------------------------------------

describe("DataInputPane — selectColumnType — column default in VALUE mode [Group 5, Risk 2]", () => {

   // Why High Value: switching to VALUE with no existing column must snap to first column to prevent empty column submission
   it("should set columnValue to first available column when switching to VALUE with empty columnValue", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.columns = [{ value: "col1", label: "col1", tooltip: "" }];
      comp.model.columnValue = "";

      comp.selectColumnType(ComboMode.VALUE);

      expect(comp.model.columnValue).toBe("col1");
   });

   // Risk Point/Contract: VALUE mode with an existing value must be a no-op — must not overwrite user's column choice
   it("should leave columnValue unchanged when switching to VALUE with an existing columnValue", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.columns = [{ value: "col1", label: "col1", tooltip: "" }, { value: "col2", label: "col2", tooltip: "" }];
      comp.model.columnValue = "col2";

      comp.selectColumnType(ComboMode.VALUE);

      expect(comp.model.columnValue).toBe("col2");
   });
});

// ---------------------------------------------------------------------------
// Group 6 — isChooseCellEnabled + isSelected: state predicates [Risk 2]
// ---------------------------------------------------------------------------

describe("DataInputPane — isChooseCellEnabled + isSelected — state predicates [Group 6, Risk 2]", () => {

   // Why High Value: the Choose Cell button and cell highlight logic depend on these predicates being correct
   it("should return true from isChooseCellEnabled when table is set and variable is false; false otherwise", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.model.table = "Query1";
      comp.model.variable = false;
      expect(comp.isChooseCellEnabled()).toBe(true);

      comp.model.variable = true;
      expect(comp.isChooseCellEnabled()).toBe(false);   // variable → disabled

      comp.model.variable = false;
      comp.model.table = "";
      // "" && ... short-circuits to "" (falsy but not boolean false) — the method's return is used as a boolean
      expect(comp.isChooseCellEnabled()).toBeFalsy();   // no table → disabled
   });

   // Risk Point/Contract: isSelected must return true ONLY when both row index AND column header match exactly
   it("should return true from isSelected when rowIndex and columnHeader match the model values", async () => {
      const { fixture } = await renderPure();
      const comp = fixture.componentInstance;

      comp.model.rowValue = "1";
      comp.model.columnValue = "col1";
      comp.popupTable = {
         tableName: "Query1", numRows: 1, columnHeaders: ["col1", "col2"],
         rowData: [], page: 1,
      } as PopupEmbeddedTable;

      // Row index 0 on page 1 → getRowIndex(0) = (0+1) + (1-1)*10 = 1 → matches rowValue "1"
      expect(comp.isSelected(0, 0)).toBe(true);    // row match + col match
      expect(comp.isSelected(0, 1)).toBe(false);   // wrong column
      expect(comp.isSelected(1, 0)).toBe(false);   // row 2 ≠ rowValue "1"

      comp.model.rowValue = "";
      expect(comp.isSelected(0, 0)).toBe(false);   // empty rowValue → no selection
   });
});
