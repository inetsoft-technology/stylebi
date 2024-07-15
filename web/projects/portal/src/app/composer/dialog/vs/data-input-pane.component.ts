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
import { HttpClient, HttpHeaders } from "@angular/common/http";
import { Component, Input, OnInit, ViewChild } from "@angular/core";
import { Tool } from "../../../../../../shared/util/tool";
import { FixedDropdownDirective } from "../../../widget/fixed-dropdown/fixed-dropdown.directive";
import { ComboMode } from "../../../widget/dynamic-combo-box/dynamic-combo-box-model";
import { TreeNodeModel } from "../../../widget/tree/tree-node-model";
import { DataInputPaneModel } from "../../data/vs/data-input-pane-model";

const ROW_URI: string = "../vs/dataInput/rows/";
const COLUMN_URI: string = "../vs/dataInput/columns/";
const POPUP_TABLE_URI: string = "../vs/dataInput/popupTable/";

@Component({
   selector: "data-input-pane",
   templateUrl: "data-input-pane.component.html",
   styleUrls: ["data-input-pane.component.scss"]
})
export class DataInputPane implements OnInit {
   @Input() model: DataInputPaneModel;
   @Input() variableValues: string[] = [];
   @Input() runtimeId: string = "";
   @Input() checkBox: boolean = false;
   headers: HttpHeaders;
   columns: any[] = [];
   rows: string[] = [];
   selectedRow: string = "";
   rowType: ComboMode = ComboMode.VALUE;
   popupTable: PopupEmbeddedTable;
   @ViewChild(FixedDropdownDirective) dropdown: FixedDropdownDirective;

   constructor(private http: HttpClient) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   ngOnInit() {
      this.updateColumns();
   }

   selectTarget(targetNode: TreeNodeModel): void {
      this.model.tableLabel = targetNode.label;
      this.model.table = targetNode.data;
      this.model.variable = targetNode.type === "variable";
      this.updateColumns();
   }

   selectColumn(column: string): void {
      this.model.columnValue = column;
      this.updateRows();
   }

   updateColumns(): void {
      if(!this.model.variable && this.model.table && this.model.table.length > 0) {
         this.http.get(COLUMN_URI + Tool.byteEncode(this.runtimeId) + "/" +
               Tool.byteEncode(this.model.table))
            .subscribe(
               (data: any) => {
                  this.columns = [];
                  for(let i: number = 0; i < data.columnlist.length; i++) {
                     this.columns.push({value: data.columnlist[i],
                        label: data.columnlist[i], tooltip: data.descriptionlist[i]});
                  }

                  if(this.columns && this.columns.length > 0) {
                     if(this.model.columnValue && this.model.columnValue.length > 0) {
                        if(this.model.columnValue.charAt(0) != "$" &&
                           this.model.columnValue.charAt(0) != "=" &&
                           this.getIndex(this.model.columnValue) < 0)
                        {
                           this.model.columnValue = this.columns[0].label;
                        }
                     }
                     else {
                        this.model.columnValue = this.columns[0].label;
                     }
                  }

                  this.updateRows();
               },
               (err) => {
                  this.model.columnValue = "";
                  this.columns = [];
                  this.model.rowValue = "";
                  this.rows = [];
                  //TODO handle error
               }
            );
      }
      else {
         this.model.columnValue = "";
         this.columns = [];
         this.model.rowValue = "";
         this.rows = [];
         this.updateSelectedRow("");
      }
   }

   selectRow(row: string): void {
      if(row.charAt(0) == "$" || row.charAt(0) == "=") {
         this.model.rowValue = row;
      }
      else {
         this.model.rowValue = (this.rows.indexOf(row) + 1) + "";
      }

      this.updateSelectedRow(row);
   }

   updateRows(): void {
      if(this.model.columnValue && this.model.columnValue.charAt(0) != "=") {
         this.http.get(ROW_URI + Tool.byteEncode(this.runtimeId) + "/" +
               Tool.byteEncode(this.model.table) + "/" +
               Tool.byteEncode(this.model.columnValue))
            .subscribe(
               (data: string[]) => {
                  this.rows = [];
                  for(let i = 1; i < data.length; i++) {
                     this.rows.push(i + " : " +
                        (data[i] == null || data[i] == "null" ? "" : data[i]));
                  }

                  if(this.model.rowValue && this.model.rowValue.length > 0) {
                     if(this.model.rowValue.charAt(0) == "$") {
                        this.updateSelectedRow(this.model.rowValue);
                        this.rowType = ComboMode.VARIABLE;
                     }
                     else if(this.model.rowValue.charAt(0) == "=") {
                        this.updateSelectedRow(this.model.rowValue);
                        this.rowType = ComboMode.EXPRESSION;
                     }
                     else if(this.rows && this.rows.length > 0) {
                        if(this.rows.length >= Number(this.model.rowValue)) {
                           this.updateSelectedRow(this.rows[Number(this.model.rowValue) - 1]);
                           this.rowType = ComboMode.VALUE;
                        }
                        else {
                           this.model.rowValue = "1";
                           this.updateSelectedRow(this.rows[0]);
                           this.rowType = ComboMode.VALUE;
                        }
                     }
                  }
                  else if(this.rows && this.rows.length > 0){
                     this.model.rowValue = "1";
                     this.updateSelectedRow(this.rows[0]);
                     this.rowType = ComboMode.VALUE;
                  }
               },
               (err) => {
                  this.model.rowValue = "";
                  this.rows = [];
                  //TODO handle error
               }
            );
      }
      else {
         this.rows = [];
         if(this.model.rowValue) {
            if(this.model.rowValue.charAt(0) == "$") {
               this.updateSelectedRow(this.model.rowValue);
               this.rowType = ComboMode.VARIABLE;
            }
            else if(this.model.rowValue.charAt(0) == "=") {
               this.updateSelectedRow(this.model.rowValue);
               this.rowType = ComboMode.EXPRESSION;
            }
            else {
               this.model.rowValue = "";
               this.updateSelectedRow("");
               this.rowType = ComboMode.VALUE;
            }
         }
      }
   }

