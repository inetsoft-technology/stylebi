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

import { of } from "rxjs";
import { Condition } from "../../common/data/condition/condition";
import { ConditionItemPaneProvider } from "../../common/data/condition/condition-item-pane-provider";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { JunctionOperator } from "../../common/data/condition/junction-operator";
import { JunctionOperatorType } from "../../common/data/condition/junction-operator-type";
import { DataRef } from "../../common/data/data-ref";
import { XSchema } from "../../common/data/xschema";
import { ConditionItemPane } from "./condition-item-pane.component";
import { ConditionPane } from "./condition-pane.component";

export function makeField(name: string = "state", overrides: Partial<DataRef> = {}): DataRef {
   return {
      classType: "BaseField",
      name,
      attribute: name,
      view: name,
      dataType: XSchema.STRING,
      entity: "Orders",
      ...overrides
   };
}

export function makeCondition(overrides: Partial<Condition> = {}): Condition {
   return {
      jsonType: "condition",
      field: makeField(),
      operation: ConditionOperation.EQUAL_TO,
      values: [{ value: "AK", type: ConditionValueType.VALUE }],
      level: 0,
      equal: false,
      negated: false,
      ...overrides
   };
}

export function makeJunction(
   type: JunctionOperatorType = JunctionOperatorType.AND,
   level: number = 0
): JunctionOperator {
   return {
      jsonType: "junction",
      type,
      level
   };
}

export function makeConditionList() {
   return [
      makeCondition({ field: makeField("state"), level: 0 }),
      makeJunction(JunctionOperatorType.AND, 0),
      makeCondition({ field: makeField("city"), values: [{ value: "Boston", type: ConditionValueType.VALUE }], level: 0 }),
      makeJunction(JunctionOperatorType.OR, 0),
      makeCondition({ field: makeField("zip"), values: [{ value: "02110", type: ConditionValueType.VALUE }], level: 0 })
   ];
}

export function makeProvider(overrides: Partial<ConditionItemPaneProvider> = {}) {
   const provider = {
      getConditionOperations: vi.fn(() => [
         ConditionOperation.EQUAL_TO,
         ConditionOperation.BETWEEN,
         ConditionOperation.TOP_N,
         ConditionOperation.BOTTOM_N,
         ConditionOperation.NULL
      ]),
      getConditionValueTypes: vi.fn(() => [ConditionValueType.VALUE]),
      getExpressionTypes: vi.fn(() => [ExpressionType.JS]),
      isNegationAllowed: vi.fn(() => true),
      getData: vi.fn(() => of({ rows: [] })),
      getVariables: vi.fn(() => of(["var1"])),
      getColumnTree: vi.fn(() => of({ label: "root", children: [] })),
      getScriptDefinitions: vi.fn(() => of({ defs: [] })),
      isBrowseDataEnabled: vi.fn(() => true),
      getGrayedOutFields: vi.fn(() => [makeField("blocked")]),
      setFormula: vi.fn(),
      isSqlMergeable: vi.fn(() => false),
      ...overrides
   };

   return provider as unknown as ConditionItemPaneProvider & Record<string, ReturnType<typeof vi.fn>>;
}

export function createConditionItemPane(options: {
   provider?: ConditionItemPaneProvider & Record<string, any>;
   condition?: Condition;
   dialogResult?: Promise<any>;
} = {}) {
   const provider = options.provider || makeProvider();
   const dialog = {
      open: vi.fn(() => ({ result: options.dialogResult || Promise.resolve({}) }))
   } as any;
   const uiContextService = { isVS: vi.fn(() => true) } as any;
   const comp = new ConditionItemPane(dialog, uiContextService);

   comp.provider = provider;
   comp.condition = options.condition || makeCondition();
   comp.variableNames = ["var1"];
   comp.fields = [makeField("state"), makeField("city")];
   (comp as any).formulaEditorDialog = {};

   return { comp, provider, dialog };
}

export function createConditionPane() {
   const modal = { open: vi.fn() } as any;
   const comp = new ConditionPane(modal);
   return { comp, modal };
}
