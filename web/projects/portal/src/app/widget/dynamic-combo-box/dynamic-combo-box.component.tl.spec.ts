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
 * DynamicComboBox — single pass (+memory leak)
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] — isVariableEnabled() + selectType VARIABLE guard (Bug #17341, #17765)
 *   Group 2 [Risk 3] — value setter -> type detection (Bug #19027)
 *   Group 3 [Risk 3] — selectType EXPRESSION queues showFormulaEditor via setTimeout (+memory leak)
 *   Group 4 [Risk 2] — getInputClass: grayed-out detection and colon-to-dot normalisation
 *   Group 5 [Risk 2] — selectValue() updates _value and emits valueChange
 *   Group 6 [Risk 2] — getCurrentValue() branching: editable / label / values lookup
 *   Group 7 [Risk 1] — isValueEnabled, isExampleEnable, isValuesDefinedAndNotEmpty
 *
 * Old spec ported (Risk 3):
 *   Bug #17341: isVariableEnabled() = false when variables = []
 *   Bug #17765: selectType(VARIABLE) is a no-op when isVariableEnabled() is false
 *   Bug #19027: value "$(var1)" -> type stays VARIABLE after ngOnInit
 *
 * Confirmed bugs (it.fails):
 *   Bug — setTimeout leak (Group 3): selectType(EXPRESSION) queues a 0ms timer that fires on the
 *     dead component because there is no ngOnDestroy to cancel it. Fix: add ngOnDestroy that stores
 *     the timer ID and calls clearTimeout on destroy.
 *
 * Out of scope:
 *   showFormulaEditor() — opens NgbModal overlay; integration-level, requires live modal DOM.
 *   closeDropdowns() — delegates to ViewChildren QueryList; no DOM in test.
 *   nodesSelected() — delegates to selectValue + closeDropdowns; covered transitively.
 *   createValueTree() / getTreeColTooltip() / selectedNodes — tree helpers, Risk 1, display-only.
 *   dropdownMinWidth getter — reads nativeElement.offsetWidth; DOM measurement.
 *   onChanged() — native <select> event delegation; covered by e2e.
 *   getDisplayClass() — depends on complex val object shape; Risk 1, display-only.
 *   ngOnChanges normalColumn branch — re-calls updateType, covered transitively by value setter tests.
 */

import { NO_ERRORS_SCHEMA } from "@angular/core";
import { render } from "@testing-library/angular";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { DynamicComboBox } from "./dynamic-combo-box.component";
import { ComboMode } from "./dynamic-combo-box-model";
import { ComponentTool } from "../../common/util/component-tool";

const MODAL_MOCK = { open: vi.fn() };

async function renderComponent(props: Partial<Record<string, any>> = {}) {
   const { fixture } = await render(DynamicComboBox, {
      schemas: [NO_ERRORS_SCHEMA],
      providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
      componentProperties: { variables: [], values: [], grayedOutValues: [], ...props },
   });
   return fixture.componentInstance as DynamicComboBox;
}

afterEach(() => vi.restoreAllMocks());

// ---------------------------------------------------------------------------
// Group 1: isVariableEnabled() + selectType VARIABLE guard [Risk 3]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — isVariableEnabled and selectType VARIABLE guard", () => {
   // 🔁 Regression-sensitive (Bug #17341): when the viewsheet has no variables, the variable
   //    option must be disabled; removing this guard lets users pick a non-existent variable.
   it("should return false from isVariableEnabled when variables is an empty array", async () => {
      const comp = await renderComponent({ variables: [] });
      expect(comp.isVariableEnabled()).toBe(false);
   });

   it("should return true from isVariableEnabled when variables are provided", async () => {
      const comp = await renderComponent({ variables: ["$(var1)", "$(var2)"] });
      expect(comp.isVariableEnabled()).toBe(true);
   });

   // 🔁 Regression-sensitive (Bug #17765): clicking the variable type option when there are no
   //    variables must be a no-op; without the guard, _value becomes undefined and breaks bindings.
   it("should be a no-op (undefined return) when selectType is called with VARIABLE and no variables", async () => {
      const comp = await renderComponent({ variables: [] });
      const spy = vi.fn();
      comp.typeChange.subscribe(spy);

      const result = comp.selectType(new MouseEvent("click"), ComboMode.VARIABLE);

      expect(result).toBeUndefined();
      expect(spy).not.toHaveBeenCalled();
   });

   it("should change type when selectType is called with VARIABLE and variables are available", async () => {
      const comp = await renderComponent({ variables: ["$(var1)"], type: ComboMode.VALUE });
      const emitted: ComboMode[] = [];
      comp.typeChange.subscribe(t => emitted.push(t));

      comp.selectType(new MouseEvent("click"), ComboMode.VARIABLE);
      // typeChange is EventEmitter(true) — async; emission delivered via setTimeout(0)
      await new Promise(r => setTimeout(r, 0));

      expect(comp.type).toBe(ComboMode.VARIABLE);
      expect(emitted).toContain(ComboMode.VARIABLE);
   });
});

