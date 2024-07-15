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
import { Component, Input, OnInit } from "@angular/core";
import { RepositoryEntryType } from "../../../../../../../shared/data/repository-entry-type.enum";
import { MaterializedModel } from "../../../../../../../shared/util/model/mv/materialized-model";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { AnalyzeMVModel } from "../../../data/model/analyze-mv-model";
import { MVTreeModel } from "../../../data/model/mv-tree-model";

@Component({
   selector: "analyze-mv-pane",
   templateUrl: "./analyze-mv-pane.component.html",
   styleUrls: ["./analyze-mv-pane.component.scss"]
})
export class AnalyzeMVPane implements OnInit {
   @Input() selectedNodes: MVTreeModel[] = [];
   @Input() analyzeMVModel: AnalyzeMVModel;
   @Input() securityEnabled = false;
   @Input() models: MaterializedModel[] = [];
   selectedMVs: MaterializedModel[] = [];
   sortType: any;
   tableHeaders: NameLabelTuple[] = [
      {name: "sheets", label: "_#(js:Asset)"},
      {name: "table", label: "_#(js:Table)"},
      {name: "users", label: "_#(js:Users)"},
      {name: "existString", label: "_#(js:Exists)"},
      {name: "dataString", label: "_#(js:Has Data)"},
      {name: "lastModifiedTime", label: "_#(js:Last Modified Time)"},
      {name: "cycle", label: "_#(js:Cycle)"}
   ];

   constructor() {
      this.selectedMVs = this.models.slice();
   }

   ngOnInit() {
   }

   selectionAll(select: boolean) {
      if(select) {
         this.selectedMVs = this.models.slice();
      }
      else {
         this.selectedMVs = [];
      }
   }

   selectionChanged(select: boolean, model: MaterializedModel) {
      if(select && !this.isModelSelected(model)) {
         this.selectedMVs.push(model);
      }
      else if(!select && this.isModelSelected(model)) {
         this.selectedMVs.splice(this.selectedMVs.indexOf(model), 1);
      }
   }

   isModelAllSelected(): boolean {
      return this.models && this.models.length > 0
         && !this.models.some(m => !this.selectedMVs.includes(m));
   }

   isModelSelected(model): boolean {
      return this.selectedMVs.includes(model);
   }

   changeSortType(sortType: any) {
      this.sortType = sortType;
   }

   isFullDataVisible(): boolean {
      return this.selectedNodes != null &&
         !!this.selectedNodes.find(node => node.type === RepositoryEntryType.VIEWSHEET);
   }
}
