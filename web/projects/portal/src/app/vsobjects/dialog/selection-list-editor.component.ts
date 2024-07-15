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
import { Component, Input, OnInit } from "@angular/core";
import { XSchema } from "../../common/data/xschema";
import { SelectionListEditorModel } from "../model/selection-list-editor-model";
import { Tool } from "../../../../../shared/util/tool";

const COLUMNS_URI: string = "../api/vs/selectionList/columns/";

@Component({
   selector: "selection-list-editor",
   templateUrl: "selection-list-editor.component.html"
})
export class SelectionListEditor implements OnInit {
   @Input() model: SelectionListEditorModel;
   @Input() runtimeId: string;
   @Input() showApplySelection: boolean = true;
   headers: HttpHeaders;
   localTable: string;
   localColumn: string;
   localValue: string;
   localForm: boolean;
   columns: string[] = [];
   columnTooltips: string[] = [];
   columnDataTypes: string[] = [];

   constructor(private http: HttpClient) {
      this.headers = new HttpHeaders({
         "Content-Type": "application/json"
      });
   }

   ngOnInit(): void {
      this.localForm = !this.model.form;
      this.localTable = this.model.table;
      this.localColumn = this.model.column;
      this.localValue = this.model.value;
      this.updateColumns();
   }

   selectTable(index: number): void {
      if(this.localTable == this.model.tables[index]) {
         return;
      }

      this.localTable = this.model.tables[index];
      this.updateColumns();
   }

   updateColumns(): void {
      if(this.localTable && this.localTable.length > 0) {
         this.http.get(COLUMNS_URI + Tool.encodeURIPath(this.runtimeId) + "/" +
                       encodeURIComponent(this.localTable))
            .subscribe(
               (data: any) => {
                  this.columns = data.columns;
                  this.columnTooltips = data.tooltips;
                  this.columnDataTypes = data.dataTypes;

                  if(this.columns) {
                     if(this.columns.indexOf(this.localColumn) == -1) {
                        this.localColumn = this.columns[0] ? this.columns[0] : "";
                     }

                     if(this.columns.indexOf(this.localValue) == -1) {
                        this.localValue = this.columns[0] ? this.columns[0] : "";
                     }
                  }
                  else {
                     this.localColumn = "";
                     this.localValue = "";
                  }
               },
               (err) => {
                  this.columns = [];
                  this.columnTooltips = [];
                  this.localColumn = "";
                  this.localValue = "";
                  //TODO handle error
               }
            );
      }
      else {
         this.columns = [];
         this.columnTooltips = [];
         this.localColumn = "";
         this.localValue = "";
      }
   }

   public updateModel(): void {
      this.model.form = !this.localForm;
      this.model.table = this.localTable;
      this.model.column = this.localColumn;
      this.model.value = this.localValue;

      if(this.columns.indexOf(this.localValue) == -1) {
         this.model.dataType = XSchema.STRING;
      }
      else {
         this.model.dataType = this.columnDataTypes[this.columns.indexOf(this.localValue)];
      }
   }

   getToolTip(idx: number): string {
      return (this.model.ltableDescriptions && this.model.ltableDescriptions[idx])
         ? this.model.ltableDescriptions[idx] : "";
   }
}
