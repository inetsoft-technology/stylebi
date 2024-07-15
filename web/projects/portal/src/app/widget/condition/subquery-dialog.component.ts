/*
 * This file is part of StyleBI.
 * Copyright (C) 2024  InetSoft Technology
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
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { SubqueryTable } from "../../common/data/condition/subquery-table";
import { SubqueryValue } from "../../common/data/condition/subquery-value";
import { DataRef } from "../../common/data/data-ref";
import { ColumnRef } from "../../binding/data/column-ref";

@Component({
   selector: "subquery-dialog",
   templateUrl: "subquery-dialog.component.html"
})
export class SubqueryDialog implements OnInit {
   @Input() subqueryTables: SubqueryTable[];
   @Input() value: SubqueryValue;
   @Input() showOriginalName: boolean = false;
   selectedTable: SubqueryTable;
   @Output() onCommit: EventEmitter<SubqueryValue> = new EventEmitter<SubqueryValue>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();

   ngOnInit(): void {
      let tables: SubqueryTable[] = this.getAvailableTables();

      if(this.value == null) {
         if(tables != null && tables.length > 0) {
            this.selectedTable = tables[0];
         }

         this.value = <SubqueryValue> {
            query: this.selectedTable != null ? this.selectedTable.name : null,
            attribute: this.selectedTable != null && this.selectedTable.columns != null &&
               this.selectedTable.columns.length > 0 ? this.selectedTable.columns[0] : null,
            subAttribute: null,
            mainAttribute: null
         };
      }
      else {
         for(let i = 0; i < tables.length; i++) {
            if(tables[i].name == this.value.query) {
               this.selectedTable = tables[i];
               break;
            }
         }
      }
   }

   cancel(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.onCommit.emit(this.value);
   }

   changeSelectedTable(query: string): void {
      this.value.query = query;

      for(let i = 0; i < this.subqueryTables.length; i++) {
         if(this.subqueryTables[i].name === query) {
            this.selectedTable = this.subqueryTables[i];
            this.value.attribute = this.selectedTable.columns != null &&
               this.selectedTable.columns.length > 0 ? this.selectedTable.columns[0] : null;
            this.value.subAttribute = null;
            return;
         }
      }
   }

   attributeChanged(attribute: DataRef) {
      this.value.attribute = attribute;
   }

   subAttributeChanged(subAttribute: DataRef) {
      this.value.subAttribute = subAttribute;
   }

   mainAttributeChanged(mainAttribute: DataRef) {
      this.value.mainAttribute = mainAttribute;
   }

   isValid(): boolean {
      return this.value != null &&
         ((this.value.subAttribute == null && this.value.mainAttribute == null) ||
         (this.value.subAttribute != null && this.value.mainAttribute != null));
   }

   getAvailableTables(): SubqueryTable[] {
      let tables: SubqueryTable[] = [];

      for(let i = 0; i < this.subqueryTables.length; i++) {
         if(!this.subqueryTables[i].currentTable) {
            tables.push(this.subqueryTables[i]);
         }
      }

      return tables;
   }

   getCurrentTableColumns(): DataRef[] {
      for(let i = 0; i < this.subqueryTables.length; i++) {
         if(this.subqueryTables[i].currentTable) {
            return this.subqueryTables[i].columns;
         }
      }

      return null;
   }

   dataRefsEqual(ref1: DataRef, ref2: DataRef): boolean {
      if(ref1 == null || ref2 == null) {
         return ref1 == ref2;
      }

      return ref1.entity === ref2.entity && ref1.attribute === ref2.attribute;
   }

   getTooltip(dataRef: DataRef): string {
      return this.showOriginalName ? ColumnRef.getTooltip(<ColumnRef> dataRef) : dataRef.description;
   }
}
