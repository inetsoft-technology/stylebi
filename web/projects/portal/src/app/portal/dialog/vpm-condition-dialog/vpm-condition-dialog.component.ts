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
import { Component, Output, EventEmitter, OnInit, Input } from "@angular/core";
import { VPMConditionDialogModel } from "../../data/model/datasources/database/vpm/condition/vpm-condition-dialog-model";
import { ConditionItemModel } from "../../data/model/datasources/database/vpm/condition/condition-item-model";
import { DataConditionItemPaneProvider } from "../../data/model/datasources/database/vpm/condition/clause/data-condition-item-pane-provider";
import { HttpClient } from "@angular/common/http";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { isValidConditionList } from "../../data/model/datasources/database/vpm/condition/util/vpm-condition.util";
import { ComponentTool } from "../../../common/util/component-tool";

@Component({
   selector: "vpm-condition-dialog",
   templateUrl: "vpm-condition-dialog.component.html"
})
export class VPMConditionDialog implements OnInit {
   @Input() model: VPMConditionDialogModel;
   @Input() runtimeId: string; // runtime query id
   @Input() isWSQuery: boolean = false; // whether is ws sql bound table
   @Input() subQuery: boolean = false; // whether is subquery
   @Output() onCommit: EventEmitter<ConditionItemModel[]> = new EventEmitter<ConditionItemModel[]>();
   @Output() onCancel: EventEmitter<string> = new EventEmitter<string>();
   provider: DataConditionItemPaneProvider;
   conditionListValid: boolean = true;
   private dirtyCondition: ConditionItemModel;

   constructor(private httpClient: HttpClient,
               private modalService: NgbModal)
   {
   }

   ngOnInit(): void {
      this.updateConditionListValidity();
      this.provider = new DataConditionItemPaneProvider(this.httpClient, this.model.databaseName,
         this.model.partition ? this.model.tableName : null, this.model.operations,
         this.model.sessionOperations, this.runtimeId, this.isWSQuery, this.subQuery);
   }

   /**
    * Called when condition list is changed. Update the models condition list and validity.
    * @param conditionList
    */
   conditionListChanged(conditionList: ConditionItemModel[]) {
      this.model.conditionList = conditionList;
      this.updateConditionListValidity();
      this.dirtyCondition = null;
   }

   conditionChanged(value: ConditionItemModel) {
      this.dirtyCondition = value;
   }

   /**
    * Check and update condition list validity.
    */
   private updateConditionListValidity() {
      this.conditionListValid = isValidConditionList(this.model.conditionList);
   }

   /**
    * Called when user clicks ok on dialog. Return the updated condition list.
    */
   ok(): void {
      this.checkDirtyConditions().then((confirm: boolean) => {
         if(confirm) {
            this.onCommit.emit(this.model.conditionList);
         }
      });
   }

   /**
    * Called when user clicks cancel on dialog. Close dialog.
    */
   cancel(): void {
      this.onCancel.emit("cancel");
   }

   private checkDirtyConditions(): Promise<boolean> {
      if(this.dirtyCondition) {
         return Promise.resolve(ComponentTool.showConfirmDialog(
            this.modalService,
            "_#(js:data.vpm.confirmDialogTitle)",
            "_#(js:data.vpm.confirmSubmit)")
            .then((result: string) => {
               this.dirtyCondition = null;
               return result === "ok";
            }));
      }
      else {
         return Promise.resolve(true);
      }
   }
}
