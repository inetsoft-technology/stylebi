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
import { Component, EventEmitter, Input, Output } from "@angular/core";
import { PhysicalModelDefinition } from "../../../../model/datasources/database/physical-model/physical-model-definition";
import { PhysicalTableModel } from "../../../../model/datasources/database/physical-model/physical-table-model";

@Component({
   selector: "physical-model-edit-table",
   templateUrl: "physical-model-edit-table.component.html",
   styleUrls: ["physical-model-edit-table.component.scss"]
})
export class PhysicalModelEditTableComponent {
   @Input() physicalModel: PhysicalModelDefinition;
   @Input() databaseName: string;
   @Input() supportFullOuterJoin: boolean = false;
   @Input() disabled = false;
   @Input() isDuplicateTableName: (name: string) => boolean;
   @Output() tableChange: EventEmitter<PhysicalTableModel> = new EventEmitter<PhysicalTableModel>();
   editJoins: boolean = true;
   navCollapsed: boolean = true;
   private _table: PhysicalTableModel;

   @Input()
   set table(physicalTable: PhysicalTableModel) {
      if(physicalTable != this._table) {
         this._table = physicalTable;

         if(!!physicalTable.alias) {
            // auto aliases are not available on alias tables
            this.editJoins = true;
         }
      }
   }

   get table(): PhysicalTableModel {
      return this._table;
   }
}