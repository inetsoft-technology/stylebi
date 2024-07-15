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
import {Component, EventEmitter, Input, OnInit, Output} from "@angular/core";
import { MatLegacyDialog as MatDialog, MatLegacyDialogConfig as MatDialogConfig } from "@angular/material/legacy-dialog";
import { NameLabelTuple } from "../../../../../../../shared/util/name-label-tuple";
import { ComponentTool } from "../../../../common/util/component-tool";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { MaterializedModel } from "../../../../../../../shared/util/model/mv/materialized-model";
import { CreateUpdateMvRequest } from "../../../../../../../shared/util/model/mv/create-update-mv-request";

@Component({
   selector: "create-mv-pane",
   templateUrl: "./create-mv-pane.component.html",
   styleUrls: ["./create-mv-pane.component.scss"]
})
export class CreateMVPane implements OnInit {
   @Input() models: MaterializedModel[] = [];
   @Input() cycles: NameLabelTuple[] = [];
   @Input() mvCycle = "";
   @Input() runInBackground: boolean = false;
   @Output() hideExist = new EventEmitter<boolean>();
   @Output() hideData = new EventEmitter<boolean>();
   @Output() create = new EventEmitter<CreateUpdateMvRequest>();
   @Output() showPlan = new EventEmitter<CreateUpdateMvRequest>();
   @Output() setCycle = new EventEmitter<CreateUpdateMvRequest>();
   @Output() selectedMVsChanged = new EventEmitter<string[]>();
   selectedMVs: string[] = [];
   generateData: boolean = true;
   hideMV: boolean = false;
   hideMVData: boolean = false;
   sortType: any;
   tableHeaders: NameLabelTuple[] = [
      {name: "sheets", label: "_#(js:Asset)"},
      {name: "table", label: "_#(js:Table)"},
      {name: "users", label: "_#(js:Users)"},
      {name: "exists", label: "_#(js:Exists)"},
      {name: "hasData", label: "_#(js:Has Data)"},
      {name: "lastModifiedTime", label: "_#(js:Last Modified Time)"},
      {name: "cycle", label: "_#(js:Cycle)"}
   ];

   constructor(private modalService: NgbModal) { }

   ngOnInit() {
      this.selectedMVs = this.models.map(model => model.name);
      this.selectedMVsChanged.emit(this.selectedMVs);
   }

   isModelSelected(model): boolean {
      return this.selectedMVs.includes(model.name);
   }

   selectionChanged(select: boolean, model: MaterializedModel) {
      if(select && !this.isModelSelected(model)) {
         this.selectedMVs.push(model.name);
      }
      else if(!select && this.isModelSelected(model)) {
         this.selectedMVs.splice(this.selectedMVs.indexOf(model.name), 1);
      }

      this.selectedMVsChanged.emit(this.selectedMVs);
   }

   isModelAllSelected(): boolean {
      return !this.models.some(m => !this.selectedMVs.includes(m.name));
   }

   selectionAll(select: boolean) {
      if(select) {
         this.selectedMVs = this.models.map(model => model.name);
      }
      else {
         this.selectedMVs = [];
      }

      this.selectedMVsChanged.emit(this.selectedMVs);
   }

   cycleChange() {
      if(!this.selectedMVs || this.selectedMVs.length == 0) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:select.materialized.view)");
         return;
      }

      this.setCycle.emit(<CreateUpdateMvRequest>{
         mvNames: this.selectedMVs,
         cycle: this.mvCycle
      });
   }

   createOrUpdate() {
      if(!this.selectedMVs || this.selectedMVs.length == 0) {
         ComponentTool.showMessageDialog(this.modalService, "_#(js:Error)",
            "_#(js:select.materialized.view)");
         return;
      }

      this.create.emit(<CreateUpdateMvRequest>{
         mvNames: this.selectedMVs,
         noData: !this.generateData,
         runInBackground: this.runInBackground,
         cycle: this.mvCycle});
   }

   showPlanClicked() {
      this.showPlan.emit(<CreateUpdateMvRequest>{
         mvNames: this.selectedMVs
      });
   }

   changeHideExistingMV(checked: boolean) {
      this.hideMV = checked;
      this.hideMVData = this.hideMV ? false : this.hideMVData;
      this.hideExist.emit(this.hideMV);
   }

   changeHideExistingMVWithData(checked: boolean) {
      this.hideMVData = checked;
      this.hideMV = this.hideMVData ? false : this.hideMV;
      this.hideData.emit(this.hideMVData);
   }

   runInBackgroundChanged(checked: boolean) {
      if(checked) {
         this.generateData = true;
      }
   }

   generateDataChanged(checked: boolean) {
      if(!checked) {
         this.runInBackground = false;
      }
   }

   changeSortType(sortType: any) {
      this.sortType = sortType;
   }
}
