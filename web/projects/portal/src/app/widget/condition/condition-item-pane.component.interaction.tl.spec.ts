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
 * ConditionItemPane - Pass 1: Interaction
 *
 * Risk-first coverage:
 *   Group 1 [Risk 3] - lifecycle updateCondition and fields tree construction
 *   Group 2 [Risk 3] - field/operation/value change emit contracts
 *   Group 3 [Risk 2] - provider delegation methods
 *   Group 4 [Risk 3] - formula editor modal result handling
 *
 * Out of scope this pass: pure display predicates and default-value branch matrix.
 */

import { SimpleChange } from "@angular/core";
import { firstValueFrom } from "rxjs";
import { FormulaType } from "../../common/data/formula-type";
import { XSchema } from "../../common/data/xschema";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import {
   createConditionItemPane,
   makeCondition,
   makeField,
   makeProvider
} from "./condition-pane.test-helpers";

afterEach(() => vi.restoreAllMocks());

describe("ConditionItemPane - lifecycle and fields setter [Group 1, Risk 3]", () => {
   it("should build fieldsModel tree grouped by entity and default query fields group", () => {
      const { comp } = createConditionItemPane();
      const state = makeField("state", { entity: "Orders", description: "State" });
      const city = makeField("city", { entity: "Customers" });
      const loose = makeField("loose", { entity: null });

      comp.fields = [state, city, loose];

      expect(comp.fieldsModel.list).toEqual([state, city, loose]);
      expect(comp.fieldsModel.tree.children.map(node => node.label))
         .toEqual(["Orders", "Customers", "_#(js:Query Fields)"]);
      expect(comp.fieldsModel.tree.children[0].children[0]).toEqual(
         expect.objectContaining({ label: "state", data: state, tooltip: "State", leaf: true })
      );
   });

   it("should initialize formula from condition field and load provider-driven editor options", () => {
      const provider = makeProvider({
         getConditionOperations: vi.fn(() => [ConditionOperation.BETWEEN]),
         getConditionValueTypes: vi.fn(() => [ConditionValueType.FIELD]),
         isNegationAllowed: vi.fn(() => false)
      } as any);
      const condition = makeCondition({ operation: ConditionOperation.EQUAL_TO, negated: true });
      const { comp } = createConditionItemPane({ provider, condition });

      comp.ngOnInit();

      expect(comp.formula).toBe(condition.field);
      expect(comp.operations).toEqual([ConditionOperation.BETWEEN]);
      expect(comp.condition.operation).toBe(ConditionOperation.BETWEEN);
      expect(comp.valueTypes).toEqual([ConditionValueType.FIELD]);
      expect(comp.negationAllowed).toBe(false);
      expect(comp.condition.negated).toBe(false);
   });

   it("should refresh condition state on ngOnChanges", () => {
      const provider = makeProvider({
         getConditionOperations: vi.fn(() => [ConditionOperation.DATE_IN])
      } as any);
      const condition = makeCondition({
         operation: ConditionOperation.DATE_IN,
         values: [{ value: "AK", type: ConditionValueType.VALUE, choiceQuery: "old" } as any]
      });
      const { comp } = createConditionItemPane({ provider, condition });

      comp.ngOnChanges({ condition: new SimpleChange(null, condition, false) });

      expect(provider.getConditionOperations).toHaveBeenCalledWith(condition);
      expect(comp.condition.values[0]["choiceQuery"]).toBeNull();
   });
});

