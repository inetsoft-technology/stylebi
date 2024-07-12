/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { HttpClient } from "@angular/common/http";
import { Observable } from "rxjs";
import { BrowseDataModel } from "../../../common/data/browse-data-model";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { AssemblyConditionItemPaneProvider } from "./assembly-condition-item-pane-provider";

export class RankingConditionItemPaneProvider extends AssemblyConditionItemPaneProvider {
   constructor(http: HttpClient, runtimeId: string, assemblyName: string) {
      super(http, runtimeId, assemblyName);
   }

   getConditionOperations(condition: Condition): ConditionOperation[] {
      return [ConditionOperation.TOP_N, ConditionOperation.BOTTOM_N];
   }

   getConditionValueTypes(condition: Condition): ConditionValueType[] {
      return [ConditionValueType.VALUE, ConditionValueType.VARIABLE];
   }

   getExpressionTypes(condition: Condition): ExpressionType[] {
      return [];
   }

   isNegationAllowed(condition: Condition): boolean {
      return false;
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      return null;
   }

   getColumnTree(value: ExpressionValue): Observable<TreeNodeModel> {
      return null;
   }
}
