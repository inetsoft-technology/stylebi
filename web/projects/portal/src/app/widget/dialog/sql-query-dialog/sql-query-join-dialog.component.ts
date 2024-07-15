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
import { Component, EventEmitter, Input, OnDestroy, OnInit, Output } from "@angular/core";
import { Observable ,  Subscription } from "rxjs";
import { AssetUtil } from "../../../binding/util/asset-util";
import { AssetEntry } from "../../../../../../shared/data/asset-entry";
import { Tool } from "../../../../../../shared/util/tool";
import { JoinItem } from "../../../composer/data/ws/join-item";

@Component({
   selector: "sql-query-join-dialog",
   templateUrl: "sql-query-join-dialog.component.html"
})
export class SQLQueryJoinDialog implements OnInit, OnDestroy {
   @Input() joins: JoinItem[];
   @Input() join: JoinItem;
   @Input() supportsFullOuterJoin: boolean;
   @Input() tables: {[key: string]: AssetEntry};
   @Input() columnCache: {[tableName: string]: Observable<AssetEntry[]>} = {};
   columnSub1: Subscription;
   columnSub2: Subscription;
   tempColumn1: AssetEntry;
   tempColumn2: AssetEntry;
   columns1: AssetEntry[];
   columns2: AssetEntry[];
   operators: string[] = ["=", "<>", ">", "<", ">=", "<="];
   editIndex: number = -1;
   error: string = null;
   @Output() onCommit: EventEmitter<JoinItem> = new EventEmitter<JoinItem>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   formValid = () => !this.error;

   ngOnInit(): void {
      if(this.join == null) {
         this.join = <JoinItem> {
            operator: this.operators[0],
            all1: false,
            all2: false,
         };
      }
      else {
         for(let i = 0; i < this.joins.length; i++) {
            if(Tool.isEquals(this.join, this.joins[i])) {
               this.editIndex = i;
               break;
            }
         }
      }

      let firstTableName = Object.keys(this.tables)[0];

      if(firstTableName != undefined) {
         if(this.join.table1 == null || this.join.table2 == null) {
            this.columnSub1 = this.columnCache[firstTableName].subscribe(entries => {
               this.updateColumns(entries, true, true);
            });
            this.columnSub2 = this.columnCache[firstTableName].subscribe(entries => {
               this.updateColumns(entries, false, true);
            });

            this.join.table1 = firstTableName;
            this.join.table2 = firstTableName;
         }
         else {
            this.columnSub1 = this.columnCache[this.join.table1].subscribe(entries => {
               this.updateColumns(entries, true, true);
            });
            this.columnSub2 = this.columnCache[this.join.table2].subscribe(entries => {
               this.updateColumns(entries, false, true);
            });
         }
      }

      this.validate();
   }

   ngOnDestroy() {
      if(this.columnSub1 && !this.columnSub1.closed) {
         this.columnSub1.unsubscribe();
      }

      if(this.columnSub2 && !this.columnSub2.closed) {
         this.columnSub2.unsubscribe();
      }
   }

   close(): void {
      this.onCancel.emit("cancel");
   }

   ok(): void {
      this.setJoinColumns();
      this.onCommit.emit(this.join);
   }

   private setJoinColumns(): void {
      this.join.column1 = !!this.tempColumn1?.properties["attribute"] ?
         this.tempColumn1?.properties["attribute"] : this.join.column1;
      this.join.column2 = !!this.tempColumn2?.properties["attribute"] ?
         this.tempColumn2?.properties["attribute"] : this.join.column2;
   }

   tableChange(tableName: string, leftTable: boolean) {
      if(leftTable) {
         this.join.table1 = tableName;
         this.columns1 = null;

         if(!this.columnSub1.closed) {
            this.columnSub1.unsubscribe();
         }
      }
      else {
         this.join.table2 = tableName;
         this.columns2 = null;

         if(!this.columnSub2.closed) {
            this.columnSub2.unsubscribe();
         }
      }

      let temp = this.columnCache[tableName].subscribe((entries) => {
         this.updateColumns(entries, leftTable);
         this.validate();
      });

      if(leftTable) {
         this.columnSub1 = temp;
      }
      else {
         this.columnSub2 = temp;
      }
   }

   /**
    * Update the currently selected column.
    *
    * @param entries the columns as asset entries
    * @param leftTable if true, the left table column is modified; otherwise, the right table column is
    * @param initialUpdate if true, treats it as initial update and tries to select the existing join columns
    */
   private updateColumns(entries: AssetEntry[], leftTable: boolean, initialUpdate: boolean = false) {
      if(leftTable) {
         this.columns1 = entries;

         if(initialUpdate && this.join.column1) {
            this.tempColumn1 = entries.find((entry) => entry.properties["attribute"] === this.join.column1);
         }

         if(!initialUpdate || this.tempColumn1 == undefined) {
            this.tempColumn1 = entries[0];
         }
      }
      else {
         this.columns2 = entries;

         if(initialUpdate && this.join.column2) {
            this.tempColumn2 = entries.find((entry) => entry.properties["attribute"] === this.join.column2);
         }

         if(!initialUpdate || this.tempColumn2 == undefined) {
            this.tempColumn2 = entries[0];
         }
      }
   }

   getTableNames(): string[] {
      return this.tables != null ? Object.keys(this.tables) : null;
   }

   /**
    * Checks whether the given join is a duplicate
    */
   isDuplicate(): boolean {
      this.setJoinColumns();
      const dup = this.joins && this.joins.find(
         (join0, idx) => idx != this.editIndex && Tool.isEquals(join0, this.join));

      if(dup) {
         this.error = "_#(js:common.sqlquery.joinDuplicate)";
         return true;
      }

      return false;
   }

   /**
    * Checks whether the given full outer join is valid if specified
    */
   isFullOuterJoinValid(): boolean {
      let res = this.join == null || this.supportsFullOuterJoin ||
         !(this.join.all1 && this.join.all2);

      if(!res) {
         this.error = "Full Outer Join is not supported for the given datasource.";
      }

      return res;
   }

   /** Checks whether column types are compatible */
   columnTypesCompatible(): boolean {
      let res = AssetUtil.isMergeable(
         this.tempColumn1?.properties["dtype"],
         this.tempColumn2?.properties["dtype"]);

      if(!res) {
         this.error = "Columns have incompatible types.";
      }

      return res;
   }

   isRepeatedTables(): boolean {
      let repeat = !!this.join && !!this.join.table1 && this.join.table1 === this.join.table2;

      if(repeat) {
         this.error = "_#(js:common.sqlquery.joinTableRepeat)";
      }

      return repeat;
   }

   /**
    * Checks whether the join is valid
    */
   validate(): boolean {
      this.error = null;
      return !this.isDuplicate() && this.isFullOuterJoinValid() && this.columnTypesCompatible() &&
         !this.isRepeatedTables();
   }

   changeOperator(operator: string) {
      this.join.operator = operator;

      if(operator !== "=") {
         this.join.all1 = this.join.all2 = false;
      }

      this.validate();
   }
}
