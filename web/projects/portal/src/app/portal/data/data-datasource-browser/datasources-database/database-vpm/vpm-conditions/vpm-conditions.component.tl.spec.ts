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
 * VPMConditionsComponent — single pass
 *
 * Coverage:
 *   Group 1 — conditions setter: first condition auto-selected; empty clears state; setter
 *             triggers editCondition → refreshColumns cascade
 *   Group 2 — addCondition / getNextConditionName: correct name generation; new condition selected
 *   Group 3 [Risk 2] — deleteSelectedCondition: confirmation dialog; splice + tableChange on ok;
 *             no-op on cancel
 *   Group 4 [Risk 2] — deleteCondition: confirmation dialog; splice + tableChange on ok;
 *             editingCondition cleared if deleted
 *   Group 5 — editCondition: single-select vs ctrl/shift multi-select; existsNames update;
 *             refreshedColumns(false) emitted; refreshColumns(true) triggered
 *   Group 6 [Risk 2] — refreshColumns: TABLE POST / PHYSICAL_MODEL GET; refreshingColumns flag;
 *             tableNameNull short-circuit; updateFieldTypes cascade
 *   Group 7 [Risk 2] — chooseTable: modal result sets tableName + tableChange + refreshColumns +
 *             clearClauses; cancel restores; typeChange reverts type on cancel
 *   Group 8 — editClauses: modal result updates editingCondition.clauses
 *   Group 9 — utility: expressionChanged, clearClauses, tableNameNull
 *
 * Note: Modal interactions use Promise (.result.then) which cannot be cancelled; they are
 * short-lived and benign after destroy. HTTP subscriptions are guarded by takeUntil(destroy$).
 */

import { Component, EventEmitter, Input, NO_ERRORS_SCHEMA, Output } from "@angular/core";
import { provideHttpClient } from "@angular/common/http";
import { render, waitFor } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { http, HttpResponse as MswHttpResponse } from "msw";

import { server } from "@test-mocks/server";
import { ComponentTool } from "../../../../../../common/util/component-tool";
import { ConditionModel } from "../../../../model/datasources/database/vpm/condition/condition-model";
import { ConditionTypes } from "../../../../model/datasources/database/vpm/condition/condition-types.enum";
import { ClauseModel } from "../../../../model/datasources/database/vpm/condition/clause/clause-model";
import { ClauseValueTypes } from "../../../../model/datasources/database/vpm/condition/clause/clause-value-types";
import { SplitPane } from "../../../../../../widget/split-pane/split-pane.component";
import { DataModelScriptPane } from "../../database-physical-model/data-model-script-pane/data-model-script-pane.component";
import { ChooseTableDialog } from "../../../../../dialog/choose-table-dialog/choose-table-dialog.component";
import { VPMConditionDialog } from "../../../../../dialog/vpm-condition-dialog/vpm-condition-dialog.component";
import { VPMConditionsComponent } from "./vpm-conditions.component";

// ---------------------------------------------------------------------------
// Stubs
// ---------------------------------------------------------------------------

@Component({ selector: "split-pane", template: "<ng-content />" })
class SplitPaneStub {
   @Input() sizes: number[];
   @Input() gutterSize: number;
   @Input() direction: string;
   @Input() minSize: number;
   @Input() snapOffset: number;
   @Input() dragEnable: boolean;
   @Input() displayed: boolean;
}

@Component({ selector: "data-model-script-pane", template: "" })
class DataModelScriptPaneStub {
   @Input() script: string;
   @Input() sql: boolean;
   @Output() expressionChange = new EventEmitter<string>();
}

@Component({ selector: "choose-table-dialog", template: "" })
class ChooseTableDialogStub {
   @Input() databaseName: string;
   @Input() conditionType: any;
   @Input() tableName: string;
   @Output() onCommit = new EventEmitter<string>();
   @Output() onCancel = new EventEmitter<string>();
}

@Component({ selector: "vpm-condition-dialog", template: "" })
class VPMConditionDialogStub {
   @Input() model: any;
   @Output() onCommit = new EventEmitter<any[]>();
   @Output() onCancel = new EventEmitter<string>();
}

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

const DB = "myDB";

function makeCondition(overrides: Partial<ConditionModel> = {}): ConditionModel {
   return {
      name: "cond1",
      clauses: [],
      type: ConditionTypes.TABLE,
      tableName: "",
      script: "",
      ...overrides,
   };
}

