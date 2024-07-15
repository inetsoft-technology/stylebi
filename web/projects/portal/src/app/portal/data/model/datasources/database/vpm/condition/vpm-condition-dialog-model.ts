/*
 * inetsoft-web - StyleBI is a business intelligence web application.
 * Copyright © 2024 InetSoft Technology (info@inetsoft.com)
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
 * You should have received a copy of the GNU Affrero General Public License
 * along with this program. If not, see <http://www.gnu.org/licenses/>.
 */
import { OperationModel } from "./clause/operation-model";
import { ConditionItemModel } from "./condition-item-model";
import { VPMColumnModel } from "./vpm-column-model";

export class VPMConditionDialogModel {
   constructor(databaseName: string, tableName: string, partition: boolean,
               fields: VPMColumnModel[], conditionList: ConditionItemModel[],
               operations: OperationModel[], sessionOperations: OperationModel[])
   {
      this.databaseName = databaseName;
      this.tableName = tableName;
      this.partition = partition;
      this.fields = fields;
      this.conditionList = conditionList;
      this.operations = operations;
      this.sessionOperations = sessionOperations;

      if(this.fields) {
         this.fields = [...this.fields];

         this.fields.sort((f1, f2) => {
            return f1.name > f2.name ? 1 : f1.name == f2.name ? 0 : -1;
         });
      }
   }

   databaseName: string;
   tableName: string;
   partition: boolean = false;
   fields: VPMColumnModel[] = [];
   conditionList: ConditionItemModel[] = [];
   operations: OperationModel[] = [];
   sessionOperations: OperationModel[] = [null, null];
}