describe("ConditionItemPane - field and value changes [Group 2, Risk 3]", () => {
   it("should reset operation and values when a different field is selected", () => {
      const provider = makeProvider({
         getConditionOperations: vi.fn(() => [ConditionOperation.BETWEEN, ConditionOperation.EQUAL_TO])
      } as any);
      const { comp } = createConditionItemPane({ provider });
      comp.updateCondition();
      const emitSpy = vi.spyOn(comp.conditionChange, "emit");

      comp.fieldChanged(makeField("amount", { dataType: XSchema.STRING }));

      expect(comp.condition.field.view).toBe("amount");
      expect(comp.condition.operation).toBe(ConditionOperation.BETWEEN);
      expect(comp.condition.values).toEqual([
         { value: "", type: ConditionValueType.VALUE },
         { value: "", type: ConditionValueType.VALUE }
      ]);
      expect(emitSpy).toHaveBeenCalledWith(comp.condition);
   });

   it("should not emit when selected field is deeply equal to the current field", () => {
      const { comp } = createConditionItemPane();
      const emitSpy = vi.spyOn(comp.conditionChange, "emit");

      comp.fieldChanged({ ...comp.condition.field });

      expect(emitSpy).not.toHaveBeenCalled();
   });

   it("should rebuild default values when operation changes", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({
            field: makeField("amount", { dataType: XSchema.INTEGER }),
            operation: ConditionOperation.TOP_N
         })
      });
      comp.updateCondition();
      const emitSpy = vi.spyOn(comp.conditionChange, "emit");

      comp.operationChanged();

      expect(comp.condition.values).toEqual([
         { value: { n: 3 }, type: ConditionValueType.VALUE }
      ]);
      expect(emitSpy).toHaveBeenCalledWith(comp.condition);
   });

   it("should initialize values array when first value editor emits", () => {
      const { comp } = createConditionItemPane({
         condition: makeCondition({ values: null })
      });
      const nextValue = { value: "CA", type: ConditionValueType.VALUE };
      const emitSpy = vi.spyOn(comp.conditionChange, "emit");

      comp.onConditionValueChange(nextValue);

      expect(comp.condition.values).toEqual([nextValue]);
      expect(emitSpy).toHaveBeenCalledWith(comp.condition);
   });
});

describe("ConditionItemPane - provider delegation [Group 3, Risk 2]", () => {
   it("should delegate data, variables, column tree, and script definitions to provider", async () => {
      const { comp, provider } = createConditionItemPane();
      const expressionValue = { expression: "a + b" } as any;

      await firstValueFrom(comp.getData());
      await firstValueFrom(comp.getVariables());
      await firstValueFrom(comp.getColumnTree(expressionValue, "OldFormula"));
      await firstValueFrom(comp.getScriptDefinitions(expressionValue));

      expect(provider.getData).toHaveBeenCalledWith(comp.condition);
      expect(provider.getVariables).toHaveBeenCalledWith(comp.condition);
      expect(provider.getColumnTree).toHaveBeenCalledWith(expressionValue, ["var1"], "OldFormula");
      expect(provider.getScriptDefinitions).toHaveBeenCalledWith(expressionValue);
   });
});

describe("ConditionItemPane - openFormulaEdit [Group 4, Risk 3]", () => {
   it("should load column tree, open modal, rename edited formula field, and persist formula", async () => {
      const result = {
         formulaName: "NewFormula",
         formulaType: FormulaType.SQL,
         dataType: XSchema.INTEGER,
         expression: "amount + 1"
      };
      const condition = makeCondition({
         field: {
            ...makeField("OldFormula"),
            classType: "FormulaField",
            name: "OldFormula",
            attribute: "OldFormula",
            view: "OldFormula",
            formulaType: FormulaType.SCRIPT,
            exp: "amount"
         } as any
      });
      const { comp, provider, dialog } = createConditionItemPane({
         condition,
         dialogResult: Promise.resolve(result)
      });
      comp.formula = comp.condition.field;
      const renameSpy = vi.spyOn(comp.expressionRenamed, "emit");

      comp.openFormulaEdit(false);
      await Promise.resolve();

      expect(provider.getColumnTree).toHaveBeenCalledWith(null, ["var1"], "OldFormula");
      expect(comp.columnTreeModel).toEqual({ label: "root", children: [] });
      expect(dialog.open).toHaveBeenCalledWith((comp as any).formulaEditorDialog, {
         windowClass: "formula-dialog"
      });
      expect(renameSpy).toHaveBeenCalledWith({ oname: "OldFormula", nname: "NewFormula" });
      expect(comp.condition.field).toEqual(expect.objectContaining({
         name: "NewFormula",
         attribute: "NewFormula",
         view: "NewFormula",
         formulaType: FormulaType.SQL,
         dataType: XSchema.INTEGER,
         exp: "amount + 1"
      }));
      expect(provider.setFormula).toHaveBeenCalledWith(result);
   });

   it("should create new formula with SQL type when provider is SQL mergeable", () => {
      const provider = makeProvider({
         isSqlMergeable: vi.fn(() => true)
      } as any);
      const { comp } = createConditionItemPane({ provider });

      comp.openFormulaEdit(true);

      expect((comp.formula as any).classType).toBe("FormulaField");
      expect((comp.formula as any).formulaType).toBe(FormulaType.SQL);
   });
});
