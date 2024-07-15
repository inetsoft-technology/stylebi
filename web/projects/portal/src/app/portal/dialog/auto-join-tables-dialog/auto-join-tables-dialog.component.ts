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
import { Component, Input, OnInit, Output, EventEmitter, ViewChild, ElementRef, AfterViewInit } from "@angular/core";
import { PhysicalModelDefinition } from "../../data/model/datasources/database/physical-model/physical-model-definition";
import { AutoJoinColumnsModel } from "../../data/model/datasources/database/physical-model/auto-join/auto-join-columns-model";
import { AutoJoinColumnModel } from "../../data/model/datasources/database/physical-model/auto-join/auto-join-column-model";
import { HttpClient } from "@angular/common/http";
import { PhysicalTableModel } from "../../data/model/datasources/database/physical-model/physical-table-model";
import { AutoJoinNameColumnModel } from "../../data/model/datasources/database/physical-model/auto-join/auto-join-name-column-model";
import { JoinModel } from "../../data/model/datasources/database/physical-model/join-model";
import { JoinType } from "../../data/model/datasources/database/physical-model/join-type.enum";
import { MergingRule } from "../../data/model/datasources/database/physical-model/merging-rule.enum";
import { Cardinality } from "../../data/model/datasources/database/physical-model/cardinality.enum";
import { AutoJoinMetaColumnModel } from "../../data/model/datasources/database/physical-model/auto-join/auto-join-meta-column-model";
import { CardinalityHelperModel } from "../../data/model/datasources/database/physical-model/cardinality-helper-model";
import {
   EditJoinEventItem,
   EditJoinsEvent
} from "../../data/model/datasources/database/events/edit-joins-event";

const AUTO_JOIN_URI: string = "../api/data/physicalmodel/autoJoin/";
const JOIN_CARDINALITY_URI: string = "../api/data/physicalmodel/cardinality/";
const ADD_AUTO_JOIN_URI: string = "../api/data/physicalmodel/add/autoJoin";

@Component({
   selector: "auto-join-tables-dialog",
   templateUrl: "auto-join-tables-dialog.component.html"
})
export class AutoJoinTablesDialog implements OnInit, AfterViewInit {
   @Input() physicalModel: PhysicalModelDefinition;
   @Input() databaseName: string;
   @Output() onCommit: EventEmitter<string> = new EventEmitter<string>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   @ViewChild("radioInput") radioInput: ElementRef;
   joinOnName: boolean = true;
   autoJoinColumns: AutoJoinColumnsModel = {
      nameColumns: [],
      metaColumns: [],
      metaAvailable: false
   };
   availableColumns: AutoJoinColumnModel[] = [];
   finishedAddingJoins: boolean = true;
   joinsPending: number = 0;

   constructor(private httpClient: HttpClient) {
   }

   ngOnInit(): void {
      this.refreshColumns();
   }

   ngAfterViewInit(): void {
      this.radioInput.nativeElement.focus();
   }

   /**
    * Send request to get the available name and meta columns.
    */
   private refreshColumns(): void {
      this.httpClient.post<AutoJoinColumnsModel>(AUTO_JOIN_URI + this.databaseName,
                                                 this.physicalModel)
         .subscribe(
            data => {
               this.autoJoinColumns = data;

               if(this.autoJoinColumns.metaAvailable) {
                  this.joinOnName = false;
                  this.availableColumns = this.autoJoinColumns.metaColumns;
               }
               else {
                  this.availableColumns = this.autoJoinColumns.nameColumns;
               }
            },
            err => {}
         );
   }

   /**
    * Called when join on radio is changed. Show the correct available columns.
    */
   joinOnChanged(): void {
      if(this.joinOnName) {
         this.availableColumns = this.autoJoinColumns.nameColumns;
      }
      else {
         this.availableColumns = this.autoJoinColumns.metaColumns;
      }
   }

