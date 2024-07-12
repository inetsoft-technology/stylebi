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
import { HttpClient } from "@angular/common/http";
import { Component, Input, OnInit, ViewChild } from "@angular/core";
import { NgbModal } from "@ng-bootstrap/ng-bootstrap";
import { ComponentTool } from "../../../../../../../common/util/component-tool";
import {
   CONDITION_INSERT_MESSAGE,
   CONDITION_MODIFIED_MESSAGE
} from "../../../../../../../widget/condition/condition-dialog.service";
import {
   VPMConditionPane
} from "../../../../../../dialog/vpm-condition-dialog/vpm-condition-pane/vpm-condition-pane.component";
import {
   QueryConditionPaneModel
} from "../../../../../model/datasources/database/query/query-condition-pane-model";
import {
   ClauseModel
} from "../../../../../model/datasources/database/vpm/condition/clause/clause-model";
import {
   OperationModel
} from "../../../../../model/datasources/database/vpm/condition/clause/operation-model";
import {
   DataConditionItemPaneProvider
} from "../../../../../model/datasources/database/vpm/condition/clause/data-condition-item-pane-provider";
import {
   ConditionItemModel
} from "../../../../../model/datasources/database/vpm/condition/condition-item-model";
import {
   isValidCondition,
   isValidConditionList
} from "../../../../../model/datasources/database/vpm/condition/util/vpm-condition.util";

@Component({
   selector: "query-conditions-pane",
   templateUrl: "./query-conditions-pane.component.html",
   styleUrls: ["./query-conditions-pane.component.scss"]
})
export class QueryConditionsPaneComponent implements OnInit {
   @Input() model: QueryConditionPaneModel;
   @Input() runtimeId: string;
   @Input() databaseName: string;
   @Input() operations: OperationModel[];
   @Input() sessionOperations: OperationModel[];
   @Input() havingCondition: boolean = false;
   @ViewChild("conditionPane") conditionPane: VPMConditionPane;
   provider: DataConditionItemPaneProvider;
   conditionListValid: boolean = true;
   private dirtyCondition: ConditionItemModel;

   constructor(private http: HttpClient,
               private modalService: NgbModal)
   {
   }

   ngOnInit() {
      this.provider = new DataConditionItemPaneProvider(this.http, this.databaseName,
         null, this.operations, this.sessionOperations, this.runtimeId, true);
   }

   /**
    * Called when condition list is changed. Update the models condition list and validity.
    * @param conditionList
    */
   conditionListChanged(conditionList: ConditionItemModel[]) {
      this.model.conditions = conditionList;
      this.updateConditionListValidity();
      this.dirtyCondition = null;
   }

   updateConditionListValidity() {
      this.conditionListValid = isValidConditionList(this.model.conditions);
   }

   conditionChanged(value: ConditionItemModel): void {
      this.dirtyCondition = value;
   }

   public checkDirtyConditions(): Promise<void> {
      let valid: boolean =
         !!this.dirtyCondition && isValidCondition(<ClauseModel> this.dirtyCondition);

      if(valid) {
         return Promise.resolve(ComponentTool.showConfirmDialog(
            this.modalService,
            "_#(js:data.vpm.confirmDialogTitle)",
            this.dirtyConditionPromptMessage(),
            {yes: "_#(js:Yes)", no: "_#(js:No)"})
            .then((result: string) => {
               if(result === "yes") {
                  this.conditionPane.processDirtyCondition();
               }

               this.dirtyCondition = null;
            }));
      }
      else {
         return Promise.resolve();
      }
   }

   dirtyConditionPromptMessage(): string {
      return this.conditionPane.selectedIndex == null || this.conditionPane.selectedIndex < 0 ?
         CONDITION_INSERT_MESSAGE : CONDITION_MODIFIED_MESSAGE;
   }
}
