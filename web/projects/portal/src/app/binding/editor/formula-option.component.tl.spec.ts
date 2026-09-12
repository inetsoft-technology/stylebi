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
 * FormulaOption — single pass
 *
 * Risk-first coverage:
 *   Group 1 [Risk 2] — ngOnInit/initSecondaryColumn: secondary column init from aggregate
 *   Group 2 [Risk 3] — changeFormulaValue/fixSecondaryColumn: formula switch + warn on numeric loss
 *   Group 3 [Risk 2] — npValueChange/isNValid/hasN: N-parameter clamp and visibility
 *   Group 4 [Risk 2] — getAvailableFields: exclude CalculateRef from combobox options
 *   Group 5 [Risk 2] — bug regressions: #19370 expression secondary, #20322 formula enabled guard
 *
 * HTTP: no HTTP — binding editor local state only
 *
 * Out of scope:
 *   openFormulaDialog — opens modal, covered via changeFormulaValue side effects
 *   isPercentFormula — trivial getter, covered via changeFormulaValue branches
 */

import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { TestUtils } from "../../common/test/test-utils";
import { XSchema } from "../../common/data/xschema";
import { ComponentTool } from "../../common/util/component-tool";
import { BindingService } from "../services/binding.service";
import { AggregateFormula } from "../util/aggregate-formula";
import { FormulaOption } from "./formula-option.component";

function createFormulaOption(aggregate = TestUtils.createMockBAggregateRef("id")): FormulaOption {
   const comp = new FormulaOption(
      { assemblyName: "table1" } as BindingService,
      {} as NgbModal
   );
   comp.aggregate = aggregate;
   comp.availableFields = [
      TestUtils.createMockDataRef("state"),
      TestUtils.createMockDataRef("id")
   ];
   comp.formulaObjs = [{ label: "Sum", value: "Sum" }];
   return comp;
}

describe("FormulaOption — ngOnInit [Group 1, Risk 2]", () => {
   it("should populate availableValues and default numValue for Nth formula", () => {
      const comp = createFormulaOption();
      comp.aggregate.formula = AggregateFormula.NTH_LARGEST.formulaName;
      comp.aggregate.numValue = null;

      comp.ngOnInit();

      expect(comp.availableValues.length).toBeGreaterThan(0);
      expect(comp.aggregate.numValue).toBe("1");
      expect(comp.hasN()).toBe(true);
   });
});

describe("FormulaOption — changeFormulaValue [Group 2, Risk 3]", () => {
   it("should emit formulaChange and clear secondary column when switching away from with-formula", () => {
      const comp = createFormulaOption();
      comp.aggregate.formula = AggregateFormula.CORRELATION.formulaName;
      comp.aggregate.secondaryColumn = comp.availableFields[0];
      comp.aggregate.secondaryColumnValue = "state";
      const changed = vi.fn();
      comp.formulaChange.subscribe(changed);

      comp.changeFormulaValue(AggregateFormula.SUM.formulaName);

      expect(comp.aggregate.formula).toBe(AggregateFormula.SUM.formulaName);
      expect(comp.aggregate.secondaryColumn).toBeNull();
      expect(comp.aggregate.secondaryColumnValue).toBeNull();
      expect(changed).toHaveBeenCalled();
   });

   it("should warn when applying numeric formula to string aggregate", () => {
      const comp = createFormulaOption();
      comp.aggregate.formula = AggregateFormula.MAX.formulaName;
      comp.aggregate.dataType = XSchema.STRING;
      const warnSpy = vi.spyOn(ComponentTool, "showMessageDialog").mockImplementation(() => null as any);

      comp.changeFormulaValue(AggregateFormula.SUM.formulaName);

      expect(warnSpy).toHaveBeenCalled();
      warnSpy.mockRestore();
   });
});

describe("FormulaOption — npValueChange / isNValid [Group 3, Risk 2]", () => {
   it("should clamp invalid numeric input to 1", () => {
      const comp = createFormulaOption();
      comp.aggregate.formula = AggregateFormula.NTH_LARGEST.formulaName;

      comp.npValueChange("0");
      expect(comp.aggregate.numValue).toBe("1");

      comp.npValueChange("abc");
      expect(comp.aggregate.numValue).toBe("1");

      comp.npValueChange("3");
      expect(comp.aggregate.numValue).toBe("3");
      expect(comp.isNValid()).toBe(true);
   });

   it("should accept dynamic expression for numValue", () => {
      const comp = createFormulaOption();
      comp.npValueChange("={param}");

      expect(comp.aggregate.numValue).toBe("={param}");
      expect(comp.isNValid()).toBe(true);
   });
});

describe("FormulaOption — getAvailableFields [Group 4, Risk 2]", () => {
   it("should exclude aggregate CalculateRef without baseOnDetail from secondary column list", () => {
      const calcRef = TestUtils.createMockDataRef("calc1");
      calcRef.classType = "CalculateRef";
      (calcRef as any).baseOnDetail = false;

      const comp = createFormulaOption();
      comp.availableFields = [TestUtils.createMockDataRef("state"), calcRef];
      comp.aggregate.formula = AggregateFormula.CORRELATION.formulaName;

      const fields = comp.getAvailableFields();

      expect(fields.some(f => f.value === "calc1")).toBe(false);
      expect(fields.some(f => f.value === "state")).toBe(true);
   });
});

describe("FormulaOption — bug regressions [Group 5, Risk 2]", () => {
   // 🔁 Regression-sensitive: Bug #19370 — expression secondary must not resolve to column ref
   it("should keep dynamic secondaryColumnValue and null secondaryColumn for expression", () => {
      const comp = createFormulaOption();
      comp.aggregate.formula = AggregateFormula.CORRELATION.formulaName;

      comp.changeSecondColumnValue("={id}");

      expect(comp.aggregate.secondaryColumn).toBeNull();
      expect(comp.aggregate.secondaryColumnValue).toBe("={id}");
   });

   // 🔁 Regression-sensitive: Bug #20322 — null formulaOptionModel must not disable combobox
   it("should enable formula combobox when formulaOptionModel is null", () => {
      const comp = createFormulaOption();
      comp.aggregate.formulaOptionModel = null;

      expect(comp.isFormulaEnabled()).toBe(true);
   });

   it("should disable formula when aggregateStatus is set on formulaOptionModel", () => {
      const comp = createFormulaOption();
      comp.aggregate.formulaOptionModel = { aggregateStatus: true };

      expect(comp.isFormulaEnabled()).toBe(false);
   });
});
