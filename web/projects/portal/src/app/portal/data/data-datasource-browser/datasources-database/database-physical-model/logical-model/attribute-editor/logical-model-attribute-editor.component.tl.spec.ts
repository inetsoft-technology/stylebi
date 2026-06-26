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
 * LogicalModelAttributeEditor — Single Pass (+ Memory Leak)
 *
 * Coverage plan:
 *   Group 1  — ngOnInit: POST to COLUMNS_URI; form controls added
 *   Group 2  — ngOnDestroy / memory-leak: clearTimeout cancels pending scheduleReset timer
 *   Group 3  — existNames setter post-init: scheduleReset defers resetFormControl via setTimeout
 *   Group 4  — updateFormulas: formula list matches attribute dataType
 *   Group 5  — findSelectFormula: search in currentFormulas, fallback to defaultRefTypes[0]
 *   Group 6  — selectFormula: updates selectedFormula, selectedFormulaLabel, attribute.refType
 *   Group 7  — drillString getter: "None" or joined path names
 *   Group 8  — iconFunction: correct icon class per dataType
 *   Group 9  — selectPhysicalColumn: updates attribute fields and selectedNode
 *   Group 10 — onAttributeChanged: form valueChanges triggers emit
 */

import { HttpClientTestingModule, HttpTestingController } from "@angular/common/http/testing";
import { Component, NO_ERRORS_SCHEMA } from "@angular/core";
import { TestBed } from "@angular/core/testing";
import { FormsModule, ReactiveFormsModule, UntypedFormGroup } from "@angular/forms";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { render } from "@testing-library/angular";
import { vi } from "vitest";

import { XSchema } from "../../../../../../../common/data/xschema";
import { AttributeModel } from "../../../../../model/datasources/database/physical-model/logical-model/attribute-model";
import { TreeNodeModel } from "../../../../../../../widget/tree/tree-node-model";
import { AttributeFormattingPane } from "./format-dialog/attribute-formatting-pane.component";
import { DropdownView } from "../../../../../../../widget/dropdown-view/dropdown-view.component";
import { TreeDropdownComponent } from "../../../../../../../widget/tree/tree-dropdown.component";
import { LogicalModelAttributeEditor } from "./logical-model-attribute-editor.component";

// ---------------------------------------------------------------------------
// Stubs — prevent deep DI chains of heavy child components
// ---------------------------------------------------------------------------

@Component({ selector: "tree-dropdown", template: "", standalone: true })
class TreeDropdownStub {}

@Component({ selector: "dropdown-view", template: "", standalone: true })
class DropdownViewStub {}

@Component({ selector: "attribute-formatting-pane", template: "", standalone: true })
class AttributeFormattingPaneStub {}

// ---------------------------------------------------------------------------
// Factory helpers
// ---------------------------------------------------------------------------

function makeAttribute(overrides: Partial<AttributeModel> = {}): AttributeModel {
   return {
      name: "testAttr",
      description: "",
      baseElement: false,
      elementType: "attribute",
      oldName: "testAttr",
      visible: true,
      table: "TestTable",
      dataType: XSchema.STRING,
      qualifiedName: "TestTable.testAttr",
      column: "testAttr",
      format: null,
      drillInfo: null,
      browseData: false,
      aggregate: false,
      parseable: false,
      refType: { formula: "None", type: 0 },
      ...overrides,
   } as AttributeModel;
}

function makeColumnsTree(): TreeNodeModel {
   return {
      label: "root",
      data: null,
      leaf: false,
      children: [
         {
            label: "TestTable",
            data: "TestTable",
            leaf: false,
            children: [
               {
                  label: "testAttr",
                  data: {
                     qualifiedName: "TestTable.testAttr",
                     table: "TestTable",
                     name: "testAttr",
                     dataType: XSchema.STRING,
                  },
                  leaf: true,
                  children: [],
               },
            ],
         },
      ],
   };
}

// ---------------------------------------------------------------------------
// Render helper — returns httpMock so tests control HTTP flushing
// ---------------------------------------------------------------------------

let httpMock: HttpTestingController;

async function renderEditor(
   attributeOverrides: Partial<AttributeModel> = {},
   formOverrides: UntypedFormGroup = null
) {
   const attribute = makeAttribute(attributeOverrides);
   const form = formOverrides ?? new UntypedFormGroup({});

   const result = await render(LogicalModelAttributeEditor, {
      inputs: { form, attribute, databaseName: "testDb", physicalModelName: "pm", logicalModelName: "lm" },
      imports: [HttpClientTestingModule, FormsModule, ReactiveFormsModule],
      providers: [
         { provide: NgbModal, useValue: { open: vi.fn() } },
      ],
      importOverrides: [
         { replace: TreeDropdownComponent, with: TreeDropdownStub },
         { replace: DropdownView, with: DropdownViewStub },
         { replace: AttributeFormattingPane, with: AttributeFormattingPaneStub },
      ],
      schemas: [NO_ERRORS_SCHEMA],
   });

   httpMock = TestBed.inject(HttpTestingController);

   return {
      comp: result.fixture.componentInstance as LogicalModelAttributeEditor,
      fixture: result.fixture,
   };
}