   selectColumnType(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         if(!this.model.columnValue && this.columns && this.columns.length > 0) {
            this.model.columnValue = this.columns[0].value;
         }
      }
   }

   selectRowType(type: ComboMode): void {
      if(type == ComboMode.VALUE) {
         if(this.rows && this.rows.length > 0) {
            this.updateSelectedRow(this.rows[0]);
            this.model.rowValue = "1";
         }
      }

      this.rowType = type;
   }

   selectPopupCell(row: number, col: number): void {
      this.model.columnValue = this.popupTable.columnHeaders[col];
      this.model.rowValue = this.getRowIndex(row)  + "";
      this.updateRows();

      if(this.dropdown) {
         this.dropdown.close();
      }
   }

   private initPopupTable(): void {
      this.popupTable.numPages = Math.ceil((this.popupTable.numRows) / 10);

      if(this.model.rowValue && this.model.rowValue.length > 0 &&
         this.model.rowValue.charAt(0) != "$" && this.model.rowValue.charAt(0) != "=")
      {
         this.popupTable.page = Math.floor((Number(this.model.rowValue) - 1) / 10) + 1;
      }
      else {
         this.popupTable.page = 1;
      }

      this.initPopupTableData();
   }

   private initPopupTableData() {
      this.popupTable.pageData = [];
      let pageModifier = (this.popupTable.page - 1) * 10;

      for(let i = 0; i < 10; i++) {
         if(i + pageModifier >= this.popupTable.numRows) {
            return;
         }

         this.popupTable.pageData[i] = this.popupTable.rowData[i + pageModifier];
      }
   }

   updatePopupTablePage($event, page: number) {
      page = Number(page);

      if($event.keyCode && $event.keyCode !== 13) {
         // enter key only
         return;
      }

      $event.preventDefault();
      $event.stopPropagation();

      if(page && page > 0 && page <= this.popupTable.numPages) {
         this.popupTable.page = page;
         this.initPopupTableData();
      }
   }

   isChooseCellEnabled(): boolean {
      return (this.model.table && this.model.table.length > 0) && !this.model.variable;
   }

   loadPopupTable(): void {
      if(!this.isChooseCellEnabled()) {
         return;
      }

      if(this.popupTable && this.popupTable.tableName == this.model.table) {
         this.initPopupTable();
         return;
      }

      this.popupTable = null;
      this.http.get(POPUP_TABLE_URI + encodeURIComponent(this.model.table) + "/" +
                    Tool.encodeURIPath(this.runtimeId))
         .subscribe(
            (data: PopupEmbeddedTable) => {
               this.popupTable = data;
               this.initPopupTable();
            },
            (err) => {
               // TODO handle error loading table data
            }
         );
   }

   getRowIndex(i: number): number {
      return (i + 1) + ((this.popupTable.page - 1) * 10);
   }

   isSelected(row: number, col: number): boolean {
      if(this.model.rowValue && this.getRowIndex(row) == Number(this.model.rowValue) &&
         this.model.columnValue && this.popupTable.columnHeaders[col] == this.model.columnValue)
      {
         return true;
      }

      return false;
   }

   disableRowCol(): boolean {
      return !this.model.table || this.model.table.length == 0 || this.model.variable;
   }

   getIndex(value: string) {
      for(let i = 0; i < this.columns.length; i++) {
         if(this.columns[i].label == value) {
            return i;
         }
      }

      return -1;
   }

   updateSelectedRow(val: string): void {
      this.selectedRow = val;

      if(val != null) {
         let vals: string[] = val.split(":");

         if(vals != null && vals.length > 1) {
            this.model.defaultValue = vals[1].trim();
         }
      }
   }

   getPageLabel(): string {
      return Tool.formatCatalogString("_#(js:nOfTotal)", ["", this.popupTable.numPages]);
   }
}

export interface PopupEmbeddedTable {
   numPages?: number;
   page?: number;
   pageData?: string[][];
   tableName: string;
   numRows: number;
   columnHeaders: string[];
   rowData: string[][];
}
