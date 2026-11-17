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

import { Condition } from "../../common/data/condition/condition";
import { ConditionOperation } from "../../common/data/condition/condition-operation";
import { ExpressionType } from "../../common/data/condition/expression-type";
import { VSConditionItemPaneProvider } from "./vs-condition-item-pane-provider";

describe("VSConditionItemPaneProvider", () => {
   // Bug #81532: a highlight condition value must not offer the SQL expression type.
   const types = (isHighlight: boolean) => {
      const provider = new VSConditionItemPaneProvider(
         {} as any, "rt1", "Table1", "Table1", [], isHighlight, []);
      const condition = <Condition> {operation: ConditionOperation.EQUAL_TO, values: []};
      return provider.getExpressionTypes(condition);
   };

   it("offers only the JS expression type for a highlight", () => {
      expect(types(true)).toEqual([ExpressionType.JS]);
   });

   it("keeps both expression types for a non-highlight condition", () => {
      expect(types(false)).toEqual([ExpressionType.JS, ExpressionType.SQL]);
   });
});