/** Flush the two HTTP requests created on every render. */
function flushInitHttp() {
   httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
   httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes")).flush(makeColumnsTree());
}

// ---------------------------------------------------------------------------
// Lifecycle
// ---------------------------------------------------------------------------

afterEach(() => {
   vi.restoreAllMocks();
   httpMock?.verify();
});

// ===========================================================================
// Group 1 — ngOnInit
// ===========================================================================

describe("Group 1 — ngOnInit", () => {
   it("should POST to COLUMNS_URI on init", async () => {
      await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      const req = httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes"));
      expect(req.request.method).toBe("POST");
      req.flush(null);
   });

   it("should add name, dataType, refType controls to the form after ngOnInit", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.form.contains("name")).toBe(true);
      expect(comp.form.contains("dataType")).toBe(true);
      expect(comp.form.contains("refType")).toBe(true);
   });

   it("should set columnsTree when COLUMNS_URI response arrives", async () => {
      const { comp, fixture } = await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes")).flush(makeColumnsTree());
      fixture.detectChanges();
      expect(comp.columnsTree?.children?.[0]?.label).toBe("TestTable");
   });

   it("should set columnsTree to null when COLUMNS_URI errors", async () => {
      const { comp, fixture } = await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes"))
         .flush("error", { status: 500, statusText: "Server Error" });
      fixture.detectChanges();
      expect(comp.columnsTree).toBeNull();
   });
});

// ===========================================================================
// Group 2 — ngOnDestroy / memory-leak
// ===========================================================================

// Bypass: resetFormControl is private — accessed via (comp as any) to spy on the timer callback.
// Cross-reference: Group 3 tests that scheduleReset defers the call; this group tests destroy cancels it.
describe("Group 2 — ngOnDestroy / memory-leak", () => {
   it("should cancel pending scheduleReset timer on destroy so resetFormControl does not fire after", async () => {
      const { comp, fixture } = await renderEditor();
      flushInitHttp();

      vi.useFakeTimers();
      try {
         const resetSpy = vi.spyOn(comp as any, "resetFormControl").mockImplementation(() => {});
         try {
            comp.existNames = ["otherName"]; // post-init → scheduleReset() → setTimeout queued
            fixture.destroy();              // ngOnDestroy → clearTimeout(resetPending)
            vi.runAllTimers();              // if timer was cleared, callback should not fire
            expect(resetSpy).not.toHaveBeenCalled();
         } finally {
            resetSpy.mockRestore();
         }
      } finally {
         vi.useRealTimers();
      }
   });

   it("should unsubscribe from form valueChanges on destroy so onAttributeChanged is not emitted", async () => {
      const { comp, fixture } = await renderEditor();
      flushInitHttp();

      fixture.destroy(); // ngOnDestroy → unsubscribeForm()

      const emitSpy = vi.spyOn(comp.onAttributeChanged, "emit");
      try {
         comp.form.patchValue({ name: "changed" });
         expect(emitSpy).not.toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });
});

// ===========================================================================
// Group 3 — existNames setter post-init
// ===========================================================================

// Bypass: resetFormControl is private — accessed via (comp as any) to verify scheduling behavior.
// Cross-reference: Group 2 tests that ngOnDestroy cancels any pending scheduleReset timer.
describe("Group 3 — existNames setter post-init (scheduleReset)", () => {
   it("should defer resetFormControl via setTimeout when existNames is set after init", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      // At this point inited=true

      vi.useFakeTimers();
      try {
         const resetSpy = vi.spyOn(comp as any, "resetFormControl").mockImplementation(() => {});
         try {
            comp.existNames = ["otherName"];
            expect(resetSpy).not.toHaveBeenCalled(); // not called yet — timer pending
            vi.runAllTimers();
            expect(resetSpy).toHaveBeenCalledTimes(1);
         } finally {
            resetSpy.mockRestore();
         }
      } finally {
         vi.useRealTimers();
      }
   });

   it("should coalesce multiple rapid existNames sets into a single resetFormControl call", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();

      vi.useFakeTimers();
      try {
         const resetSpy = vi.spyOn(comp as any, "resetFormControl").mockImplementation(() => {});
         try {
            comp.existNames = ["a"];
            comp.existNames = ["a", "b"];
            comp.existNames = ["a", "b", "c"];
            vi.runAllTimers();
            expect(resetSpy).toHaveBeenCalledTimes(1); // coalesced via clearTimeout + setTimeout
         } finally {
            resetSpy.mockRestore();
         }
      } finally {
         vi.useRealTimers();
      }
   });
});