function makeClause(overrides: Partial<ClauseModel> = {}): ClauseModel {
   const VALUE_TYPE = ClauseValueTypes.VALUE as unknown as ClauseValueTypes;
   return {
      type: "",
      junc: false,
      level: 0,
      negated: false,
      value1: { type: VALUE_TYPE, expression: "" },
      value2: { type: VALUE_TYPE, expression: "" },
      value3: { type: VALUE_TYPE, expression: "" },
      operation: { name: "equals", symbol: "=" },
      ...overrides,
   };
}

async function renderComp(
   conditions: any[] = [],
   modalMockOverride?: Partial<{ open: ReturnType<typeof vi.fn> }>
) {
   const modalMock = { open: vi.fn(), ...modalMockOverride };
   const { fixture } = await render(VPMConditionsComponent, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [
         provideHttpClient(),
         { provide: NgbModal, useValue: modalMock },
      ],
      importOverrides: [
         { replace: SplitPane, with: SplitPaneStub },
         { replace: DataModelScriptPane, with: DataModelScriptPaneStub },
         { replace: ChooseTableDialog, with: ChooseTableDialogStub },
         { replace: VPMConditionDialog, with: VPMConditionDialogStub },
      ],
      componentProperties: {
         databaseName: DB,
         conditions,
      },
   });
   const comp = fixture.componentInstance as VPMConditionsComponent;
   return { comp, fixture, modalMock };
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
});

// ---------------------------------------------------------------------------
// Group 1 — conditions setter
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — conditions setter", () => {
   it("should set editingCondition to the first condition when non-empty", async () => {
      const c1 = makeCondition({ name: "first" });
      const { comp } = await renderComp([c1]);

      expect(comp.editingCondition).toBe(comp.conditions[0]);
      expect(comp.editingCondition.name).toBe("first");
   });

   it("should initialise selectedConditions to [first] when non-empty", async () => {
      const c1 = makeCondition();
      const { comp } = await renderComp([c1]);

      expect(comp.selectedConditions).toHaveLength(1);
      expect(comp.selectedConditions[0]).toBe(comp.conditions[0]);
   });

   it("should clear editingCondition and selectedConditions when conditions is empty", async () => {
      const { comp } = await renderComp([]);

      expect(comp.editingCondition).toBeNull();
      expect(comp.selectedConditions).toHaveLength(0);
   });

   it("should trigger refreshColumns HTTP when first condition has a tableName", async () => {
      const columns = [{ name: "col1", type: "string" }];
      server.use(
         http.post("*/api/data/vpm/columns/*", () => MswHttpResponse.json(columns))
      );
      const c1 = makeCondition({ tableName: "MyTable" });
      const { comp } = await renderComp([c1]);

      await waitFor(() => expect(comp.currentColumns).toHaveLength(1));
      expect(comp.currentColumns[0].name).toBe("col1");
   });
});

// ---------------------------------------------------------------------------
// Group 2 — addCondition / getNextConditionName
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — addCondition", () => {
   it("should name the first condition 'condition' when list is empty", async () => {
      const { comp } = await renderComp([]);
      comp.addCondition();

      expect(comp.conditions).toHaveLength(1);
      expect(comp.conditions[0].name).toBe("condition");
   });

   it("should name the next condition 'condition1' when 'condition' already exists", async () => {
      const { comp } = await renderComp([makeCondition({ name: "condition" })]);
      comp.addCondition();

      const names = comp.conditions.map(c => c.name);
      expect(names).toContain("condition1");
   });

   it("should set editingCondition to the new condition after addCondition", async () => {
      const { comp } = await renderComp([]);
      comp.addCondition();

      expect(comp.editingCondition).toBe(comp.conditions[0]);
      expect(comp.editingCondition.name).toBe("condition");
   });

   it("should fill the gap in the name sequence", async () => {
      // "condition" and "condition2" exist → gap at 1 → next is "condition1"
      const { comp } = await renderComp([
         makeCondition({ name: "condition" }),
         makeCondition({ name: "condition2" }),
      ]);
      comp.addCondition();

      const newNames = comp.conditions.map(c => c.name);
      expect(newNames).toContain("condition1");
   });
});