// ---------------------------------------------------------------------------
// Group 2: value setter -> type detection [Risk 3]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — value setter type detection", () => {
   // 🔁 Regression-sensitive (Bug #19027): when a variable is pre-selected, the type must
   //    be detected as VARIABLE; regression causes the type to reset to VALUE, losing the binding.
   it("should detect VARIABLE type for a value matching $(name) pattern (Bug #19027)", async () => {
      const comp = await renderComponent({ variables: ["$(var1)"] });
      comp.value = "$(var1)";
      expect(comp.type).toBe(ComboMode.VARIABLE);
   });

   it("should detect EXPRESSION type for a value starting with =", async () => {
      const comp = await renderComponent();
      comp.value = "=SUM(field)";
      expect(comp.type).toBe(ComboMode.EXPRESSION);
   });

   it("should detect VALUE type for a plain string", async () => {
      const comp = await renderComponent();
      comp.value = "plainValue";
      expect(comp.type).toBe(ComboMode.VALUE);
   });

   it("should detect VALUE type for null", async () => {
      const comp = await renderComponent();
      comp.value = null;
      expect(comp.type).toBe(ComboMode.VALUE);
   });

   it("should keep VARIABLE type after ngOnInit when value is a variable", async () => {
      const comp = await renderComponent({ variables: ["$(var1)", "$(var2)"] });
      comp.value = "$(var1)";
      comp.ngOnInit();
      expect(comp.type).toBe(ComboMode.VARIABLE);
   });
});

// ---------------------------------------------------------------------------
// Group 3: selectType EXPRESSION -> setTimeout showFormulaEditor [Risk 3]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — selectType EXPRESSION deferred dialog (+memory leak)", () => {
   // 🔁 Regression-sensitive: switching to EXPRESSION must open the formula editor;
   //    the editor is deferred via setTimeout so other state changes can settle first.
   //    Memory leak: there is no ngOnDestroy to cancel the timer, so showFormulaEditor
   //    will fire even if the component is destroyed before the timer expires.
   it("should schedule showFormulaEditor via a zero-delay timer when switching to EXPRESSION", async () => {
      const comp = await renderComponent({ type: ComboMode.VALUE, variables: [] });
      const showSpy = vi.spyOn(comp, "showFormulaEditor").mockImplementation(() => {});

      vi.useFakeTimers();
      comp.selectType(new MouseEvent("click"), ComboMode.EXPRESSION);

      expect(showSpy).not.toHaveBeenCalled(); // not fired synchronously
      vi.advanceTimersByTime(1);
      expect(showSpy).toHaveBeenCalledOnce();
      vi.useRealTimers();
   });

   it("should NOT schedule showFormulaEditor when the type does not change", async () => {
      // ngOnInit calls updateType(this.value); set value="=expr" so type stays EXPRESSION
      const comp = await renderComponent({ value: "=expr" });
      const showSpy = vi.spyOn(comp, "showFormulaEditor").mockImplementation(() => {});

      vi.useFakeTimers();
      comp.selectType(new MouseEvent("click"), ComboMode.EXPRESSION);
      vi.advanceTimersByTime(1);

      expect(showSpy).not.toHaveBeenCalled();
      vi.useRealTimers();
   });

   // Bug: DynamicComboBox has no ngOnDestroy; the 0ms timer queued by selectType(EXPRESSION)
   // fires on the dead component.  Fix: add ngOnDestroy that clears the pending timer ID.
   it.fails("should not invoke showFormulaEditor after the component is destroyed", async () => {
      const { fixture } = await render(DynamicComboBox, {
         schemas: [NO_ERRORS_SCHEMA],
         providers: [{ provide: NgbModal, useValue: MODAL_MOCK }],
         componentProperties: { variables: [], values: [], grayedOutValues: [], type: ComboMode.VALUE },
      });
      const comp = fixture.componentInstance as DynamicComboBox;
      const showSpy = vi.spyOn(comp, "showFormulaEditor").mockImplementation(() => {});

      vi.useFakeTimers();
      comp.selectType(new MouseEvent("click"), ComboMode.EXPRESSION);
      fixture.destroy(); // component destroyed, timer still pending
      vi.advanceTimersByTime(1); // timer fires on dead component
      expect(showSpy).not.toHaveBeenCalled(); // currently FAILS — proves the leak
      vi.useRealTimers();
   });
});

