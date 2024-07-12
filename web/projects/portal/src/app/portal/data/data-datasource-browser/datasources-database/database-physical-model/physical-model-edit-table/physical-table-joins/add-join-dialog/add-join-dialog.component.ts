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
import { Component, OnInit, Input, EventEmitter, Output, ViewChild, ElementRef, AfterViewInit } from "@angular/core";
import { PhysicalTableModel } from "../../../../../../model/datasources/database/physical-model/physical-table-model";
import { JoinModel } from "../../../../../../model/datasources/database/physical-model/join-model";
import { JoinType } from "../../../../../../model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../../../../../model/datasources/database/physical-model/merging-rule.enum";
import { Cardinality } from "../../../../../../model/datasources/database/physical-model/cardinality.enum";
import { HttpClient } from "@angular/common/http";
import { Observable, of } from "rxjs";
import { GetSqlColumnsEvent } from "../../../../../../model/datasources/database/events/get-sql-columns-event";
import {
   EditJoinEventItem,
   EditJoinsEvent
} from "../../../../../../model/datasources/database/events/edit-joins-event";
import { ComponentTool } from "../../../../../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { GetTableColumnEvent } from "../../../../../../model/datasources/database/events/get-table-column-event";


const TABLE_COLUMNS_URI: string = "../api/data/physicalmodel/columns";
const SQL_TABLE_COLUMNS_URI: string = "../api/data/physicalmodel/views/columns";
const CHECK_JOIN_EXIST_URI: string = "../api/data/physicalmodel/join/exist";

@Component({
   selector: "add-join-dialog",
   templateUrl: "add-join-dialog.component.html"
})
export class AddJoinDialog implements OnInit, AfterViewInit {
   @Input() database: string;
   @Input() table: PhysicalTableModel;
   @Input() tables: PhysicalTableModel[] = [];
   @Input() id: string;
   @Output() onCommit: EventEmitter<JoinModel> = new EventEmitter<JoinModel>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("selectFocus") selectFocus: ElementRef;
   join: JoinModel = {
      type: JoinType.EQUAL,
      orderPriority: 1,
      weak: false,
      mergingRule: MergingRule.AND,
      cardinality: Cardinality.MANY_TO_ONE,
      table: null,
      column: null,
      foreignTable: null,
      foreignColumn: null,
      baseJoin: false
   };
   columns: string[] = [];
   foreignTables: PhysicalTableModel[] = [];
   foreignColumns: string[] = [];
   filteredForeignColumns: string[] = [];

   constructor(private httpClient: HttpClient, private modalService: NgbModal) {
   }

   ngOnInit(): void {
      this.foreignTables = this.tables
         .filter(modelTable => modelTable.qualifiedName != this.table.qualifiedName);

      this.getTableColumns(this.table.qualifiedName)
         .subscribe(
            data => {
               this.columns = data;
            },
            err => {}
         );
   }

   ngAfterViewInit(): void {
      this.selectFocus.nativeElement.focus();
   }

   /**
    * Called when user changes column or foreign table. Update the available foreign columns.
    * @param tableChange   true if the foreign table was changed
    */
   joinSelectionChanged(tableChange: boolean = false): void {
      if(!!this.join.foreignTable && !!this.join.column) {
         if(tableChange || this.foreignColumns.length == 0) {
            this.getTableColumns(this.join.foreignTable)
               .subscribe(
                  data => {
                     this.foreignColumns = data;
                     this.filterColumns();
                  },
                  err => {}
               );
         }
         else {
            this.filterColumns();
         }
      }
   }

   /**
    * Send request to get the columns of the given table.
    * @param name the table name
    * @returns {any} observable holding list of columns
    */
   private getTableColumns(name: string): Observable<string[]> {
      const tableModel: PhysicalTableModel =
         this.tables.find(modelTable => modelTable.qualifiedName == name);

      if(!tableModel) {
         return of([]);
      }

      const path: string = tableModel.path;
      let request: Observable<string[]>;

      if(!!tableModel.sql) {
         const index: number = path.lastIndexOf("/");
         const database: string = index == -1 ? path : path.substring(0, index);
         request = this.httpClient.post<string[]>(SQL_TABLE_COLUMNS_URI ,
                                                  new GetSqlColumnsEvent(tableModel.sql, database));
      }
      else {
         let event = new GetTableColumnEvent(this.database, this.id, tableModel?.qualifiedName);
         request = this.httpClient.post<string[]>(TABLE_COLUMNS_URI, event);
      }

      return request;
   }

   /**
    * Filter all foreign columns into the ones currently available for selection.
    */
   private filterColumns(): void {
      this.filteredForeignColumns = this.foreignColumns.filter(foreignColumn => {
         return !this.table.joins
            .some(tableJoin => tableJoin.foreignColumn == foreignColumn &&
                  tableJoin.column == this.join.column &&
                  tableJoin.foreignTable == this.join.foreignTable);
      });
      this.join.foreignColumn = this.filteredForeignColumns
         .indexOf(this.join.foreignColumn) == -1 ? null : this.join.foreignColumn;
   }

   /**
    * Called when user clicks ok on dialog. Return the updated condition list.
    */
   ok(): void {
      let item: EditJoinEventItem = new EditJoinEventItem(this.table, this.join);
      let event: EditJoinsEvent = new EditJoinsEvent([ item ], this.id);

      this.httpClient.post<boolean>(CHECK_JOIN_EXIST_URI, event).subscribe(exist => {
         if(exist == true) {
            ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
               "_#(js:data.physicalmodel.joinExist)");
         }
         else {
            this.onCommit.emit(this.join);
         }
      });
   }

   /**
    * Called when user clicks cancel on dialog. Close dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