// ---------------------------------------------------------------------------
// Group 3 — deleteSelectedCondition [Risk 2]
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — deleteSelectedCondition", () => {
   it("should open the confirm dialog with the single-condition message", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockReturnValue(
         new Promise(() => {})
      );
      try {
         const c1 = makeCondition({ name: "c1" });
         const { comp } = await renderComp([c1]);

         comp.deleteSelectedCondition();

         expect(confirmSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.any(String),
            "_#(js:data.vpm.confirmSingleCondition)"
         );
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should use the multi-condition message when more than one is selected", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockReturnValue(
         new Promise(() => {})
      );
      try {
         const c1 = makeCondition({ name: "c1" });
         const c2 = makeCondition({ name: "c2" });
         const { comp } = await renderComp([c1, c2]);
         // manually select both
         comp.selectedConditions = [comp.conditions[0], comp.conditions[1]];

         comp.deleteSelectedCondition();

         expect(confirmSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.any(String),
            "_#(js:data.vpm.confirmConditions)"
         );
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should remove the condition and emit tableChange when user confirms", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const tableChangeSpy = vi.fn();
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      comp.tableChange.subscribe(tableChangeSpy);
      try {
         comp.deleteSelectedCondition();

         await waitFor(() => expect(comp.conditions).toHaveLength(0));
         expect(tableChangeSpy).toHaveBeenCalledTimes(1);
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should not remove any condition when user cancels", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      try {
         comp.deleteSelectedCondition();

         await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
         expect(comp.conditions).toHaveLength(1);
      } finally {
         confirmSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 4 — deleteCondition [Risk 2]
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — deleteCondition", () => {
   it("should open the confirm dialog on deleteCondition", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockReturnValue(
         new Promise(() => {})
      );
      try {
         const c1 = makeCondition({ name: "c1" });
         const { comp } = await renderComp([c1]);

         comp.deleteCondition(comp.conditions[0], 0);

         expect(confirmSpy).toHaveBeenCalledWith(
            expect.anything(),
            expect.any(String),
            "_#(js:data.vpm.confirmSingleCondition)"
         );
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should splice the condition and emit tableChange on ok", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const tableChangeSpy = vi.fn();
      const c1 = makeCondition({ name: "c1" });
      const c2 = makeCondition({ name: "c2" });
      const { comp } = await renderComp([c1, c2]);
      comp.tableChange.subscribe(tableChangeSpy);
      try {
         comp.deleteCondition(comp.conditions[0], 0);

         await waitFor(() => expect(comp.conditions).toHaveLength(1));
         expect(comp.conditions[0].name).toBe("c2");
         expect(tableChangeSpy).toHaveBeenCalledTimes(1);
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should clear editingCondition when the deleted condition was being edited", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("ok");
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      // conditions setter auto-selected c1 as editingCondition
      expect(comp.editingCondition).toBe(comp.conditions[0]);
      try {
         comp.deleteCondition(comp.conditions[0], 0);

         await waitFor(() => expect(comp.editingCondition).toBeNull());
      } finally {
         confirmSpy.mockRestore();
      }
   });

   it("should not remove when user cancels", async () => {
      const confirmSpy = vi.spyOn(ComponentTool, "showConfirmDialog").mockResolvedValue("cancel");
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      try {
         comp.deleteCondition(comp.conditions[0], 0);

         await waitFor(() => expect(confirmSpy).toHaveBeenCalled());
         expect(comp.conditions).toHaveLength(1);
      } finally {
         confirmSpy.mockRestore();
      }
   });
});

// ---------------------------------------------------------------------------
// Group 5 — editCondition
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — editCondition", () => {
   it("should set editingCondition and replace selectedConditions on plain click", async () => {
      const c1 = makeCondition({ name: "c1" });
      const c2 = makeCondition({ name: "c2" });
      const { comp } = await renderComp([c1, c2]);
      // c1 was auto-selected by setter; now click c2
      comp.editCondition(new MouseEvent("click"), comp.conditions[1]);

      expect(comp.editingCondition).toBe(comp.conditions[1]);
      expect(comp.selectedConditions).toHaveLength(1);
      expect(comp.selectedConditions[0]).toBe(comp.conditions[1]);
   });

   it("should append to selectedConditions when ctrlKey is pressed", async () => {
      const c1 = makeCondition({ name: "c1" });
      const c2 = makeCondition({ name: "c2" });
      const { comp } = await renderComp([c1, c2]);
      // c1 is auto-selected; ctrl+click c2
      comp.editCondition(
         new MouseEvent("click", { ctrlKey: true }),
         comp.conditions[1]
      );

      expect(comp.selectedConditions).toHaveLength(2);
      expect(comp.editingCondition).toBe(comp.conditions[1]);
   });

   it("should update existsNames to all condition names except the edited one", async () => {
      const c1 = makeCondition({ name: "alpha" });
      const c2 = makeCondition({ name: "beta" });
      const c3 = makeCondition({ name: "gamma" });
      const { comp } = await renderComp([c1, c2, c3]);
      // editing c2 → existsNames should be [alpha, gamma]
      comp.editCondition(new MouseEvent("click"), comp.conditions[1]);

      expect(comp.existsNames).toContain("alpha");
      expect(comp.existsNames).toContain("gamma");
      expect(comp.existsNames).not.toContain("beta");
   });

   it("should emit refreshedColumns(false) immediately when editCondition is called", async () => {
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      const emitted: boolean[] = [];
      comp.refreshedColumns.subscribe(v => emitted.push(v));

      comp.editCondition(new MouseEvent("click"), comp.conditions[0]);

      // first emission is false (before HTTP)
      expect(emitted[0]).toBe(false);
   });
});