// ===========================================================================
// Group 4 — updateFormulas
// ===========================================================================

describe("Group 4 — updateFormulas via attribute dataType", () => {
   it("should set dateFormulas for DATE dataType", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.DATE });
      flushInitHttp();
      // dateFormulas contains None/Max/Min/Count/DistinctCount
      expect(comp.currentFormulas.some(f => f.label.includes("Max"))).toBe(true);
      expect(comp.currentFormulas.some(f => f.label.includes("Sum"))).toBe(false);
   });

   it("should set stringFormulas for STRING dataType", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.STRING });
      flushInitHttp();
      // stringFormulas includes Concat
      expect(comp.currentFormulas.some(f => f.label.includes("Concat"))).toBe(true);
   });

   it("should set boolFormulas for BOOLEAN dataType", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.BOOLEAN });
      flushInitHttp();
      // boolFormulas only has None/Count/DistinctCount
      expect(comp.currentFormulas.length).toBe(3);
      expect(comp.currentFormulas.some(f => f.label.includes("Sum"))).toBe(false);
   });

   it("should set numberFormulas for numeric (DOUBLE) dataType", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.DOUBLE });
      flushInitHttp();
      // numberFormulas includes Sum/Average/StandardDeviation etc.
      expect(comp.currentFormulas.some(f => f.label.includes("Sum"))).toBe(true);
      expect(comp.currentFormulas.some(f => f.label.includes("StandardDeviation"))).toBe(true);
   });
});

// ===========================================================================
// Group 5 — findSelectFormula
// ===========================================================================

describe("Group 5 — findSelectFormula", () => {
   it("should find a formula in currentFormulas when dataType matches", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.DOUBLE });
      flushInitHttp();
      const sumData = { formula: "Sum", type: 2 };
      const result = comp.findSelectFormula(sumData);
      expect(result.label).toContain("Sum");
   });

   it("should fall back to defaultRefTypes when not in currentFormulas", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.STRING });
      flushInitHttp();
      const noneData = { formula: "None", type: 0 };
      const result = comp.findSelectFormula(noneData);
      // { formula: "None", type: 0 } is in defaultRefTypes (not currentFormulas for strings)
      expect(result?.data?.formula).toBe("None");
   });

   it("should return defaultRefTypes[0] when not found anywhere", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      const unknownData = { formula: "Unknown", type: 999 };
      const result = comp.findSelectFormula(unknownData);
      // Bypass: defaultRefTypes is private; referenced here to avoid hard-coding the fallback value.
      expect(result).toEqual(comp["defaultRefTypes"][0]);
   });
});

// ===========================================================================
// Group 6 — selectFormula
// ===========================================================================

describe("Group 6 — selectFormula", () => {
   it("should set selectedFormula and attribute.refType to ref.data", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.DOUBLE });
      flushInitHttp();
      const sumRef = { label: "_#(js:Sum)", data: { formula: "Sum", type: 2 } };
      comp.selectFormula(sumRef);
      expect(comp.selectedFormula).toBe(sumRef.data);
      expect(comp.attribute.refType).toBe(sumRef.data);
   });

   it("should include 'Measure' i18n token prefix in selectedFormulaLabel for non-default ref types", async () => {
      const { comp } = await renderEditor({ dataType: XSchema.DOUBLE });
      flushInitHttp();
      const sumRef = { label: "_#(js:Sum)", data: { formula: "Sum", type: 2 } };
      comp.selectFormula(sumRef);
      // The template i18n token for "Measure:" is preserved literally in test environment
      expect(comp.selectedFormulaLabel).toContain("_#(js:Measure):");
   });

   it("should NOT include 'Measure:' prefix for default ref types like None", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      const noneRef = { label: "_#(js:None)", data: { formula: "None", type: 0 } };
      comp.selectFormula(noneRef);
      expect(comp.selectedFormulaLabel).not.toContain("Measure:");
   });

   it("should call dropdown.close() when dropdown is provided", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      const dropdown = { close: vi.fn() } as any;
      comp.selectFormula(comp["defaultRefTypes"][0], dropdown);
      expect(dropdown.close).toHaveBeenCalledTimes(1);
   });
});

// ===========================================================================
// Group 7 — drillString getter
// ===========================================================================

