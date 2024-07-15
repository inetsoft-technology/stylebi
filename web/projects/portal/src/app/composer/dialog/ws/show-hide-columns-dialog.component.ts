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
import { HttpParams } from "@angular/common/http";
import { Component, EventEmitter, Input, OnInit, Output } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { Tool } from "../../../../../../shared/util/tool";
import { ColumnRef } from "../../../binding/data/column-ref";
import { ModelService } from "../../../widget/services/model.service";
import { ShowHideColumnsDialogModel } from "../../data/vs/show-hide-columns-dialog-model";
import { WSSetColumnVisibilityEvent } from "../../gui/ws/socket/ws-set-column-visibility-event";
import { ComponentTool } from "../../../common/util/component-tool";
import { AbstractTableAssembly } from "../../data/ws/abstract-table-assembly";

const SHOW_HIDE_DIALOG_MODEL_URI: string = "../api/composer/ws/dialog/show-hide-columns-dialog-model/";
const CONTROLLER_SET_COLUMN_VISIBILITY = "/events/composer/worksheet/set-column-visibility";

export interface SearchResult {
   column: ColumnRef;
   index: number;
}

@Component({
   selector: "show-hide-columns-dialog",
   templateUrl: "./show-hide-columns-dialog.component.html",
   styleUrls: ["./show-hide-columns-dialog.component.scss"]
})
export class ShowHideColumnsDialogComponent implements OnInit {
   @Input() table: AbstractTableAssembly;
   @Input() runtimeId: string;
   @Input() showColumnName: boolean;
   @Output() onCommit: EventEmitter<any> = new EventEmitter<any>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   model: ShowHideColumnsDialogModel;
   origModel: ShowHideColumnsDialogModel;
   searchResults: SearchResult[] = [];

   constructor(private modelService: ModelService,
               private ngbModal: NgbModal)
   {
   }

   ngOnInit(): void {
      const params = new HttpParams().set("table", Tool.byteEncode(this.tableName));
      const uri = SHOW_HIDE_DIALOG_MODEL_URI + Tool.byteEncode(this.runtimeId);

      this.modelService.getModel(uri, params).subscribe(
         (data) => {
            this.model = <ShowHideColumnsDialogModel> data;
            this.origModel = Tool.clone(this.model);
            this.initSearchResults();
         },
         () => {
            console.error("Could not fetch visibility information for " + this.tableName);
            this.cancel();
         }
      );
   }

   get tableName(): string {
      return this.table.name;
   }

   initSearchResults(): void {
      if(!!this.model) {
         this.searchResults = [];

         for(let i = 0; i < this.model.columns.length; i++) {
            this.searchResults.push({column: this.model.columns[i], index: i});
         }
      }
   }

   showHideAll(visible: boolean): void {
      this.searchResults.forEach(result => {
         this.model.columns[result.index].visible = visible;
      });
   }

   getTooltip(col: any): string {
      return ColumnRef.getTooltip(col);
   }

   changeColVisibility(col: SearchResult): void {
      this.model.columns[col.index].visible = !this.model.columns[col.index].visible;
   }

   search(searchStr: string): void {
      if(!!searchStr) {
         this.searchResults = [];

         for(let i = 0; i < this.model.columns.length; i++) {
            let col = this.model.columns[i];

            if(!!col.alias && col.alias.toLowerCase().includes(searchStr.toLowerCase()) ||
               !!col.attribute && col.attribute.toLowerCase().includes(searchStr.toLowerCase()))
            {
               this.searchResults.push({column: col, index: i});
            }
         }
      }
      else {
         this.initSearchResults();
      }
   }

   getColumn(column: ColumnRef): string {
      if(this.showColumnName && !!column.name) {
         return ColumnRef.getCaption(column);
      }
      else if(column.alias) {
         return column.alias;
      }

      return column.attribute;
   }

   getChangedColumns(): string[] {
      const columns: string[] = [];

      if(this.origModel && this.model) {
         for(let i = 0; i < this.origModel.columns.length; i++) {
            const origCol =  this.origModel.columns[i];
            const col =  this.model.columns[i];

            if(origCol.visible != col.visible) {
               columns.push(col.name);
            }
         }
      }

      return columns;
   }

   invisibleAllColumns(): boolean {
      return !this.model.columns.some(col => col.visible);
   }

   isValid(): boolean {
      return !!this.model && this.model.columns.length > 0;
   }

   ok() {
      const changedCols = this.getChangedColumns();

      if((this.table.isEmbeddedTable() || this.table.isMirrorTable()) && (this.invisibleAllColumns() && !this.table.crosstab)) {
         ComponentTool.showMessageDialog(this.ngbModal, "_#(js:Warning)",
            "_#(js:composer.ws.embeddedTable.noVisibleColumn)");
         this.cancel();
         return;
      }

      if(changedCols.length > 0) {
         const event = new WSSetColumnVisibilityEvent();
         event.setAssemblyName(this.tableName);
         event.setColumnName(changedCols);

         this.onCommit.emit({
            model: event,
            controller: CONTROLLER_SET_COLUMN_VISIBILITY
         });
      }
      else {
         this.cancel();
      }
   }

   cancel() {
      this.onCancel.emit("cancel");
   }
}