// ---------------------------------------------------------------------------
// Group 6 — refreshColumns [Risk 2]
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — refreshColumns", () => {
   it("should not make any HTTP request when tableName is empty", async () => {
      const postSpy = vi.fn();
      server.use(http.post("*/api/data/vpm/columns/*", () => { postSpy(); return MswHttpResponse.json([]); }));
      const c1 = makeCondition({ tableName: "" });
      const { comp } = await renderComp([c1]);

      comp.editCondition(new MouseEvent("click"), comp.conditions[0]);

      // no HTTP call, currentColumns remains empty
      expect(comp.currentColumns).toHaveLength(0);
      expect(postSpy).not.toHaveBeenCalled();
   });

   it("should POST to TABLE_COLUMNS_URI for TABLE type and update currentColumns", async () => {
      const cols = [{ name: "id", type: "integer" }, { name: "name", type: "string" }];
      server.use(http.post("*/api/data/vpm/columns/*", () => MswHttpResponse.json(cols)));
      const c1 = makeCondition({ tableName: "CUSTOMER", type: ConditionTypes.TABLE });
      const { comp } = await renderComp([c1]);

      await waitFor(() => expect(comp.currentColumns).toHaveLength(2));
      expect(comp.currentColumns[0].name).toBe("id");
   });

   it("should GET from physicalModel/tables for PHYSICAL_MODEL type", async () => {
      const cols = [{ name: "pm_col", type: "string" }];
      server.use(http.get("*/api/data/vpm/physicalModel/tables", () => MswHttpResponse.json(cols)));
      const c1 = makeCondition({
         tableName: "PMTable",
         type: ConditionTypes.PHYSICAL_MODEL,
      });
      const { comp } = await renderComp([c1]);

      await waitFor(() => expect(comp.currentColumns).toHaveLength(1));
      expect(comp.currentColumns[0].name).toBe("pm_col");
   });

   it("should set refreshingColumns to false after HTTP completes", async () => {
      server.use(http.post("*/api/data/vpm/columns/*", () => MswHttpResponse.json([])));
      const c1 = makeCondition({ tableName: "T", type: ConditionTypes.TABLE });
      const { comp } = await renderComp([c1]);

      await waitFor(() => expect(comp.refreshingColumns).toBe(false));
   });

   it("should emit refreshedColumns(true) after HTTP completes when updateFieldTypes=true", async () => {
      server.use(http.post("*/api/data/vpm/columns/*", () => MswHttpResponse.json([])));
      const c1 = makeCondition({ tableName: "T", type: ConditionTypes.TABLE });
      const { comp } = await renderComp([c1]);
      const emitted: boolean[] = [];
      comp.refreshedColumns.subscribe(v => emitted.push(v));

      // editCondition calls refreshColumns(true)
      comp.editCondition(new MouseEvent("click"), comp.conditions[0]);

      await waitFor(() => expect(emitted).toContain(true));
   });
});