   /**
    * Helper method to get the physical table with the given name from the physical model.
    * @param name the physical table name to find
    * @returns {PhysicalTableModel} the Physical Table Model
    */
   private getPhysicalTable(name: string): PhysicalTableModel {
      return this.physicalModel.tables.find(table => table.qualifiedName == name);
   }

   /**
    * User clicked ok. Apply the selected auto joins.
    */
   ok(): void {
      let joinItems: EditJoinEventItem[] = [];
      const selectedColumns: AutoJoinColumnModel[] =
         this.availableColumns.filter(column => column.selected);

      if(this.joinOnName) {
         selectedColumns.forEach((nameColumn: AutoJoinNameColumnModel) => {
            const tables: string[] = nameColumn.tables;
            const columnName: string = nameColumn.column;

            // Make a join with the given column between each table listed
            for(let i = 0; i < tables.length; i++) {
               const physicalTable1: PhysicalTableModel = this.getPhysicalTable(tables[i]);

               for(let j = i + 1; j < tables.length; j++) {
                  const physicalTable2: PhysicalTableModel = this.getPhysicalTable(tables[j]);

                  let found: boolean = physicalTable1.joins.some(join =>
                     join.foreignTable == physicalTable2.qualifiedName &&
                     join.foreignColumn == nameColumn.column && join.column == columnName);

                  if(!found) {
                     found = physicalTable2.joins.some(join =>
                        join.foreignTable == physicalTable1.qualifiedName &&
                        join.foreignColumn == columnName && join.column == columnName);
                  }

                  if(found) {
                     continue;
                  }

                  // if join was not found already in either table, create and add the new join
                  const newJoin: JoinModel = {
                     type: JoinType.EQUAL,
                     orderPriority: 1,
                     weak: false,
                     mergingRule: MergingRule.AND,
                     cardinality: Cardinality.MANY_TO_ONE,
                     table: physicalTable1.qualifiedName,
                     column: columnName,
                     foreignTable: physicalTable2.qualifiedName,
                     foreignColumn: columnName,
                     baseJoin: false
                  };

                  joinItems.push(new EditJoinEventItem(physicalTable1, newJoin));
               }
            }
         });
      }
      else {
         selectedColumns.forEach((metaColumn: AutoJoinMetaColumnModel) => {
            const localColumn: string = metaColumn.column;
            const localTable: string = metaColumn.table;
            const physicalTable: PhysicalTableModel = this.getPhysicalTable(localTable);

            metaColumn.forColumns.forEach(column => {
               const forColumn: string = column.column;
               const forTable: string = column.table;

               // self join is not supported, dont join on non selected tables, and check if join
               // already exists
               if(forTable != localTable && !!this.getPhysicalTable(forTable) &&
                  !physicalTable.joins.some(join => join.foreignTable == forTable &&
                     join.foreignColumn == forColumn && join.column == localColumn))
               {
                  const newJoin: JoinModel = {
                     type: JoinType.EQUAL,
                     orderPriority: 1,
                     weak: false,
                     mergingRule: MergingRule.AND,
                     cardinality: Cardinality.MANY_TO_ONE,
                     table: localTable,
                     column: localColumn,
                     foreignTable: forTable,
                     foreignColumn: forColumn,
                     baseJoin: false
                  };

                  joinItems.push(new EditJoinEventItem(physicalTable, newJoin));
               }
            });
         });
      }

      this.addAutoJoinToTable(joinItems);
   }

   /**
    * Add the new auto join to the given table. Update pending joins counter and close dialog if
    * this is the last join to add.
    * @param joinItems   the items to add.
    */
   private addAutoJoinToTable(joinItems: EditJoinEventItem[]): void {
      let event: EditJoinsEvent = new EditJoinsEvent(joinItems, this.physicalModel.id);

      this.httpClient.post(ADD_AUTO_JOIN_URI, event)
         .subscribe(() => {
            this.onCommit.emit(null);
         });
   }

   /**
    * Close the dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }
}
