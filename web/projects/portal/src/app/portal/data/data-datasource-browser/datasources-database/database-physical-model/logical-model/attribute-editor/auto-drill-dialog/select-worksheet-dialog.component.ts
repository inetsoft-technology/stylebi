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
import { Component, ElementRef, EventEmitter, Input, Output, ViewChild } from "@angular/core";
import { HttpClient, HttpParams } from "@angular/common/http";
import { AssetType } from "../../../../../../../../../../../shared/data/asset-type";
import { DrillSubQueryModel } from "../../../../../../model/datasources/database/physical-model/logical-model/drill-sub-query-model";
import { TreeNodeModel } from "../../../../../../../../widget/tree/tree-node-model";
import { AssetEntry } from "../../../../../../../../../../../shared/data/asset-entry";
import {
   AutoDrillWorksheetParameters
} from "../../../../../../model/datasources/database/physical-model/logical-model/auto-drill-worksheet-parameters";
import { SelectedItem } from "../../logical-model.component";
import {
   EntityModel
} from "../../../../../../model/datasources/database/physical-model/logical-model/entity-model";
import {
   QueryFieldModel
} from "../../../../../../model/datasources/database/query/query-field-model";

const LOAD_PARMS_URL = "../api/portal/data/autodrill/worksheet/params";

@Component({
   selector: "select-worksheet-dialog",
   templateUrl: "select-worksheet-dialog.component.html",
   styleUrls: ["select-worksheet-dialog.component.scss"]
})
export class SelectWorksheetDialog {
   @Input() entities: EntityModel[];
   @Input() fields: QueryFieldModel[];
   @Output() onCommit = new EventEmitter<DrillSubQueryModel>();
   @Output() onCancel = new EventEmitter();
   @ViewChild("dropdownBody") dropdownBody: ElementRef;
   _selectedSubQuery: DrillSubQueryModel;
   selectedEntry: AssetEntry;
   queryParams: string[];
   closeMenu: boolean;

   @Input() set selectedSubQuery(model: DrillSubQueryModel) {
      this._selectedSubQuery = model;
      this.selectedEntry = model?.entry;
   }

   get selectedSubQuery(): DrillSubQueryModel {
      return this._selectedSubQuery;
   }

   constructor(public http: HttpClient) {
   }

   selectWorksheet(node: TreeNodeModel) {
      this.selectedEntry = node?.data;

      if(this.selectedSubQuery == null) {
         this.selectedSubQuery = {entry: this.selectedEntry};
      }
      else {
         this.selectedSubQuery.entry = this.selectedEntry;
      }

      this.loadQueryParameters(this.selectedEntry);
   }

   loadQueryParameters(entry: AssetEntry) {
      if(entry.type === AssetType.WORKSHEET) {
         const params: HttpParams = new HttpParams()
            .set("wsIdentifier", entry.identifier);

         this.http.get<AutoDrillWorksheetParameters>(LOAD_PARMS_URL, {params})
            .subscribe((res: AutoDrillWorksheetParameters) => {
               this.queryParams = res?.queryParams;
            });
      }
   }

   getSelectedParamVal(param: string): string {
      if(this.selectedSubQuery == null || !this.selectedSubQuery.params ||
            this.selectedSubQuery.params.length == 0 || !param)
      {
         return null;
      }

      let index = this.selectedSubQuery.params.findIndex(p => p.key == param);

      if(index == -1) {
         return null;
      }

      return this.selectedSubQuery.params[index].value;
   }

   getSelectedInfo(param: string): {expanded: EntityModel[], selectedItem: SelectedItem} {
      let info: any = {};
      let label = this.getSelectedParamVal(param);

      if(!label || label.indexOf(".") == -1) {
         info.selectedItem = {entity: -1, attribute: -1};
         info.expanded = [];

         return info;
      }

      let entityName: string = label.substring(0, label.lastIndexOf("."));
      let attribute: string = label.substring(label.lastIndexOf(".") + 1);

      if(!this.entities) {
         info.selectedItem = {entity: -1, attribute: -1};
         info.expanded = [];

         return info;
      }

      for(let i = 0; i < this.entities.length; i++) {
         if(this.entities[i].name == entityName) {
            let entity = this.entities[i];
            info.selectedItem = {entity: i, attribute: -1};
            info.expanded = [entity];

            for(let j = 0; j < entity.attributes.length; j++) {
               if(entity.attributes[j].name == attribute) {
                  info.selectedItem.attribute = j;
                  return info;
               }
            }
         }
      }

      return info;
   }

   get dropdownMinWidth(): number {
      return this.dropdownBody && this.dropdownBody.nativeElement
         ? this.dropdownBody.nativeElement.offsetWidth : null;
   }

   selectItem(field: string, paramName: string) {
      if(!field) {
         this.closeMenu = false;
         return;
      }

      if(!this.selectedSubQuery.params) {
         this.selectedSubQuery.params = [{key: paramName, value: field}];
      }
      else {
         let index = this.selectedSubQuery.params.findIndex(p => p.key == paramName);

         if(index == -1) {
            this.selectedSubQuery.params.push({key: paramName, value: field});
         }
         else {
            this.selectedSubQuery.params[index] = {key: paramName, value: field};
         }
      }

      this.closeMenu = true;
   }

   ok() {
      this.onCommit.emit(this.selectedSubQuery);
   }

   clear() {
      this.selectedSubQuery = null;
      this.selectedEntry = null;
   }

   cancel() {
      this.onCancel.emit();
   }

   okDisabled(): boolean {
      return !!this.selectedEntry && this.selectedEntry.type != AssetType.WORKSHEET;
   }
}