// ---------------------------------------------------------------------------
// Group 7 — chooseTable [Risk 2]
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — chooseTable", () => {
   it("should open NgbModal with the chooseTableDialog template reference", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "OldTable" });
      const { comp, modalMock } = await renderComp([c1]);
      const resultPromise = Promise.resolve("NewTable");
      modalMock.open.mockReturnValue({ result: resultPromise });

      comp.chooseTable();

      expect(modalMock.open).toHaveBeenCalledTimes(1);
   });

   it("should set tableName to the result and emit tableChange on success", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "OldTable" });
      const { comp, modalMock } = await renderComp([c1]);
      const tableChangeSpy = vi.fn();
      comp.tableChange.subscribe(tableChangeSpy);
      modalMock.open.mockReturnValue({ result: Promise.resolve("NewTable") });

      comp.chooseTable();

      await waitFor(() => expect(comp.editingCondition.tableName).toBe("NewTable"));
      expect(tableChangeSpy).toHaveBeenCalledTimes(1);
   });

   it("should restore old tableName on cancel", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "OldTable" });
      const { comp, modalMock } = await renderComp([c1]);
      modalMock.open.mockReturnValue({ result: Promise.reject("dismissed") });

      comp.chooseTable();

      await waitFor(() => expect(comp.editingCondition.tableName).toBe("OldTable"));
   });

   it("should revert type to TABLE when typeChange=true and user cancels (was TABLE)", async () => {
      // When typeChange, editingCondition.type is the NEW type (PHYSICAL_MODEL).
      // On cancel, it should revert back to the original type (TABLE).
      const c1 = makeCondition({ name: "c1", tableName: "T", type: ConditionTypes.TABLE });
      const { comp, modalMock } = await renderComp([c1]);
      // Simulate: user changed type to PHYSICAL_MODEL, triggering chooseTable(true)
      // The component temporarily clears tableName and then reverts on cancel.
      comp.editingCondition.type = ConditionTypes.PHYSICAL_MODEL;
      modalMock.open.mockReturnValue({ result: Promise.reject("dismissed") });

      comp.chooseTable(true);

      // type should be reverted back from PHYSICAL_MODEL to TABLE
      await waitFor(() => expect(comp.editingCondition.type).toBe(ConditionTypes.TABLE));
   });
});

// ---------------------------------------------------------------------------
// Group 8 — editClauses
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — editClauses", () => {
   it("should update editingCondition.clauses with the modal result", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "T" });
      const { comp, modalMock } = await renderComp([c1]);
      const newClauses = [{ junc: false, value1: {}, value2: {}, value3: {} }];
      modalMock.open.mockReturnValue({ result: Promise.resolve(newClauses) });

      comp.editClauses();

      await waitFor(() => expect(comp.editingCondition.clauses).toBe(newClauses));
   });

   it("should not change clauses when the modal is dismissed", async () => {
      const originalClause = makeClause();
      // tableName="" so refreshColumns short-circuits; ClausePipe requires proper clause structure
      const c1 = makeCondition({ name: "c1", tableName: "", clauses: [originalClause] });
      const { comp, modalMock } = await renderComp([c1]);
      modalMock.open.mockReturnValue({ result: Promise.reject("dismissed") });

      comp.editClauses();

      await waitFor(() => expect(comp.editingCondition.clauses).toHaveLength(1));
      expect(comp.editingCondition.clauses[0]).toBe(originalClause);
   });
});

// ---------------------------------------------------------------------------
// Group 9 — utility methods
// ---------------------------------------------------------------------------

describe("VPMConditionsComponent — utility methods", () => {
   it("expressionChanged should set editingCondition.script to the given value", async () => {
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);

      comp.expressionChanged("return true;");

      expect(comp.editingCondition.script).toBe("return true;");
   });

   it("clearClauses should set editingCondition.clauses to []", async () => {
      const clause = makeClause();
      const c1 = makeCondition({ name: "c1", clauses: [clause] });
      const { comp } = await renderComp([c1]);

      comp.clearClauses();

      expect(comp.editingCondition.clauses).toHaveLength(0);
   });

   it("tableNameNull should return true when tableName is empty", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "" });
      const { comp } = await renderComp([c1]);

      expect(comp.tableNameNull).toBe(true);
   });

   it("tableNameNull should return false when tableName is non-empty", async () => {
      const c1 = makeCondition({ name: "c1", tableName: "MyTable" });
      const { comp } = await renderComp([c1]);

      expect(comp.tableNameNull).toBe(false);
   });

   it("isSelected should return true when condition is in selectedConditions", async () => {
      const c1 = makeCondition({ name: "c1" });
      const { comp } = await renderComp([c1]);
      // conditions setter auto-selected c1
      expect(comp.isSelected(comp.conditions[0])).toBe(true);
   });

   it("isSelected should return false when condition is not in selectedConditions", async () => {
      const c1 = makeCondition({ name: "c1" });
      const c2 = makeCondition({ name: "c2" });
      const { comp } = await renderComp([c1, c2]);
      // Only c1 is auto-selected
      expect(comp.isSelected(comp.conditions[1])).toBe(false);
   });
});
