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
import { VariableInputDialogModel } from "../../../../../widget/dialog/variable-input-dialog/variable-input-dialog-model";

export class WSCollectVariablesOverEvent {
   private values: string[][];
   private types: string[];
   private names: string[];
   private usedInOneOf: boolean[];
   private userSelected: boolean[];
   private initial: boolean;
   private refreshColumns: boolean;

   constructor(model?: VariableInputDialogModel) {
      if(model) {
         this.values = [];
         this.types = [];
         this.names = [];
         this.usedInOneOf = [];
         this.userSelected = [];

         model.varInfos.forEach((info) => {
            this.values.push(info.value);
            this.types.push(info.type);
            this.names.push(info.name);
            this.userSelected.push(info.userSelected);
            this.usedInOneOf.push(info.usedInOneOf);
         });
      }
   }

   public setValues(values: any[][]) {
      this.values = values;
   }

   public setTypes(types: string[]) {
      this.types = types;
   }

   public setNames(names: string[]) {
      this.names = names;
   }

   public setInitial(initial: boolean) {
      this.initial = initial;
   }

   public setRefreshColumns(refreshColumns: boolean) {
      this.refreshColumns = refreshColumns;
   }
}