describe("Group 7 — drillString getter", () => {
   it("should return 'None' when drillInfo is null", async () => {
      const { comp } = await renderEditor({ drillInfo: null });
      flushInitHttp();
      expect(comp.drillString).toBe("None");
   });

   it("should return 'None' when drillInfo.paths is empty", async () => {
      const { comp } = await renderEditor({ drillInfo: { paths: [] } as any });
      flushInitHttp();
      expect(comp.drillString).toBe("None");
   });

   it("should return comma-joined path names when drillInfo.paths is non-empty", async () => {
      const { comp } = await renderEditor({
         drillInfo: {
            paths: [{ name: "PathA" }, { name: "PathB" }],
         } as any,
      });
      flushInitHttp();
      expect(comp.drillString).toBe("PathA, PathB");
   });
});

// ===========================================================================
// Group 8 — iconFunction
// ===========================================================================

describe("Group 8 — iconFunction", () => {
   it("should return data-table-icon for null node", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction(null)).toBe("data-table-icon");
   });

   it("should return data-table-icon for node with no data", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: null } as TreeNodeModel)).toBe("data-table-icon");
   });

   it("should return date-field-icon for DATE dataType", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: { dataType: XSchema.DATE } } as TreeNodeModel))
         .toBe("date-field-icon");
   });

   it("should return datetime-field-icon for TIME_INSTANT dataType", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: { dataType: XSchema.TIME_INSTANT } } as TreeNodeModel))
         .toContain("datetime-field-icon");
   });

   it("should return text-field-icon for STRING dataType", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: { dataType: XSchema.STRING } } as TreeNodeModel))
         .toContain("text-field-icon");
   });

   it("should return number-field-icon for DOUBLE dataType", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: { dataType: XSchema.DOUBLE } } as TreeNodeModel))
         .toContain("number-field-icon");
   });

   it("should return boolean-field-icon for BOOLEAN dataType", async () => {
      const { comp } = await renderEditor();
      flushInitHttp();
      expect(comp.iconFunction({ data: { dataType: XSchema.BOOLEAN } } as TreeNodeModel))
         .toContain("boolean-field-icon");
   });
});

// ===========================================================================
// Group 9 — selectPhysicalColumn
// ===========================================================================

describe("Group 9 — selectPhysicalColumn", () => {
   it("should update attribute qualifiedName, table, and column from node data", async () => {
      const { comp, fixture } = await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes")).flush(makeColumnsTree());
      fixture.detectChanges();

      const targetNode: TreeNodeModel = {
         label: "colB",
         leaf: true,
         children: [],
         data: {
            qualifiedName: "OtherTable.colB",
            table: "OtherTable",
            name: "colB",
            dataType: XSchema.DOUBLE,
         },
      };

      comp.selectPhysicalColumn(targetNode);

      expect(comp.attribute.qualifiedName).toBe("OtherTable.colB");
      expect(comp.attribute.table).toBe("OtherTable");
      expect(comp.attribute.column).toBe("colB");
   });

   it("should set selectedNode to the target node", async () => {
      const { comp, fixture } = await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes")).flush(makeColumnsTree());
      fixture.detectChanges();

      const targetNode: TreeNodeModel = {
         label: "colB",
         leaf: true,
         children: [],
         data: { qualifiedName: "OtherTable.colB", table: "OtherTable", name: "colB", dataType: XSchema.INTEGER },
      };

      comp.selectPhysicalColumn(targetNode);
      expect(comp.selectedNode).toBe(targetNode);
   });

   it("should call updateDataType with the node dataType when a matching type is found", async () => {
      const { comp, fixture } = await renderEditor();
      httpMock.expectOne(req => req.url.includes("logicalModel/attribute/format")).flush(null);
      httpMock.expectOne(req => req.url.includes("logicalModel/tables/nodes")).flush(makeColumnsTree());
      fixture.detectChanges();

      const targetNode: TreeNodeModel = {
         label: "colB",
         leaf: true,
         children: [],
         data: { qualifiedName: "OtherTable.colB", table: "OtherTable", name: "colB", dataType: XSchema.DOUBLE },
      };

      comp.selectPhysicalColumn(targetNode);
      expect(comp.attribute.dataType).toBe(XSchema.DOUBLE);
   });
});

// ===========================================================================
// Group 10 — onAttributeChanged emission
// ===========================================================================

describe("Group 10 — onAttributeChanged emission via form valueChanges", () => {
   it("should emit onAttributeChanged when a form control value changes", async () => {
      const { comp, fixture } = await renderEditor();
      flushInitHttp();
      fixture.detectChanges();

      const emitSpy = vi.spyOn(comp.onAttributeChanged, "emit");
      try {
         comp.form.get("name").setValue("newName");
         expect(emitSpy).toHaveBeenCalled();
      } finally {
         emitSpy.mockRestore();
      }
   });
});