// ---------------------------------------------------------------------------
// Group 4: getInputClass [Risk 2]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — getInputClass", () => {
   it("should return 'grayed-out-field' when the value is in grayedOutValues", async () => {
      const comp = await renderComponent({ grayedOutValues: ["optionA"] });
      expect(comp.getInputClass("optionA")).toBe("grayed-out-field");
   });

   it("should normalise colon to dot before checking grayedOutValues", async () => {
      // "table:column" -> "table.column" before lookup
      const comp = await renderComponent({ grayedOutValues: ["table.column"] });
      expect(comp.getInputClass("table:column")).toBe("grayed-out-field");
   });

   it("should return empty string when the value is not in grayedOutValues", async () => {
      const comp = await renderComponent({ grayedOutValues: ["other"] });
      expect(comp.getInputClass("visibleOption")).toBe("");
   });
});

// ---------------------------------------------------------------------------
// Group 5: selectValue [Risk 2]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — selectValue", () => {
   it("should emit valueChange with the choice value when choice has a value property", async () => {
      const comp = await renderComponent();
      const emitted: any[] = [];
      comp.valueChange.subscribe(v => emitted.push(v));

      comp.selectValue({ value: "myValue", label: "My Label" });

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe("myValue");
   });

   it("should emit valueChange with the choice itself when no value property", async () => {
      const comp = await renderComponent();
      const emitted: any[] = [];
      comp.valueChange.subscribe(v => emitted.push(v));

      comp.selectValue("rawChoice");

      expect(emitted).toHaveLength(1);
      expect(emitted[0]).toBe("rawChoice");
   });

   it("should update the label property when label is set and choice has a label", async () => {
      const comp = await renderComponent({ label: "OldLabel" });
      comp.selectValue({ value: "v", label: "NewLabel" });
      expect(comp.label).toBe("NewLabel");
   });
});

// ---------------------------------------------------------------------------
// Group 6: getCurrentValue [Risk 2]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — getCurrentValue", () => {
   it("should return value directly when editable=true", async () => {
      const comp = await renderComponent({ editable: true });
      comp.value = "editedValue";
      expect(comp.getCurrentValue()).toBe("editedValue");
   });

   it("should return label when label is set and editable=false", async () => {
      const comp = await renderComponent({ label: "My Label", editable: false });
      expect(comp.getCurrentValue()).toBe("My Label");
   });

   it("should return value when values is null and editable=false", async () => {
      const comp = await renderComponent({ values: null, editable: false, label: null });
      comp.value = "direct";
      expect(comp.getCurrentValue()).toBe("direct");
   });

   it("should return the matching choice label when values contains a match", async () => {
      const comp = await renderComponent({
         values: [{ value: "v1", label: "First" }],
         editable: false,
         label: null,
      });
      comp.value = "v1";
      expect(comp.getCurrentValue()).toBe("First");
   });

   it("should return value when no matching choice is found", async () => {
      const comp = await renderComponent({
         values: [{ value: "other", label: "Other" }],
         editable: false,
         label: null,
      });
      comp.value = "noMatch";
      expect(comp.getCurrentValue()).toBe("noMatch");
   });
});

// ---------------------------------------------------------------------------
// Group 7: isValueEnabled, isExampleEnable, isValuesDefinedAndNotEmpty [Risk 1]
// ---------------------------------------------------------------------------

describe("DynamicComboBox — boolean helpers", () => {
   it("should return true from isValueEnabled for a normal string", async () => {
      const comp = await renderComponent();
      expect(comp.isValueEnabled("regular")).toBe(true);
   });

   it("should return false from isValueEnabled for '(Target Formula)' when enableFormulaLabel=false", async () => {
      const comp = await renderComponent({ enableFormulaLabel: false, label: null });
      expect(comp.isValueEnabled("(Target Formula)")).toBe(false);
   });

   it("should return true from isExampleEnable when examples has a truthy item at the index", async () => {
      const comp = await renderComponent({ examples: ["ex1", "ex2"] });
      expect(comp.isExampleEnable(0)).toBe(true);
   });

   it("should return false from isExampleEnable when examples is null", async () => {
      const comp = await renderComponent({ examples: null });
      expect(comp.isExampleEnable(0)).toBe(false);
   });

   it("should return true from isValuesDefinedAndNotEmpty when values has items", async () => {
      const comp = await renderComponent({ values: ["a", "b"] });
      expect(comp.isValuesDefinedAndNotEmpty()).toBe(true);
   });

   it("should return false from isValuesDefinedAndNotEmpty when values is empty", async () => {
      const comp = await renderComponent({ values: [] });
      expect(comp.isValuesDefinedAndNotEmpty()).toBe(false);
   });
});
