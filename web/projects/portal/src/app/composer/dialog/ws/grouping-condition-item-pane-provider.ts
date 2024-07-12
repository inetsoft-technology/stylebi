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
import {of as observableOf,  Observable } from "rxjs";
import {pluck, map} from "rxjs/operators";
import { HttpParams } from "@angular/common/http";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { BrowseDataModel } from "../../../common/data/browse-data-model";
import { AssetConditionItemPaneProvider } from "../../../common/data/condition/asset-condition-item-pane-provider";
import { Condition } from "../../../common/data/condition/condition";
import { ConditionOperation } from "../../../common/data/condition/condition-operation";
import { ConditionValueType } from "../../../common/data/condition/condition-value-type";
import { ExpressionType } from "../../../common/data/condition/expression-type";
import { ExpressionValue } from "../../../common/data/condition/expression-value";
import { XSchema } from "../../../common/data/xschema";
import { ModelService } from "../../../widget/services/model.service";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";

const ATTRIBUTE_DATA_URI: string = "../api/composer/ws/asset-entry-attribute-data";
const DATE_RANGES_URI: string = "../api/composer/ws/assembly-condition-dialog/date-ranges";

export class GroupingConditionItemPaneProvider extends AssetConditionItemPaneProvider {
   private _asset: AssetEntry;
   private _variableNames: string[];

   constructor(private modelService: ModelService,
               private runtimeId: string)
   {
      super();
   }

   public set asset(asset: AssetEntry) {
      this._asset = asset;
   }

   public set variableNames(names: string[]) {
      this._variableNames = names;
   }

   getConditionOperations(condition: Condition): ConditionOperation[] {
      let operations: ConditionOperation[] = [];

      if(condition != null) {
         if(condition.field != null) {
            if(condition.field.dataType == XSchema.BOOLEAN) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.NULL);
            }
            else if(condition.field.dataType == XSchema.DATE ||
               condition.field.dataType == XSchema.TIME_INSTANT)
            {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.DATE_IN);
               operations.push(ConditionOperation.ONE_OF);
               operations.push(ConditionOperation.LESS_THAN);
               operations.push(ConditionOperation.GREATER_THAN);
               operations.push(ConditionOperation.BETWEEN);
               operations.push(ConditionOperation.NULL);
            }
            else if(condition.field.dataType == XSchema.TIME) {
               operations.push(ConditionOperation.EQUAL_TO);
               operations.push(ConditionOperation.ONE_OF);
               operations.push(ConditionOperation.LESS_THAN);
               operations.push(ConditionOperation.GREATER_THAN);
               operations.push(ConditionOperation.BETWEEN);
               operations.push(ConditionOperation.NULL);
            }
         }

         if(condition.values[0] != null &&
            condition.values[0].type == ConditionValueType.SESSION_DATA)
         {
            if(condition.values[0].value == "$(_ROLES_)" ||
               condition.values[0].value == "$(_GROUPS_)")
            {
               operations.push(ConditionOperation.ONE_OF);
            }
            else {
               operations.push(ConditionOperation.EQUAL_TO);
            }
         }
      }

      if(operations.length == 0) {
         operations.push(ConditionOperation.EQUAL_TO);
         operations.push(ConditionOperation.ONE_OF);
         operations.push(ConditionOperation.LESS_THAN);
         operations.push(ConditionOperation.GREATER_THAN);
         operations.push(ConditionOperation.BETWEEN);
         operations.push(ConditionOperation.STARTING_WITH);
         operations.push(ConditionOperation.CONTAINS);
         operations.push(ConditionOperation.LIKE);
         operations.push(ConditionOperation.NULL);
      }

      return operations;
   }

   getConditionValueTypes(condition: Condition): ConditionValueType[] {
      const valueTypes = [
         ConditionValueType.VALUE,
         ConditionValueType.VARIABLE,
      ];

      if((condition.field == null || condition.field.dataType == XSchema.STRING) &&
         (condition.operation == ConditionOperation.EQUAL_TO ||
            condition.operation == ConditionOperation.ONE_OF))
      {
         valueTypes.push(ConditionValueType.SESSION_DATA);
      }

      return valueTypes;
   }

   getExpressionTypes(condition: Condition): ExpressionType[] {
      return [ExpressionType.SQL, ExpressionType.JS];
   }

   isNegationAllowed(condition: Condition): boolean {
      return !(condition != null && (condition.operation === ConditionOperation.TOP_N ||
         condition.operation === ConditionOperation.BOTTOM_N));
   }

   getData(condition: Condition): Observable<BrowseDataModel> {
      if(condition.operation == ConditionOperation.DATE_IN) {
         const params = new HttpParams().set("runtimeId", this.runtimeId);
         return this.modelService.getModel<BrowseDataModel>(DATE_RANGES_URI, params);
      }
      else {
         // As in GroupingDialog_Script.as
         if(!this._asset) {
            return null;
         }

         if((this._asset.properties.subType && this._asset.properties.subType !== "jdbc") ||
            this._asset.properties.isJDBC === "false")
         {
            this._asset.properties.isJDBC = false + "";
            return null;
         }

         this._asset.properties.isJDBC = true + "";

         const params = new HttpParams().set("attribute", condition.field.name);

         return this.modelService.sendModel<BrowseDataModel>(ATTRIBUTE_DATA_URI, this._asset, params)
            .pipe(
               map(response => response.body)
            );
      }
   }

   isBrowseDataEnabled(condition: Condition): boolean {
      return this._asset &&
         !((this._asset.properties.subType && this._asset.properties.subType !== "jdbc") ||
            this._asset.properties.isJDBC === "false");
   }

   getVariables(condition: Condition): Observable<any[]> {
      return observableOf(this._variableNames);
   }

   getColumnTree(value: ExpressionValue): Observable<TreeNodeModel> {
      return null;
   }
}
